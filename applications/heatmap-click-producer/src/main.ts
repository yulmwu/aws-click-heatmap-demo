import { PutRecordCommand } from '@aws-sdk/client-kinesis'
import { makeKinesisClient } from './aws'

type ClickEvent = {
    event_type: 'click'
    event_time: string
    event_time_ms: number
    page_id: string
    x: number
    y: number
    viewport_width: number
    viewport_height: number
    dpr: number
    user_agent: string
}

const $ = (id: string) => document.getElementById(id) as any

const regionInput = $('region') as HTMLInputElement
const streamInput = $('stream') as HTMLInputElement
const pageIdInput = $('pageId') as HTMLInputElement
const statusInput = $('status') as HTMLInputElement
const logEl = $('log') as HTMLPreElement
const stage = $('stage') as HTMLDivElement
const dot = $('dot') as HTMLDivElement
const btnToggle = $('btnToggle') as HTMLButtonElement
const btnTest = $('btnTest') as HTMLButtonElement

const REGION = import.meta.env.VITE_AWS_REGION || 'ap-northeast-2'
const STREAM = import.meta.env.VITE_KINESIS_STREAM_NAME || ''
const PAGE_ID = import.meta.env.VITE_PAGE_ID || 'demo-page'

const accessKeyId = import.meta.env.VITE_AWS_ACCESS_KEY_ID || ''
const secretAccessKey = import.meta.env.VITE_AWS_SECRET_ACCESS_KEY || ''

regionInput.value = REGION
streamInput.value = STREAM
pageIdInput.value = PAGE_ID

let enabled = false
let client: any = null

function setStatus(s: string) {
    statusInput.value = s
}

function setLog(obj: any) {
    logEl.textContent = JSON.stringify(obj, null, 2)
}

function initClient() {
    if (!accessKeyId || !secretAccessKey) {
        setStatus('Missing AWS creds in .env')
        return null
    }
    return makeKinesisClient(regionInput.value.trim(), {
        accessKeyId,
        secretAccessKey,
    })
}

function getStageSize() {
    const rect = stage.getBoundingClientRect()
    const width = Math.round(rect.width)
    const height = Math.round(rect.height)
    return {
        width: width > 0 ? width : window.innerWidth,
        height: height > 0 ? height : window.innerHeight,
    }
}

function buildEvent(x: number, y: number): ClickEvent {
    const now = new Date()
    const { width, height } = getStageSize()
    return {
        event_type: 'click',
        event_time: now.toISOString(),
        event_time_ms: now.getTime(),
        page_id: pageIdInput.value || 'demo-page',
        x,
        y,
        viewport_width: width,
        viewport_height: height,
        dpr: window.devicePixelRatio || 1,
        user_agent: navigator.userAgent,
    }
}

async function sendEvent(evt: ClickEvent) {
    if (!client) throw new Error('Kinesis client not initialized')
    const streamName = streamInput.value.trim()
    if (!streamName) throw new Error('Stream name is empty')

    const data = new TextEncoder().encode(JSON.stringify(evt))
    const partitionKey = `${evt.page_id}-${Math.floor(Math.random() * 1e9)}`

    const cmd = new PutRecordCommand({
        StreamName: streamName,
        PartitionKey: partitionKey,
        Data: data,
    })

    return await client.send(cmd)
}

function placeDot(x: number, y: number) {
    const rect = stage.getBoundingClientRect()
    dot.style.left = `${x - rect.left}px`
    dot.style.top = `${y - rect.top}px`
    dot.style.display = 'block'
}

btnToggle.addEventListener('click', () => {
    enabled = !enabled
    if (enabled) {
        client = initClient()
        if (!client) {
            enabled = false
            btnToggle.textContent = 'Start sending'
            return
        }
        btnToggle.textContent = 'Stop sending'
        setStatus('Enabled')
    } else {
        btnToggle.textContent = 'Start sending'
        setStatus('Disabled')
    }
})

btnTest.addEventListener('click', async () => {
    try {
        if (!client) client = initClient()
        if (!client) return
        const evt = buildEvent(10, 10)
        setLog({ event: evt, status: 'sending...' })
        const resp = await sendEvent(evt)
        setLog({ event: evt, response: resp })
        setStatus('Sent test event')
    } catch (e: any) {
        setStatus('Error')
        setLog({ error: String(e?.message || e) })
    }
})

stage.addEventListener('click', async (e: MouseEvent) => {
    try {
        placeDot(e.clientX, e.clientY)

        const rect = stage.getBoundingClientRect()
        const x = Math.max(0, Math.min(rect.width, e.clientX - rect.left))
        const y = Math.max(0, Math.min(rect.height, e.clientY - rect.top))

        const evt = buildEvent(Math.round(x), Math.round(y))
        setLog({ event: evt, note: enabled ? 'sending...' : 'sending disabled (toggle start)' })

        if (!enabled) return
        const resp = await sendEvent(evt)
        setLog({ event: evt, response: resp })
        setStatus('Sent')
    } catch (e: any) {
        setStatus('Error')
        setLog({ error: String(e?.message || e) })
    }
})
