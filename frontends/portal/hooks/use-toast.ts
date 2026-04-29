import { toast as sonnerToast } from 'sonner'

type ToastType = 'default' | 'destructive' | 'success'

interface ToastOptions {
  title: string
  description?: string
  variant?: ToastType
  duration?: number
}

export function useToast() {
  const toast = ({ title, description, variant, duration = 4000 }: ToastOptions) => {
    const message = description ? `${title}: ${description}` : title

    if (variant === 'destructive') {
      sonnerToast.error(title, { description, duration })
    } else if (variant === 'success') {
      sonnerToast.success(title, { description, duration })
    } else {
      sonnerToast(title, { description, duration })
    }
  }

  return { toast }
}

export { useToast as default }
