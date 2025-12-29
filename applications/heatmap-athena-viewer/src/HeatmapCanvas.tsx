import React, { useEffect, useRef } from 'react'
import { colorFor, clamp } from './utils'

export function HeatmapCanvas(props: { gridSize: number; cells: number[][] }) {
    const { gridSize, cells } = props
    const ref = useRef<HTMLCanvasElement | null>(null)

    useEffect(() => {
        const canvas = ref.current
        if (!canvas) return
        const ctx = canvas.getContext('2d')
        if (!ctx) return

        const W = canvas.width
        const H = canvas.height
        ctx.clearRect(0, 0, W, H)

        const max = cells.reduce((m, row) => Math.max(m, ...row), 0)
        const cellW = W / gridSize
        const cellH = H / gridSize

        ctx.fillStyle = '#fff'
        ctx.fillRect(0, 0, W, H)

        for (let y = 0; y < gridSize; y++) {
            for (let x = 0; x < gridSize; x++) {
                const v = cells[y]?.[x] ?? 0
                if (v <= 0) continue
                const t = max > 0 ? v / max : 0
                ctx.fillStyle = colorFor(t)
                ctx.globalAlpha = clamp(0.15 + 0.85 * t, 0.15, 1)
                ctx.fillRect(x * cellW, y * cellH, cellW, cellH)
            }
        }
        ctx.globalAlpha = 1

        ctx.strokeStyle = '#ddd'
        ctx.lineWidth = 1
        for (let i = 0; i <= gridSize; i++) {
            ctx.beginPath()
            ctx.moveTo(i * cellW, 0)
            ctx.lineTo(i * cellW, H)
            ctx.stroke()
            ctx.beginPath()
            ctx.moveTo(0, i * cellH)
            ctx.lineTo(W, i * cellH)
            ctx.stroke()
        }
    }, [gridSize, cells])

    return (
        <canvas
            ref={ref}
            width={720}
            height={520}
            style={{
                width: '100%',
                height: 'auto',
                border: '1px solid #ccc',
                background: '#fafafa',
            }}
        />
    )
}
