'use client'

import { Bell } from 'lucide-react'
import { UserMenu } from '@/components/auth/user-menu'

export function TopBar() {
  return (
    <header
      className="h-14 border-b flex items-center px-6 gap-4 shrink-0 bg-card"
      style={{ borderColor: 'hsl(var(--border))' }}
    >
      <div className="ml-auto flex items-center gap-3">
        <button aria-label="Notifications" className="relative p-2 rounded-lg hover:bg-muted transition-colors">
          <Bell size={18} className="text-muted-foreground" />
        </button>
        <UserMenu />
      </div>
    </header>
  )
}
