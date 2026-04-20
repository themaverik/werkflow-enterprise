import { useQuery, useMutation, useQueryClient, UseQueryOptions } from '@tanstack/react-query'
import {
  getTasks,
  getTaskById,
  claimTask,
  unclaimTask,
  completeTask,
  delegateTask,
  getTaskFormData,
  getTaskHistory,
  getTaskSummary,
  searchTasks,
  submitTaskForm,
  getProcessHistory,
} from '@/lib/api/tasks'
import type {
  Task,
  TaskListResponse,
  TaskQueryParams,
  TaskCompleteRequest,
  TaskDelegateRequest,
  TaskFormData,
  TaskHistory,
  TaskSummary,
} from '@/lib/types/task'

export const TASK_QUERY_KEYS = {
  all: ['tasks'] as const,
  lists: () => [...TASK_QUERY_KEYS.all, 'list'] as const,
  list: (params: TaskQueryParams) => [...TASK_QUERY_KEYS.lists(), params] as const,
  details: () => [...TASK_QUERY_KEYS.all, 'detail'] as const,
  detail: (id: string) => [...TASK_QUERY_KEYS.details(), id] as const,
  formData: (id: string) => [...TASK_QUERY_KEYS.all, 'form', id] as const,
  history: (id: string) => [...TASK_QUERY_KEYS.all, 'history', id] as const,
  summary: () => [...TASK_QUERY_KEYS.all, 'summary'] as const,
  processHistory: (id: string) => [...TASK_QUERY_KEYS.all, 'process-history', id] as const,
  search: (query: string, params: TaskQueryParams) => [...TASK_QUERY_KEYS.all, 'search', query, params] as const,
}

export function useTasks(
  params?: TaskQueryParams,
  options?: UseQueryOptions<TaskListResponse, Error>
) {
  return useQuery<TaskListResponse, Error>({
    queryKey: TASK_QUERY_KEYS.list(params || {}),
    queryFn: () => getTasks(params),
    staleTime: 30000,
    ...options,
  })
}

export function useTask(
  taskId: string,
  includeVariables: boolean = true,
  options?: UseQueryOptions<Task, Error>
) {
  return useQuery<Task, Error>({
    queryKey: TASK_QUERY_KEYS.detail(taskId),
    queryFn: () => getTaskById(taskId, includeVariables),
    enabled: !!taskId,
    staleTime: 60000,
    ...options,
  })
}

export function useTaskFormData(
  taskId: string,
  options?: UseQueryOptions<TaskFormData, Error>
) {
  return useQuery<TaskFormData, Error>({
    queryKey: TASK_QUERY_KEYS.formData(taskId),
    queryFn: () => getTaskFormData(taskId),
    enabled: !!taskId,
    staleTime: 300000,
    ...options,
  })
}

export function useTaskHistory(
  taskId: string,
  options?: UseQueryOptions<TaskHistory[], Error>
) {
  return useQuery<TaskHistory[], Error>({
    queryKey: TASK_QUERY_KEYS.history(taskId),
    queryFn: () => getTaskHistory(taskId),
    enabled: !!taskId,
    staleTime: 60000,
    ...options,
  })
}

export function useTaskSummary(options?: UseQueryOptions<TaskSummary, Error>) {
  return useQuery<TaskSummary, Error>({
    queryKey: TASK_QUERY_KEYS.summary(),
    queryFn: getTaskSummary,
    staleTime: 30000,
    ...options,
  })
}

export function useSearchTasks(
  searchText: string,
  params?: TaskQueryParams,
  options?: UseQueryOptions<TaskListResponse, Error>
) {
  return useQuery<TaskListResponse, Error>({
    queryKey: TASK_QUERY_KEYS.search(searchText, params || {}),
    queryFn: () => searchTasks(searchText, params),
    enabled: !!searchText && searchText.length > 2,
    staleTime: 30000,
    ...options,
  })
}

