'use client'

import { useCookieConsent } from '@/hooks/use-cookie-consent'

interface Category {
  id: keyof Pick<ReturnType<typeof useCookieConsent>['consent'] & object, 'analytics' | 'preferences' | 'marketing'>
  label: string
  description: string
  required: boolean
}

const CATEGORIES: Category[] = [
  {
    id: 'analytics',
    label: 'Analytics',
    description:
      'Helps us understand how the platform is used (page views, workflow completions, task durations). Data is aggregated and processed internally — no third-party analytics services are used.',
    required: false,
  },
  {
    id: 'preferences',
    label: 'Preferences',
    description:
      'Remembers UI settings such as sidebar state and selected locale so they persist across sessions.',
    required: false,
  },
  {
    id: 'marketing',
    label: 'Marketing',
    description:
      'Currently unused. Reserved for future opt-in communication preferences.',
    required: false,
  },
]

export default function CookieSettingsPage() {
  const { consent, loaded, acceptAll, rejectNonEssential, saveConsent } = useCookieConsent()

  function toggle(id: 'analytics' | 'preferences' | 'marketing') {
    const current = consent ?? { analytics: false, preferences: false, marketing: false }
    saveConsent({
      analytics: id === 'analytics' ? !current.analytics : (current.analytics ?? false),
      preferences: id === 'preferences' ? !current.preferences : (current.preferences ?? false),
      marketing: id === 'marketing' ? !current.marketing : (current.marketing ?? false),
    })
  }

  return (
    <article style={prose}>
      <h1 style={h1Style}>Cookie Settings</h1>
      <p style={metaStyle}>Manage your cookie preferences below. Changes take effect immediately and are saved in your browser.</p>

      <section style={{ marginBottom: 36 }}>
        <h2 style={h2Style}>What are cookies?</h2>
        <p>Cookies and similar technologies (such as localStorage) are small pieces of data stored in your browser. The Werkflow Portal uses them to keep you signed in, remember your preferences, and understand how the platform is being used.</p>
        <p>Because this is an enterprise portal accessed only after authentication, there are no third-party advertising or tracking cookies. The categories below reflect what this platform actually sets.</p>
      </section>

      <section style={{ marginBottom: 36 }}>
        <h2 style={h2Style}>Manage preferences</h2>

        {/* Essential — always on */}
        <div style={cardStyle}>
          <div style={cardHeader}>
            <div>
              <span style={categoryLabel}>Essential</span>
              <span style={requiredBadge}>Always active</span>
            </div>
            <ToggleSwitch checked={true} disabled />
          </div>
          <p style={cardDesc}>
            Required for the platform to function. Includes your Keycloak session token, CSRF protection, and the consent preference stored here. Cannot be disabled.
          </p>
          <CookieTable rows={[
            { name: 'next-auth.session-token', purpose: 'Authentication session', duration: 'Session / configurable' },
            { name: 'next-auth.csrf-token', purpose: 'CSRF protection', duration: 'Session' },
            { name: 'werkflow_consent (localStorage)', purpose: 'Stores your cookie consent preferences', duration: 'Persistent — until cleared' },
          ]} />
        </div>

        {/* Dynamic categories */}
        {CATEGORIES.map((cat) => {
          const isEnabled = loaded && consent !== null ? (consent[cat.id] ?? false) : false
          return (
            <div key={cat.id} style={cardStyle}>
              <div style={cardHeader}>
                <span style={categoryLabel}>{cat.label}</span>
                <ToggleSwitch
                  checked={isEnabled}
                  disabled={!loaded}
                  onChange={() => toggle(cat.id as 'analytics' | 'preferences' | 'marketing')}
                />
              </div>
              <p style={cardDesc}>{cat.description}</p>
            </div>
          )
        })}
      </section>

      <section style={{ marginBottom: 36 }}>
        <h2 style={h2Style}>Bulk actions</h2>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          <button onClick={rejectNonEssential} style={secondaryBtn}>Reject non-essential</button>
          <button onClick={acceptAll} style={primaryBtn}>Accept all</button>
        </div>
      </section>

      <section style={{ marginBottom: 36 }}>
        <h2 style={h2Style}>Your rights</h2>
        <p>You can change or withdraw your consent at any time on this page. For GDPR data subject rights (access, erasure, portability) and CCPA requests, see our <a href="/legal/privacy" style={linkStyle}>Privacy Policy</a> or email <a href="mailto:support@werkflow.cloud" style={linkStyle}>support@werkflow.cloud</a>.</p>
        <p>California residents: we do not sell or share your personal information for cross-context behavioural advertising.</p>
      </section>
    </article>
  )
}

