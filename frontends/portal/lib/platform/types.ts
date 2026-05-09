// Platform Semantics Service — shared type definitions
// Used across BPMN, DMN, and Form designers and admin pages.

export interface CandidateGroupEntry {
  key: string
  label: string
  kind: 'SYSTEM' | 'BUSINESS'
  tier: 1 | 2
  readOnly: boolean
  isManagerTier: boolean
  mappedFromRoles: string[]
}

export interface CategoryEntry {
  id: string
  code: string
  displayName: string
  icon: string | null
  color: string | null
  displayOrder: number
  artifactCount: number
}

export interface TagEntry {
  tag: string
  usageCount: number
}

export interface VisibilityPolicyEntry {
  managerScope: 'OWN_DEPT' | 'ALL_DEPTS'
  managerTierGroups: string[]
}

export interface DepartmentEntry {
  code: string
  displayName: string
  memberCount: number
}

export interface FeelExpressionCatalog {
  configVars: {
    monetary: Array<{
      key: string
      label: string
      value: string
      currency: string
      feelExpressions: string[]
    }>
  }
  custodyVars: {
    groupResolutions: Array<{
      key: string
      candidateGroups: string[]
      feelExpression: string
    }>
    lookupExpressions: string[]
  }
}

export interface PlatformCapabilityResponse {
  tier: 'ENTERPRISE' | 'OSS'
  erpConnected: boolean
  configured: {
    configVars: {
      count: number
      types: string[]
      monetaryLevels: string[]
      currency: string
    }
    custodyVars: {
      count: number
      keys: string[]
    }
    roleMappings: {
      tier1Count: number
      tier2Count: number
      managerTierGroups: string[]
    }
    departments: { count: number }
    categories: { count: number }
    visibilityPolicy: { managerScope: string }
  }
}

export interface ArtifactMetadata {
  departmentCode?: string
  categoryCode?: string
  tags: string[]
}

export interface CategoryRequest {
  displayName: string
  code: string
  icon?: string
  color?: string
  displayOrder?: number
}

export interface LocaleEntry {
  currencyCode: string
  locale: string
  numberFormat: string
  dateFormat: string
  timezone: string
}
