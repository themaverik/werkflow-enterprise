import Link from 'next/link'
import type { Metadata } from 'next'

export const metadata: Metadata = {
  title: 'Werkflow — Legal',
}

export default function LegalLayout({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        minHeight: '100vh',
        background: '#f8fafc',
        display: 'flex',
        flexDirection: 'column',
        fontFamily: "'DM Sans', sans-serif",
      }}
    >
      <header
        style={{
          background: '#111c27',
          padding: '14px 40px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          borderBottom: '1px solid rgba(255,255,255,0.08)',
        }}
      >
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src="/werkflow-logo.png" alt="Werkflow" style={{ height: 36, width: 'auto' }} />
        <Link
          href="/dashboard"
          style={{
            fontSize: 13,
            color: 'rgba(255,255,255,0.6)',
            textDecoration: 'none',
            display: 'flex',
            alignItems: 'center',
            gap: 6,
          }}
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M19 12H5M12 5l-7 7 7 7" />
          </svg>
          Back to portal
        </Link>
      </header>

      <main
        style={{
          flex: 1,
          maxWidth: 760,
          width: '100%',
          margin: '0 auto',
          padding: '48px 24px 80px',
        }}
      >
        {children}
      </main>

      <footer
        style={{
          background: '#111c27',
          borderTop: '1px solid rgba(255,255,255,0.08)',
          padding: '20px 40px',
          display: 'flex',
          gap: 24,
          alignItems: 'center',
          flexWrap: 'wrap',
          fontSize: 12,
          color: 'rgba(255,255,255,0.35)',
        }}
      >
        <span>© {new Date().getFullYear()} Werkflow. All rights reserved.</span>
        <Link href="/legal/privacy" style={{ color: 'rgba(255,255,255,0.5)', textDecoration: 'none' }}>Privacy Policy</Link>
        <Link href="/legal/terms" style={{ color: 'rgba(255,255,255,0.5)', textDecoration: 'none' }}>Terms of Use</Link>
        <Link href="/legal/cookies" style={{ color: 'rgba(255,255,255,0.5)', textDecoration: 'none' }}>Cookie Settings</Link>
      </footer>
    </div>
  )
}
