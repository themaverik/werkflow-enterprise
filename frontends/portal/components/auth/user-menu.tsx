'use client'

import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { useTranslations } from "next-intl"
import { useAuth } from "@/lib/auth/auth-context"
import { useAuthorization } from "@/lib/auth/use-authorization"

export function UserMenu() {
  const { user, isAuthenticated, logout } = useAuth()
  const { displayRole } = useAuthorization()
  const t = useTranslations('nav')

  if (!isAuthenticated || !user) {
    return null
  }

  const userInitials = user.username
    ? user.username
        .split(" ")
        .map((n) => n[0])
        .join("")
        .toUpperCase()
    : "U"

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" className="relative h-10 w-10 rounded-full p-0" data-testid="user-menu-trigger">
          <div className="flex h-full w-full items-center justify-center rounded-full bg-primary text-primary-foreground">
            {userInitials}
          </div>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent className="w-56" align="end" forceMount>
        <DropdownMenuLabel className="font-normal">
          <div className="flex flex-col space-y-1">
            <p className="text-sm font-medium leading-none">{user.username}</p>
            <p className="text-xs leading-none text-muted-foreground">
              {user.email}
            </p>
            {displayRole && (
              <p className="text-xs leading-none text-muted-foreground">
                {displayRole}
              </p>
            )}
          </div>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <a href="/tasks">{t('myTasks')}</a>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <a href="/requests">{t('myRequests')}</a>
        </DropdownMenuItem>
        {user.roles?.some(r => ["ADMIN", "WORKFLOW_ADMIN", "SUPER_ADMIN"].includes(r)) && (
          <>
            <DropdownMenuSeparator />
            <DropdownMenuItem asChild>
              <a href="/processes">{t('processDesigner')}</a>
            </DropdownMenuItem>
            <DropdownMenuItem asChild>
              <a href="/forms">{t('formBuilder')}</a>
            </DropdownMenuItem>
          </>
        )}
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <button
            onClick={() => logout()}
            className="w-full text-left"
            data-testid="sign-out-btn"
          >
            {t('signOut')}
          </button>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
