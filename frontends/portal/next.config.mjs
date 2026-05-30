import createNextIntlPlugin from 'next-intl/plugin'

const withNextIntl = createNextIntlPlugin('./i18n/request.ts')

// 'standalone' bundles the server for Docker; Netlify's Next.js plugin handles its own output.
const isDockerBuild = process.env.DOCKER_BUILD === 'true'

// Backend base URLs — override in Netlify env vars to point at the deployed backend.
const engineBaseUrl = process.env.ENGINE_BASE_URL || 'http://localhost:8081'
const adminBaseUrl = process.env.ADMIN_BASE_URL || 'http://localhost:8083'

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
