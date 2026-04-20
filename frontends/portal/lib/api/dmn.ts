import { apiClient } from './client'

// ---- types ----

export interface DmnDecisionDto {
  id: string
  key: string
  name: string
  version: number
  deploymentId: string
  tenantId: string
  deployedAt: string
}

export interface DmnExecutionDto {
  id: string
  decisionKey: string
  decisionName: string
  inputs: Record<string, unknown>
  outputs: Record<string, unknown>
  matchedRuleCount: number
  processInstanceId: string | null
  executedAt: string
}

export interface DmnTestResultDto {
  inputs: Record<string, unknown>
  resultList: Array<Record<string, unknown>>
  matchedRuleCount: number
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// ---- API functions ----

export async function listDecisions(): Promise<DmnDecisionDto[]> {
  const response = await apiClient.get<DmnDecisionDto[]>('/api/v1/dmn/decisions')
  return response.data
}

export async function getDecision(key: string): Promise<DmnDecisionDto> {
  const response = await apiClient.get<DmnDecisionDto>(`/api/v1/dmn/decisions/${key}`)
  return response.data
}

export async function getDecisionXml(key: string): Promise<string> {
  const response = await apiClient.get<string>(`/api/v1/dmn/decisions/${key}/xml`, {
    headers: { Accept: 'application/xml' },
    responseType: 'text',
  })
  return response.data
}

export async function deployDecision(name: string, dmnXml: string): Promise<DmnDecisionDto> {
  const response = await apiClient.post<DmnDecisionDto>('/api/v1/dmn/decisions', {
    name,
    dmnXml,
  })
  return response.data
}

export async function redeployDecision(
  key: string,
  name: string,
  dmnXml: string
): Promise<DmnDecisionDto> {
  const response = await apiClient.put<DmnDecisionDto>(`/api/v1/dmn/decisions/${key}`, {
    name,
    dmnXml,
  })
  return response.data
}

export async function deleteDeployment(deploymentId: string): Promise<void> {
  await apiClient.delete(`/api/v1/dmn/decisions/deployment/${deploymentId}`)
}

export async function testDecision(
  key: string,
  inputs: Record<string, unknown>
): Promise<DmnTestResultDto> {
  const response = await apiClient.post<DmnTestResultDto>(
    `/api/v1/dmn/decisions/${key}/test`,
    inputs
  )
  return response.data
}

export async function getExecutionHistory(
  key: string,
  page = 0,
  size = 20
): Promise<PageResponse<DmnExecutionDto>> {
  const response = await apiClient.get<PageResponse<DmnExecutionDto>>(
    `/api/v1/dmn/decisions/${key}/executions`,
    { params: { page, size } }
  )
  return response.data
}
