import { adminApiClient } from './client'

export interface DeadLetterJob {
  jobId: string
  jobType: string
  processInstanceId: string | null
  processDefinitionKey: string | null
  executionId: string | null
  tenantId: string | null
  exceptionMessage: string | null
  createTime: string | null
  dueDate: string | null
  retries: number
}

export async function listDeadLetterJobs(tenantId?: string): Promise<DeadLetterJob[]> {
  const params = tenantId ? { tenantId } : {}
  const response = await adminApiClient.get<DeadLetterJob[]>('/api/v1/admin/jobs/dead-letter', { params })
  return response.data
}

export async function retryDeadLetterJob(jobId: string, retries?: number): Promise<void> {
  await adminApiClient.post(`/api/v1/admin/jobs/dead-letter/${jobId}/retry`, retries ? { retries } : {})
}

export async function deleteDeadLetterJob(jobId: string): Promise<void> {
  await adminApiClient.delete(`/api/v1/admin/jobs/dead-letter/${jobId}`)
}
