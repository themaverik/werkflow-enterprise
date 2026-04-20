'use client'

import { useState } from 'react';
import { useSession } from 'next-auth/react';
import { Activity, CheckCircle2, XCircle, Clock, AlertTriangle, RefreshCw, TrendingUp, Briefcase, Package, DollarSign, Users } from 'lucide-react';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import {
  getAllDepartmentStats,
  getDepartmentWorkflows,
  getAllWorkflowInstances,
  type DepartmentWorkflowStats,
  type WorkflowInstance,
} from '@/lib/api/workflows';

const DEPARTMENTS = [
  { id: 'all', label: 'All Departments', icon: Activity, color: 'text-gray-600' },
  { id: 'hr', label: 'HR', icon: Users, color: 'text-blue-600' },
  { id: 'finance', label: 'Finance', icon: DollarSign, color: 'text-green-600' },
  { id: 'procurement', label: 'Procurement', icon: Briefcase, color: 'text-purple-600' },
  { id: 'inventory', label: 'Inventory', icon: Package, color: 'text-orange-600' },
];

const STATUS_FILTERS = [
  { id: 'all', label: 'All Status' },
  { id: 'active', label: 'Active' },
  { id: 'completed', label: 'Completed' },
  { id: 'failed', label: 'Failed' },
  { id: 'suspended', label: 'Suspended' },
] as const;

