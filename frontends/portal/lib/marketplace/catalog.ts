/**
 * Static marketplace catalog — seed connectors for M4.8.
 *
 * In a future milestone this list can be fetched from a remote URL
 * (e.g. the raw GitHub URL of the werkflow-public marketplace directory).
 * For M4.8, the catalog is statically imported to avoid any additional
 * network dependency at runtime.
 */

export type ConnectorCategory =
  | 'data-source'
  | 'notification'
  | 'ai'
  | 'storage'
  | 'messaging'
  | 'identity'
  | 'custom'

export type TransportType =
  | 'rest'
  | 'database'
  | 'graphql'
  | 'webhook'
  | 'mcp'
  | 'messaging'
  | 'grpc'
  | 'file'
  | 'mock'

export type AuthType =
  | 'none'
  | 'bearer'
  | 'basic'
  | 'api-key'
  | 'oauth2-client-credentials'
  | 'mtls'

/** Slim summary used on the marketplace card — not the full ConnectorDefinition envelope. */
export interface MarketplaceConnector {
  /** Stable key — matches metadata.key in the full connector definition. */
  key: string
  displayName: string
  description: string
  version: string
  category: ConnectorCategory
  tags: string[]
  vendor: string
  /** 'official' connectors are maintained by the Werkflow core team. */
  source: 'official' | 'community'
  transport: TransportType
  /** Primary auth type advertised on the card. */
  primaryAuth: AuthType
  /** Number of operations defined in the full connector definition. */
  operationCount: number
  /** SPDX license expression. */
  license: string
  documentationUrl?: string
}

/** The static marketplace catalog. */
export const MARKETPLACE_CATALOG: MarketplaceConnector[] = [
  {
    key: 'werkflow-erp',
    displayName: 'Werkflow ERP',
    description:
      'Official connector for the Werkflow ERP system. Provides access to HR, Procurement, Inventory, and Finance endpoints.',
    version: '1.0.0',
    category: 'data-source',
    tags: ['erp', 'hr', 'procurement', 'inventory', 'finance'],
    vendor: 'Werkflow',
    source: 'official',
    transport: 'rest',
    primaryAuth: 'api-key',
    operationCount: 7,
    license: 'Apache-2.0',
    documentationUrl:
      'https://github.com/werkflow-platform/werkflow-public/tree/main/marketplace/connectors/werkflow-erp',
  },
  {
    key: 'slack-web-api',
    displayName: 'Slack',
    description:
      'Send messages and list channels via the Slack Web API. Requires a Slack Bot Token with chat:write and channels:read scopes.',
    version: '1.0.0',
    category: 'notification',
    tags: ['slack', 'messaging', 'notification'],
    vendor: 'Slack Technologies',
    source: 'community',
    transport: 'rest',
    primaryAuth: 'bearer',
    operationCount: 2,
    license: 'Apache-2.0',
    documentationUrl: 'https://api.slack.com/methods',
  },
  {
    key: 'github-rest-api',
    displayName: 'GitHub',
    description:
      'Interact with GitHub repositories. Supports creating issues, listing repositories, and inspecting pull requests.',
    version: '1.0.0',
    category: 'custom',
    tags: ['github', 'git', 'devops', 'issues'],
    vendor: 'GitHub, Inc.',
    source: 'community',
    transport: 'rest',
    primaryAuth: 'bearer',
    operationCount: 3,
    license: 'Apache-2.0',
    documentationUrl: 'https://docs.github.com/en/rest',
  },
  {
    key: 'postgres-readonly',
    displayName: 'PostgreSQL (Read-Only)',
    description:
      'Read-only access to a PostgreSQL database via named, parameterized queries. No dynamic SQL — all queries registered at definition time.',
    version: '1.0.0',
    category: 'data-source',
    tags: ['postgresql', 'database', 'sql', 'read-only'],
    vendor: 'Community',
    source: 'community',
    transport: 'database',
    primaryAuth: 'none',
    operationCount: 3,
    license: 'Apache-2.0',
    documentationUrl:
      'https://github.com/werkflow-platform/werkflow-public/tree/main/marketplace/connectors/community/postgres-readonly',
  },
  {
    key: 'openai-chat',
    displayName: 'OpenAI Chat Completions',
    description:
      'Generate text with OpenAI GPT models. Use for classification, summarization, drafting, and extraction within workflow service tasks.',
    version: '1.0.0',
    category: 'ai',
    tags: ['openai', 'gpt', 'ai', 'llm'],
    vendor: 'OpenAI',
    source: 'community',
    transport: 'rest',
    primaryAuth: 'bearer',
    operationCount: 1,
    license: 'Apache-2.0',
    documentationUrl: 'https://platform.openai.com/docs/api-reference/chat',
  },
]

/** Returns a human-readable transport label for display. */
export function transportLabel(transport: TransportType): string {
  const labels: Record<TransportType, string> = {
    rest: 'REST',
    database: 'Database',
    graphql: 'GraphQL',
    webhook: 'Webhook',
    mcp: 'MCP',
    messaging: 'Messaging',
    grpc: 'gRPC',
    file: 'File',
    mock: 'Mock',
  }
  return labels[transport] ?? transport
}

/** Returns a human-readable auth label for display. */
export function authLabel(auth: AuthType): string {
  const labels: Record<AuthType, string> = {
    none: 'No auth',
    bearer: 'Bearer token',
    basic: 'Basic auth',
    'api-key': 'API key',
    'oauth2-client-credentials': 'OAuth2',
    mtls: 'mTLS',
  }
  return labels[auth] ?? auth
}

/** Maps a marketplace auth type to its connector authScheme. */
const AUTH_TYPE_TO_SCHEME: Partial<Record<AuthType, string>> = {
  'api-key': 'API_KEY',
  bearer: 'BEARER',
  basic: 'BASIC',
  'oauth2-client-credentials': 'OAUTH2_CLIENT_CREDENTIALS',
  none: 'NONE',
}

export function authTypeToScheme(auth: AuthType): string {
  return AUTH_TYPE_TO_SCHEME[auth] ?? 'NONE'
}

/**
 * Returns the ConnectorRequest payload (for POST /api/connectors) derived from
 * a marketplace connector. The caller supplies the baseUrl and credentialRef —
 * these are runtime deployment values that cannot be embedded in the catalog.
 * Secret material lives in an OpenBao-backed credential (Phase B.6); the connector
 * stores only a reference to it.
 */
export function buildInstallPayload(
  connector: MarketplaceConnector,
  opts: {
    baseUrl: string
    credentialRef?: string
    environment?: string
  }
): {
  connectorKey: string
  displayName: string
  baseUrl: string
  environment: string
  active: boolean
  authScheme: string
  credentialRef?: string
} {
  const authScheme = authTypeToScheme(connector.primaryAuth)
  return {
    connectorKey: connector.key,
    displayName: connector.displayName,
    baseUrl: opts.baseUrl,
    environment: opts.environment ?? 'development',
    active: true,
    authScheme,
    credentialRef: authScheme === 'NONE' ? undefined : opts.credentialRef,
  }
}
