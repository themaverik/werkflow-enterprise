import Link from "next/link"

const ACCENT = '#149ba5'
const DARK = '#111c27'

const features = [
  {
    icon: 'M9 3H5a2 2 0 0 0-2 2v4m6-6h10l4 4v10a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V3zm0 0',
    title: 'Process Automation',
    desc: 'Design end-to-end workflows with BPMN 2.0. Route tasks, trigger approvals, and automate multi-step operations visually.',
    color: '#149ba5',
    href: '/processes',
  },
  {
    icon: 'M3 3h18M3 9h18M3 15h18M3 21h18',
    title: 'Decision Tables',
    desc: 'Model complex business rules with DMN. Separate logic from process flows for clear, auditable decision management.',
    color: '#0891b2',
    href: '/decisions',
  },
  {
    icon: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6zm0 0v6h6',
    title: 'Dynamic Forms',
    desc: 'Build rich forms with conditional logic, file uploads, and workflow task binding — no code required.',
    color: '#16a34a',
    href: '/forms',
  },
  {
    icon: 'M9 11l3 3L22 4M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11',
    title: 'Task Management',
    desc: 'Unified task inbox for every role. Claim, complete, delegate, and track work across all active processes.',
    color: '#c27b00',
    href: '/tasks',
  },
  {
    icon: 'M18 20V10M12 20V4M6 20v-6',
    title: 'Analytics & SLA',
    desc: 'Track process throughput, SLA compliance, and identify bottlenecks with real-time dashboards and CSV export.',
    color: '#1d4ed8',
    href: '/analytics',
  },
  {
    icon: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z',
    title: 'Role-Based Access',
    desc: 'Fine-grained authority with DOA levels, department routing, and Keycloak-backed SSO for enterprise identity.',
    color: '#dc2626',
    href: '/dashboard',
  },
]

const steps = [
  { n: '01', title: 'Design', desc: 'Model processes with BPMN and decision tables in the visual studio.' },
  { n: '02', title: 'Configure', desc: 'Map roles, set DOA thresholds, and attach dynamic forms to tasks.' },
  { n: '03', title: 'Deploy', desc: 'Publish to the Flowable engine. Live instantly — no downtime.' },
  { n: '04', title: 'Monitor', desc: 'Track SLA compliance and process health from the analytics dashboard.' },
]

function Icon({ d, size = 20, stroke = 'currentColor' }: { d: string; size?: number; stroke?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={stroke} strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round">
      <path d={d} />
    </svg>
  )
}

