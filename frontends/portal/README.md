# Werkflow Portal

Next.js 15 portal for process design, form authoring, task management, and tenant administration.

## Technology Stack

- Next.js 15 (App Router)
- React 18, TypeScript 5
- Tailwind CSS + shadcn/ui
- bpmn-js (BPMN designer), dmn-js (DMN editor), Form.js (form builder)
- React Query (server state)
- NextAuth v5 (Keycloak PKCE-disabled, confidential client)

## Port

- **3000** — development server

## Getting Started

```bash
cd frontends/portal
npm install
cp .env.local.example .env.local
# edit .env.local (see below)
npm run dev
```

## Key Environment Variables

| Variable | Description |
|----------|-------------|
| `NEXTAUTH_URL` | Public URL of this app (e.g. `http://localhost:3000`) |
| `AUTH_SECRET` | NextAuth session secret (32+ chars) |
| `KEYCLOAK_CLIENT_ID` | Keycloak client ID (`werkflow-portal`) |
| `KEYCLOAK_CLIENT_SECRET` | Must match realm.json exactly (32 chars — truncation causes silent 401) |
| `KEYCLOAK_ISSUER` | `http://localhost:8090/realms/werkflow` |
| `ADMIN_SERVICE_URL` | Internal admin service URL (e.g. `http://localhost:8083`) |
| `ENGINE_SERVICE_URL` | Internal engine URL (e.g. `http://localhost:8081`) |
| `ERP_SERVICE_URL` | Internal ERP URL (e.g. `http://localhost:8084`) |

## Key Routes

| Route | Description |
|-------|-------------|
| `/processes` | BPMN process list (ADR-010 visibility-filtered) |
| `/processes/[key]/design` | BPMN designer with properties panel, expression builder, custody panel |
| `/forms` | Form schema list |
| `/forms/[key]/design` | Form.js editor |
| `/decisions` | DMN decision list |
| `/services` | Service catalog (PSS category + dept + visibility filter) |
| `/tasks`, `/requests` | Task inbox and submitted requests |
| `/admin/tenant/*` | Tenant setup: approval authority, role mappings, datasources, custody groups |
| `/admin/connectors` | Connector registry |

## Build

```bash
npm run build   # TypeScript gate — catches type errors without a running server
npm start       # production server
```

## Troubleshooting

**Port 3000 in use:**
```bash
lsof -ti:3000 | xargs kill -9
```

**NextAuth 401 with empty roles:** Check `KEYCLOAK_CLIENT_SECRET` matches `realm.json` exactly (32 chars).

**bpmn-js properties panel styles not applying:** Properties panel uses Preact internally — Tailwind classes are not injected. Use inline styles for any component rendered inside the panel.