export function useClaimTask() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ taskId, assignee }: { taskId: string; assignee: string }) =>
      claimTask(taskId, assignee),
    onMutate: async ({ taskId, assignee }) => {
      await queryClient.cancelQueries({ queryKey: TASK_QUERY_KEYS.detail(taskId) })

      const previousTask = queryClient.getQueryData<Task>(TASK_QUERY_KEYS.detail(taskId))

      if (previousTask) {
        queryClient.setQueryData<Task>(TASK_QUERY_KEYS.detail(taskId), {
          ...previousTask,
          assignee,
        })
      }

      return { previousTask }
    },
    onError: (error, variables, context) => {
      if (context?.previousTask) {
        queryClient.setQueryData(
          TASK_QUERY_KEYS.detail(variables.taskId),
          context.previousTask
        )
      }
    },
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: TASK_QUERY_KEYS.detail(variables.taskId) })
      queryClient.invalidateQueries({ queryKey: TASK_QUERY_KEYS.lists() })
      queryClient.invalidateQueries({ queryKey: TASK_QUERY_KEYS.summary() })
    },
  })
}

export function useUnclaimTask() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (taskId: string) => unclaimTask(taskId),
    onMutate: async (taskId) => {
      await queryClient.cancelQueries({ queryKey: TASK_QUERY_KEYS.detail(taskId) })

      const previousTask = queryClient.getQueryData<Task>(TASK_QUERY_KEYS.detail(taskId))

      if (previousTask) {
        queryClient.setQueryData<Task>(TASK_QUERY_KEYS.detail(taskId), {
          ...previousTask,
          assignee: undefined,
        })
      }

      return { previousTask }
    },
    onError: (error, taskId, context) => {
      if (context?.previousTask) {
        queryClient.setQueryData(TASK_QUERY_KEYS.detail(taskId), context.previousTask)
      }
    },
    onSuccess: (_, taskId) => {
      queryClient.invalidateQueries({ queryKey: TASK_QUERY_KEYS.detail(taskId) })
      queryClient.invalidateQueries({ queryKey: TASK_QUERY_KEYS.lists() })
      queryClient.invalidateQueries({ queryKey: TASK_QUERY_KEYS.summary() })
    },
  })
}

export function useCompleteTask() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ taskId, data }: { taskId: string; data?: TaskCompleteRequest }) =>
      completeTask(taskId, data),
    onSuccess: (_, variables) => {
      queryClient.removeQueries({ queryKey: TASK_QUERY_KEYS.detail(variables.taskId) })
      queryClient.invalidateQueries({ queryKey: TASK_QUERY_KEYS.lists() })
      queryClient.invalidateQueries({ queryKey: TASK_QUERY_KEYS.summary() })
    },
  })
}

export function useDelegateTask() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ taskId, data }: { taskId: string; data: TaskDelegateRequest }) =>
      delegateTask(taskId, data),
    onSuccess: (data, variables) => {
      queryClient.setQueryData(TASK_QUERY_KEYS.detail(variables.taskId), data)
      queryClient.invalidateQueries({ queryKey: TASK_QUERY_KEYS.lists() })
      queryClient.invalidateQueries({ queryKey: TASK_QUERY_KEYS.summary() })
      queryClient.invalidateQueries({ queryKey: TASK_QUERY_KEYS.history(variables.taskId) })
    },
  })
}

export function useSubmitTaskForm() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ taskId, formData }: { taskId: string; formData: Record<string, any> }) =>
      submitTaskForm(taskId, formData),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: TASK_QUERY_KEYS.formData(variables.taskId) })
      queryClient.invalidateQueries({ queryKey: TASK_QUERY_KEYS.detail(variables.taskId) })
      queryClient.invalidateQueries({ queryKey: TASK_QUERY_KEYS.lists() })
    },
  })
}

export function useProcessHistory(
  processInstanceId: string | undefined,
  options?: UseQueryOptions<any, Error>
) {
  return useQuery<any, Error>({
    queryKey: TASK_QUERY_KEYS.processHistory(processInstanceId ?? ''),
    queryFn: () => getProcessHistory(processInstanceId!),
    enabled: !!processInstanceId,
    staleTime: 60000,
    ...options,
  })
}
