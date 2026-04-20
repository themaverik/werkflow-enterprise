'use client'

import { Button } from '@/components/ui/button'
import { Globe } from 'lucide-react'

/**
 * Language switcher stub — English only for the Community release.
 *
 * To add a new language:
 * 1. Copy `messages/en.json` and translate all values
 * 2. Add the locale to `next.config.mjs` and `i18n/request.ts`
 * 3. Replace this stub with a locale selector (see docs/TRANSLATION-GUIDE.md)
 */
export function LanguageSwitcher() {
  return (
    <Button variant="ghost" size="sm" disabled className="gap-1.5 text-xs">
      <Globe className="h-4 w-4" />
      EN
    </Button>
  )
}
