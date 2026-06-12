'use client'

import Link from 'next/link'
import { Plus, Rocket } from 'lucide-react'
import { Button } from '@/components/ui/button'

// ─── Step data ────────────────────────────────────────────────────────────────
const STEP_COLORS = ['#149ba5', '#7c3aed', '#c2410c', '#0891b2', '#16a34a'] as const

const GUIDE_STEPS = [
  { title: 'Connect a form or Start blank', desc: 'Begin from a linked form or an empty BPMN canvas', num: 1 },
  { title: 'Design the flow',               desc: 'Drag tasks, gateways and events visually',          num: 2 },
  { title: 'Configure tasks and events',    desc: 'Set assignees, forms and DOA routing',              num: 3 },
  { title: 'Add conditions and link DMN',   desc: 'Branch with gateways and wire DMN decisions',       num: 4 },
  { title: 'Deploy',                        desc: 'Validate and push live to the engine',              num: 5 },
] as const

function StepIconSvg({ index, color }: { index: number; color: string }) {
  const paths: string[][] = [
    ['M12 5v14', 'M5 12h14'],
    ['M3 6h3l3 12 3-6 3 6 3-12h3'],
    ['M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6zm0 0v6h6', 'M8 13h8', 'M8 17h5'],
    ['M12 3 L21 12 L12 21 L3 12 Z', 'M9.4 9.4 L14.6 14.6', 'M14.6 9.4 L9.4 14.6'],
    ['M4.5 16.5c-1.5 1.26-2 5-2 5s3.74-.5 5-2c.71-.84.7-2.13-.09-2.91a2.18 2.18 0 0 0-2.91-.09z','M12 15l-3-3a22 22 0 0 1 2-3.95A12.88 12.88 0 0 1 22 2c0 2.72-.78 7.5-6 11a22.35 22.35 0 0 1-4 2z','M9 12H4s.55-3.03 2-4c1.62-1.08 5 0 5 0','M12 15v5s3.03-.55 4-2c1.08-1.62 0-5 0-5'],
  ]
  return (
    <svg width={22} height={22} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={1.9} strokeLinecap="round" strokeLinejoin="round">
      {paths[index].map((d, j) => <path key={j} d={d} />)}
    </svg>
  )
}

// ─── Component ────────────────────────────────────────────────────────────────
interface GuideSectionProps {
  isManagerOrAbove: boolean
}

export function GuideSection({ isManagerOrAbove }: GuideSectionProps) {
  return (
    <div className="bg-card border border-border rounded-xl overflow-hidden">
      {/* Header */}
      <div
        style={{ background: 'linear-gradient(90deg, #f0fdf9, #f0f9fa)' }}
        className="px-6 py-[18px] border-b border-border"
      >
        <div className="flex items-center gap-2 text-[15px] font-bold text-foreground">
          <Rocket size={18} className="text-primary" strokeWidth={1.8} />
          How to create a process
        </div>
        <div className="text-xs text-muted-foreground mt-1">
          Five steps to design and deploy a BPMN workflow
        </div>
      </div>

      {/* Steps — gradient rail */}
      <div className="px-6 py-7 relative">
        {/* Connector rail behind nodes */}
        <div style={{
          position: 'absolute', top: 56, left: '10%', right: '10%',
          height: 2.5, borderRadius: 2, opacity: 0.45, zIndex: 0,
          background: 'linear-gradient(90deg,#149ba5,#7c3aed,#c2410c,#0891b2,#16a34a)',
        }} />
        <div className="relative z-[1] flex gap-2">
          {GUIDE_STEPS.map((step, i) => {
            const color = STEP_COLORS[i]
            return (
              <div key={i} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                {/* Icon node */}
                <div style={{
                  position: 'relative', width: 56, height: 56, borderRadius: 16,
                  background: '#fff', border: `1.5px solid ${color}30`,
                  boxShadow: '0 1px 2px rgba(15,30,42,0.05)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}>
                  <div style={{
                    width: 44, height: 44, borderRadius: 12,
                    background: color + '18',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}>
                    <StepIconSvg index={i} color={color} />
                  </div>
                  {/* Number chip */}
                  <div style={{
                    position: 'absolute', top: -8, right: -8,
                    width: 22, height: 22, borderRadius: 99,
                    background: color, color: '#fff',
                    fontSize: 11, fontWeight: 700,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    border: '2.5px solid #fff',
                    boxShadow: '0 1px 3px rgba(15,30,42,0.18)',
                  }}>
                    {step.num}
                  </div>
                </div>
                {/* Title */}
                <div style={{
                  marginTop: 16, fontSize: 13, fontWeight: 700, color: 'hsl(var(--foreground))',
                  textAlign: 'center', lineHeight: 1.35, maxWidth: 170,
                  minHeight: 53, display: 'flex', alignItems: 'flex-start', justifyContent: 'center',
                }}>
                  {step.title}
                </div>
                {/* Description */}
                <div style={{
                  marginTop: 5, fontSize: 11.5, color: 'hsl(var(--muted-foreground))',
                  lineHeight: 1.55, textAlign: 'center', maxWidth: 168,
                }}>
                  {step.desc}
                </div>
              </div>
            )
          })}
        </div>
      </div>

      {/* CTA for managers */}
      {isManagerOrAbove && (
        <div
          style={{ background: 'linear-gradient(90deg, #f0fdf9, #f0f9fa)' }}
          className="px-6 py-4 border-t border-border flex items-center justify-between"
        >
          <span className="text-[13px] text-muted-foreground">
            Ready to design your first workflow?
          </span>
          <Button asChild size="sm" className="text-primary-foreground">
            <Link href="/processes/new">
              <Plus size={13} strokeWidth={2} className="mr-1.5" />
              Create New Process
            </Link>
          </Button>
        </div>
      )}
    </div>
  )
}
