import { apiClient } from './client'

export interface StartProcessRequest {
  processDefinitionKey: string
  businessKey?: string
  variables?: Record<string, any>
}

export interface ProcessInstanceResponse {
  processInstanceId: string
  processDefinitionId: string
  processDefinitionKey: string
  businessKey?: string
  suspended: boolean
  ended: boolean
  startTime?: string
  endTime?: string
  variables?: Record<string, any>
}

export interface TaskResponse {
  taskId: string
  taskName: string
  taskDefinitionKey: string
  processInstanceId: string
  processDefinitionId: string
  assignee?: string
  owner?: string
  createTime: string
  dueDate?: string
  priority: number
  suspended: boolean
  description?: string
  variables?: Record<string, any>
}

export interface CompleteTaskRequest {
  taskId: string
  variables?: Record<string, any>
  comment?: string
}

// Start a new workflow process
export async function startProcess(data: StartProcessRequest): Promise<ProcessInstanceResponse> {
  try {
    const response = await apiClient.post('/api/process-instances', data)
    return response.data
  } catch (error: any) {
    const message = error?.response?.data?.message || error?.message || 'Failed to start process'
    throw new Error(message)
  }
}

// Get process instance by ID
export async function getProcessInstance(processInstanceId: string): Promise<ProcessInstanceResponse> {
  const response = await apiClient.get(`/workflows/processes/${processInstanceId}`, {
    params: { includeVariables: true }
  })
  return response.data
}

// Get all process instances for a process definition
export async function getProcessInstances(processDefinitionKey: string): Promise<ProcessInstanceResponse[]> {
  const response = await apiClient.get(`/workflows/processes`, {
    params: { processDefinitionKey }
  })
  return response.data
}

// Get tasks by assignee
export async function getTasksByAssignee(assignee: string): Promise<TaskResponse[]> {
  const response = await apiClient.get(`/workflows/tasks/assignee/${assignee}`)
  return response.data
}

// Get tasks by group
export async function getTasksByGroup(group: string): Promise<TaskResponse[]> {
  const response = await apiClient.get(`/workflows/tasks/group/${group}`)
  return response.data
}

// Get tasks for a process instance
export async function getTasksByProcessInstance(processInstanceId: string): Promise<TaskResponse[]> {
  const response = await apiClient.get(`/workflows/tasks/process/${processInstanceId}`)
  return response.data
}

// Get task by ID
export async function getTask(taskId: string): Promise<TaskResponse> {
  const response = await apiClient.get(`/workflows/tasks/${taskId}`)
  return response.data
}

// Complete a task
export async function completeTask(data: CompleteTaskRequest): Promise<void> {
  await apiClient.post('/workflows/tasks/complete', data)
}

// Claim a task
export async function claimTask(taskId: string, userId: string): Promise<void> {
  await apiClient.post(`/workflows/tasks/${taskId}/claim`, null, {
    params: { userId }
  })
}

// Delete a process instance
export async function deleteProcessInstance(processInstanceId: string, deleteReason?: string): Promise<void> {
  await apiClient.delete(`/workflows/processes/${processInstanceId}`, {
    params: { deleteReason }
  })
}

// Get process variables
export async function getProcessVariables(processInstanceId: string): Promise<Record<string, any>> {
  const response = await apiClient.get(`/workflows/processes/${processInstanceId}/variables`)
  return response.data
}

// Set process variables
export async function setProcessVariables(processInstanceId: string, variables: Record<string, any>): Promise<void> {
  await apiClient.put(`/workflows/processes/${processInstanceId}/variables`, variables)
}

// ================================================================
// MONITORING & STATISTICS APIs
// ================================================================

export interface ProcessStatistics {
  activeProcesses: number
  completedToday: number
  failedToday: number
  avgCompletionTime: string
  totalDeployed: number
  activeUsers: number
}

export interface RunningProcessInstance {
  id: string
  processDefinitionKey: string
  processDefinitionName: string
  businessKey?: string
  startTime: string
  startedBy: string
  currentActivity: string
  status: 'active' | 'suspended'
}

export interface ActivityLogEntry {
  id: string
  type: 'completed' | 'started' | 'failed' | 'deployed'
  message: string
  timestamp: string
  user: string
}

// Get process execution statistics
export async function getProcessStatistics(): Promise<ProcessStatistics> {
  const response = await apiClient.get('/workflows/statistics')
  return response.data
}

// Get all running process instances with details
export async function getRunningProcesses(): Promise<RunningProcessInstance[]> {
  const response = await apiClient.get('/workflows/processes/running')
  return response.data
}

// Get recent activity logs
export async function getActivityLogs(limit: number = 50): Promise<ActivityLogEntry[]> {
  const response = await apiClient.get('/workflows/activity', {
    params: { limit }
  })
  return response.data
}

// Suspend a process instance
export async function suspendProcessInstance(processInstanceId: string): Promise<void> {
  await apiClient.post(`/workflows/processes/${processInstanceId}/suspend`)
}

// Activate a suspended process instance
export async function activateProcessInstance(processInstanceId: string): Promise<void> {
  await apiClient.post(`/workflows/processes/${processInstanceId}/activate`)
}

// ================================================================
// ANALYTICS APIs
// ================================================================

