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

export function colorFor(value01: number) {
    const h = 240 - 240 * clamp(value01, 0, 1)
    return `hsl(${h}, 100%, 55%)`
}
