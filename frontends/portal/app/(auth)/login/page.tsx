import { signIn } from "@/auth"
import { ProcessFlowBackdrop } from '@/components/layout/process-flow-backdrop'

export default async function LoginPage() {
  return (
    <div style={{ display: 'flex', minHeight: '100vh', fontFamily: "'DM Sans', sans-serif" }}>

      {/* Left panel — brand */}
      <div style={{
        width: '42%', background: '#111c27', position: 'relative',
        overflow: 'hidden', display: 'flex', flexDirection: 'column',
        justifyContent: 'space-between', padding: '40px 44px',
        flexShrink: 0,
      }}>
        <ProcessFlowBackdrop variant="onDark" />
        {/* Radial glow */}
        <div style={{
          position: 'absolute', top: '35%', left: '30%', width: 320, height: 320,
          borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(20,155,165,0.18) 0%, transparent 70%)',
          pointerEvents: 'none',
        }} />

        {/* Logo */}
        <div style={{ position: 'relative' }}>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/werkflow-logo.png" alt="Werkflow" style={{ height: 100, width: 'auto', objectFit: 'contain' }} />
        </div>

        {/* Center copy */}
        <div style={{ position: 'relative' }}>
          <div style={{ fontSize: 11, fontWeight: 600, color: '#149ba5', letterSpacing: '0.1em', textTransform: 'uppercase', marginBottom: 14 }}>
            Enterprise Workflow Platform
          </div>
          <h1 style={{ fontSize: 30, fontWeight: 700, color: '#fff', lineHeight: 1.25, marginBottom: 16, letterSpacing: '-0.5px' }}>
            Automate the work.<br />
            <span style={{ color: '#149ba5' }}>Own the outcome.</span>
          </h1>
          <p style={{ fontSize: 14, color: 'rgba(255,255,255,0.5)', lineHeight: 1.7, maxWidth: 370, marginBottom: 28 }}>
            Design, deploy and monitor business processes<br />
            — procurement, onboarding, approvals and more<br />
            — all in one place.
          </p>
          <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: 12 }}>
            {[
              { label: 'BPMN process designer', icon: <path d="M20 6L9 17l-5-5" /> },
              { label: 'Role-based task routing', icon: <><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><path d="M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z"/></> },
              { label: 'Live instance monitoring', icon: <path d="M23 6l-9.5 9.5-5-5L1 18" /> },
            ].map(({ label, icon }) => (
              <li key={label} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <span style={{ width: 22, height: 22, borderRadius: 6, background: 'rgba(20,155,165,0.16)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#149ba5" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    {icon}
                  </svg>
                </span>
                <span style={{ fontSize: 13, color: 'rgba(255,255,255,0.6)' }}>{label}</span>
              </li>
            ))}
          </ul>
        </div>

        {/* Bottom version */}
        <div style={{ position: 'relative', fontSize: 11, color: 'rgba(255,255,255,0.25)' }}>
          Werkflow Enterprise
        </div>
      </div>

      {/* Right panel — form */}
      <div style={{
        flex: 1, background: '#f8fafc', display: 'flex',
        alignItems: 'center', justifyContent: 'center', padding: 32,
      }}>
        <div style={{ width: '100%', maxWidth: 380 }}>
          <div style={{ marginBottom: 32 }}>
            <h2 style={{ fontSize: 24, fontWeight: 700, color: '#0f1e2a', marginBottom: 8, letterSpacing: '-0.3px' }}>
              Sign in to Werkflow
            </h2>
            <p style={{ fontSize: 14, color: '#6b7e8c' }}>
              Use your organisation credentials to continue.
            </p>
          </div>

          <form action={async () => {
            "use server"
            await signIn("keycloak", { redirectTo: "/dashboard" }, { prompt: "login" })
          }}>
            <button
              type="submit"
              style={{
                width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10,
                background: '#149ba5', color: '#fff',
                padding: '13px 24px', borderRadius: 10,
                fontSize: 14, fontWeight: 600, border: 'none', cursor: 'pointer',
                fontFamily: 'inherit', letterSpacing: '0.01em',
                boxShadow: '0 4px 14px rgba(20,155,165,0.25)',
                transition: 'background .15s',
              }}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4M10 17l5-5-5-5M15 12H3" />
              </svg>
              Sign in with Keycloak
            </button>
          </form>

          <p style={{ marginTop: 24, fontSize: 12, color: '#94a8b3', textAlign: 'center', lineHeight: 1.6 }}>
            By signing in you agree to the{' '}
            <a href="/legal/terms" style={{ color: '#149ba5', textDecoration: 'none', fontWeight: 500 }}>Terms of Use</a>
            {' '}and{' '}
            <a href="/legal/privacy" style={{ color: '#149ba5', textDecoration: 'none', fontWeight: 500 }}>Privacy Policy</a>.
          </p>
        </div>
      </div>
    </div>
  )
}
