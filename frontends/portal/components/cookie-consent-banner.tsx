'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useCookieConsent } from '@/hooks/use-cookie-consent'

export function CookieConsentBanner() {
  const { needsBanner, acceptAll, rejectNonEssential } = useCookieConsent()
  const [dismissed, setDismissed] = useState(false)

  const visible = needsBanner && !dismissed

  function handleAcceptAll() {
    acceptAll()
    setDismissed(true)
  }

  function handleReject() {
    rejectNonEssential()
    setDismissed(true)
  }

  return (
    <div
      role="dialog"
      aria-label="Cookie consent"
      aria-modal="false"
      aria-hidden={!visible}
      suppressHydrationWarning
      style={{
        position: 'fixed',
        bottom: 0,
        left: 0,
        right: 0,
        zIndex: 9999,
        background: '#111c27',
        borderTop: '1px solid rgba(255,255,255,0.08)',
        padding: '16px 24px',
        display: visible ? 'flex' : 'none',
        alignItems: 'center',
        gap: 16,
        flexWrap: 'wrap',
        fontFamily: "'DM Sans', sans-serif",
        fontSize: 13,
        color: 'rgba(255,255,255,0.75)',
      }}
    >
      <p style={{ flex: '1 1 300px', margin: 0, lineHeight: 1.6 }}>
        We use cookies and similar technologies to operate this platform and, with your
        consent, to analyse usage. Essential cookies are always active.{' '}
        <Link
          href="/legal/cookies"
          style={{ color: '#149ba5', textDecoration: 'underline', fontWeight: 500 }}
        >
          Cookie settings
        </Link>{' '}
        &middot;{' '}
        <Link
          href="/legal/privacy"
          style={{ color: '#149ba5', textDecoration: 'underline', fontWeight: 500 }}
        >
          Privacy Policy
        </Link>
      </p>

      <div style={{ display: 'flex', gap: 8, flexShrink: 0, flexWrap: 'wrap' }}>
        <button
          onClick={handleReject}
          style={{
            padding: '8px 16px',
            borderRadius: 8,
            border: '1px solid rgba(255,255,255,0.2)',
            background: 'transparent',
            color: 'rgba(255,255,255,0.75)',
            fontSize: 13,
            fontWeight: 500,
            cursor: 'pointer',
            fontFamily: 'inherit',
            whiteSpace: 'nowrap',
          }}
        >
          Reject non-essential
        </button>
        <button
          onClick={handleAcceptAll}
          style={{
            padding: '8px 16px',
            borderRadius: 8,
            border: 'none',
            background: '#149ba5',
            color: '#fff',
            fontSize: 13,
            fontWeight: 600,
            cursor: 'pointer',
            fontFamily: 'inherit',
            whiteSpace: 'nowrap',
            boxShadow: '0 2px 8px rgba(20,155,165,0.3)',
          }}
        >
          Accept all
        </button>
      </div>
    </div>
  )
}
