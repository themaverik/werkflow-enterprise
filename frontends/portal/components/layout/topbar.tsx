import { Bell, Search } from 'lucide-react'
import { UserMenu } from '@/components/auth/user-menu'

export function TopBar() {
  return (
    <header
      className="h-14 border-b flex items-center px-6 gap-4 shrink-0 bg-card"
      style={{ borderColor: 'hsl(var(--border))' }}
    >
      <div className="flex-1 flex items-center gap-2 max-w-sm">
        <div className="relative flex-1">
          <Search
            size={14}
            className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground"
          />
          <input
            placeholder="Search..."
            className="w-full pl-8 pr-3 py-1.5 text-sm rounded-lg bg-background border border-border focus:outline-none focus:ring-2 focus:ring-primary/40"
          />
        </div>
      </div>
      <div className="ml-auto flex items-center gap-3">
        <button className="relative p-2 rounded-lg hover:bg-muted transition-colors">
          <Bell size={18} className="text-muted-foreground" />
        </button>
        <UserMenu />
      </div>
    </header>
  )
}
