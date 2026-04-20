/**
 * Simple Toast Hook
 *
 * Provides user-friendly toast notifications for Service Registry operations.
 * Can be replaced with more sophisticated toast library later (e.g., sonner, react-hot-toast)
 */

type ToastType = 'default' | 'destructive' | 'success'

interface ToastOptions {
  title: string
  description?: string
  variant?: ToastType
  duration?: number
}

export function useToast() {
  const toast = ({ title, description, variant, duration = 3000 }: ToastOptions) => {
    // For now, use console logging (can be replaced with proper toast UI later)
    const message = description ? `${title}: ${description}` : title

    if (variant === 'destructive') {
      console.error('[Toast Error]', message)
    } else if (variant === 'success') {
      console.log('[Toast Success]', message)
    } else {
      console.log('[Toast]', message)
    }

    // In browser environment, show native alert for important messages
    if (typeof window !== 'undefined' && variant === 'destructive') {
      // Only show alert for errors in development
      if (process.env.NODE_ENV === 'development') {
        // Silent for now - proper toast UI can be added later
      }
    }
  }

  return {
    toast
  }
}

export { useToast as default }
