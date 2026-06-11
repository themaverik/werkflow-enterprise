'use client'

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { SessionProvider, useSession } from 'next-auth/react'
import { useRef, useState } from 'react'
import { setApiClientToken } from '@/lib/api/client'
import { AuthProvider } from '@/lib/auth/auth-context'
import { TooltipProvider } from '@/components/ui/tooltip'
import { TokenExpiredDialog } from '@/components/auth/token-expired-dialog'

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 60 * 1000, // 1 minute
            refetchOnWindowFocus: false,
          },
        },
      })
  )

  return (
    <SessionProvider>
      <AuthProvider>
        <QueryClientProvider client={queryClient}>
          <TooltipProvider delayDuration={300}>
            <ApiTokenProvider>
              <TokenExpiredDialog />
              {children}
            </ApiTokenProvider>
          </TooltipProvider>
        </QueryClientProvider>
      </AuthProvider>
    </SessionProvider>
  )
}

// Separate component to handle API client token initialization
function ApiTokenProvider({ children }: { children: React.ReactNode }) {
  const { data: session } = useSession()
  const tokenRef = useRef<string | null>(null)
  // Update ref synchronously on every render so the getter always reads the current token
  tokenRef.current = session?.accessToken || null

  // Register synchronously during render so the getter is available before any child effects fire
  setApiClientToken(async () => tokenRef.current)

  return <>{children}</>
}
