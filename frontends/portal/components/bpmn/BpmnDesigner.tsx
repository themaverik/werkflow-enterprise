'use client'

import { useEffect, useRef, useState } from 'react'
import BpmnModeler from 'bpmn-js/lib/Modeler'
import { Button } from '@/components/ui/button'
import ExpressionBuilder from './ExpressionBuilder'
import ServiceTaskPropertiesPanel from './ServiceTaskPropertiesPanel'
import { Card } from '@/components/ui/card'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { deployBpmn, saveDraft } from '@/lib/api/flowable'
import { generateBlankBpmn, downloadBpmn, extractProcessName } from '@/lib/bpmn/utils'
import { Save, Download, Upload, ZoomIn, ZoomOut, Maximize2, Settings, CheckCircle } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { fetchDoaLevels, DoaLevel } from '@/lib/api/doa'
import { listCustodyMappings, CustodyMappingResponse } from '@/lib/api/custody'

// Import BPMN.js CSS
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'
import 'bpmn-js/dist/assets/bpmn-js.css'

// Import Properties Panel CSS
import '@bpmn-io/properties-panel/dist/assets/properties-panel.css'

// Import Properties Panel modules
import {
  BpmnPropertiesPanelModule,
  BpmnPropertiesProviderModule,
} from 'bpmn-js-properties-panel'

import FlowablePropertiesProviderModule from '@/lib/bpmn/flowable-properties-module'
import { setFormSchemaOptions, setNotificationTemplateOptions, setGroupOptions, setProcessDefinitionOptions, setDmnDecisionOptions } from '@/lib/bpmn/flowable-properties-provider'
import { getFormDefinitions, getNotificationTemplates, getGroups, getProcessDefinitions } from '@/lib/api/flowable'
import { listDecisions } from '@/lib/api/dmn'

const DEFAULT_EXPRESSION_VARIABLES = ['doaLevel', 'custodyGroup', 'totalAmount', 'status', 'departmentId', 'requesterId']

interface BpmnDesignerProps {
  initialXml?: string
  processId?: string
}

