export default function MonitoringPage() {
  return (
    <div className="p-6">
      <h1 className="text-2xl font-semibold mb-2">Process Health</h1>
      <p className="text-muted-foreground">
        Service health is aggregated at <code>/api/health</code>. Full monitoring UI coming in M4.
      </p>
    </div>
  )
}
