'use client'

import { useEffect, useState } from 'react'
import { useSession, signIn } from 'next-auth/react'
import { useAuth } from '@/lib/auth/auth-context'
import { onTokenExpired } from '@/lib/auth/token-expired-event'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'

export function TokenExpiredDialog() {
  const [isOpen, setIsOpen] = useState(false)
  const [isLoading, setIsLoading] = useState(false)
  const { data: session } = useSession()
  const { login } = useAuth()

  // Auto-redirect on token refresh failure (e.g., due to backend redeployment)
  useEffect(() => {
    if ((session as any)?.error === 'RefreshAccessTokenError') {
      signIn('keycloak', { callbackUrl: window.location.pathname })
    }
  }, [(session as any)?.error])

  useEffect(() => {
    // If we have a valid session, close the dialog
    if (session?.user && (session as any)?.accessToken) {
      setIsOpen(false)
      return
    }

    // Subscribe to token expired events
    const unsubscribe = onTokenExpired(() => {
      // Only show dialog if we don't have a valid session
      if (!session?.user) {
        setIsOpen(true)
      }
    })

    return unsubscribe
  }, [session])

  const handleReLogin = async () => {
    setIsLoading(true)
    try {
      await login()
      // Dialog will auto-close when session is updated
    } catch (error) {
      console.error('Re-login failed:', error)
      setIsLoading(false)
    }
  }

  const handleDismiss = () => {
    setIsOpen(false)
  }

  return (
    <Dialog open={isOpen} onOpenChange={setIsOpen}>
      <DialogContent className="sm:max-w-[400px]">
        <DialogHeader>
          <DialogTitle>Session Expired</DialogTitle>
          <DialogDescription>
            Your authentication session has expired. Please log in again to continue.
          </DialogDescription>
        </DialogHeader>

        <DialogFooter className="gap-2">
          <Button
            variant="outline"
            onClick={handleDismiss}
            disabled={isLoading}
          >
            Dismiss
          </Button>
          <Button
            onClick={handleReLogin}
            disabled={isLoading}
          >
            {isLoading ? 'Logging in...' : 'Log In Again'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
