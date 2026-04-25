import { apiClient } from './client'

export interface ProcessInstance {
  id: string
  processDefinitionId: string
  processDefinitionKey: string
  businessKey?: string
  suspended: boolean
  ended: boolean
  startTime?: string
  variables?: Record<string, any>
}

export interface BpmnDeploymentRequest {
  name: string
  bpmnXml: string
  owningDepartment?: string
}

export interface DeploymentResponse {
  id: string
  name: string
}

export interface ProcessDefinitionResponse {
  id: string
  key: string
  name: string
  version: number
  deploymentId: string
  resourceName: string
  hasStartFormKey?: boolean
  startFormKey?: string
  owningDepartment?: string
  suspended?: boolean
}

export interface StartFormResponse {
  formKey: string
  version: number
  schema: any
  description?: string
  formType?: string
}

export interface CreateFormRequest {
  formKey: string
  schemaJson: Record<string, any>
  owningDepartment?: string
}

export interface UpdateFormRequest {
  schemaJson: Record<string, any>
}

export interface ProcessDraftRequest {
  processKey: string
  name: string
  bpmnXml: string
}

export interface ProcessDraftResponse {
  id: string
  processKey: string
  name: string
  bpmnXml: string
  createdBy: string
  updatedBy: string
  createdAt: string
  updatedAt: string
}

export type ProcessDraftSummaryResponse = Omit<ProcessDraftResponse, 'bpmnXml'>

export interface FormDefinitionResponse {
  key: string
  name: string
  formJson: string
  owningDepartment?: string
}

export interface FormVersionResponse {
  id: number
  formKey: string
  formJson: string
  version: number
  isActive: boolean
  createdAt: string
  createdBy: string
}

// Deploy BPMN process
export async function deployBpmn(data: BpmnDeploymentRequest): Promise<DeploymentResponse> {
  const response = await apiClient.post('/api/process-definitions/deploy', data)
  return response.data
}

// Get all process definitions
export async function getProcessDefinitions(): Promise<ProcessDefinitionResponse[]> {
  const response = await apiClient.get('/api/process-definitions')
  return response.data
}

// Get BPMN XML for a process definition
export async function getProcessDefinitionXml(processDefinitionId: string): Promise<string> {
  const response = await apiClient.get(`/api/process-definitions/${processDefinitionId}/xml`)
  return response.data
}

// Delete a deployment (includes all process definitions within it)
export async function deleteDeployment(deploymentId: string): Promise<void> {
  await apiClient.delete(`/api/process-definitions/deployment/${deploymentId}`)
}

export async function createForm(data: CreateFormRequest): Promise<DeploymentResponse> {
  const response = await apiClient.post('/api/forms', data)
  return response.data
}

export async function updateForm(formKey: string, schemaJson: Record<string, any>): Promise<DeploymentResponse> {
  const response = await apiClient.put(`/api/forms/${formKey}`, { schemaJson })
  return response.data
}

// Get all form definitions
export async function getFormDefinitions(): Promise<FormDefinitionResponse[]> {
  const response = await apiClient.get('/api/forms')
  return response.data
}

// Get form definition by key
export async function getFormDefinition(formKey: string): Promise<FormDefinitionResponse> {
  const response = await apiClient.get(`/api/forms/${formKey}`)
  return response.data
}

// Delete a form definition
export async function deleteFormDefinition(formKey: string): Promise<void> {
  await apiClient.delete(`/api/forms/${formKey}`)
}

// Process draft API
export async function saveDraft(processKey: string, name: string, bpmnXml: string): Promise<ProcessDraftResponse> {
  const response = await apiClient.post('/api/process-drafts', { processKey, name, bpmnXml })
  return response.data
}

export async function getDraft(processKey: string): Promise<ProcessDraftResponse | null> {
  try {
    const response = await apiClient.get(`/api/process-drafts/${processKey}`)
    return response.data
  } catch (error: any) {
    if (error?.response?.status === 404) return null
    throw error
  }
}

export async function deleteDraft(processKey: string): Promise<void> {
  await apiClient.delete(`/api/process-drafts/${processKey}`)
}

export async function listDrafts(): Promise<ProcessDraftSummaryResponse[]> {
  const response = await apiClient.get('/api/process-drafts')
  return response.data ?? []
}

// Get form version history
export async function getFormVersions(formKey: string): Promise<FormVersionResponse[]> {
  const response = await apiClient.get(`/api/forms/${formKey}/versions`)
  return response.data
}

// Rollback form to a specific version
export async function rollbackForm(formKey: string, version: number): Promise<FormDefinitionResponse> {
  const response = await apiClient.post(`/api/forms/${formKey}/rollback/${version}`)
  return response.data
}

// Get form data for a task
export async function getTaskFormData(taskId: string): Promise<any> {
  const response = await apiClient.get(`/api/v1/tasks/${taskId}/form`)
  return response.data
}

// Get start form for a process definition
export async function getProcessStartForm(processDefinitionId: string): Promise<StartFormResponse> {
  const response = await apiClient.get(`/api/process-definitions/${processDefinitionId}/start-form`)
  return response.data
}

// Get available notification templates (for BPMN designer dropdown)
export interface NotificationTemplateInfo {
  key: string
  name: string
  channel: string
}

export async function getNotificationTemplates(): Promise<NotificationTemplateInfo[]> {
  const response = await apiClient.get('/api/notification-templates')
  return response.data
}

// Get available groups (for BPMN designer candidate groups dropdown)
export interface GroupInfo {
  id: string
  name: string
}

export async function getGroups(): Promise<GroupInfo[]> {
  const response = await apiClient.get('/api/groups')
  return response.data
}

// Get registered JavaDelegate bean names (for BPMN designer delegate expression dropdown)
export async function getDelegates(): Promise<string[]> {
  const response = await apiClient.get('/api/delegates')
  return response.data
}
