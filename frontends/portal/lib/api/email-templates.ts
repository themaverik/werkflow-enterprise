import { apiClient } from './client'

export interface EmailTemplateInfo {
  key: string
  name: string
  channel: string
}

export interface EmailTemplateResponse {
  id: number
  key: string
  name: string
  channel: string
  subject: string | null
  body: string
  designJson: string | null
  linkedFormKey: string | null
  createdAt: string
  updatedAt: string
}

export interface EmailTemplateRequest {
  key: string
  name?: string
  channel: string
  subject?: string
  body: string
  designJson?: string | null
  linkedFormKey?: string | null
}

export async function listEmailTemplates(): Promise<EmailTemplateInfo[]> {
  const response = await apiClient.get('/api/notification-templates')
  return response.data ?? []
}

export async function listEmailTemplatesFull(): Promise<EmailTemplateResponse[]> {
  const response = await apiClient.get('/api/notification-templates/all')
  return response.data ?? []
}

export async function getEmailTemplate(key: string): Promise<EmailTemplateResponse> {
  const response = await apiClient.get(`/api/notification-templates/${encodeURIComponent(key)}`)
  return response.data
}

export async function createEmailTemplate(request: EmailTemplateRequest): Promise<EmailTemplateResponse> {
  const response = await apiClient.post('/api/notification-templates', request)
  return response.data
}

export async function updateEmailTemplate(key: string, request: EmailTemplateRequest): Promise<EmailTemplateResponse> {
  const response = await apiClient.put(`/api/notification-templates/${encodeURIComponent(key)}`, request)
  return response.data
}

export async function deleteEmailTemplate(key: string): Promise<void> {
  await apiClient.delete(`/api/notification-templates/${encodeURIComponent(key)}`)
}