export interface ProcessAnalytics {
  totalProcesses: number
  avgCompletionRate: number
  avgDuration: string
  activeUsers: number
  changeFromLastPeriod: {
    processes: number
    completionRate: number
    duration: number
    users: number
  }
}

export interface ProcessMetric {
  processName: string
  processDefinitionKey: string
  totalInstances: number
  completed: number
  failed: number
  active: number
  avgDuration: string
  completionRate: number
}

export interface ActivityMetric {
  activity: string
  activityId: string
  avgDuration: string
  bottleneck: boolean
  instances: number
}

export interface ProcessTrend {
  date: string
  completed: number
  started: number
  failed: number
}

// Get process analytics overview
export async function getProcessAnalytics(timeRange: string = '7d'): Promise<ProcessAnalytics> {
  const response = await apiClient.get('/workflows/analytics/overview', {
    params: { timeRange }
  })
  return response.data
}

// Get process-specific metrics
export async function getProcessMetrics(timeRange: string = '7d'): Promise<ProcessMetric[]> {
  const response = await apiClient.get('/workflows/analytics/processes', {
    params: { timeRange }
  })
  return response.data
}

// Get activity bottleneck analysis
export async function getActivityMetrics(timeRange: string = '7d'): Promise<ActivityMetric[]> {
  const response = await apiClient.get('/workflows/analytics/activities', {
    params: { timeRange }
  })
  return response.data
}

// Get process trends over time
export async function getProcessTrends(timeRange: string = '7d'): Promise<ProcessTrend[]> {
  const response = await apiClient.get('/workflows/analytics/trends', {
    params: { timeRange }
  })
  return response.data
}

// ================================================================
// MULTI-DEPARTMENT WORKFLOW APIs
// ================================================================

export interface DepartmentWorkflowStats {
  department: string
  totalWorkflows: number
  activeWorkflows: number
  completedWorkflows: number
  failedWorkflows: number
  suspendedWorkflows: number
}

export interface WorkflowInstance {
  id: string
  processDefinitionKey: string
  processDefinitionName: string
  department: string
  businessKey?: string
  startTime: string
  endTime?: string
  startedBy: string
  currentActivity?: string
  status: 'active' | 'completed' | 'failed' | 'suspended'
  variables?: Record<string, any>
}

// Get workflow statistics for all departments
export async function getAllDepartmentStats(): Promise<DepartmentWorkflowStats[]> {
  const response = await apiClient.get('/workflows/departments/stats')
  return response.data
}

// Get workflow statistics for a specific department
export async function getDepartmentStats(department: string): Promise<DepartmentWorkflowStats> {
  const response = await apiClient.get(`/workflows/departments/${department}/stats`)
  return response.data
}

// Get workflow instances for a specific department
export async function getDepartmentWorkflows(
  department: string,
  status?: 'active' | 'completed' | 'failed' | 'suspended',
  limit: number = 20
): Promise<WorkflowInstance[]> {
  const response = await apiClient.get(`/workflows/departments/${department}/instances`, {
    params: { status, limit }
  })
  return response.data
}

// Get all workflow instances across departments
export async function getAllWorkflowInstances(
  status?: 'active' | 'completed' | 'failed' | 'suspended',
  limit: number = 20,
  startedBy?: string
): Promise<WorkflowInstance[]> {
  const response = await apiClient.get('/workflows/instances', {
    params: { status, limit, startedBy }
  })
  return response.data
}

// ================================================================
// TASK MANAGEMENT APIs (Enhanced)
// ================================================================

export interface TaskInfo {
  taskId: string
  taskName: string
  taskDefinitionKey: string
  processInstanceId: string
  processDefinitionId: string
  processDefinitionKey: string
  processDefinitionName: string
  assignee?: string
  owner?: string
  candidateGroups?: string[]
  createTime: string
  dueDate?: string
  priority: number
  suspended: boolean
  description?: string
  formKey?: string
  businessKey?: string
  processVariables?: Record<string, any>
}

// Get tasks assigned to current user (My Tasks)
export async function getMyTasks(): Promise<TaskInfo[]> {
  const response = await apiClient.get('/workflows/tasks/my-tasks')
  return response.data
}

// Get tasks available to current user's groups (Group Tasks)
export async function getGroupTasks(): Promise<TaskInfo[]> {
  const response = await apiClient.get('/workflows/tasks/group-tasks')
  return response.data
}

// Get task details by ID with full information
export async function getTaskDetails(taskId: string): Promise<TaskInfo> {
  const response = await apiClient.get(`/workflows/tasks/${taskId}/details`)
  return response.data
}

// Claim a task (assign to current user)
export async function claimTaskForCurrentUser(taskId: string): Promise<void> {
  await apiClient.post(`/workflows/tasks/${taskId}/claim`)
}

// Unclaim a task (release assignment)
export async function unclaimTask(taskId: string): Promise<void> {
  await apiClient.post(`/workflows/tasks/${taskId}/unclaim`)
}

// Complete task with variables (enhanced)
export async function completeTaskWithVariables(
  taskId: string,
  variables: Record<string, any>,
  comment?: string
): Promise<void> {
  await apiClient.post(`/workflows/tasks/${taskId}/complete`, {
    variables,
    comment
  })
}
