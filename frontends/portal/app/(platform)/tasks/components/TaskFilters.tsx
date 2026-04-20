'use client'

import { useState } from 'react'
import { useTranslations } from 'next-intl'
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card"
import { Label } from "@/components/ui/label"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { X, Filter } from "lucide-react"
import type { TaskFilter } from "@/lib/types/task"

interface TaskFiltersProps {
  filters: TaskFilter
  onFilterChange: (filters: TaskFilter) => void
  userDepartment?: string
}

export function TaskFilters({ filters, onFilterChange, userDepartment }: TaskFiltersProps) {
  const t = useTranslations('tasks')
  const [localFilters, setLocalFilters] = useState<TaskFilter>(filters)

  const handleFilterChange = (key: keyof TaskFilter, value: any) => {
    const newFilters = { ...localFilters, [key]: value }
    setLocalFilters(newFilters)
    onFilterChange(newFilters)
  }

  const clearFilters = () => {
    const defaultFilters: TaskFilter = {}
    setLocalFilters(defaultFilters)
    onFilterChange(defaultFilters)
  }

  const hasActiveFilters = Object.keys(localFilters).some(key => {
    const value = localFilters[key as keyof TaskFilter]
    return value !== undefined && value !== null && value !== ''
  })

  const activeFilterCount = Object.keys(localFilters).filter(key => {
    const value = localFilters[key as keyof TaskFilter]
    return value !== undefined && value !== null && value !== ''
  }).length

  return (
    <Card>
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <CardTitle className="text-base flex items-center gap-2">
            <Filter className="h-4 w-4" />
            {t('filters')}
            {activeFilterCount > 0 && (
              <Badge variant="secondary" className="ml-2">
                {activeFilterCount}
              </Badge>
            )}
          </CardTitle>
          {hasActiveFilters && (
            <Button
              variant="ghost"
              size="sm"
              onClick={clearFilters}
              className="h-8 px-2"
            >
              <X className="h-4 w-4 mr-1" />
              {t('clear')}
            </Button>
          )}
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-3">
          <div>
            <Label className="text-sm font-medium mb-2 block">{t('taskAssignment')}</Label>
            <div className="space-y-2">
              <label className="flex items-center space-x-2 cursor-pointer">
                <input
                  type="radio"
                  name="assignment"
                  checked={localFilters.myTasks === true}
                  onChange={() => handleFilterChange('myTasks', true)}
                  className="w-4 h-4"
                />
                <span className="text-sm">{t('myTasksFilter')}</span>
              </label>
              <label className="flex items-center space-x-2 cursor-pointer">
                <input
                  type="radio"
                  name="assignment"
                  checked={localFilters.teamTasks === true}
                  onChange={() => handleFilterChange('teamTasks', true)}
                  className="w-4 h-4"
                />
                <span className="text-sm">{t('teamTasksFilter')}</span>
              </label>
              <label className="flex items-center space-x-2 cursor-pointer">
                <input
                  type="radio"
                  name="assignment"
                  checked={localFilters.unassigned === true}
                  onChange={() => handleFilterChange('unassigned', true)}
                  className="w-4 h-4"
                />
                <span className="text-sm">{t('unassigned')}</span>
              </label>
              <label className="flex items-center space-x-2 cursor-pointer">
                <input
                  type="radio"
                  name="assignment"
                  checked={!localFilters.myTasks && !localFilters.teamTasks && !localFilters.unassigned}
                  onChange={() => {
                    handleFilterChange('myTasks', undefined)
                    handleFilterChange('teamTasks', undefined)
                    handleFilterChange('unassigned', undefined)
                  }}
                  className="w-4 h-4"
                />
                <span className="text-sm">{t('allTasks')}</span>
              </label>
            </div>
          </div>

          <div className="border-t pt-3">
            <Label className="text-sm font-medium mb-2 block">{t('priority')}</Label>
            <div className="space-y-2">
              <label className="flex items-center space-x-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={localFilters.priority === 100}
                  onChange={(e) => handleFilterChange('priority', e.target.checked ? 100 : undefined)}
                  className="w-4 h-4"
                />
                <span className="text-sm">{t('urgent')}</span>
              </label>
              <label className="flex items-center space-x-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={localFilters.priority === 75}
                  onChange={(e) => handleFilterChange('priority', e.target.checked ? 75 : undefined)}
                  className="w-4 h-4"
                />
                <span className="text-sm">{t('high')}</span>
              </label>
              <label className="flex items-center space-x-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={localFilters.priority === 50}
                  onChange={(e) => handleFilterChange('priority', e.target.checked ? 50 : undefined)}
                  className="w-4 h-4"
                />
                <span className="text-sm">{t('medium')}</span>
              </label>
              <label className="flex items-center space-x-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={localFilters.priority === 25}
                  onChange={(e) => handleFilterChange('priority', e.target.checked ? 25 : undefined)}
                  className="w-4 h-4"
                />
                <span className="text-sm">{t('low')}</span>
              </label>
            </div>
          </div>

          {userDepartment && (
            <div className="border-t pt-3">
              <Label className="text-sm font-medium mb-2 block">{t('department')}</Label>
              <div className="space-y-2">
                <label className="flex items-center space-x-2 cursor-pointer">
                  <input
                    type="radio"
                    name="department"
                    checked={localFilters.department === userDepartment}
                    onChange={() => handleFilterChange('department', userDepartment)}
                    className="w-4 h-4"
                  />
                  <span className="text-sm">{t('myDepartment', { dept: userDepartment })}</span>
                </label>
                <label className="flex items-center space-x-2 cursor-pointer">
                  <input
                    type="radio"
                    name="department"
                    checked={!localFilters.department}
                    onChange={() => handleFilterChange('department', undefined)}
                    className="w-4 h-4"
                  />
                  <span className="text-sm">{t('allDepartments')}</span>
                </label>
              </div>
            </div>
          )}

          <div className="border-t pt-3">
            <Label className="text-sm font-medium mb-2 block">{t('dueDate')}</Label>
            <div className="space-y-2">
              <label className="flex items-center space-x-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={!!localFilters.dueBefore && !localFilters.dueToday}
                  onChange={(e) => {
                    const newFilters: TaskFilter = {
                      ...localFilters,
                      dueBefore: e.target.checked ? new Date().toISOString() : undefined,
                      dueToday: undefined,
                    }
                    setLocalFilters(newFilters)
                    onFilterChange(newFilters)
                  }}
                  className="w-4 h-4"
                />
                <span className="text-sm">{t('overdue')}</span>
              </label>
              <label className="flex items-center space-x-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={!!localFilters.dueToday}
                  onChange={(e) => {
                    const tomorrow = new Date()
                    tomorrow.setDate(tomorrow.getDate() + 1)
                    const newFilters: TaskFilter = {
                      ...localFilters,
                      dueToday: e.target.checked ? true : undefined,
                      dueBefore: e.target.checked ? tomorrow.toISOString() : undefined,
                    }
                    setLocalFilters(newFilters)
                    onFilterChange(newFilters)
                  }}
                  className="w-4 h-4"
                />
                <span className="text-sm">{t('dueToday')}</span>
              </label>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
