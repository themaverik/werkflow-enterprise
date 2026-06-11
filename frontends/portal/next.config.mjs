import createNextIntlPlugin from 'next-intl/plugin'

const withNextIntl = createNextIntlPlugin('./i18n/request.ts')

// 'standalone' bundles the server for Docker; Netlify's Next.js plugin handles its own output.
const isDockerBuild = process.env.DOCKER_BUILD === 'true'

// Backend base URLs — override in Netlify env vars to point at the deployed backend.
const engineBaseUrl = process.env.ENGINE_BASE_URL || 'http://localhost:8081'
const adminBaseUrl = process.env.ADMIN_BASE_URL || 'http://localhost:8083'
// KC public URL — browser makes direct cross-origin requests (not proxied through Next.js).
const keycloakPublicUrl = process.env.NEXT_PUBLIC_KEYCLOAK_URL || 'http://localhost:8090'
// API base origin — used by any client code that calls NEXT_PUBLIC_API_URL directly.
const apiBaseOrigin = (process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api').replace(/\/api.*$/, '')

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  swcMinify: true,
  ...(isDockerBuild ? { output: 'standalone' } : {}),
  env: {
    NEXT_PUBLIC_APP_NAME: process.env.NEXT_PUBLIC_APP_NAME || 'Werkflow Portal',
    NEXT_PUBLIC_API_URL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api',
  },
  async headers() {
    return [
      {
        source: '/(.*)',
        headers: [
          { key: 'X-Frame-Options', value: 'DENY' },
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
          {
            key: 'Permissions-Policy',
            value: 'camera=(), microphone=(), geolocation=(), payment=()',
          },
          {
            key: 'Strict-Transport-Security',
            value: 'max-age=63072000; includeSubDomains; preload',
          },
          {
            key: 'Content-Security-Policy',
            // unsafe-eval required by bpmn-js/dmn-js rendering pipeline (accepted trade-off).
            // unsafe-inline required by bpmn-js Preact renderer and shadcn CSS tokens.
            value: `default-src 'self'; script-src 'self' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self'; worker-src 'self' blob:; connect-src 'self' ${engineBaseUrl} ${adminBaseUrl} ${keycloakPublicUrl} ${apiBaseOrigin}; frame-ancestors 'none'; object-src 'none'; base-uri 'self'`,
          },
        ],
      },
    ]
  },
  async rewrites() {
    return [
      {
        source: '/api/engine/:path*',
        destination: `${engineBaseUrl}/api/:path*`,
      },
      {
        source: '/api/admin/:path*',
        destination: `${adminBaseUrl}/api/:path*`,
      },
    ]
  },
  webpack: (config, { isServer }) => {
    config.module.rules.push({
      test: /\.bpmn$/,
      use: 'raw-loader',
    })

    if (isServer) {
      config.externals = [...(config.externals || []), 'formiojs', 'bpmn-js', 'dmn-js', '@formio/react']
    }

    return config
  },
}

export default withNextIntl(nextConfig)
