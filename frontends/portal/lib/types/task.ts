export type TaskStatus = 'open' | 'claimed' | 'in_progress' | 'completed'
export type TaskPriority = 'low' | 'medium' | 'high' | 'urgent'

export interface Task {
  id: string
  name: string
  description?: string
  assignee?: string
  owner?: string
  createTime: string
  dueDate?: string
  endTime?: string
  priority?: number
  processInstanceId?: string
  processDefinitionId?: string
  processDefinitionKey?: string
  processDefinitionName?: string
  taskDefinitionKey?: string
  formKey?: string
  suspended?: boolean
  candidateGroups?: string[]
  candidateUsers?: string[]
  processVariables?: Record<string, any>
  taskLocalVariables?: Record<string, any>
  delegationState?: 'pending' | 'resolved'
  category?: string
  tenantId?: string
  /**
   * Whether the BPMN gateway following this task routes decision='escalate'.
   * Populated only by the single-task getTaskById endpoint; null/undefined on list responses.
   * Treat null or undefined as false (fail-closed).
   */
  canEscalate?: boolean
}

export interface TaskListResponse {
  data: Task[]
  total: number
  start: number
  size: number
  sort?: string
  order?: string
}

export interface TaskFilter {
  assignee?: string
  candidateUser?: string
  candidateGroups?: string[]
  processDefinitionKey?: string
  processDefinitionName?: string
  taskDefinitionKey?: string
  priority?: number
  dueBefore?: string
  dueAfter?: string
  createdBefore?: string
  createdAfter?: string
  status?: TaskStatus[]
  department?: string
  searchText?: string
  unassigned?: boolean
  myTasks?: boolean
  teamTasks?: boolean
  dueToday?: boolean
}

export interface TaskSortOption {
  field: 'createTime' | 'dueDate' | 'priority' | 'name'
  order: 'asc' | 'desc'
}

export interface TaskQueryParams {
  start?: number
  size?: number
  sort?: string
  order?: 'asc' | 'desc'
  assignee?: string
  candidateUser?: string
  candidateGroups?: string
  processDefinitionKey?: string
  taskDefinitionKey?: string
  priority?: number
  dueBefore?: string
  dueAfter?: string
  createdBefore?: string
  createdAfter?: string
  includeProcessVariables?: boolean
  includeTaskLocalVariables?: boolean
  nameLike?: string
  descriptionLike?: string
  unassigned?: boolean
}

export interface TaskClaimRequest {
  assignee: string
}

export interface TaskCompleteRequest {
  variables?: Record<string, any>
}

export interface TaskDelegateRequest {
  assignee: string
  reason?: string
}

export interface TaskFormData {
  formKey: string
  formData: any
  processVariables: Record<string, any>
}

export interface TaskHistory {
  id: string
  taskId: string
  action: 'create' | 'claim' | 'complete' | 'delegate' | 'escalate'
  userId: string
  userName?: string
  timestamp: string
  comment?: string
  data?: Record<string, any>
}

export interface UserClaims {
  sub: string
  preferred_username: string
  email?: string
  name?: string
  department?: string
  roles?: string[]
  groups?: string[]
  doaLevel?: number
  managerId?: string
}

export interface TaskEligibility {
  canClaim: boolean
  canComplete: boolean
  canDelegate: boolean
  reasons: string[]
}

export interface TaskSummary {
  total: number
  myTasks: number
  teamTasks: number
  unassigned: number
  overdue: number
  dueToday: number
  highPriority: number
}

export type NotificationType =
  | 'task_assigned'
  | 'task_delegated'
  | 'task_completed'
  | 'approval_required'
  | 'approval_approved'
  | 'approval_rejected'
  | 'task_escalated'
  | 'task_due_soon'
  | 'process_completed'

export type NotificationPriority = 'low' | 'medium' | 'high' | 'urgent'

export interface Notification {
  id: string
  userId: string
  type: NotificationType
  priority: NotificationPriority
  title: string
  message: string
  taskId?: string
  processInstanceId?: string
  isRead: boolean
  createdAt: string
  readAt?: string
  actionUrl?: string
  metadata?: Record<string, any>
}
