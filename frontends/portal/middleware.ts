import { auth } from "@/auth"
import { NextResponse } from 'next/server'

export default auth((req) => {
  const isLoggedIn = !!req.auth
  const { pathname, search } = req.nextUrl

  // Public routes that don't require authentication.
  // CRIT-05: only /api/auth/* is public — all other /api/* routes go through auth.
  const isPublicRoute =
    pathname === '/' ||
    pathname === '/login' ||
    pathname.startsWith('/error') ||
    pathname.startsWith('/api/auth') ||
    pathname.startsWith('/_next') ||
    pathname === '/favicon.ico' ||
    pathname.startsWith('/legal')

  if (isPublicRoute) {
    return undefined
  }

  // Redirect legacy /studio/ and /portal/ URLs to their new paths
  if (pathname.startsWith('/studio/')) {
    const newPath = pathname.replace(/^\/studio/, '')
    return NextResponse.redirect(new URL(newPath + search, req.url))
  }
  if (pathname.startsWith('/portal/')) {
    const newPath = pathname.replace(/^\/portal/, '')
    return NextResponse.redirect(new URL(newPath + search, req.url))
  }

  // All other routes require authentication
  if (!isLoggedIn) {
    const loginUrl = new URL('/login', req.url)
    loginUrl.searchParams.set('callbackUrl', pathname + search)
    return NextResponse.redirect(loginUrl)
  }

  return undefined
})

export const config = {
  matcher: [
    '/((?!api|_next/static|_next/image|favicon.ico|.*\\.png|.*\\.svg|.*\\.jpg|.*\\.jpeg|.*\\.ico|.*\\.webp).*)',
  ],
}