export default function BpmnDesigner({ initialXml, processId }: BpmnDesignerProps) {
  const t = useTranslations('bpmn')
  const containerRef = useRef<HTMLDivElement>(null)
  const propertiesPanelRef = useRef<HTMLDivElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const modelerRef = useRef<any>(null)
  const [modeler, setModeler] = useState<BpmnModeler | null>(null)
  const [processName, setProcessName] = useState('')
  const [hasChanges, setHasChanges] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showProperties, setShowProperties] = useState(true)
  const queryClient = useQueryClient()
  const router = useRouter()

  const [selectedElement, setSelectedElement] = useState<{ type: string; businessObject: Record<string, any> } | null>(null)

  const [doaLevels, setDoaLevels] = useState<DoaLevel[]>([])
  const [custodyMappings, setCustodyMappings] = useState<CustodyMappingResponse[]>([])

  const [draftSuccess, setDraftSuccess] = useState(false)
  const [draftError, setDraftError] = useState<string | null>(null)
  const [deployError, setDeployError] = useState<string | null>(null)

  const saveDraftMutation = useMutation({
    mutationFn: async () => {
      if (!modeler) throw new Error('Modeler not initialized')
      const { xml } = await modeler.saveXML({ format: true })
      if (!xml) throw new Error('Failed to generate XML')
      const key = processId ? processId.split(':')[0] : (processName || 'draft')
      return saveDraft(key, processName || 'Draft', xml)
    },
    onSuccess: () => {
      setHasChanges(false)
      setDraftSuccess(true)
      setDraftError(null)
      setTimeout(() => setDraftSuccess(false), 3000)
    },
    onError: (error: Error) => {
      setDraftError(error.message)
    }
  })

  // Initialize BPMN modeler
  useEffect(() => {
    if (!containerRef.current || !propertiesPanelRef.current) return

    // Guard against React StrictMode double-invoke: the cleanup runs before the
    // second invocation, destroying the first modeler. Without this flag the
    // first modeler's in-flight importXML promise would resolve/reject on the
    // destroyed instance, causing a spurious "Failed to load BPMN diagram" error.
    let cancelled = false

    const bpmnModeler = new BpmnModeler({
      container: containerRef.current,
      keyboard: {
        bindTo: document
      },
      height: '100%',
      propertiesPanel: {
        parent: propertiesPanelRef.current
      },
      additionalModules: [
        BpmnPropertiesPanelModule,
        BpmnPropertiesProviderModule,
        FlowablePropertiesProviderModule
      ]
    })

    // Load initial diagram
    const xmlToLoad = initialXml || generateBlankBpmn()
    bpmnModeler
      .importXML(xmlToLoad)
      .then(() => {
        if (cancelled) return
        const canvas = bpmnModeler.get('canvas')
        canvas.zoom('fit-viewport')

        // Extract process name — only set if user hasn't typed something already
        const name = extractProcessName(xmlToLoad)
        if (name) setProcessName(prev => prev || name)
      })
      .catch((err: Error) => {
        if (cancelled) return
        console.error('Error importing BPMN:', err)
        setError('Failed to load BPMN diagram')
      })

    setModeler(bpmnModeler)
    modelerRef.current = bpmnModeler

    // Listen for diagram changes
    const eventBus = bpmnModeler.get('eventBus')
    const handleChange = () => setHasChanges(true)

    eventBus.on('commandStack.changed', handleChange)

    const handleSelectionChange = (event: any) => {
      const elements: any[] = event.newSelection
      setSelectedElement(elements.length === 1 ? elements[0] : null)
    }
    eventBus.on('selection.changed', handleSelectionChange)

    return () => {
      cancelled = true
      eventBus.off('commandStack.changed', handleChange)
      eventBus.off('selection.changed', handleSelectionChange)
      setSelectedElement(null)
      bpmnModeler.destroy()
    }
  }, [initialXml])

  // Fetch dropdown data for the properties panel with retry on 401
  useEffect(() => {
    let cancelled = false

    const refreshPropertiesPanel = () => {
      const mod = modelerRef.current
      if (!mod) return
      const selection = mod.get('selection')
      const selectedElements = selection.get()
      if (selectedElements.length > 0) {
        const eb = mod.get('eventBus')
        eb.fire('elements.changed', { elements: selectedElements })
      }
    }

    const fetchForms = (attempt = 0) => {
      getFormDefinitions()
        .then((forms) => {
          if (!cancelled) {
            setFormSchemaOptions(forms.map((f) => ({ key: f.key, name: f.name })))
            refreshPropertiesPanel()
          }
        })
        .catch((err: any) => {
          if (!cancelled && err?.response?.status === 401 && attempt < 3) {
            setTimeout(() => fetchForms(attempt + 1), 1500)
          } else if (!cancelled) {
            console.error('Failed to load form definitions for properties panel:', err)
          }
        })
    }

    const fetchTemplates = (attempt = 0) => {
      getNotificationTemplates()
        .then((templates) => {
          if (!cancelled) {
            setNotificationTemplateOptions(templates.map((t) => ({ key: t.key, name: t.name })))
            refreshPropertiesPanel()
          }
        })
        .catch((err: any) => {
          if (!cancelled && err?.response?.status === 401 && attempt < 3) {
            setTimeout(() => fetchTemplates(attempt + 1), 1500)
          } else if (!cancelled) {
            console.error('Failed to load notification templates for properties panel:', err)
          }
        })
    }

    const fetchGroups = (attempt = 0) => {
      getGroups()
        .then((groups) => {
          if (!cancelled) {
            setGroupOptions(groups.map((g) => ({ id: g.id, name: g.name })))
            refreshPropertiesPanel()
          }
        })
        .catch((err: any) => {
          if (!cancelled && err?.response?.status === 401 && attempt < 3) {
            setTimeout(() => fetchGroups(attempt + 1), 1500)
          } else if (!cancelled) {
            console.error('Failed to load groups for properties panel:', err)
          }
        })
    }

    const fetchProcessDefinitions = () => {
      getProcessDefinitions()
        .then((defs) => {
          if (cancelled) return
          // Deduplicate by key — keep highest version per key, exclude suspended
          const byKey = new Map<string, { key: string; name: string; version: number }>()
          for (const def of defs) {
            if (def.suspended) continue
            const existing = byKey.get(def.key)
            if (!existing || def.version > existing.version) {
              byKey.set(def.key, { key: def.key, name: def.name || def.key, version: def.version })
            }
          }
          setProcessDefinitionOptions(Array.from(byKey.values()))
          refreshPropertiesPanel()
        })
        .catch((err: any) => {
          if (!cancelled) console.error('Failed to load process definitions for Trigger Process:', err)
        })
    }

    const fetchDmnDecisions = () => {
      listDecisions()
        .then((decisions) => {
          if (!cancelled) {
            setDmnDecisionOptions(decisions.map((d) => ({ key: d.key, name: d.name || d.key })))
            refreshPropertiesPanel()
          }
        })
        .catch((err: any) => {
          if (!cancelled) console.error('Failed to load DMN decisions for Business Rule Task:', err)
        })
    }

    const fetchDoaLevels_ = (attempt = 0) => {
      fetchDoaLevels()
        .then((levels) => {
          if (!cancelled) {
            setDoaLevels(levels)
          }
        })
        .catch((err: any) => {
          if (!cancelled && err?.response?.status === 401 && attempt < 3) {
            setTimeout(() => fetchDoaLevels_(attempt + 1), 1500)
          } else if (!cancelled) {
            console.error('Failed to load DOA levels for gateway condition builder:', err)
          }
        })
    }

    const fetchCustodyMappings_ = () => {
      listCustodyMappings('default')
        .then((mappings) => { if (!cancelled) setCustodyMappings(mappings) })
        .catch(() => { /* non-critical, silently skip */ })
    }

    fetchForms()
    fetchTemplates()
    fetchGroups()
    fetchProcessDefinitions()
    fetchDmnDecisions()
    fetchDoaLevels_()
    fetchCustodyMappings_()
    return () => { cancelled = true }
  }, [])

  // Deploy to backend
  const deployMutation = useMutation({
    mutationFn: async () => {
      if (!modeler) throw new Error('Modeler not initialized')

      // Sync the process element name in the diagram with the UI state before export
      if (processName) {
        try {
          const elementRegistry = (modeler as any).get('elementRegistry')
          const modeling = (modeler as any).get('modeling')
          const processEl = (elementRegistry.getAll() as any[]).find((el: any) => el.type === 'bpmn:Process')
          if (processEl) {
            modeling.updateProperties(processEl, { name: processName })
          }
        } catch (err) {
          console.warn('Could not sync process name to diagram:', err)
        }
      }

      const { xml } = await modeler.saveXML({ format: true })
      if (!xml) throw new Error('Failed to generate XML')

      return deployBpmn({
        name: processName || 'New Process',
        bpmnXml: xml
      })
    },
    onSuccess: () => {
      setHasChanges(false)
      setDeployError(null)
      queryClient.invalidateQueries({ queryKey: ['processDefinitions'] })
      router.push('/processes')
    },
    onError: (error: Error) => {
      setDeployError(error.message)
    }
  })

  // Download as file
  const handleDownload = async () => {
    if (!modeler) return

    try {
      const { xml } = await modeler.saveXML({ format: true })
      if (xml) {
        const filename = `${processName || 'process'}.bpmn20.xml`
        downloadBpmn(xml, filename)
      }
    } catch (err) {
      console.error('Error downloading BPMN:', err)
      setError('Failed to download diagram')
    }
  }

  // Load from file
  const handleUpload = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file || !modeler) return

    const reader = new FileReader()
    reader.onload = async (e) => {
      const xml = e.target?.result as string
      try {
        await modeler.importXML(xml)
        const canvas = modeler.get('canvas')
        canvas.zoom('fit-viewport')
        setHasChanges(true)

        const name = extractProcessName(xml)
        if (name) setProcessName(name)
      } catch (err) {
        console.error('Error loading BPMN:', err)
        setError('Failed to load BPMN file')
      }
    }
    reader.readAsText(file)
  }

  // Zoom controls
  const handleZoomIn = () => {
    if (!modeler) return
    const canvas = modeler.get('canvas')
    canvas.zoom(canvas.zoom() + 0.1)
  }

  const handleZoomOut = () => {
    if (!modeler) return
    const canvas = modeler.get('canvas')
    canvas.zoom(canvas.zoom() - 0.1)
  }

  const handleFitViewport = () => {
    if (!modeler) return
    const canvas = modeler.get('canvas')
    canvas.zoom('fit-viewport')
  }

  return (
    <div className="flex flex-col h-screen">
      {/* Toolbar */}
      <Card className="border-b rounded-none">
        <div className="flex items-center justify-between p-4">
          <div className="flex items-center gap-4 flex-1">
            <input
              type="text"
              value={processName}
              onChange={(e) => setProcessName(e.target.value)}
              placeholder={t('processNamePlaceholder')}
              className="border rounded px-3 py-2 w-64"
            />
            {hasChanges && (
              <span className="text-sm text-amber-600">{t('unsavedChanges')}</span>
            )}
          </div>

          <div className="flex items-center gap-2">
            {/* Properties panel toggle */}
            <Button
              variant={showProperties ? "default" : "outline"}
              size="sm"
              onClick={() => setShowProperties(!showProperties)}
              title="Toggle properties panel"
            >
              <Settings className="h-4 w-4" />
            </Button>

            {/* Zoom controls */}
            <div className="flex items-center gap-1 border-l pl-2">
              <Button variant="outline" size="sm" onClick={handleZoomOut} title="Zoom out">
                <ZoomOut className="h-4 w-4" />
              </Button>
              <Button variant="outline" size="sm" onClick={handleZoomIn} title="Zoom in">
                <ZoomIn className="h-4 w-4" />
              </Button>
              <Button variant="outline" size="sm" onClick={handleFitViewport} title="Fit to viewport">
                <Maximize2 className="h-4 w-4" />
              </Button>
            </div>

            {/* File operations */}
            <input
              ref={fileInputRef}
              type="file"
              accept=".bpmn,.bpmn20.xml,.xml"
              onChange={handleUpload}
              className="hidden"
            />
            <Button
              variant="outline"
              size="sm"
              onClick={() => fileInputRef.current?.click()}
              title="Load from file"
            >
              <Upload className="h-4 w-4 mr-2" />
              {t('load')}
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={handleDownload}
              title="Download as file"
            >
              <Download className="h-4 w-4 mr-2" />
              {t('download')}
            </Button>

            {/* Save/Deploy */}
            {draftSuccess && (
              <span className="text-sm text-green-600 flex items-center gap-1">
                <CheckCircle className="h-4 w-4" /> {t('saveSuccess')}
              </span>
            )}
            {draftError && (
              <span className="text-sm text-red-600">{t('draftFailedInline', { error: draftError })}</span>
            )}
            {deployError && (
              <span className="text-sm text-red-600">{t('deployFailedInline', { error: deployError })}</span>
            )}
            <Button
              variant="outline"
              size="sm"
              onClick={() => saveDraftMutation.mutate()}
              disabled={saveDraftMutation.isPending}
            >
              <Save className="h-4 w-4 mr-2" />
              {saveDraftMutation.isPending ? t('saving') : t('saveDraft')}
            </Button>
            <Button
              size="sm"
              onClick={() => deployMutation.mutate()}
              disabled={deployMutation.isPending || !processName}
            >
              {deployMutation.isPending ? t('deploying') : t('deploy')}
            </Button>
          </div>
        </div>
      </Card>

      {/* Error display */}
      {error && (
        <div className="bg-destructive/10 text-destructive p-3 text-sm">
          {error}
        </div>
      )}

      {/* Main content area with canvas and properties panel */}
      <div className="flex flex-1 overflow-hidden">
        {/* BPMN Canvas */}
        <div ref={containerRef} className="flex-1 bg-gray-50" />

        {/* Properties Panel */}
        {showProperties && (
          <div className="w-80 border-l bg-white overflow-auto flex flex-col">
            {/* Native bpmn-js properties panel — hidden when a custom panel takes over */}
            <div
              ref={propertiesPanelRef}
              className={
                isSequenceFlow(selectedElement) || isExternalApiServiceTask(selectedElement)
                  ? 'hidden'
                  : 'flex-1'
              }
            />

            {/* Sequence flow condition panel */}
            {isSequenceFlow(selectedElement) && modeler && (
              <div className="p-3 flex-1 overflow-auto">
                <ExpressionBuilder
                  value={readConditionExpression(selectedElement)}
                  onChange={(expr) => writeConditionExpression(selectedElement, modeler, expr)}
                  availableVariables={DEFAULT_EXPRESSION_VARIABLES}
                  catalogValues={{
                    ...(doaLevels.length > 0 && {
                      doaLevel: doaLevels.map((d) => ({
                        value: d.doaLevel,
                        label: d.label ? `${d.doaLevel} — ${d.label}` : d.doaLevel,
                      })),
                    }),
                    ...(custodyMappings.length > 0 && {
                      custodyGroup: custodyMappings.map((m) => ({
                        value: m.custodyGroup,
                        label: m.displayName ? `${m.custodyGroup} — ${m.displayName}` : m.custodyGroup,
                      })),
                    }),
                  }}
                />
              </div>
            )}

            {/* Service task contract panel */}
            {isExternalApiServiceTask(selectedElement) && modeler && (
              <div className="flex-1 overflow-auto">
                <ServiceTaskPropertiesPanel element={selectedElement} modeler={modeler} />
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

function isSequenceFlow(element: { type: string; businessObject: Record<string, any> } | null): boolean {
  return element?.type === 'bpmn:SequenceFlow'
}

function isExternalApiServiceTask(element: { type: string; businessObject: Record<string, any> } | null): boolean {
  return (
    element?.type === 'bpmn:ServiceTask' &&
    element?.businessObject?.get('flowable:actionType') === 'EXTERNAL_API_CALL'
  )
}

function readConditionExpression(element: { type: string; businessObject: Record<string, any> } | null): string {
  return element?.businessObject?.conditionExpression?.body || ''
}

function writeConditionExpression(element: { type: string; businessObject: Record<string, any> } | null, modeler: any, expression: string): void {
  const moddle = modeler.get('moddle')
  const modeling = modeler.get('modeling')
  if (!expression) {
    modeling.updateProperties(element, { conditionExpression: undefined })
    return
  }
  const formalExpr = moddle.create('bpmn:FormalExpression', { body: expression })
  modeling.updateProperties(element, { conditionExpression: formalExpr })
}
