'use client'

import { DatasourceForm } from '../_components/DatasourceForm'

export default function NewDatasourcePage() {
  return (
    <div className="max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Register Datasource</h1>
        <p className="text-sm text-muted-foreground mt-0.5">
          Add a new JDBC datasource for use with database connector workflows.
        </p>
      </div>
      <DatasourceForm />
    </div>
  )
}
