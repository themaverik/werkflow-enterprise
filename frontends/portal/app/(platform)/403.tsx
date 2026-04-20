import Link from 'next/link'
import { ArrowLeft, LogOut } from 'lucide-react'

export default function AccessDeniedPage() {
  return (
    <div className="flex items-center justify-center min-h-screen bg-gradient-to-b from-gray-50 to-gray-100">
      <div className="max-w-md w-full px-6 py-12 text-center">
        <div className="mb-6">
          <h1 className="text-6xl font-bold text-red-600 mb-4">403</h1>
          <h2 className="text-2xl font-semibold text-gray-900 mb-2">Access Denied</h2>
          <p className="text-gray-600 mb-6">
            You do not have permission to access this resource. Please contact your administrator if you
            believe this is a mistake.
          </p>
        </div>

        <div className="bg-white rounded-lg shadow p-6 mb-6">
          <div className="text-sm text-gray-600 space-y-2">
            <p>
              <strong>Required Roles:</strong> Your account may not have the necessary permissions for this area.
            </p>
            <p>
              <strong>Next Steps:</strong> Try navigating to another section or log out and log back in.
            </p>
          </div>
        </div>

        <div className="space-y-3">
          <Link
            href="/dashboard"
            className="inline-flex items-center justify-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors w-full"
          >
            <ArrowLeft size={16} />
            Go to Dashboard
          </Link>

          <a
            href="/api/auth/logout"
            className="inline-flex items-center justify-center gap-2 px-4 py-2 bg-gray-200 text-gray-800 rounded-md hover:bg-gray-300 transition-colors w-full"
          >
            <LogOut size={16} />
            Logout
          </a>
        </div>

        <p className="text-xs text-gray-500 mt-6">
          If you need access to this resource, please contact your administrator.
        </p>
      </div>
    </div>
  )
}
