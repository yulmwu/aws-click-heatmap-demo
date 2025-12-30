import http from 'node:http'
import { URL } from 'node:url'
import fs from 'node:fs'
import path from 'node:path'
import { AthenaClient, StartQueryExecutionCommand, GetQueryExecutionCommand } from '@aws-sdk/client-athena'
import { GetObjectCommand, S3Client } from '@aws-sdk/client-s3'
import Papa from 'papaparse'

const PORT = Number(process.env.PORT || 8787)
const DIST_DIR = path.resolve(process.cwd(), 'dist')
const INDEX_FILE = path.join(DIST_DIR, 'index.html')

loadDotEnv()

function loadDotEnv() {
    const envPath = path.resolve(process.cwd(), '.env')
    if (!fs.existsSync(envPath)) return
    const lines = fs.readFileSync(envPath, 'utf-8').split(/\r?\n/)
    for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed || trimmed.startsWith('#')) continue
        const idx = trimmed.indexOf('=')
        if (idx === -1) continue
        const key = trimmed.slice(0, idx).trim()
        let value = trimmed.slice(idx + 1).trim()
        if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.slice(1, -1)
        }
        if (!process.env[key]) process.env[key] = value
    }
}

function resolveEnv(key, fallbackKey) {
    return process.env[key] || (fallbackKey ? process.env[fallbackKey] : '')
}

function resolveCreds() {
    const accessKeyId = resolveEnv('AWS_ACCESS_KEY_ID', 'VITE_AWS_ACCESS_KEY_ID')
    const secretAccessKey = resolveEnv('AWS_SECRET_ACCESS_KEY', 'VITE_AWS_SECRET_ACCESS_KEY')
    const sessionToken = resolveEnv('AWS_SESSION_TOKEN', 'VITE_AWS_SESSION_TOKEN')
    if (accessKeyId && secretAccessKey) {
        return { accessKeyId, secretAccessKey, sessionToken: sessionToken || undefined }
    }
    return undefined
}

function defaultQuery(db, table) {
    return `
SELECT
  grid_x,
  grid_y,
  SUM(clicks) AS clicks
FROM "${db}"."${table}"
WHERE from_unixtime(window_end / 1000) >= date_add('hour', -1, current_timestamp)
GROUP BY 1,2
ORDER BY clicks DESC
LIMIT 2000;
`.trim()
}

async function readJson(req) {
    const chunks = []
    for await (const chunk of req) chunks.push(chunk)
    if (!chunks.length) return {}
    const raw = Buffer.concat(chunks).toString('utf-8')
    return JSON.parse(raw)
}

async function bodyToString(body) {
    if (!body) return ''
    if (typeof body === 'string') return body
    const chunks = []
    for await (const chunk of body) chunks.push(chunk)
    return Buffer.concat(chunks).toString('utf-8')
}

function parseS3Uri(uri) {
    const cleaned = uri.replace('s3://', '')
    const slash = cleaned.indexOf('/')
    if (slash === -1) return { bucket: cleaned, key: '' }
    return { bucket: cleaned.slice(0, slash), key: cleaned.slice(slash + 1) }
}

