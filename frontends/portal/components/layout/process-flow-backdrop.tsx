'use client'

import { useRef, useEffect } from 'react'

type Variant = 'onDark' | 'onLight'
interface Props { variant: Variant }

const ON_DARK = {
  line:       'rgba(255,255,255,0.10)',
  lineSoft:   'rgba(255,255,255,0.055)',
  nodeStroke: 'rgba(255,255,255,0.13)',
  nodeAccent: 'rgba(20,155,165,0.42)',
  nodeFill:   'rgba(255,255,255,0.015)',
  glyph:      'rgba(255,255,255,0.22)',
  token:      '#149ba5',
  tokenOp:    0.55,
  svgStyle:   'mask-image:radial-gradient(ellipse 95% 80% at 45% 42%,#000 45%,transparent 92%);-webkit-mask-image:radial-gradient(ellipse 95% 80% at 45% 42%,#000 45%,transparent 92%);',
}

const ON_LIGHT = {
  line:       'rgba(20,155,165,0.16)',
  lineSoft:   'rgba(15,30,42,0.07)',
  nodeStroke: 'rgba(15,30,42,0.10)',
  nodeAccent: 'rgba(20,155,165,0.30)',
  nodeFill:   'rgba(255,255,255,0.55)',
  glyph:      'rgba(20,155,165,0.32)',
  token:      '#149ba5',
  tokenOp:    0.40,
  svgStyle:   'mask-image:radial-gradient(ellipse 78% 62% at 50% 44%,transparent 0%,transparent 26%,#000 72%);-webkit-mask-image:radial-gradient(ellipse 78% 62% at 50% 44%,transparent 0%,transparent 26%,#000 72%);',
}

type Theme = typeof ON_DARK

// ─── Geometry ────────────────────────────────────────────────────────────────
interface EventNode  { kind: 'event';  id: string; sub: 'start' | 'end'; x: number; y: number; accent: boolean }
interface TaskNode   { kind: 'task';   id: string; x: number; y: number; w: number; h: number }
interface GateNode   { kind: 'gate';   id: string; sub: 'x' | 'p'; x: number; y: number; s: number; accent: boolean }
type Node = EventNode | TaskNode | GateNode

const NODES: Node[] = [
  // flow A
  { kind: 'event', id: 'a1', sub: 'start', x: 118, y: 190,  accent: true },
  { kind: 'task',  id: 'a2', x: 305,  y: 190,  w: 108, h: 46 },
  { kind: 'gate',  id: 'a3', sub: 'x', x: 500,  y: 190,  s: 26, accent: true },
  { kind: 'task',  id: 'a4', x: 700,  y: 110,  w: 96,  h: 44 },
  { kind: 'task',  id: 'a5', x: 700,  y: 270,  w: 96,  h: 44 },
  { kind: 'gate',  id: 'a6', sub: 'p', x: 900,  y: 190,  s: 26, accent: false },
  { kind: 'task',  id: 'a7', x: 1086, y: 190,  w: 108, h: 46 },
  { kind: 'event', id: 'a8', sub: 'end', x: 1300, y: 190, accent: true },
  // flow B
  { kind: 'event', id: 'b1', sub: 'start', x: 96,  y: 560,  accent: true },
  { kind: 'task',  id: 'b2', x: 288,  y: 560,  w: 108, h: 46 },
  { kind: 'gate',  id: 'b3', sub: 'x', x: 488,  y: 560,  s: 26, accent: true },
  { kind: 'task',  id: 'b4', x: 688,  y: 478,  w: 96,  h: 44 },
  { kind: 'task',  id: 'b5', x: 688,  y: 642,  w: 96,  h: 44 },
  { kind: 'gate',  id: 'b6', sub: 'p', x: 888,  y: 560,  s: 26, accent: false },
  { kind: 'task',  id: 'b7', x: 1080, y: 560,  w: 108, h: 46 },
  { kind: 'event', id: 'b8', sub: 'end', x: 1300, y: 560, accent: true },
  // flow C
  { kind: 'event', id: 'c1', sub: 'start', x: 206,  y: 884, accent: true },
  { kind: 'task',  id: 'c2', x: 408,  y: 884,  w: 108, h: 46 },
  { kind: 'gate',  id: 'c3', sub: 'x', x: 612,  y: 884,  s: 26, accent: true },
  { kind: 'task',  id: 'c4', x: 820,  y: 804,  w: 96,  h: 44 },
  { kind: 'task',  id: 'c5', x: 820,  y: 968,  w: 96,  h: 44 },
  { kind: 'event', id: 'c6', sub: 'end', x: 1044, y: 884, accent: true },
]

