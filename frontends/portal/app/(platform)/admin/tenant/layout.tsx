import { auth } from '@/auth'
import { redirect } from 'next/navigation'

const ALLOWED_ROLES = ['ADMIN', 'SUPER_ADMIN']

export default async function TenantAdminLayout({ children }: { children: React.ReactNode }) {
  const session = await auth()
  const roles: string[] = (session?.user as { roles?: string[] })?.roles ?? []
  const isAllowed = roles.some((r) => ALLOWED_ROLES.includes(r))

  if (!isAllowed) {
    redirect('/dashboard')
  }

  return <>{children}</>
}
