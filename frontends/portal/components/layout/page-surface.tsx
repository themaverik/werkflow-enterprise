import type { ReactNode } from 'react'

interface PageSurfaceProps {
  children: ReactNode
  className?: string
}

/**
 * White card canvas that sits over the app's grey content background.
 * Wrap page content in this to give tables and forms a contained surface.
 *
 * Usage:
 *   <PageSurface>
 *     <h1>...</h1>
 *     ...
 *   </PageSurface>
 */
export function PageSurface({ children, className = '' }: PageSurfaceProps) {
  return (
    <div className={`bg-card border border-border rounded-lg p-6 ${className}`}>
      {children}
    </div>
  )
}