interface Conn { from: string; to: string; isSpine: boolean }
const CONNS: Conn[] = [
  { from: 'a1', to: 'a2', isSpine: true  },
  { from: 'a2', to: 'a3', isSpine: true  },
  { from: 'a3', to: 'a4', isSpine: false },
  { from: 'a3', to: 'a5', isSpine: false },
  { from: 'a4', to: 'a6', isSpine: false },
  { from: 'a5', to: 'a6', isSpine: false },
  { from: 'a6', to: 'a7', isSpine: true  },
  { from: 'a7', to: 'a8', isSpine: true  },
  { from: 'b1', to: 'b2', isSpine: true  },
  { from: 'b2', to: 'b3', isSpine: true  },
  { from: 'b3', to: 'b4', isSpine: false },
  { from: 'b3', to: 'b5', isSpine: false },
  { from: 'b4', to: 'b6', isSpine: false },
  { from: 'b5', to: 'b6', isSpine: false },
  { from: 'b6', to: 'b7', isSpine: true  },
  { from: 'b7', to: 'b8', isSpine: true  },
  { from: 'c1', to: 'c2', isSpine: true  },
  { from: 'c2', to: 'c3', isSpine: true  },
  { from: 'c3', to: 'c4', isSpine: false },
  { from: 'c3', to: 'c5', isSpine: false },
  { from: 'c4', to: 'c6', isSpine: true  },
]

const TOKEN_SPINES: string[][] = [
  ['a1','a2','a3','a4','a6','a7','a8'],
  ['b1','b2','b3','b5','b6','b7','b8'],
  ['c1','c2','c3','c4','c6'],
]

const TOKEN_DURATIONS  = [18, 23, 28]
const TOKEN_BEGINS     = [-1, -7, -13]

// ─── Helpers ──────────────────────────────────────────────────────────────────
function nodeById(id: string): Node {
  const n = NODES.find(n => n.id === id)
  if (!n) throw new Error(`Node not found: ${id}`)
  return n
}

function nodeCenter(n: Node): { x: number; y: number } {
  return { x: n.x, y: n.y }
}

function nodeEdgeX(n: Node, side: 'right' | 'left'): number {
  if (n.kind === 'event') return side === 'right' ? n.x + 15 : n.x - 15
  if (n.kind === 'task')  return side === 'right' ? n.x + n.w / 2 : n.x - n.w / 2
  // gate
  return side === 'right' ? n.x + n.s : n.x - n.s
}

function nodeEdgeY(n: Node, side: 'bottom' | 'top'): number {
  if (n.kind === 'event') return side === 'bottom' ? n.y + 15 : n.y - 15
  if (n.kind === 'task')  return side === 'bottom' ? n.y + n.h / 2 : n.y - n.h / 2
  // gate
  return side === 'bottom' ? n.y + n.s : n.y - n.s
}

// ─── SVG generation ───────────────────────────────────────────────────────────
function renderEvent(n: EventNode, T: Theme): string {
  const stroke = n.accent ? T.nodeAccent : T.nodeStroke
  const sw = n.sub === 'end' ? 3.2 : 2
  return `<circle cx="${n.x}" cy="${n.y}" r="15" fill="${T.nodeFill}" stroke="${stroke}" stroke-width="${sw}"/>`
}

function renderTask(n: TaskNode, T: Theme): string {
  const x = n.x - n.w / 2
  const y = n.y - n.h / 2
  return `<rect x="${x}" y="${y}" width="${n.w}" height="${n.h}" rx="11" fill="${T.nodeFill}" stroke="${T.nodeStroke}" stroke-width="1.4"/>`
}

function renderGate(n: GateNode, T: Theme): string {
  const { x, y, s } = n
  const g = s * 0.34
  const stroke = n.accent ? T.nodeAccent : T.nodeStroke
  const diamond = `<path d="M ${x},${y - s} L ${x + s},${y} L ${x},${y + s} L ${x - s},${y} Z" fill="${T.nodeFill}" stroke="${stroke}" stroke-width="1.4"/>`
  let glyph = ''
  if (n.sub === 'x') {
    glyph = `<line x1="${x - g}" y1="${y - g}" x2="${x + g}" y2="${y + g}" stroke="${T.glyph}" stroke-width="1.4"/>` +
            `<line x1="${x + g}" y1="${y - g}" x2="${x - g}" y2="${y + g}" stroke="${T.glyph}" stroke-width="1.4"/>`
  } else {
    glyph = `<line x1="${x - g}" y1="${y}" x2="${x + g}" y2="${y}" stroke="${T.glyph}" stroke-width="1.4"/>` +
            `<line x1="${x}" y1="${y - g}" x2="${x}" y2="${y + g}" stroke="${T.glyph}" stroke-width="1.4"/>`
  }
  return diamond + glyph
}

