import { apiClient } from './client'
import type {
  Task,
  TaskListResponse,
  TaskQueryParams,
  TaskClaimRequest,
  TaskCompleteRequest,
  TaskDelegateRequest,
  TaskFormData,
  TaskHistory,
  TaskSummary,
} from '@/lib/types/task'

export class TaskApiError extends Error {
  constructor(
    message: string,
    public statusCode?: number,
    public details?: any
  ) {
    super(message)
    this.name = 'TaskApiError'
  }
}

export class TaskNotFoundError extends TaskApiError {
  constructor(taskId: string) {
    super(`Task '${taskId}' not found`, 404)
    this.name = 'TaskNotFoundError'
  }
}

export class TaskClaimError extends TaskApiError {
  constructor(message: string, statusCode?: number) {
    super(message, statusCode)
    this.name = 'TaskClaimError'
  }
}

export class TaskCompleteError extends TaskApiError {
  constructor(message: string, statusCode?: number) {
    super(message, statusCode)
    this.name = 'TaskCompleteError'
  }
}

/**
 * Flowable serializes process variables as { value, type, valueInfo } wrappers
 * when returning them from the REST API (e.g. GET /tasks/{id}?includeProcessVariables=true).
 * This function unwraps those objects so components always receive plain scalar values.
 */
function unwrapProcessVariables(variables: Record<string, any>): Record<string, any> {
  if (!variables || typeof variables !== 'object') return {}
  const result: Record<string, any> = {}
  for (const [key, entry] of Object.entries(variables)) {
    if (
      entry !== null &&
      typeof entry === 'object' &&
      'value' in entry &&
      'type' in entry &&
      !Array.isArray(entry)
    ) {
      // Flowable variable wrapper - unwrap to the primitive value
      result[key] = entry.value
    } else {
      result[key] = entry
    }
  }
  return result
}

function handleApiError(error: any, context: string): never {
  if (error.response) {
    const status = error.response.status
    const data = error.response.data

    switch (status) {
      case 404:
        throw new TaskNotFoundError(context)
      case 409:
        throw new TaskClaimError('Task already claimed by another user', 409)
      case 400:
        throw new TaskApiError(
          data.message || 'Invalid request data',
          400,
          data.errors || data
        )
      case 401:
        throw new TaskApiError('Authentication required', 401)
      case 403:
        throw new TaskApiError('Access denied', 403)
      case 500:
        throw new TaskApiError(
          'Internal server error. Please try again later.',
          500,
          data
        )
      default:
        throw new TaskApiError(
          data.message || `Task API error (${status})`,
          status,
          data
        )
    }
  }

  if (error.request) {
    throw new TaskApiError('Cannot connect to Task API. Please ensure the backend service is running.')
  }

  throw new TaskApiError(error.message || 'Unknown error occurred')
}

export async function getTasks(params?: TaskQueryParams): Promise<TaskListResponse> {
  try {
    const queryParams: any = {
      start: params?.start || 0,
      size: params?.size || 20,
      sort: params?.sort || 'createTime',
      order: params?.order || 'desc',
    }

    if (params?.assignee) {
      queryParams.assignee = params.assignee
    }

    if (params?.candidateUser) {
      queryParams.candidateUser = params.candidateUser
    }

    if (params?.candidateGroups) {
      queryParams.candidateGroups = params.candidateGroups
    }

    if (params?.processDefinitionKey) {
      queryParams.processDefinitionKey = params.processDefinitionKey
    }

    if (params?.taskDefinitionKey) {
      queryParams.taskDefinitionKey = params.taskDefinitionKey
    }

    if (params?.priority !== undefined) {
      queryParams.priority = params.priority
    }

    if (params?.dueBefore) {
      queryParams.dueBefore = params.dueBefore
    }

    if (params?.dueAfter) {
      queryParams.dueAfter = params.dueAfter
    }

    if (params?.createdBefore) {
      queryParams.createdBefore = params.createdBefore
    }

    if (params?.createdAfter) {
      queryParams.createdAfter = params.createdAfter
    }

    if (params?.nameLike) {
      queryParams.nameLike = params.nameLike
    }

    if (params?.descriptionLike) {
      queryParams.descriptionLike = params.descriptionLike
    }

    if (params?.unassigned) {
      queryParams.unassigned = params.unassigned
    }

    if (params?.includeProcessVariables) {
      queryParams.includeProcessVariables = params.includeProcessVariables
    }

    if (params?.includeTaskLocalVariables) {
      queryParams.includeTaskLocalVariables = params.includeTaskLocalVariables
    }

    const response = await apiClient.get('/api/v1/tasks', { params: queryParams })

    const tasks = Array.isArray(response.data) ? response.data : response.data.data || []
    const total = response.data.total || tasks.length

    return {
      data: tasks.map((task: any) => ({
        ...task,
        createTime: task.createTime || task.created,
        dueDate: task.dueDate || task.due,
        processDefinitionName: task.processDefinitionName || task.processName,
        processVariables: unwrapProcessVariables(task.variables || task.processVariables || {}),
      })),
      total,
      start: params?.start || 0,
      size: params?.size || 20,
      sort: params?.sort,
      order: params?.order,
    }
  } catch (error: any) {
    handleApiError(error, 'task list')
  }
}

