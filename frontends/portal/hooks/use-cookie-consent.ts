'use client'

import { useState, useEffect, useCallback } from 'react'

export interface ConsentPreferences {
  essential: true
  analytics: boolean
  preferences: boolean
  marketing: boolean
  timestamp: string
  version: string
}

const CONSENT_KEY = 'werkflow_consent'
const CONSENT_VERSION = '1.0'

export function useCookieConsent() {
  const [consent, setConsentState] = useState<ConsentPreferences | null>(null)
  const [loaded, setLoaded] = useState(false)

  useEffect(() => {
    try {
      const stored = localStorage.getItem(CONSENT_KEY)
      if (stored) {
        const parsed = JSON.parse(stored) as ConsentPreferences
        if (parsed.version === CONSENT_VERSION) {
          setConsentState(parsed)
        }
      }
    } catch {
      // ignore parse errors — treat as no consent given
    }
    setLoaded(true)
  }, [])

  const saveConsent = useCallback(
    (prefs: { analytics: boolean; preferences: boolean; marketing: boolean }) => {
      const next: ConsentPreferences = {
        essential: true,
        analytics: prefs.analytics,
        preferences: prefs.preferences,
        marketing: prefs.marketing,
        timestamp: new Date().toISOString(),
        version: CONSENT_VERSION,
      }
      localStorage.setItem(CONSENT_KEY, JSON.stringify(next))
      setConsentState(next)
    },
    [],
  )

  const acceptAll = useCallback(() => {
    saveConsent({ analytics: true, preferences: true, marketing: true })
  }, [saveConsent])

  const rejectNonEssential = useCallback(() => {
    saveConsent({ analytics: false, preferences: false, marketing: false })
  }, [saveConsent])

  return {
    consent,
    loaded,
    hasConsented: loaded && consent !== null,
    needsBanner: loaded && consent === null,
    acceptAll,
    rejectNonEssential,
    saveConsent,
  }
}