function renderConn(c: Conn, T: Theme): string {
  const from = nodeById(c.from)
  const to   = nodeById(c.to)
  const fc   = nodeCenter(from)
  const tc   = nodeCenter(to)

  let sx: number, sy: number, ex: number, ey: number

  // Determine exit/entry points based on relative position
  const dy = tc.y - fc.y
  if (Math.abs(dy) < 5) {
    // Straight horizontal
    sx = nodeEdgeX(from, 'right')
    sy = fc.y
    ex = nodeEdgeX(to, 'left')
    ey = tc.y
    const stroke = c.isSpine ? T.line : T.lineSoft
    const markerId = c.isSpine ? 'pf-arr' : 'pf-arr-s'
    return `<line x1="${sx}" y1="${sy}" x2="${ex}" y2="${ey}" stroke="${stroke}" stroke-width="1.2" marker-end="url(#${markerId})"/>`
  }

  // Orthogonal elbow
  if (dy > 0) {
    // going down
    sx = nodeEdgeX(from, 'right')
    sy = fc.y
    ex = nodeEdgeX(to, 'left')
    ey = tc.y
  } else {
    // going up
    sx = nodeEdgeX(from, 'right')
    sy = fc.y
    ex = nodeEdgeX(to, 'left')
    ey = tc.y
  }

  const mx = (sx + ex) / 2
  const stroke = c.isSpine ? T.line : T.lineSoft
  const markerId = c.isSpine ? 'pf-arr' : 'pf-arr-s'
  return `<path d="M ${sx},${sy} L ${mx},${sy} L ${mx},${ey} L ${ex},${ey}" fill="none" stroke="${stroke}" stroke-width="1.2" marker-end="url(#${markerId})"/>`
}

function buildSpinePath(spine: string[]): string {
  const pts: string[] = []
  for (let i = 0; i < spine.length; i++) {
    const n = nodeById(spine[i])
    const c = nodeCenter(n)
    if (i === 0) {
      pts.push(`M ${c.x},${c.y}`)
    } else {
      const prev = nodeById(spine[i - 1])
      const pc   = nodeCenter(prev)
      const dy = c.y - pc.y
      if (Math.abs(dy) < 5) {
        pts.push(`L ${c.x},${c.y}`)
      } else {
        const mx = (pc.x + c.x) / 2
        pts.push(`L ${mx},${pc.y} L ${mx},${c.y} L ${c.x},${c.y}`)
      }
    }
  }
  return pts.join(' ')
}

function renderToken(spine: string[], index: number, T: Theme): string {
  const d   = buildSpinePath(spine)
  const dur = TOKEN_DURATIONS[index]
  const begin = TOKEN_BEGINS[index]
  const op  = T.tokenOp
  return `<circle r="3.4" fill="${T.token}">
  <animateMotion dur="${dur}s" begin="${begin}s" repeatCount="indefinite" path="${d}"/>
  <animate attributeName="opacity" values="0;${op};${op};0" keyTimes="0;0.07;0.9;1" dur="${dur}s" begin="${begin}s" repeatCount="indefinite"/>
</circle>`
}

function buildSvg(T: Theme): string {
  const defs = `<defs>
  <marker id="pf-arr" markerWidth="6" markerHeight="6" refX="5" refY="2" orient="auto">
    <path d="M 0 0 L 6 2 L 0 4 Z" fill="${T.line}"/>
  </marker>
  <marker id="pf-arr-s" markerWidth="6" markerHeight="6" refX="5" refY="2" orient="auto">
    <path d="M 0 0 L 6 2 L 0 4 Z" fill="${T.lineSoft}"/>
  </marker>
</defs>`

  const connsSvg = CONNS.map(c => renderConn(c, T)).join('\n')
  const nodesSvg = NODES.map(n => {
    if (n.kind === 'event') return renderEvent(n, T)
    if (n.kind === 'task')  return renderTask(n, T)
    return renderGate(n as GateNode, T)
  }).join('\n')
  const tokensSvg = TOKEN_SPINES.map((spine, i) => renderToken(spine, i, T)).join('\n')

  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1440 1024" preserveAspectRatio="xMidYMid slice" style="position:absolute;inset:0;width:100%;height:100%;pointer-events:none;${T.svgStyle}">
${defs}
${connsSvg}
${nodesSvg}
${tokensSvg}
</svg>`
}

// ─── Component ───────────────────────────────────────────────────────────────
export function ProcessFlowBackdrop({ variant }: Props) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (ref.current) ref.current.innerHTML = buildSvg(variant === 'onDark' ? ON_DARK : ON_LIGHT)
  }, [variant])
  return <div ref={ref} aria-hidden="true" style={{ position: 'absolute', inset: 0, pointerEvents: 'none' }} />
}
