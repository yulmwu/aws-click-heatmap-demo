export function parseS3Uri(uri: string): { bucket: string; key: string } {
    if (!uri.startsWith('s3://')) throw new Error(`Not an s3 uri: ${uri}`)
    const noScheme = uri.slice('s3://'.length)
    const idx = noScheme.indexOf('/')
    if (idx < 0) return { bucket: noScheme, key: '' }
    return { bucket: noScheme.slice(0, idx), key: noScheme.slice(idx + 1) }
}

export async function bodyToString(body: any): Promise<string> {
    if (!body) return ''
    if (typeof body.transformToString === 'function') return await body.transformToString()
    try {
        return await new Response(body as any).text()
    } catch {
        if (body instanceof Blob) return await body.text()
    }
    return String(body)
}

export function clamp(n: number, a: number, b: number) {
    return Math.max(a, Math.min(b, n))
}

export function colorFor(value01: number, alpha = 1) {
    const t = clamp(value01, 0, 1)
    const stops = [
        [80, 160, 255],
        [20, 210, 255],
        [0, 220, 170],
        [255, 210, 0],
        [255, 80, 0],
    ]
    const scaled = t * (stops.length - 1)
    const idx = Math.min(stops.length - 2, Math.max(0, Math.floor(scaled)))
    const local = scaled - idx
    const a = stops[idx]
    const b = stops[idx + 1]
    const r = Math.round(a[0] + (b[0] - a[0]) * local)
    const g = Math.round(a[1] + (b[1] - a[1]) * local)
    const bl = Math.round(a[2] + (b[2] - a[2]) * local)
    return `rgba(${r}, ${g}, ${bl}, ${clamp(alpha, 0, 1)})`
}
