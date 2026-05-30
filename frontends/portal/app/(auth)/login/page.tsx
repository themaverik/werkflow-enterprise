import { signIn } from "@/auth"

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
        {/* Radial glow */}
        <div style={{
          position: 'absolute', top: '35%', left: '30%', width: 320, height: 320,
          borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(20,155,165,0.18) 0%, transparent 70%)',
          pointerEvents: 'none',
        }} />
        {/* Geometric mesh overlay */}
        <svg style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', pointerEvents: 'none', opacity: 0.06 }}
          xmlns="http://www.w3.org/2000/svg">
          <polygon points="0,0 200,0 100,160" fill="white" />
          <polygon points="200,0 400,0 300,160" fill="white" opacity="0.5" />
          <polygon points="0,160 200,160 100,320" fill="white" opacity="0.4" />
          <polygon points="100,0 300,0 200,200" fill="white" opacity="0.3" />
        </svg>

        {/* Logo */}
        <div style={{ position: 'relative' }}>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/werkflow-logo.png" alt="Werkflow" style={{ height: 70, width: 'auto', objectFit: 'contain' }} />
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
          <p style={{ fontSize: 14, color: 'rgba(255,255,255,0.5)', lineHeight: 1.7, maxWidth: 300, marginBottom: 28 }}>
            Design, deploy, and manage enterprise workflows — approvals, procurement, onboarding — from one unified platform.
          </p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {[
              ['BPMN 2.0 Process Automation', 'M9 3H5a2 2 0 0 0-2 2v4m6-6h10l4 4v10a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V3zm0 0'],
              ['DMN Decision Tables', 'M3 3h18M3 9h18M3 15h18M3 21h18'],
              ['Role-Based Task Routing', 'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2M9 3a4 4 0 1 1 0 8 4 4 0 0 1 0-8zm13 8-3 3-1.5-1.5'],
            ].map(([text]) => (
              <div key={text} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <div style={{ width: 6, height: 6, borderRadius: '50%', background: '#149ba5', flexShrink: 0 }} />
                <span style={{ fontSize: 13, color: 'rgba(255,255,255,0.6)' }}>{text}</span>
              </div>
            ))}
          </div>
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
