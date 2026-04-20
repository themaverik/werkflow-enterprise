'use client'

import { useState } from 'react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import { Loader2, Search, UserPlus } from "lucide-react"

export interface TeamMember {
  id: string
  name: string
  email: string
  department?: string
  currentTaskCount?: number
  avatar?: string
}

export interface DelegationModalProps {
  isOpen: boolean
  onClose: () => void
  onDelegate: (assignee: string, reason: string) => Promise<void>
  isSubmitting?: boolean
}

export function DelegationModal({
  isOpen,
  onClose,
  onDelegate,
  isSubmitting = false,
}: DelegationModalProps) {
  const [selectedUser, setSelectedUser] = useState<TeamMember | null>(null)
  const [reason, setReason] = useState('')
  const [searchQuery, setSearchQuery] = useState('')
  const [validationError, setValidationError] = useState('')

  const mockTeamMembers: TeamMember[] = [
    { id: 'user1', name: 'John Doe', email: 'john.doe@example.com', department: 'Finance', currentTaskCount: 3 },
    { id: 'user2', name: 'Jane Smith', email: 'jane.smith@example.com', department: 'Finance', currentTaskCount: 5 },
    { id: 'user3', name: 'Bob Johnson', email: 'bob.johnson@example.com', department: 'Finance', currentTaskCount: 2 },
    { id: 'user4', name: 'Alice Williams', email: 'alice.williams@example.com', department: 'Finance', currentTaskCount: 4 },
  ]

  const filteredMembers = mockTeamMembers.filter(member =>
    member.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    member.email.toLowerCase().includes(searchQuery.toLowerCase())
  )

  const handleDelegate = async () => {
    if (!selectedUser) {
      setValidationError('Please select a team member')
      return
    }

    if (!reason.trim()) {
      setValidationError('Please provide a reason for delegation')
      return
    }

    setValidationError('')
    await onDelegate(selectedUser.id, reason)

    setSelectedUser(null)
    setReason('')
    setSearchQuery('')
  }

  const handleClose = () => {
    if (!isSubmitting) {
      setSelectedUser(null)
      setReason('')
      setSearchQuery('')
      setValidationError('')
      onClose()
    }
  }

  const getTaskLoadColor = (taskCount?: number) => {
    if (!taskCount) return 'secondary'
    if (taskCount >= 5) return 'destructive'
    if (taskCount >= 3) return 'default'
    return 'outline'
  }

  return (
    <Dialog open={isOpen} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-[600px]">
        <DialogHeader>
          <DialogTitle>Delegate Task</DialogTitle>
          <DialogDescription>
            Assign this task to a team member in your department
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="search">Search Team Members</Label>
            <div className="relative">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input
                id="search"
                placeholder="Search by name or email..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-9"
                disabled={isSubmitting}
              />
            </div>
          </div>

          <div className="space-y-2">
            <Label>Available Team Members</Label>
            <div className="border rounded-lg max-h-[200px] overflow-y-auto">
              {filteredMembers.length === 0 ? (
                <div className="text-center py-8 text-sm text-muted-foreground">
                  No team members found
                </div>
              ) : (
                <div className="divide-y">
                  {filteredMembers.map((member) => (
                    <div
                      key={member.id}
                      className={`p-3 cursor-pointer hover:bg-muted transition-colors ${
                        selectedUser?.id === member.id ? 'bg-muted' : ''
                      }`}
                      onClick={() => setSelectedUser(member)}
                    >
                      <div className="flex items-center justify-between">
                        <div className="flex-1">
                          <div className="flex items-center gap-2">
                            <div className="h-8 w-8 rounded-full bg-primary/10 flex items-center justify-center text-sm font-semibold">
                              {member.name.charAt(0).toUpperCase()}
                            </div>
                            <div>
                              <p className="font-medium text-sm">{member.name}</p>
                              <p className="text-xs text-muted-foreground">{member.email}</p>
                            </div>
                          </div>
                        </div>
                        <div className="flex items-center gap-2">
                          <div className="text-right">
                            <div className="text-xs text-muted-foreground">Current Load</div>
                            <Badge variant={getTaskLoadColor(member.currentTaskCount)} className="text-xs">
                              {member.currentTaskCount} tasks
                            </Badge>
                          </div>
                          {selectedUser?.id === member.id && (
                            <div className="h-4 w-4 rounded-full bg-primary flex items-center justify-center">
                              <div className="h-2 w-2 rounded-full bg-white" />
                            </div>
                          )}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          {selectedUser && (
            <div className="bg-muted p-3 rounded-lg">
              <div className="flex items-center gap-2 mb-2">
                <UserPlus className="h-4 w-4" />
                <span className="font-medium text-sm">Selected:</span>
                <span className="text-sm">{selectedUser.name}</span>
              </div>
            </div>
          )}

          <div className="space-y-2">
            <Label htmlFor="reason">
              Delegation Reason <span className="text-red-500">*</span>
            </Label>
            <Textarea
              id="reason"
              placeholder="Explain why you are delegating this task..."
              value={reason}
              onChange={(e) => {
                setReason(e.target.value)
                setValidationError('')
              }}
              disabled={isSubmitting}
              rows={3}
              className={validationError ? 'border-red-500' : ''}
            />
            {validationError && (
              <p className="text-sm text-red-500">{validationError}</p>
            )}
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={handleClose} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button onClick={handleDelegate} disabled={isSubmitting}>
            {isSubmitting ? (
              <>
                <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                Delegating...
              </>
            ) : (
              <>
                <UserPlus className="h-4 w-4 mr-2" />
                Delegate Task
              </>
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
