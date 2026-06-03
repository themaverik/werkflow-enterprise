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
              Sign in
            </button>
          </form>

          <div style={{ display: 'flex', alignItems: 'center', gap: 12, margin: '24px 0' }}>
            <div style={{ flex: 1, height: 1, background: '#e2eaee' }} />
            <span style={{ fontSize: 12, color: '#a8b9c4', whiteSpace: 'nowrap' }}>or continue with</span>
            <div style={{ flex: 1, height: 1, background: '#e2eaee' }} />
          </div>

          <form action={async () => {
            "use server"
            await signIn("keycloak", { redirectTo: "/dashboard" }, { kc_idp_hint: "google" })
          }}>
            <button
              type="submit"
              style={{
                width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10,
                background: '#fff', color: '#374151',
                padding: '11px 24px', borderRadius: 10,
                fontSize: 14, fontWeight: 500, border: '1.5px solid #e2eaee', cursor: 'pointer',
                fontFamily: 'inherit', letterSpacing: '0.01em',
                transition: 'border-color .15s',
              }}
            >
              <svg width="18" height="18" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
                <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
                <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
                <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
              </svg>
              Sign in with Google
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
