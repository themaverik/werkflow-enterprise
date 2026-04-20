'use client'

import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Loader2 } from 'lucide-react'
import FormJsViewer from '@/components/forms/FormJsViewer'
import type { TaskFormData } from '@/lib/types/task'

interface FormSectionProps {
  formData: TaskFormData | undefined
  isLoading: boolean
  onSubmit: (data: Record<string, any>) => void
  isSubmitting: boolean
  readonly: boolean
}

function isValidFormJsSchema(schema: any): boolean {
  return (
    schema !== null &&
    typeof schema === 'object' &&
    Array.isArray(schema.components)
  )
}

function ProcessVariablesTable({ variables }: { variables: Record<string, any> }) {
  const entries = Object.entries(variables)

  if (entries.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">No process variables available.</p>
    )
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b">
            <th className="text-left py-2 pr-4 font-semibold text-muted-foreground w-1/3">Key</th>
            <th className="text-left py-2 font-semibold text-muted-foreground">Value</th>
          </tr>
        </thead>
        <tbody>
          {entries.map(([key, value]) => (
            <tr key={key} className="border-b last:border-b-0">
              <td className="py-2 pr-4 font-mono text-xs text-muted-foreground align-top">{key}</td>
              <td className="py-2 break-all">
                {value === null ? (
                  <span className="text-muted-foreground italic">null</span>
                ) : typeof value === 'boolean' ? (
                  <span className={value ? 'text-green-600' : 'text-red-600'}>
                    {value.toString()}
                  </span>
                ) : typeof value === 'object' ? (
                  <pre className="text-xs bg-muted rounded px-2 py-1 overflow-auto">
                    {JSON.stringify(value, null, 2)}
                  </pre>
                ) : (
                  String(value)
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function FormSection({
  formData,
  isLoading,
  onSubmit,
  isSubmitting,
  readonly,
}: FormSectionProps) {
  const hasValidSchema = formData && isValidFormJsSchema(formData.formData)
  const hasProcessVariables =
    formData && !hasValidSchema && formData.processVariables && Object.keys(formData.processVariables).length > 0

  return (
    <Card>
      <CardHeader>
        <CardTitle>Task Form</CardTitle>
        {formData?.formKey && (
          <CardDescription>Form Key: {formData.formKey}</CardDescription>
        )}
      </CardHeader>
      <CardContent>
        {isLoading && (
          <div className="flex items-center justify-center py-8 text-muted-foreground gap-2">
            <Loader2 className="h-4 w-4 animate-spin" />
            <span>Loading form...</span>
          </div>
        )}

        {!isLoading && !formData && (
          <div className="text-center py-8 text-muted-foreground">
            No form data available for this task.
          </div>
        )}

        {!isLoading && hasValidSchema && (
          <div className="space-y-4">
            <FormJsViewer
              schema={formData!.formData}
              data={formData!.processVariables}
              onSubmit={onSubmit}
              readonly={readonly}
            />
          </div>
        )}

        {!isLoading && formData && !hasValidSchema && (
          <div className="space-y-4">
            <div>
              <h4 className="font-semibold mb-3 text-sm">Process Variables</h4>
              <ProcessVariablesTable
                variables={formData.processVariables || {}}
              />
            </div>
            {hasProcessVariables && !readonly && (
              <Button
                onClick={() => onSubmit(formData!.processVariables)}
                disabled={isSubmitting || readonly}
                className="w-full"
              >
                {isSubmitting ? (
                  <>
                    <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                    Submitting...
                  </>
                ) : (
                  'Submit'
                )}
              </Button>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