export async function getTaskById(taskId: string, includeVariables: boolean = true): Promise<Task> {
  try {
    const params = includeVariables
      ? { includeProcessVariables: true, includeTaskLocalVariables: true }
      : {}

    const response = await apiClient.get(`/api/v1/tasks/${taskId}`, { params })

    const rawVariables = response.data.variables || response.data.processVariables || {}
    return {
      ...response.data,
      createTime: response.data.createTime || response.data.created,
      dueDate: response.data.dueDate || response.data.due,
      processDefinitionName: response.data.processDefinitionName || response.data.processName,
      processVariables: unwrapProcessVariables(rawVariables),
    }
  } catch (error: any) {
    handleApiError(error, taskId)
  }
}

export async function claimTask(taskId: string, assignee: string): Promise<Task> {
  try {
    const response = await apiClient.post(`/api/v1/tasks/${taskId}/claim`, { assignee })

    return {
      ...response.data,
      createTime: response.data.createTime || response.data.created,
      dueDate: response.data.dueDate || response.data.due,
    }
  } catch (error: any) {
    if (error.response?.status === 409) {
      throw new TaskClaimError('Task has already been claimed by another user', 409)
    }
    handleApiError(error, `claim task ${taskId}`)
  }
}

export async function unclaimTask(taskId: string): Promise<Task> {
  try {
    const response = await apiClient.post(`/api/v1/tasks/${taskId}/unclaim`)

    return {
      ...response.data,
      createTime: response.data.createTime || response.data.created,
      dueDate: response.data.dueDate || response.data.due,
    }
  } catch (error: any) {
    handleApiError(error, `unclaim task ${taskId}`)
  }
}

export async function completeTask(taskId: string, data?: TaskCompleteRequest): Promise<void> {
  try {
    await apiClient.post(`/api/v1/tasks/${taskId}/complete`, data)
  } catch (error: any) {
    handleApiError(error, `complete task ${taskId}`)
  }
}

export async function delegateTask(taskId: string, data: TaskDelegateRequest): Promise<Task> {
  try {
    const response = await apiClient.post(`/api/v1/tasks/${taskId}/delegate`, data)

    return {
      ...response.data,
      createTime: response.data.createTime || response.data.created,
      dueDate: response.data.dueDate || response.data.due,
    }
  } catch (error: any) {
    handleApiError(error, `delegate task ${taskId}`)
  }
}

export async function getTaskFormData(taskId: string): Promise<TaskFormData> {
  try {
    const response = await apiClient.get(`/api/v1/tasks/${taskId}/form`)

    return {
      formKey: response.data.formKey,
      formData: response.data.formData || response.data.schema,
      processVariables: response.data.processVariables || response.data.variables || {},
    }
  } catch (error: any) {
    handleApiError(error, `get form data for task ${taskId}`)
  }
}

export async function getTaskHistory(taskId: string): Promise<TaskHistory[]> {
  try {
    const response = await apiClient.get(`/api/v1/tasks/${taskId}/history`)

    const history = Array.isArray(response.data) ? response.data : response.data.data || []

    return history.map((item: any) => ({
      id: item.id,
      taskId: item.taskId || taskId,
      action: item.action || item.type,
      userId: item.userId || item.user,
      userName: item.userName || item.userFullName,
      timestamp: item.timestamp || item.time || item.endTime,
      comment: item.comment || item.message,
      data: item.data || item.variables,
    }))
  } catch (error: any) {
    handleApiError(error, `get history for task ${taskId}`)
  }
}

export async function getTaskSummary(): Promise<TaskSummary> {
  try {
    const response = await apiClient.get('/api/v1/tasks/summary')

    return {
      total: response.data.total || 0,
      myTasks: response.data.myTasks || 0,
      teamTasks: response.data.teamTasks || 0,
      unassigned: response.data.unassigned || 0,
      overdue: response.data.overdue || 0,
      dueToday: response.data.dueToday || 0,
      highPriority: response.data.highPriority || 0,
    }
  } catch (error: any) {
    handleApiError(error, 'task summary')
  }
}

export async function searchTasks(searchText: string, params?: TaskQueryParams): Promise<TaskListResponse> {
  try {
    const queryParams = {
      ...params,
      nameLike: `%${searchText}%`,
    }

    return await getTasks(queryParams)
  } catch (error: any) {
    handleApiError(error, 'task search')
  }
}

export async function submitTaskForm(taskId: string, formData: Record<string, any>): Promise<void> {
  try {
    await apiClient.post(`/api/v1/tasks/${taskId}/form/submit`, { formData })
  } catch (error: any) {
    handleApiError(error, `submit form for task ${taskId}`)
  }
}

export async function getProcessHistory(processInstanceId: string): Promise<any> {
  try {
    const response = await apiClient.get(`/workflows/processes/${processInstanceId}/history`)
    return response.data
  } catch (error: any) {
    handleApiError(error, `get process history ${processInstanceId}`)
  }
}