export default function HomePage() {
  return (
    <div style={{ fontFamily: "'DM Sans', sans-serif", color: '#0f1e2a', background: '#fff', minHeight: '100vh' }}>

      {/* Navbar */}
      <nav style={{
        height: 60, display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '0 40px', background: DARK, borderBottom: '1px solid rgba(255,255,255,0.07)',
        position: 'sticky', top: 0, zIndex: 50,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div style={{ width: 32, height: 32, borderRadius: 8, background: ACCENT, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Icon d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" size={16} stroke="#fff" />
          </div>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/werkflow-logo.png" alt="Werkflow" style={{ height: 28, width: 'auto', objectFit: 'contain' }} />
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 28 }}>
          {[['Processes', '/processes'], ['Tasks', '/tasks'], ['Decisions', '/decisions']].map(([label, href]) => (
            <Link key={href} href={href} style={{ fontSize: 13.5, fontWeight: 500, color: 'rgba(255,255,255,0.5)', textDecoration: 'none' }}>
              {label}
            </Link>
          ))}
          <Link href="/dashboard" style={{
            display: 'inline-flex', alignItems: 'center', gap: 7,
            background: ACCENT, color: '#fff', padding: '8px 20px', borderRadius: 8,
            fontSize: 13.5, fontWeight: 600, textDecoration: 'none',
          }}>
            Go to Dashboard
          </Link>
        </div>
      </nav>

      {/* Hero */}
      <section style={{
        minHeight: 'calc(100vh - 60px)', display: 'flex', flexDirection: 'column',
        alignItems: 'center', justifyContent: 'center',
        padding: '100px 40px 80px', textAlign: 'center', position: 'relative', overflow: 'hidden',
        backgroundImage: 'linear-gradient(rgba(20,155,165,0.04) 1px, transparent 1px), linear-gradient(90deg, rgba(20,155,165,0.04) 1px, transparent 1px)',
        backgroundSize: '48px 48px',
      }}>
        {/* Blobs */}
        <div style={{ position: 'absolute', width: 500, height: 400, borderRadius: '50%', background: 'rgba(20,155,165,0.10)', top: -80, left: -100, filter: 'blur(80px)', pointerEvents: 'none' }} />
        <div style={{ position: 'absolute', width: 300, height: 300, borderRadius: '50%', background: 'rgba(20,155,165,0.07)', bottom: 40, left: '20%', filter: 'blur(80px)', pointerEvents: 'none' }} />

        <div style={{ position: 'relative', maxWidth: 760 }}>
          {/* Overlapping circles logo */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 0, marginBottom: 14 }}>
            {[{ bg: '#2dcc85', ml: 0, mr: -20 }, { bg: ACCENT, ml: 0, mr: 0, zIndex: 1 }, { bg: '#5bc6db', ml: -20, mr: 0 }].map(({ bg, ml, mr, zIndex }, i) => (
              <div key={i} style={{ width: 64, height: 64, borderRadius: '50%', background: bg, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 18, fontWeight: 800, color: '#fff', marginLeft: ml, marginRight: mr, zIndex }}>
                {['W', 'ER', 'F'][i]}
              </div>
            ))}
          </div>

          <p style={{ fontSize: 12, fontWeight: 600, color: '#94a8b3', textTransform: 'uppercase', letterSpacing: '0.12em', marginBottom: 24 }}>
            Enterprise Workflow Platform
          </p>

          <h1 style={{ fontSize: 52, fontWeight: 800, letterSpacing: '-0.03em', lineHeight: 1.12, marginBottom: 20 }}>
            Automate the work.<br />
            <span style={{ color: ACCENT }}>Own the outcome.</span>
          </h1>
          <p style={{ fontSize: 17, color: '#6b7e8c', lineHeight: 1.65, marginBottom: 40, maxWidth: 580, margin: '0 auto 40px' }}>
            Design, deploy, and manage enterprise workflows — approvals, procurement, onboarding — from one unified BPMN platform.
          </p>

          <div style={{ display: 'flex', gap: 12, justifyContent: 'center', flexWrap: 'wrap' }}>
            <Link href="/dashboard" style={{
              display: 'inline-flex', alignItems: 'center', gap: 8,
              background: ACCENT, color: '#fff', padding: '13px 28px', borderRadius: 10,
              fontSize: 15, fontWeight: 600, textDecoration: 'none',
              boxShadow: '0 4px 14px rgba(20,155,165,0.3)',
            }}>
              Open Dashboard
            </Link>
            <Link href="/processes" style={{
              display: 'inline-flex', alignItems: 'center', gap: 8,
              background: '#fff', color: '#0f1e2a', padding: '13px 28px', borderRadius: 10,
              fontSize: 15, fontWeight: 600, textDecoration: 'none',
              border: '1.5px solid #e2eaee',
            }}>
              Browse Processes
            </Link>
          </div>
        </div>
      </section>

      {/* Tech strip */}
      <div style={{ background: '#f0f4f6', borderTop: '1px solid #e2eaee', borderBottom: '1px solid #e2eaee', padding: '16px 40px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 40, flexWrap: 'wrap' }}>
        {['BPMN 2.0', 'DMN Decision Tables', 'CMMN', 'Flowable Engine', 'Keycloak SSO'].map(label => (
          <div key={label} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, fontWeight: 500, color: '#6b7e8c' }}>
            <div style={{ width: 6, height: 6, borderRadius: '50%', background: ACCENT, flexShrink: 0 }} />
            {label}
          </div>
        ))}
      </div>

      {/* Features */}
      <section style={{ padding: '96px 40px', maxWidth: 1100, margin: '0 auto' }}>
        <p style={{ fontSize: 12, fontWeight: 700, textTransform: 'uppercase', color: ACCENT, marginBottom: 12, letterSpacing: '0.1em' }}>Platform Capabilities</p>
        <h2 style={{ fontSize: 36, fontWeight: 800, letterSpacing: '-0.03em', lineHeight: 1.2, marginBottom: 16 }}>Everything your enterprise needs</h2>
        <p style={{ fontSize: 16, color: '#6b7e8c', lineHeight: 1.65, maxWidth: 560, marginBottom: 56 }}>
          From initial process design to real-time monitoring — Werkflow covers the full lifecycle.
        </p>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 20 }}>
          {features.map(({ icon, title, desc, color, href }) => (
            <Link key={title} href={href} style={{
              background: '#fff', border: '1px solid #e2eaee', borderRadius: 14, padding: 28,
              textDecoration: 'none', color: 'inherit', display: 'block', transition: 'all .2s',
            }}>
              <div style={{ width: 48, height: 48, borderRadius: 12, background: color + '18', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 18 }}>
                <Icon d={icon} size={22} stroke={color} />
              </div>
              <div style={{ fontSize: 15, fontWeight: 700, color: '#0f1e2a', marginBottom: 8 }}>{title}</div>
              <p style={{ fontSize: 13.5, color: '#6b7e8c', lineHeight: 1.65, margin: 0 }}>{desc}</p>
              <div style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 13, fontWeight: 600, color: ACCENT, marginTop: 16 }}>
                Explore <span>→</span>
              </div>
            </Link>
          ))}
        </div>
      </section>

      {/* How it works */}
      <section style={{ background: '#f0f4f6', borderTop: '1px solid #e2eaee', borderBottom: '1px solid #e2eaee', padding: '96px 40px' }}>
        <div style={{ maxWidth: 1100, margin: '0 auto' }}>
          <p style={{ fontSize: 12, fontWeight: 700, textTransform: 'uppercase', color: ACCENT, marginBottom: 12, letterSpacing: '0.1em' }}>How It Works</p>
          <h2 style={{ fontSize: 36, fontWeight: 800, letterSpacing: '-0.03em', lineHeight: 1.2, marginBottom: 56 }}>From idea to live process in minutes</h2>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 32 }}>
            {steps.map(({ n, title, desc }) => (
              <div key={n}>
                <div style={{ width: 48, height: 48, borderRadius: '50%', background: ACCENT, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14, fontWeight: 800, color: '#fff', marginBottom: 20 }}>{n}</div>
                <div style={{ fontSize: 16, fontWeight: 700, color: '#0f1e2a', marginBottom: 8 }}>{title}</div>
                <p style={{ fontSize: 13.5, color: '#6b7e8c', lineHeight: 1.65, margin: 0 }}>{desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section style={{ padding: '96px 40px', textAlign: 'center', maxWidth: 760, margin: '0 auto' }}>
        <h2 style={{ fontSize: 36, fontWeight: 800, letterSpacing: '-0.03em', marginBottom: 16 }}>Ready to streamline your operations?</h2>
        <p style={{ fontSize: 16, color: '#6b7e8c', lineHeight: 1.65, marginBottom: 36 }}>
          Launch your first workflow today — no setup required for your team.
        </p>
        <Link href="/dashboard" style={{
          display: 'inline-flex', alignItems: 'center', gap: 8,
          background: ACCENT, color: '#fff', padding: '14px 32px', borderRadius: 10,
          fontSize: 15, fontWeight: 600, textDecoration: 'none',
          boxShadow: '0 4px 14px rgba(20,155,165,0.3)',
        }}>
          Get Started
        </Link>
      </section>

      {/* Footer */}
      <footer style={{ background: DARK, padding: '32px 40px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div style={{ width: 28, height: 28, borderRadius: 7, background: ACCENT, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Icon d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" size={14} stroke="#fff" />
          </div>
          <span style={{ fontSize: 14, fontWeight: 700, color: '#fff', letterSpacing: '-0.2px' }}>Werkflow</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, color: 'rgba(255,255,255,0.3)' }}>
          <span>Powered by</span>
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 80 20" style={{ height: 16, opacity: 0.5 }}>
            <rect width="80" height="20" rx="3" fill="#0A6EC5" />
            <text x="6" y="14" fontFamily="monospace" fontSize="11" fontWeight="700" fill="white">bpmn-io</text>
          </svg>
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 90 20" style={{ height: 16, opacity: 0.5 }}>
            <rect width="90" height="20" rx="3" fill="#E87B00" />
            <text x="6" y="14" fontFamily="sans-serif" fontSize="11" fontWeight="700" fill="white">Flowable</text>
          </svg>
        </div>
        <p style={{ fontSize: 12, color: 'rgba(255,255,255,0.25)' }}>© 2026 Werkflow. All rights reserved.</p>
      </footer>
    </div>
  )
}
