import React, { useMemo, useState } from 'react'
import { HeatmapCanvas } from './HeatmapCanvas'

type Row = { grid_x: number; grid_y: number; clicks: number }

const REGION = import.meta.env.VITE_AWS_REGION || 'ap-northeast-2'
const WORKGROUP = import.meta.env.VITE_ATHENA_WORKGROUP || 'primary'
const DATABASE = import.meta.env.VITE_GLUE_DATABASE || ''
const TABLE = import.meta.env.VITE_GLUE_TABLE || ''
const API_BASE = import.meta.env.VITE_API_BASE_URL || ''

function apiUrl(path: string) {
    if (!API_BASE) return path
    if (API_BASE.endsWith('/') && path.startsWith('/')) return `${API_BASE.slice(0, -1)}${path}`
    if (!API_BASE.endsWith('/') && !path.startsWith('/')) return `${API_BASE}/${path}`
    return `${API_BASE}${path}`
}

function defaultQuery(db: string, table: string) {
    return `
SELECT
  grid_x,
  grid_y,
  SUM(clicks) AS clicks
FROM "${db}"."${table}"
WHERE from_unixtime(window_end / 1000) >= date_add('hour', -1, current_timestamp)
GROUP BY 1,2
ORDER BY clicks DESC;
`.trim()
}

export default function App() {
    const [region, setRegion] = useState(REGION)
    const [workgroup, setWorkgroup] = useState(WORKGROUP)
    const [database, setDatabase] = useState(DATABASE)
    const [table, setTable] = useState(TABLE)
    const gridSize = 20
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
        setStatus('Sending query...')
        setRows([])
        setQueryId('')

        if (!database || !table) {
            setError('Set database + table first (from Glue/Athena).')
            setStatus('Error')
            return
        }

        const res = await fetch(apiUrl('/api/query'), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                query,
                region,
                workgroup,
                database,
                table,
            }),
        })

        const raw = await res.text()
        let payload: any = {}
        if (raw) {
            try {
                payload = JSON.parse(raw)
            } catch (e) {
                throw new Error(`Backend returned non-JSON response (status ${res.status})`)
            }
        }
        if (!res.ok) {
            throw new Error(payload?.error || `Backend error (status ${res.status})`)
        }

        setQueryId(payload.queryId || '')
        setRows(payload.rows || [])
        setStatus(`Done. Rows=${payload.rows?.length || 0}`)
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
                        <div style={{ fontSize: 18, fontWeight: 'bold' }}>Athena Heatmap Viewer</div>
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
                            Grid: {gridSize}×{gridSize}, Rows used: {rows.length}
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