async function handleQuery(body) {
    const region = body.region || resolveEnv('AWS_REGION', 'VITE_AWS_REGION') || 'ap-northeast-2'
    const workgroup = body.workgroup || resolveEnv('ATHENA_WORKGROUP', 'VITE_ATHENA_WORKGROUP') || 'primary'
    const database = body.database || resolveEnv('GLUE_DATABASE', 'VITE_GLUE_DATABASE')
    const table = body.table || resolveEnv('GLUE_TABLE', 'VITE_GLUE_TABLE')
    const query = body.query || (database && table ? defaultQuery(database, table) : '')

    if (!query) {
        throw new Error('Missing SQL query')
    }

    const creds = resolveCreds()
    const athena = new AthenaClient({ region, credentials: creds })
    const s3 = new S3Client({ region, credentials: creds })

    const start = await athena.send(
        new StartQueryExecutionCommand({
            QueryString: query,
            WorkGroup: workgroup,
            QueryExecutionContext: database ? { Database: database } : undefined,
        })
    )

    const qid = start.QueryExecutionId
    if (!qid) throw new Error('No QueryExecutionId returned')

    let outputLocation = ''
    for (let i = 0; i < 60; i++) {
        await new Promise((r) => setTimeout(r, 2000))
        const res = await athena.send(new GetQueryExecutionCommand({ QueryExecutionId: qid }))
        const st = res.QueryExecution?.Status?.State
        const reason = res.QueryExecution?.Status?.StateChangeReason
        if (st === 'SUCCEEDED') {
            outputLocation = res.QueryExecution?.ResultConfiguration?.OutputLocation || ''
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

    const rows = []
    for (const r of parsed.data || []) {
        const gx = Number(r.grid_x ?? 0)
        const gy = Number(r.grid_y ?? 0)
        const clicks = Number(r.clicks ?? 0)
        if (Number.isFinite(gx) && Number.isFinite(gy) && Number.isFinite(clicks)) {
            rows.push({ grid_x: gx, grid_y: gy, clicks })
        }
    }

    return { rows, queryId: qid, outputLocation }
}

function sendJson(res, status, payload) {
    const body = JSON.stringify(payload)
    res.writeHead(status, {
        'Content-Type': 'application/json',
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Headers': 'Content-Type',
        'Access-Control-Allow-Methods': 'POST,OPTIONS,GET',
    })
    res.end(body)
}

function sendText(res, status, body, contentType) {
    res.writeHead(status, {
        'Content-Type': contentType || 'text/plain; charset=utf-8',
    })
    res.end(body)
}

function mimeType(filePath) {
    const ext = path.extname(filePath).toLowerCase()
    if (ext === '.html') return 'text/html; charset=utf-8'
    if (ext === '.js') return 'text/javascript; charset=utf-8'
    if (ext === '.css') return 'text/css; charset=utf-8'
    if (ext === '.svg') return 'image/svg+xml'
    if (ext === '.png') return 'image/png'
    if (ext === '.ico') return 'image/x-icon'
    if (ext === '.json') return 'application/json; charset=utf-8'
    if (ext === '.map') return 'application/json; charset=utf-8'
    if (ext === '.woff2') return 'font/woff2'
    return 'application/octet-stream'
}

function safeResolve(root, urlPath) {
    const cleaned = urlPath.replace(/^\//, '')
    const filePath = path.resolve(root, cleaned)
    if (!filePath.startsWith(root)) return null
    return filePath
}

function serveStatic(res, urlPath) {
    if (!fs.existsSync(DIST_DIR)) {
        return sendText(res, 503, 'Missing dist/. Run: npm run build', 'text/plain; charset=utf-8')
    }
    const resolved = safeResolve(DIST_DIR, urlPath)
    if (!resolved) {
        return sendText(res, 403, 'Forbidden', 'text/plain; charset=utf-8')
    }
    let filePath = resolved
    if (fs.existsSync(filePath) && fs.statSync(filePath).isDirectory()) {
        filePath = path.join(filePath, 'index.html')
    }
    if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
        const body = fs.readFileSync(filePath)
        return sendText(res, 200, body, mimeType(filePath))
    }
    if (fs.existsSync(INDEX_FILE)) {
        const body = fs.readFileSync(INDEX_FILE)
        return sendText(res, 200, body, 'text/html; charset=utf-8')
    }
    return sendText(res, 404, 'Not found', 'text/plain; charset=utf-8')
}

function errorToPayload(err) {
    const payload = {
        error: err?.message || String(err),
        name: err?.name,
        code: err?.code,
    }
    if (err?.$metadata?.requestId) {
        payload.requestId = err.$metadata.requestId
    }
    if (err?.cause) {
        payload.cause = err.cause?.message || String(err.cause)
    }
    if (process.env.NODE_ENV !== 'production') {
        payload.stack = err?.stack
    }
    return payload
}

const server = http.createServer(async (req, res) => {
    const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`)
    if (req.method === 'OPTIONS') {
        return sendJson(res, 200, { ok: true })
    }
    if (req.method === 'GET' && url.pathname === '/health') {
        return sendJson(res, 200, { ok: true })
    }
    if (req.method === 'POST' && url.pathname === '/api/query') {
        try {
            const body = await readJson(req)
            const result = await handleQuery(body)
            return sendJson(res, 200, result)
        } catch (err) {
            console.error('Query failed:', err)
            return sendJson(res, 500, errorToPayload(err))
        }
    }
    if (req.method === 'GET' || req.method === 'HEAD') {
        return serveStatic(res, url.pathname)
    }
    return sendJson(res, 404, { error: 'Not found' })
})

server.listen(PORT, () => {
    console.log(`Athena viewer backend listening on ${PORT}`)
})
