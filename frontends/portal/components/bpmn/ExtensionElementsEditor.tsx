'use client'

import { useState, useEffect } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Plus, Trash2, Save } from 'lucide-react'

export interface ExtensionField {
  name: string
  value: string
  type: 'string' | 'expression'
}

interface ExtensionElementsEditorProps {
  element: any
  modeler: any
  onUpdate?: (fields: ExtensionField[]) => void
}

/**
 * ExtensionElementsEditor Component
 *
 * Provides visual editing of Flowable extension elements (flowable:field)
 * for ServiceTask delegate configuration.
 *
 * Features:
 * - Add/edit/delete extension fields
 * - Support for string and expression types
 * - Real-time XML generation preview
 * - Pre-configured templates for common delegates
 */
export default function ExtensionElementsEditor({
  element,
  modeler,
  onUpdate
}: ExtensionElementsEditorProps) {
  const [fields, setFields] = useState<ExtensionField[]>([])
  const [newField, setNewField] = useState<ExtensionField>({
    name: '',
    value: '',
    type: 'string'
  })
  const [isAdding, setIsAdding] = useState(false)

  useEffect(() => {
    if (element && modeler) {
      extractExtensionFields()
    }
  }, [element, modeler])

  /**
   * Extract existing extension fields from the element
   */
  const extractExtensionFields = () => {
    try {
      const businessObject = element.businessObject
      const extensionElements = businessObject.extensionElements

      if (!extensionElements) {
        setFields([])
        return
      }

      const flowableFields = extensionElements.values?.filter(
        (ext: any) => ext.$type === 'flowable:Field'
      ) || []

      const extractedFields: ExtensionField[] = flowableFields.map((field: any) => {
        const stringValue = field.string
        const expressionValue = field.expression

        return {
          name: field.name || '',
          value: stringValue || expressionValue || '',
          type: expressionValue ? 'expression' : 'string'
        }
      })

      setFields(extractedFields)
    } catch (error) {
      console.error('Error extracting extension fields:', error)
      setFields([])
    }
  }

  /**
   * Add a new field to the extension elements
   */
  const handleAddField = () => {
    if (!newField.name || !newField.value) {
      alert('Please provide both field name and value')
      return
    }

    const updatedFields = [...fields, newField]
    updateExtensionElements(updatedFields)

    setNewField({ name: '', value: '', type: 'string' })
    setIsAdding(false)
  }

  /**
   * Delete a field from extension elements
   */
  const handleDeleteField = (index: number) => {
    const updatedFields = fields.filter((_, i) => i !== index)
    updateExtensionElements(updatedFields)
  }

  /**
   * Update field value
   */
  const handleUpdateField = (index: number, updates: Partial<ExtensionField>) => {
    const updatedFields = fields.map((field, i) =>
      i === index ? { ...field, ...updates } : field
    )
    setFields(updatedFields)
  }

  /**
   * Save field changes
   */
  const handleSaveField = (index: number) => {
    updateExtensionElements(fields)
  }

  /**
   * Update the BPMN element with new extension elements
   */
  const updateExtensionElements = (updatedFields: ExtensionField[]) => {
    try {
      const modeling = modeler.get('modeling')
      const moddle = modeler.get('moddle')
      const businessObject = element.businessObject

      // Create extension elements structure
      const extensionElements = moddle.create('bpmn:ExtensionElements', {
        values: updatedFields.map(field => {
          const flowableField = moddle.create('flowable:Field', {
            name: field.name
          })

          if (field.type === 'expression') {
            flowableField.expression = field.value
          } else {
            flowableField.string = field.value
          }

          return flowableField
        })
      })

      // Update the element
      modeling.updateProperties(element, {
        extensionElements: updatedFields.length > 0 ? extensionElements : undefined
      })

      setFields(updatedFields)

      if (onUpdate) {
        onUpdate(updatedFields)
      }
    } catch (error) {
      console.error('Error updating extension elements:', error)
      alert('Failed to update extension elements')
    }
  }

  /**
   * Load template for RestServiceDelegate
   */
  const loadRestServiceTemplate = () => {
    const template: ExtensionField[] = [
      { name: 'url', value: 'http://service-name:8080/api/endpoint', type: 'string' },
      { name: 'method', value: 'POST', type: 'string' },
      { name: 'headers', value: 'Content-Type:application/json', type: 'string' },
      { name: 'body', value: '#{{}}', type: 'expression' },
      { name: 'responseVariable', value: 'serviceResponse', type: 'string' }
    ]
    updateExtensionElements(template)
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">Extension Fields</CardTitle>
        <CardDescription>
          Configure delegate parameters for service calls
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* Template loader */}
        <div className="flex gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={loadRestServiceTemplate}
            className="text-xs"
          >
            Load REST Service Template
          </Button>
        </div>

        {/* Existing fields */}
        {fields.length > 0 && (
          <div className="space-y-3">
            {fields.map((field, index) => (
              <Card key={index} className="p-3">
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <Label className="text-xs font-semibold">{field.name}</Label>
                    <div className="flex gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleSaveField(index)}
                        className="h-6 w-6 p-0"
                      >
                        <Save className="h-3 w-3" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleDeleteField(index)}
                        className="h-6 w-6 p-0"
                      >
                        <Trash2 className="h-3 w-3 text-destructive" />
                      </Button>
                    </div>
                  </div>

                  <div className="grid gap-2">
                    <div>
                      <Label className="text-xs">Name</Label>
                      <Input
                        value={field.name}
                        onChange={(e) => handleUpdateField(index, { name: e.target.value })}
                        className="h-8 text-xs"
                        placeholder="Field name"
                      />
                    </div>
                    <div>
                      <Label className="text-xs">Value</Label>
                      <Input
                        value={field.value}
                        onChange={(e) => handleUpdateField(index, { value: e.target.value })}
                        className="h-8 text-xs"
                        placeholder="Field value"
                      />
                    </div>
                    <div>
                      <Label className="text-xs">Type</Label>
                      <Select
                        value={field.type}
                        onValueChange={(value: 'string' | 'expression') =>
                          handleUpdateField(index, { type: value })
                        }
                      >
                        <SelectTrigger className="h-8 text-xs">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="string">String</SelectItem>
                          <SelectItem value="expression">Expression</SelectItem>
                        </SelectContent>
                      </Select>
                    </div>
                  </div>
                </div>
              </Card>
            ))}
          </div>
        )}

        {/* Add new field form */}
        {isAdding ? (
          <Card className="p-3 border-dashed">
            <div className="space-y-2">
              <Label className="text-xs font-semibold">New Field</Label>
              <div className="grid gap-2">
                <div>
                  <Label className="text-xs">Name</Label>
                  <Input
                    value={newField.name}
                    onChange={(e) => setNewField({ ...newField, name: e.target.value })}
                    className="h-8 text-xs"
                    placeholder="e.g., url, method, headers"
                  />
                </div>
                <div>
                  <Label className="text-xs">Value</Label>
                  <Input
                    value={newField.value}
                    onChange={(e) => setNewField({ ...newField, value: e.target.value })}
                    className="h-8 text-xs"
                    placeholder="Field value"
                  />
                </div>
                <div>
                  <Label className="text-xs">Type</Label>
                  <Select
                    value={newField.type}
                    onValueChange={(value: 'string' | 'expression') =>
                      setNewField({ ...newField, type: value })
                    }
                  >
                    <SelectTrigger className="h-8 text-xs">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="string">String</SelectItem>
                      <SelectItem value="expression">Expression</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>
              <div className="flex gap-2 mt-2">
                <Button
                  variant="default"
                  size="sm"
                  onClick={handleAddField}
                  className="flex-1 h-8 text-xs"
                >
                  Add Field
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    setIsAdding(false)
                    setNewField({ name: '', value: '', type: 'string' })
                  }}
                  className="flex-1 h-8 text-xs"
                >
                  Cancel
                </Button>
              </div>
            </div>
          </Card>
        ) : (
          <Button
            variant="outline"
            size="sm"
            onClick={() => setIsAdding(true)}
            className="w-full text-xs"
          >
            <Plus className="h-3 w-3 mr-2" />
            Add Extension Field
          </Button>
        )}

        {/* XML Preview */}
        {fields.length > 0 && (
          <div className="mt-4">
            <Label className="text-xs font-semibold">Generated XML Preview</Label>
            <pre className="mt-2 p-2 bg-muted rounded text-xs overflow-x-auto">
              {generateXmlPreview(fields)}
            </pre>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

/**
 * Generate XML preview for extension elements
 */
function generateXmlPreview(fields: ExtensionField[]): string {
  const fieldXml = fields.map(field => {
    const valueXml = field.type === 'expression'
      ? `<flowable:expression>${field.value}</flowable:expression>`
      : `<flowable:string>${field.value}</flowable:string>`

    return `  <flowable:field name="${field.name}">
    ${valueXml}
  </flowable:field>`
  }).join('\n')

  return `<extensionElements>
${fieldXml}
</extensionElements>`
}
