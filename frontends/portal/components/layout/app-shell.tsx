import { UserMenu } from '@/components/auth/user-menu'
import { Sidebar } from '@/components/layout/sidebar'
import Link from 'next/link'

export function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen flex flex-col">
      <header className="sticky top-0 z-50 w-full border-b text-white" style={{ backgroundColor: '#24303a' }}>
        <div className="flex h-14 items-center px-4">
          <Link href="/" className="mr-6 flex items-center">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src="/werkflow-logo.png"
              alt="Werkflow"
              style={{ height: '36px', width: 'auto' }}
            />
          </Link>
          <div className="flex flex-1 items-center justify-end space-x-2">
            <UserMenu />
          </div>
        </div>
      </header>
      <div className="flex flex-1">
        <Sidebar />
        <main className="flex-1 p-6">{children}</main>
      </div>
    </div>
  )
}