function ToggleSwitch({
  checked,
  disabled,
  onChange,
}: {
  checked: boolean
  disabled?: boolean
  onChange?: () => void
}) {
  return (
    <button
      role="switch"
      aria-checked={checked}
      disabled={disabled}
      onClick={onChange}
      style={{
        width: 44,
        height: 24,
        borderRadius: 12,
        border: 'none',
        background: checked ? '#149ba5' : '#cbd5e1',
        cursor: disabled ? 'not-allowed' : 'pointer',
        position: 'relative',
        flexShrink: 0,
        transition: 'background .15s',
        outline: 'none',
      }}
    >
      <span
        style={{
          position: 'absolute',
          top: 3,
          left: checked ? 23 : 3,
          width: 18,
          height: 18,
          borderRadius: '50%',
          background: '#fff',
          boxShadow: '0 1px 3px rgba(0,0,0,0.2)',
          transition: 'left .15s',
        }}
      />
    </button>
  )
}

function CookieTable({ rows }: { rows: { name: string; purpose: string; duration: string }[] }) {
  return (
    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13, marginTop: 12 }}>
      <thead>
        <tr>
          <th style={thStyle}>Name</th>
          <th style={thStyle}>Purpose</th>
          <th style={thStyle}>Duration</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r) => (
          <tr key={r.name} style={{ borderBottom: '1px solid #e2e8f0' }}>
            <td style={tdStyle}><code style={{ fontSize: 12, background: '#f1f5f9', padding: '1px 4px', borderRadius: 3 }}>{r.name}</code></td>
            <td style={tdStyle}>{r.purpose}</td>
            <td style={tdStyle}>{r.duration}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

const prose: React.CSSProperties = { color: '#1a2e3a', lineHeight: 1.7, fontSize: 15 }
const h1Style: React.CSSProperties = { fontSize: 28, fontWeight: 700, color: '#0f1e2a', marginBottom: 4, letterSpacing: '-0.4px' }
const h2Style: React.CSSProperties = { fontSize: 17, fontWeight: 600, color: '#0f1e2a', marginBottom: 12, paddingBottom: 6, borderBottom: '1px solid #e2e8f0' }
const metaStyle: React.CSSProperties = { fontSize: 13, color: '#64748b', marginBottom: 40 }
const linkStyle: React.CSSProperties = { color: '#149ba5', textDecoration: 'underline' }
const cardStyle: React.CSSProperties = { border: '1px solid #e2e8f0', borderRadius: 10, padding: 20, marginBottom: 16, background: '#fff' }
const cardHeader: React.CSSProperties = { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }
const categoryLabel: React.CSSProperties = { fontWeight: 600, fontSize: 15, color: '#0f1e2a', marginRight: 10 }
const requiredBadge: React.CSSProperties = { fontSize: 11, background: '#f0fdf4', color: '#166534', padding: '2px 8px', borderRadius: 20, fontWeight: 500 }
const cardDesc: React.CSSProperties = { fontSize: 13, color: '#475569', margin: '0 0 8px' }
const thStyle: React.CSSProperties = { textAlign: 'left', fontWeight: 600, padding: '6px 10px', background: '#f8fafc', color: '#374151', borderBottom: '2px solid #e2e8f0' }
const tdStyle: React.CSSProperties = { padding: '6px 10px', verticalAlign: 'top', color: '#374151' }
const primaryBtn: React.CSSProperties = { padding: '9px 20px', borderRadius: 8, border: 'none', background: '#149ba5', color: '#fff', fontSize: 14, fontWeight: 600, cursor: 'pointer', fontFamily: 'inherit' }
const secondaryBtn: React.CSSProperties = { padding: '9px 20px', borderRadius: 8, border: '1px solid #cbd5e1', background: '#fff', color: '#374151', fontSize: 14, fontWeight: 500, cursor: 'pointer', fontFamily: 'inherit' }
