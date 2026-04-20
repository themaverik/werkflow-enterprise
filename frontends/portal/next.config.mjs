import createNextIntlPlugin from 'next-intl/plugin'

const withNextIntl = createNextIntlPlugin('./i18n/request.ts')

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  swcMinify: true,
  output: 'standalone',
  env: {
    NEXT_PUBLIC_APP_NAME: process.env.NEXT_PUBLIC_APP_NAME || 'Werkflow Portal',
    NEXT_PUBLIC_API_URL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api',
  },
  async rewrites() {
    return [
      {
        source: '/api/engine/:path*',
        destination: 'http://localhost:8081/api/:path*',
      },
      {
        source: '/api/admin/:path*',
        destination: 'http://localhost:8083/api/:path*',
      },
    ]
  },
  webpack: (config, { isServer }) => {
    config.module.rules.push({
      test: /\.bpmn$/,
      use: 'raw-loader',
    })

    if (isServer) {
      config.externals = [...(config.externals || []), 'formiojs', 'bpmn-js', '@formio/react']
    }

    return config
  },
}

export default withNextIntl(nextConfig)