export default function MultiDepartmentWorkflowsPage() {
  const { status } = useSession();
  const [selectedDepartment, setSelectedDepartment] = useState('all');
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'completed' | 'failed' | 'suspended'>('all');

  // Fetch all department statistics with polling
  const { data: departmentStats, isLoading: statsLoading, error: statsError } = useQuery<DepartmentWorkflowStats[]>({
    queryKey: ['department-stats'],
    queryFn: getAllDepartmentStats,
    enabled: status === 'authenticated',
    refetchInterval: 30000, // Poll every 30 seconds
    staleTime: 20000,
  });

  // Fetch workflow instances based on selected department and status
  const { data: workflows, isLoading: workflowsLoading, error: workflowsError } = useQuery<WorkflowInstance[]>({
    queryKey: ['department-workflows', selectedDepartment, statusFilter],
    queryFn: () => {
      const statusValue = statusFilter === 'all' ? undefined : statusFilter;
      if (selectedDepartment === 'all') {
        return getAllWorkflowInstances(statusValue, 50);
      } else {
        return getDepartmentWorkflows(selectedDepartment, statusValue, 50);
      }
    },
    enabled: status === 'authenticated',
    refetchInterval: 30000,
    staleTime: 20000,
  });

  // Calculate aggregate statistics
  const aggregateStats = departmentStats?.reduce(
    (acc, dept) => ({
      totalWorkflows: acc.totalWorkflows + dept.totalWorkflows,
      activeWorkflows: acc.activeWorkflows + dept.activeWorkflows,
      completedWorkflows: acc.completedWorkflows + dept.completedWorkflows,
      failedWorkflows: acc.failedWorkflows + dept.failedWorkflows,
      suspendedWorkflows: acc.suspendedWorkflows + dept.suspendedWorkflows,
    }),
    { totalWorkflows: 0, activeWorkflows: 0, completedWorkflows: 0, failedWorkflows: 0, suspendedWorkflows: 0 }
  );

  // Get current department stats
  const currentDeptStats = selectedDepartment === 'all'
    ? aggregateStats
    : departmentStats?.find(d => d.department.toLowerCase() === selectedDepartment);

  // Helper function to get status badge color
  const getStatusColor = (status: string) => {
    switch (status) {
      case 'active':
        return 'bg-blue-100 text-blue-800';
      case 'completed':
        return 'bg-green-100 text-green-800';
      case 'failed':
        return 'bg-red-100 text-red-800';
      case 'suspended':
        return 'bg-yellow-100 text-yellow-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  // Helper function to get status icon
  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'active':
        return <Activity className="h-4 w-4" />;
      case 'completed':
        return <CheckCircle2 className="h-4 w-4" />;
      case 'failed':
        return <XCircle className="h-4 w-4" />;
      case 'suspended':
        return <AlertTriangle className="h-4 w-4" />;
      default:
        return <Clock className="h-4 w-4" />;
    }
  };

  // Loading state
  if (statsLoading) {
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-center h-64">
          <div className="text-center">
            <RefreshCw className="h-8 w-8 animate-spin text-blue-600 mx-auto mb-4" />
            <p className="text-gray-600">Loading workflow data...</p>
          </div>
        </div>
      </div>
    );
  }

  // Error state
  if (statsError) {
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-center h-64">
          <div className="text-center">
            <XCircle className="h-12 w-12 text-red-600 mx-auto mb-4" />
            <p className="text-gray-900 font-medium mb-2">Failed to load workflow data</p>
            <p className="text-gray-600 text-sm">{statsError?.toString() || 'Unknown error occurred'}</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Multi-Department Workflows</h1>
          <p className="mt-1 text-sm text-gray-600">
            Centralized view of all workflows across departments
          </p>
        </div>
        <div className="flex items-center space-x-2">
          <Link href="/processes/new">
            <button className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700">
              Create New Workflow
            </button>
          </Link>
        </div>
      </div>

      {/* Department Tabs */}
      <div className="bg-white shadow rounded-lg overflow-hidden">
        <div className="border-b border-gray-200">
          <nav className="flex -mb-px">
            {DEPARTMENTS.map((dept) => {
              const Icon = dept.icon;
              const isActive = selectedDepartment === dept.id;
              const deptStats = departmentStats?.find(d => d.department.toLowerCase() === dept.id) || null;
              const count = dept.id === 'all'
                ? aggregateStats?.totalWorkflows || 0
                : deptStats?.totalWorkflows || 0;

              return (
                <button
                  key={dept.id}
                  onClick={() => setSelectedDepartment(dept.id)}
                  className={`group inline-flex items-center px-6 py-4 border-b-2 font-medium text-sm ${
                    isActive
                      ? 'border-blue-500 text-blue-600'
                      : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                  }`}
                >
                  <Icon className={`-ml-0.5 mr-2 h-5 w-5 ${isActive ? dept.color : 'text-gray-400'}`} />
                  <span>{dept.label}</span>
                  <span className={`ml-2 py-0.5 px-2 rounded-full text-xs font-medium ${
                    isActive ? 'bg-blue-100 text-blue-600' : 'bg-gray-100 text-gray-600'
                  }`}>
                    {count}
                  </span>
                </button>
              );
            })}
          </nav>
        </div>

        {/* Department Statistics */}
        <div className="p-6">
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-5">
            <div className="bg-gray-50 p-4 rounded-lg">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-gray-600">Total</p>
                  <p className="mt-1 text-2xl font-semibold text-gray-900">
                    {currentDeptStats?.totalWorkflows || 0}
                  </p>
                </div>
                <TrendingUp className="h-8 w-8 text-gray-400" />
              </div>
            </div>

            <div className="bg-blue-50 p-4 rounded-lg">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-blue-700">Active</p>
                  <p className="mt-1 text-2xl font-semibold text-blue-900">
                    {currentDeptStats?.activeWorkflows || 0}
                  </p>
                </div>
                <Activity className="h-8 w-8 text-blue-400" />
              </div>
            </div>

            <div className="bg-green-50 p-4 rounded-lg">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-green-700">Completed</p>
                  <p className="mt-1 text-2xl font-semibold text-green-900">
                    {currentDeptStats?.completedWorkflows || 0}
                  </p>
                </div>
                <CheckCircle2 className="h-8 w-8 text-green-400" />
              </div>
            </div>

            <div className="bg-red-50 p-4 rounded-lg">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-red-700">Failed</p>
                  <p className="mt-1 text-2xl font-semibold text-red-900">
                    {currentDeptStats?.failedWorkflows || 0}
                  </p>
                </div>
                <XCircle className="h-8 w-8 text-red-400" />
              </div>
            </div>

            <div className="bg-yellow-50 p-4 rounded-lg">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-yellow-700">Suspended</p>
                  <p className="mt-1 text-2xl font-semibold text-yellow-900">
                    {currentDeptStats?.suspendedWorkflows || 0}
                  </p>
                </div>
                <AlertTriangle className="h-8 w-8 text-yellow-400" />
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Workflow Instances List */}
      <div className="bg-white shadow rounded-lg overflow-hidden">
        <div className="px-6 py-5 border-b border-gray-200">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-medium text-gray-900">Workflow Instances</h2>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as any)}
              className="px-3 py-1.5 text-sm border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {STATUS_FILTERS.map((filter) => (
                <option key={filter.id} value={filter.id}>
                  {filter.label}
                </option>
              ))}
            </select>
          </div>
        </div>

        {workflowsLoading ? (
          <div className="px-6 py-12 text-center">
            <RefreshCw className="h-8 w-8 animate-spin text-blue-600 mx-auto mb-3" />
            <p className="text-sm text-gray-600">Loading workflows...</p>
          </div>
        ) : workflowsError ? (
          <div className="px-6 py-12 text-center">
            <XCircle className="h-8 w-8 text-red-600 mx-auto mb-3" />
            <p className="text-sm text-gray-600">Failed to load workflows</p>
          </div>
        ) : workflows && workflows.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Process Name
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Department
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Business Key
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Started
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Current Activity
                  </th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Status
                  </th>
                  <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {workflows.map((workflow) => (
                  <tr key={workflow.id} className="hover:bg-gray-50">
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="flex items-center">
                        <div>
                          <div className="text-sm font-medium text-gray-900">
                            {workflow.processDefinitionName}
                          </div>
                          <div className="text-xs text-gray-500">
                            {workflow.processDefinitionKey}
                          </div>
                        </div>
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-800 capitalize">
                        {workflow.department}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                      {workflow.businessKey || 'N/A'}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                      {new Date(workflow.startTime).toLocaleString()}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                      {workflow.currentActivity || (workflow.status === 'completed' ? 'Completed' : 'N/A')}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium gap-1 ${getStatusColor(workflow.status)}`}>
                        {getStatusIcon(workflow.status)}
                        <span className="capitalize">{workflow.status}</span>
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                      <Link
                        href={`/processes/${workflow.id}`}
                        className="text-blue-600 hover:text-blue-900"
                      >
                        View Details
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="px-6 py-12 text-center text-gray-500">
            <Activity className="h-12 w-12 mx-auto mb-3 text-gray-400" />
            <p className="text-sm">No workflow instances found</p>
            <p className="text-xs text-gray-400 mt-1">
              {statusFilter !== 'all' && `No ${statusFilter} workflows in ${selectedDepartment === 'all' ? 'any department' : selectedDepartment}`}
            </p>
          </div>
        )}
      </div>

      {/* Department Summary Cards (for non-'all' views) */}
      {selectedDepartment === 'all' && departmentStats && departmentStats.length > 0 && (
        <div className="bg-white shadow rounded-lg p-6">
          <h2 className="text-lg font-medium text-gray-900 mb-4">Department Summary</h2>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {departmentStats
              .filter(dept => dept.department.toLowerCase() !== 'all')
              .map((dept) => {
                const deptInfo = DEPARTMENTS.find(d => d.id === dept.department.toLowerCase());
                const Icon = deptInfo?.icon || Activity;
                const color = deptInfo?.color || 'text-gray-600';

                return (
                  <div
                    key={dept.department}
                    className="border border-gray-200 rounded-lg p-4 hover:shadow-md transition-shadow cursor-pointer"
                    onClick={() => setSelectedDepartment(dept.department.toLowerCase())}
                  >
                    <div className="flex items-center justify-between mb-3">
                      <h3 className="text-sm font-medium text-gray-900 capitalize">{dept.department}</h3>
                      <Icon className={`h-5 w-5 ${color}`} />
                    </div>
                    <div className="space-y-1 text-xs">
                      <div className="flex justify-between">
                        <span className="text-gray-500">Total:</span>
                        <span className="font-medium text-gray-900">{dept.totalWorkflows}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-blue-600">Active:</span>
                        <span className="font-medium text-blue-900">{dept.activeWorkflows}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-green-600">Completed:</span>
                        <span className="font-medium text-green-900">{dept.completedWorkflows}</span>
                      </div>
                      {dept.failedWorkflows > 0 && (
                        <div className="flex justify-between">
                          <span className="text-red-600">Failed:</span>
                          <span className="font-medium text-red-900">{dept.failedWorkflows}</span>
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}
          </div>
        </div>
      )}
    </div>
  );
}
