# Shared Frontend Components

Shared React components, utilities, and hooks used across all werkflow frontend applications.

## Overview

This package contains reusable UI components, utilities, and configuration shared across:
- Admin Portal (Workflow Designer)
- HR Portal
- Future department portals (Finance, Procurement, etc.)

## Contents (Planned)

### UI Components
- Common form controls
- Data tables
- Charts and graphs
- Layout components
- Navigation components
- Modal dialogs
- Toast notifications

### Utilities
- API client helpers
- Date/time formatters
- Form validation utilities
- Authentication utilities

### Hooks
- `useAuth` - Authentication state
- `useApi` - API calls with loading/error states
- `useForm` - Form management
- `useWorkflow` - Workflow operations

### Types
- Shared TypeScript types
- API response types
- Common interfaces

### Styles
- Shared Tailwind configuration
- Common CSS utilities
- Theme configuration

## Technology Stack

- React 18
- TypeScript
- Tailwind CSS
- shadcn/ui components
- React Hook Form
- Zod validation

## Usage

Import shared components in other frontend apps:

```typescript
import { Button, Card, DataTable } from '@werkflow/shared'
import { useAuth, useApi } from '@werkflow/shared/hooks'
import { User, Workflow } from '@werkflow/shared/types'
```

## Status

**TODO**: To be populated during Phase 1-2 as components are extracted from admin-portal
