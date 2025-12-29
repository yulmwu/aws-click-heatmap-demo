import React, { useMemo, useState } from 'react'
import Papa from 'papaparse'
import { StartQueryExecutionCommand, GetQueryExecutionCommand } from '@aws-sdk/client-athena'
import { GetObjectCommand } from '@aws-sdk/client-s3'
import { makeAthenaClient, makeS3Client } from './aws'
import { bodyToString, parseS3Uri } from './utils'
import { HeatmapCanvas } from './HeatmapCanvas'

type Row = { grid_x: number; grid_y: number; clicks: number }

const REGION = import.meta.env.VITE_AWS_REGION || 'ap-northeast-2'
const WORKGROUP = import.meta.env.VITE_ATHENA_WORKGROUP || 'primary'
const DATABASE = import.meta.env.VITE_GLUE_DATABASE || ''
const TABLE = import.meta.env.VITE_GLUE_TABLE || ''

const accessKeyId = import.meta.env.VITE_AWS_ACCESS_KEY_ID || ''
const secretAccessKey = import.meta.env.VITE_AWS_SECRET_ACCESS_KEY || ''
const sessionToken = import.meta.env.VITE_AWS_SESSION_TOKEN || ''

function credsOk() {
    return !!(accessKeyId && secretAccessKey)
}

function defaultQuery(db: string, table: string) {
    return `
SELECT
  CAST(gridx AS INTEGER) AS grid_x,
  CAST(gridy AS INTEGER) AS grid_y,
  SUM(CAST(clicks AS BIGINT)) AS clicks
FROM "${db}"."${table}"
WHERE from_unixtime(CAST(windowend AS BIGINT) / 1000) >= date_add('hour', -1, current_timestamp)
GROUP BY 1,2
ORDER BY clicks DESC
LIMIT 2000;
`.trim()
}

