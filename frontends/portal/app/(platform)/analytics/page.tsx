export default function AnalyticsPage() {
  return (
    <div className="p-6">
      <h1 className="text-2xl font-semibold mb-2">Analytics</h1>
      <p className="text-muted-foreground">
        Analytics dashboard coming in M4 (M6 Group B). Backend endpoints are live at{' '}
        <code>/analytics/process-stats</code> and <code>/analytics/task-metrics</code>.
      </p>
    </div>
  )
}