export default function App() {
    const [region, setRegion] = useState(REGION)
    const [workgroup, setWorkgroup] = useState(WORKGROUP)
    const [database, setDatabase] = useState(DATABASE)
    const [table, setTable] = useState(TABLE)
    const [gridSize, setGridSize] = useState(20)
    const [query, setQuery] = useState(defaultQuery(DATABASE || 'heatmap_demo', TABLE || 'curated_heatmap'))

    const [status, setStatus] = useState('Idle')
    const [error, setError] = useState('')
    const [queryId, setQueryId] = useState('')
    const [rows, setRows] = useState<Row[]>([])

    const cells = useMemo(() => {
        const m = Array.from({ length: gridSize }, () => Array.from({ length: gridSize }, () => 0))
        for (const r of rows) {
            if (r.grid_x >= 0 && r.grid_x < gridSize && r.grid_y >= 0 && r.grid_y < gridSize) {
                m[r.grid_y][r.grid_x] += r.clicks
            }
        }
        return m
    }, [rows, gridSize])

    async function run() {
        setError('')
        setStatus('Starting query...')
        setRows([])
        setQueryId('')

        if (!credsOk()) {
            setError('Missing AWS credentials (.env).')
            setStatus('Error')
            return
        }
        if (!database || !table) {
            setError('Set database + table first (from Glue/Athena).')
            setStatus('Error')
            return
        }

        const creds = { accessKeyId, secretAccessKey, sessionToken: sessionToken || undefined }
        const athena = makeAthenaClient(region, creds)
        const s3 = makeS3Client(region, creds)

        const start = await athena.send(
            new StartQueryExecutionCommand({
                QueryString: query,
                WorkGroup: workgroup,
                QueryExecutionContext: { Database: database },
            })
        )

        const qid = start.QueryExecutionId
        if (!qid) throw new Error('No QueryExecutionId returned')
        setQueryId(qid)

        let outputLocation = ''
        for (let i = 0; i < 60; i++) {
            setStatus(`Polling Athena... (${i + 1}/60)`)
            await new Promise((r) => setTimeout(r, 2000))

            const res = await athena.send(new GetQueryExecutionCommand({ QueryExecutionId: qid }))
            const st = res.QueryExecution?.Status?.State
            const reason = res.QueryExecution?.Status?.StateChangeReason
            outputLocation = res.QueryExecution?.ResultConfiguration?.OutputLocation || ''

            if (st === 'SUCCEEDED') {
                setStatus('SUCCEEDED. Downloading result...')
                break
            }
            if (st === 'FAILED' || st === 'CANCELLED') {
                throw new Error(`Athena ${st}: ${reason || 'unknown reason'}`)
            }
        }

        if (!outputLocation) {
            throw new Error('No OutputLocation. Ensure Athena workgroup has results bucket configured.')
        }

        const { bucket, key } = parseS3Uri(outputLocation)
        const obj = await s3.send(new GetObjectCommand({ Bucket: bucket, Key: key }))
        const csv = await bodyToString(obj.Body)

        const parsed = Papa.parse(csv, { header: true, skipEmptyLines: true })
        const out: Row[] = []
        for (const r of parsed.data as any[]) {
            const gx = Number(r.grid_x ?? r.gridx ?? r.gridx)
            const gy = Number(r.grid_y ?? r.gridy ?? r.gridy)
            const clicks = Number(r.clicks ?? r.sum ?? r.count)
            if (Number.isFinite(gx) && Number.isFinite(gy) && Number.isFinite(clicks)) {
                out.push({ grid_x: gx, grid_y: gy, clicks })
            }
        }

        setRows(out)
        setStatus(`Done. Rows=${out.length}`)
    }

    return (
        <div
            style={{
                fontFamily: 'Arial, sans-serif',
                background: '#fff',
                minHeight: '100vh',
                color: '#333',
            }}
        >
            <div style={{ maxWidth: 1200, margin: '0 auto', padding: 20 }}>
                <div
                    style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 15 }}
                >
                    <div>
                        <div style={{ fontSize: 18, fontWeight: 'bold' }}>Athena Heatmap Viewer (Last 1 hour)</div>
                        <div style={{ fontSize: 13, color: '#666' }}>Athena query → S3 CSV → render heatmap.</div>
                    </div>
                    <button
                        onClick={() =>
                            run().catch((e) => {
                                setError(String(e?.message || e))
                                setStatus('Error')
                            })
                        }
                        style={{
                            background: '#28a745',
                            color: '#fff',
                            border: 'none',
                            padding: '10px 15px',
                            cursor: 'pointer',
                        }}
                    >
                        Run query
                    </button>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 15 }}>
                    <div
                        style={{
                            background: '#f9f9f9',
                            border: '1px solid #ddd',
                            padding: 15,
                        }}
                    >
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                            <Field label='Region' value={region} onChange={setRegion} />
                            <Field label='Workgroup' value={workgroup} onChange={setWorkgroup} />
                            <Field label='Database' value={database} onChange={setDatabase} />
                            <Field label='Table' value={table} onChange={setTable} />
                            <Field
                                label='Grid Size'
                                value={String(gridSize)}
                                onChange={(v) => setGridSize(Math.max(5, Math.min(200, Number(v) || 20)))}
                            />
                            <Field label='Status' value={status} disabled />
                        </div>

                        {queryId && (
                            <div
                                style={{
                                    marginTop: 10,
                                    fontFamily: 'monospace',
                                    fontSize: 12,
                                    color: '#666',
                                }}
                            >
                                QueryExecutionId: {queryId}
                            </div>
                        )}

                        {error && (
                            <div
                                style={{
                                    marginTop: 10,
                                    padding: 10,
                                    background: '#ffe6e6',
                                    border: '1px solid #ffcccc',
                                    color: '#c00',
                                }}
                            >
                                {error}
                            </div>
                        )}

                        <div style={{ marginTop: 12 }}>
                            <label style={{ fontSize: 12, color: '#555' }}>Athena SQL</label>
                            <textarea
                                value={query}
                                onChange={(e) => setQuery(e.target.value)}
                                style={{
                                    width: '100%',
                                    height: 240,
                                    marginTop: 6,
                                    padding: 8,
                                    border: '1px solid #ccc',
                                    background: '#fff',
                                    color: '#333',
                                    fontFamily: 'monospace',
                                    fontSize: 12,
                                    boxSizing: 'border-box',
                                }}
                            />
                            <div style={{ marginTop: 8, fontSize: 12, color: '#666' }}>
                                If your schema differs, edit the SQL. Time filter assumes <code>windowend</code> is
                                epoch ms.
                            </div>
                        </div>
                    </div>

                    <div
                        style={{
                            background: '#f9f9f9',
                            border: '1px solid #ddd',
                            padding: 15,
                        }}
                    >
                        <HeatmapCanvas gridSize={gridSize} cells={cells} />
                        <div style={{ marginTop: 10, fontSize: 12, color: '#666' }}>
                            Rendered grid: {gridSize}×{gridSize} · Rows used: {rows.length}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    )
}

function Field(props: { label: string; value: string; onChange?: (v: string) => void; disabled?: boolean }) {
    return (
        <div>
            <label style={{ fontSize: 12, color: '#555', display: 'block', marginBottom: 5 }}>{props.label}</label>
            <input
                value={props.value}
                disabled={props.disabled}
                onChange={(e) => props.onChange?.(e.target.value)}
                style={{
                    width: '100%',
                    padding: '8px',
                    border: '1px solid #ccc',
                    background: props.disabled ? '#eee' : '#fff',
                    color: '#333',
                    boxSizing: 'border-box',
                }}
            />
        </div>
    )
}
