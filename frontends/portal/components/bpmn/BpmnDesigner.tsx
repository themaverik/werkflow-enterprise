'use client'

import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import BpmnModeler from 'bpmn-js/lib/Modeler'
import { Button } from '@/components/ui/button'
import ExpressionBuilder from './ExpressionBuilder'
import ServiceTaskPropertiesPanel from './ServiceTaskPropertiesPanel'
import { Card } from '@/components/ui/card'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { deployBpmn, saveDraft, deleteDraft, type SaveDraftOptions } from '@/lib/api/flowable'
import { generateBlankBpmn, downloadBpmn, extractProcessName } from '@/lib/bpmn/utils'
import { Save, Download, Upload, ZoomIn, ZoomOut, Maximize2, Settings, CheckCircle } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useToast } from '@/hooks/use-toast'
import { fetchDoaLevels, DoaLevel } from '@/lib/api/doa'
import { listCustodyMappings, CustodyMappingResponse } from '@/lib/api/custody'
import { useAuth } from '@/lib/auth/auth-context'
import { platformApi } from '@/lib/platform/api'
import { usePlatformCapabilities } from '@/lib/platform/usePlatformCapabilities'
import type { CandidateGroupEntry, ArtifactMetadata } from '@/lib/platform/types'
import { ArtifactMetadataPanel } from '@/components/design/ArtifactMetadataPanel'

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
import flowableModdleDescriptor from '@/lib/bpmn/flowable-moddle.json'
import { setFormSchemaOptions, setNotificationTemplateOptions, setGroupOptions, setProcessDefinitionOptions, setDmnDecisionOptions, setDelegateOptions, setCurrentUserRoles, setProcessVariableOptions, setCustodyVarGroups } from '@/lib/bpmn/flowable-properties-provider'
import { seedComboboxCache } from '@/lib/bpmn/useVariableSources'
import { getFormDefinitions, getFormDefinition, getNotificationTemplates, getGroups, getProcessDefinitions, getDelegates } from '@/lib/api/flowable'
import { listDecisions, getDecisionXml } from '@/lib/api/dmn'
import { listProcessVariablesAt } from '@/lib/api/dtds'

// Variables set by Flowable engine infrastructure — present in every process instance
const STANDARD_EXPRESSION_VARIABLES = [
  'initiator',        // user who started the process
  'processInstanceId',
  'decision',         // set by ApprovalTaskCompletionListener or script task
  'approvalRequired', // common DMN output
  'approvedBy',
  'approvedAt',
  'approvalComments',
  'rejectionReason',
]

/** Walk the bpmn-js element registry to find all form keys and DMN decision keys
 *  referenced in the current diagram. Only these are relevant for expression variables. */
function extractReferencedKeys(modeler: any): { formKeys: string[]; dmnKeys: string[] } {
  const elementRegistry = modeler.get('elementRegistry')
  const formKeys = new Set<string>()
  const dmnKeys = new Set<string>()

  elementRegistry.getAll().forEach((element: any) => {
    const bo = element.businessObject
    if (!bo) return

    // Form key on start events and user tasks
    if (bo.$type === 'bpmn:StartEvent' || bo.$type === 'bpmn:UserTask') {
      const fk = bo.get('flowable:formKey')
      if (fk) formKeys.add(fk)
    }

    // DMN decision key in native DMN service tasks (flowable:type="dmn")
    if (bo.$type === 'bpmn:ServiceTask' && bo.get('flowable:type') === 'dmn') {
      const fields: any[] = bo.extensionElements?.values
        ?.filter((v: any) => v.$type === 'flowable:Field') ?? []
      const keyField = fields.find((v: any) => v.name === 'decisionTableReferenceKey')
      const key = keyField?.string ?? keyField?.expression ?? ''
      if (key) dmnKeys.add(key)
    }
  })

  return { formKeys: Array.from(formKeys), dmnKeys: Array.from(dmnKeys) }
}

function extractFormFieldKeys(formJson: string): string[] {
  try {
    const schema = JSON.parse(formJson)
    const components: any[] = schema.components ?? schema.fields ?? []
    return components
      .filter((c: any) => c.key && c.key !== 'submit')
      .map((c: any) => c.key as string)
  } catch {
    return []
  }
}

/** Parse a DMN XML string and return input expression names + output column names. */
function extractDmnVariables(dmnXml: string): string[] {
  try {
    const doc = new DOMParser().parseFromString(dmnXml, 'application/xml')
    const vars: string[] = []
    // Input columns: <inputExpression> text content = the variable name
    doc.querySelectorAll('inputExpression').forEach((el) => {
      const v = el.textContent?.trim()
      if (v) vars.push(v)
    })
    // Output columns: <output name="..."> attribute
    doc.querySelectorAll('output').forEach((el) => {
      const v = el.getAttribute('name')
      if (v) vars.push(v)
    })
    return vars
  } catch {
    return []
  }
}

interface BpmnDesignerProps {
  initialXml?: string
  processId?: string
  /** Pre-populated artifact metadata when opening a saved draft (optional). */
  initialMetadata?: ArtifactMetadata
}

/** Read a <flowable:field> string or expression value from a business object */
function getFlowableField(bo: any, fieldName: string): string {
  const values: any[] = bo.extensionElements?.get('values') ?? []
  const field = values.find((v: any) => v.$type === 'flowable:Field' && v.name === fieldName)
  return field?.expression ?? field?.string ?? ''
}

/** Validate action blocks before deploy — returns list of human-readable error strings */
function validateActionBlocks(modeler: any): string[] {
  const errors: string[] = []
  try {
    const elementRegistry = (modeler as any).get('elementRegistry')
    const elements: any[] = elementRegistry.getAll()
    for (const el of elements) {
      const bo = el.businessObject
      if (!bo) continue
      const actionType: string = bo.get('flowable:actionType') || ''
      const label = bo.name ? `"${bo.name}"` : `[${el.id}]`

      if (actionType === 'SEND_NOTIFICATION') {
        if (!getFlowableField(bo, 'recipient')) {
          errors.push(`${label}: Notification task is missing a recipient.`)
        }
        if (!getFlowableField(bo, 'templateKey')) {
          errors.push(`${label}: Notification task is missing a template key.`)
        }
      }

      if (actionType === 'CONNECTOR_OPERATION') {
        if (!getFlowableField(bo, 'connector')) {
          errors.push(`${label}: Connector Operation task requires a connector to be selected.`)
        }
      }

      if (actionType === 'CALL_SUBPROCESS') {
        if (!bo.get('calledElement')) {
          errors.push(`${label}: Call Subprocess task is missing a process key (calledElement).`)
        }
      }

      if (actionType === 'GROOVY_SCRIPT') {
        if (!bo.get('flowable:script')) {
          errors.push(`${label}: Groovy Script task has no script content.`)
        }
      }

      if (actionType === 'HUMAN_APPROVAL') {
        const hasAssignee = !!(bo.get('assignee') || bo.get('candidateGroups'))
        if (!hasAssignee) {
          errors.push(`${label}: User task has no assignee or candidate groups set.`)
        }
      }

      // Validate native DMN service tasks have a decision key
      if (bo.$type === 'bpmn:ServiceTask' && bo.get('flowable:type') === 'dmn') {
        if (!getFlowableField(bo, 'decisionTableReferenceKey')) {
          errors.push(`${label}: DMN Decision task is missing a decision key (decisionTableReferenceKey).`)
        }
      }
    }
  } catch (err) {
    console.warn('validateActionBlocks error:', err)
  }
  return errors
}

const DEFAULT_METADATA: ArtifactMetadata = { tags: [] }

export default function BpmnDesigner({ initialXml, processId, initialMetadata }: BpmnDesignerProps) {
  const t = useTranslations('bpmn')
  const { toast } = useToast()
  const { user, token } = useAuth()
  const { data: capabilities } = usePlatformCapabilities()
  const containerRef = useRef<HTMLDivElement>(null)
  const propertiesPanelRef = useRef<HTMLDivElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const modelerRef = useRef<any>(null)
  const [modeler, setModeler] = useState<BpmnModeler | null>(null)
  // Bug 2 fix: seed processName from initialXml immediately so saveDraftMutation
  // always has the correct name even before the async importXML resolves.
  const [processName, setProcessName] = useState(() =>
    initialXml ? (extractProcessName(initialXml) ?? '') : ''
  )
  const [hasChanges, setHasChanges] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showProperties, setShowProperties] = useState(true)
  const queryClient = useQueryClient()
  const router = useRouter()

  const [selectedElement, setSelectedElement] = useState<{ type: string; businessObject: Record<string, any> } | null>(null)
  // When true, show the bpmn-js native panel even for custom-panel action types (user clicked back)
  const [showNativePanel, setShowNativePanel] = useState(false)
  // True when the Expression Builder panel is expanded for the current sequence flow.
  // Auto-opens if the flow already carries a condition expression.
  const [showExprBuilder, setShowExprBuilder] = useState(false)
  const [showMeta, setShowMeta] = useState(true)
  const [scrollContainer, setScrollContainer] = useState<Element | null>(null)

  const [doaLevels, setDoaLevels] = useState<DoaLevel[]>([])
  const [custodyMappings, setCustodyMappings] = useState<CustodyMappingResponse[]>([])
  const [formFieldVariables, setFormFieldVariables] = useState<string[]>([])

  const [draftSuccess, setDraftSuccess] = useState(false)
  const [draftError, setDraftError] = useState<string | null>(null)
  const [artifactMetadata, setArtifactMetadata] = useState<ArtifactMetadata>(
    initialMetadata ?? DEFAULT_METADATA
  )


  const saveDraftMutation = useMutation({
    mutationFn: async () => {
      if (!modeler) throw new Error('Modeler not initialized')
      const { xml } = await modeler.saveXML({ format: true })
      if (!xml) throw new Error('Failed to generate XML')
      // Bug 2 fix: when processId is present always derive key from it; never fall
      // through to processName which may be empty during a failed-deploy → save-draft flow.
      const key = processId ? processId.split(':')[0] : (processName.trim() || 'draft')
      const name = processName.trim() || key
      const metadata: SaveDraftOptions = {
        departmentCode: artifactMetadata.departmentCode,
        categoryCode: artifactMetadata.categoryCode,
        tags: artifactMetadata.tags,
      }
      return saveDraft(key, name, xml, metadata)
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
      ],
      moddleExtensions: {
        flowable: flowableModdleDescriptor,
      }
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

    // Fetch expression variables scoped to this process (form keys + DMN columns referenced in diagram)
    let variableRefreshTimer: ReturnType<typeof setTimeout> | null = null
    const doRefreshProcessVariables = (mod: any) => {
      if (!mod) return
      const { formKeys, dmnKeys } = extractReferencedKeys(mod)
      const allVars = new Set<string>()
      const formPromises = formKeys.map((key) =>
        getFormDefinition(key)
          .then((f) => { for (const k of extractFormFieldKeys(f.formJson || '')) allVars.add(k) })
          .catch(() => {})
      )
      const dmnPromises = dmnKeys.map((key) =>
        getDecisionXml(key)
          .then((xml) => { for (const v of extractDmnVariables(xml)) allVars.add(v) })
          .catch(() => {})
      )
      Promise.all([...formPromises, ...dmnPromises]).then(() => {
        if (!cancelled) setFormFieldVariables(Array.from(allVars))
      })
    }

    // Debounced re-derive when diagram changes (form key assigned, DMN key set, etc.)
    const handleChange = () => {
      setHasChanges(true)
      if (variableRefreshTimer) clearTimeout(variableRefreshTimer)
      variableRefreshTimer = setTimeout(() => doRefreshProcessVariables(bpmnModeler), 800)
    }

    eventBus.on('commandStack.changed', handleChange)

    // Auto-default SEND_NOTIFICATION when a SendTask is placed or morphed onto the canvas
    const handleElementChanged = (event: any) => {
      const el = event.element
      if (!el || el.type !== 'bpmn:SendTask') return
      const bo = el.businessObject
      if (!bo) return
      const existing = bo.get('flowable:actionType')
      if (existing) return // already has an action type — don't overwrite
      try {
        const modeling = bpmnModeler.get('modeling')
        modeling.updateProperties(el, { 'flowable:actionType': 'SEND_NOTIFICATION' })
        if (!bo.get('flowable:delegateExpression')) {
          modeling.updateProperties(el, {
            'flowable:delegateExpression': '${notificationDelegate}',
            delegateExpression: '${notificationDelegate}',
          })
        }
        modeling.setColor(el, { fill: '#fff3e0', stroke: '#e65100' })
      } catch {
        // modeling unavailable or element removed — skip
      }
    }
    eventBus.on('element.changed', handleElementChanged)

    // Initial variable load once modeler is ready
    doRefreshProcessVariables(bpmnModeler)

    const handleSelectionChange = (event: any) => {
      const elements: any[] = event.newSelection
      const el = elements.length === 1 ? elements[0] : null
      setSelectedElement(el)
      setShowNativePanel(false)
      // Auto-open Expression Builder whenever a sequence flow is selected
      if (el?.type === 'bpmn:SequenceFlow') {
        setShowExprBuilder(true)
      } else {
        setShowExprBuilder(false)
      }
    }
    eventBus.on('selection.changed', handleSelectionChange)

    return () => {
      cancelled = true
      if (variableRefreshTimer) clearTimeout(variableRefreshTimer)
      eventBus.off('commandStack.changed', handleChange)
      eventBus.off('element.changed', handleElementChanged)
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
      // Fetch all forms for the form-picker dropdown (assign form to task)
      getFormDefinitions()
        .then((forms) => {
          if (!cancelled) {
            setFormSchemaOptions(forms.map((f) => ({ key: f.key, name: f.name })))
            // seed combobox cache so VariableComboBoxEntry-based ab-formKey gets data
            const formComboItems = forms.map((f) => ({
              id: f.key,
              name: f.name ?? f.key,
              sans: true as const,
              meta: (f as any).version !== undefined ? `v${(f as any).version}` : undefined,
            }))
            seedComboboxCache('forms-deployed', formComboItems.length > 0
              ? [{ key: 'forms', label: 'Deployed Forms', icon: 'literal' as const, items: formComboItems }]
              : [])
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
      if (!token) {
        if (attempt < 3) { setTimeout(() => fetchGroups(attempt + 1), 1500); return }
        return
      }
      // Use PSS candidate-groups — ADR-010 compliant (no _APPROVER department groups)
      platformApi.candidateGroups(token)
        .then((groups: CandidateGroupEntry[]) => {
          if (!cancelled) {
            setGroupOptions(groups)
            // Seed combobox cache so VariableComboBoxEntry (isolated React root) gets
            // the same data without requiring its own authenticated fetch.
            const systemItems = groups
              .filter((g) => g.kind === 'SYSTEM' || g.tier === 1)
              .map((g) => ({ id: g.key, name: g.label ?? g.key, sans: true, lock: true, tier: g.isManagerTier ? 'manager-tier' as const : undefined }))
            const businessItems = groups
              .filter((g) => !(g.kind === 'SYSTEM' || g.tier === 1))
              .map((g) => ({ id: g.key, name: g.label ?? g.key, sans: true, tier: g.isManagerTier ? 'manager-tier' as const : undefined }))
            const comboGroups = [
              ...(systemItems.length > 0 ? [{ key: 'system', label: 'System · Tier 1 · read-only', icon: 'system' as const, items: systemItems }] : []),
              ...(businessItems.length > 0 ? [{ key: 'business', label: 'Business · Tier 2', icon: 'business' as const, items: businessItems }] : []),
            ]
            seedComboboxCache('pss-candidate-groups', comboGroups)
            refreshPropertiesPanel()
          }
        })
        .catch((err: unknown) => {
          if (!cancelled) {
            // Fall back to legacy getGroups on PSS failure — map to minimal CandidateGroupEntry shape
            getGroups()
              .then((groups) => {
                if (!cancelled) {
                  const mapped = groups.map((g) => ({
                    key: g.id,
                    label: g.name,
                    kind: 'BUSINESS' as const,
                    tier: 2 as const,
                    readOnly: false,
                    isManagerTier: false,
                    mappedFromRoles: [],
                  }))
                  setGroupOptions(mapped)
                  const fallbackItems = mapped.map((g) => ({ id: g.key, name: g.label ?? g.key, sans: true }))
                  seedComboboxCache('pss-candidate-groups', fallbackItems.length > 0
                    ? [{ key: 'business', label: 'Business · Tier 2', icon: 'business' as const, items: fallbackItems }]
                    : [])
                  refreshPropertiesPanel()
                }
              })
              .catch(() => {})
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
      listCustodyMappings()
        .then((page) => { if (!cancelled) setCustodyMappings(page.content) })
        .catch(() => { /* non-critical, silently skip */ })
    }

    const fetchDelegates = (attempt = 0) => {
      getDelegates()
        .then((names) => {
          if (!cancelled) {
            setDelegateOptions(names)
            refreshPropertiesPanel()
          }
        })
        .catch((err: any) => {
          if (!cancelled && err?.response?.status === 401 && attempt < 3) {
            setTimeout(() => fetchDelegates(attempt + 1), 1500)
          } else if (!cancelled) {
            console.error('Failed to load delegates for properties panel:', err)
          }
        })
    }

    const fetchCustodyVarGroups = () => {
      if (!token) return
      platformApi.feelExpressions(token)
        .then((catalog) => {
          if (cancelled) return
          const groups = catalog.custodyVars.groupResolutions.map((r) => ({
            key: `\${custodyVars.${r.key}}`,
            label: `\${custodyVars.${r.key}}`,
            pattern: `→ ${r.key}_*`,
          }))
          // Always add the dynamic lookup entry
          groups.push({
            key: '${custodyVars[itemCategory]}',
            label: '${custodyVars[itemCategory]}',
            pattern: 'by variable',
          })
          setCustodyVarGroups(groups)
          // Seed combobox cache in the Group[] shape expected by useVariableSources
          const custodyItems = groups.map((g) => ({
            id: g.key,
            name: g.label,
            meta: g.pattern ?? undefined,
          }))
          seedComboboxCache('custody-feel', custodyItems.length > 0
            ? [{ key: 'custody', label: 'Custody Lookups · ADR-004', icon: 'custody' as const, items: custodyItems }]
            : [])
          refreshPropertiesPanel()
        })
        .catch(() => { /* non-critical */ })
    }

    fetchForms()
    fetchTemplates()
    fetchGroups()
    fetchProcessDefinitions()
    fetchDmnDecisions()
    fetchDoaLevels_()
    fetchCustodyMappings_()
    fetchDelegates()
    fetchCustodyVarGroups()
    return () => { cancelled = true }
  }, [])

  // Sync current user roles into the properties provider for role-filtered dropdowns
  useEffect(() => {
    if (user?.roles) {
      setCurrentUserRoles(user.roles)
    }
  }, [user?.roles])

  // Track the bpmn-io scroll container so we can portal Artifact Metadata into it.
  // We observe `propertiesPanelRef` for child mutations because the library renders
  // asynchronously — the `.bio-properties-panel-scroll-container` element does not
  // exist in the DOM at mount time.
  useEffect(() => {
    const panelRoot = propertiesPanelRef.current
    if (!panelRoot) return

    const findAndSet = () => {
      const el = panelRoot.querySelector('.bio-properties-panel-scroll-container')
      setScrollContainer(el)
    }

    // Run once in case the panel already rendered
    findAndSet()

    const observer = new MutationObserver(findAndSet)
    observer.observe(panelRoot, { childList: true, subtree: true })

    return () => observer.disconnect()
  }, [modeler])

  // Fetch DTDS process variables in scope whenever a UserTask is selected
  useEffect(() => {
    if (!modeler || !processId || !selectedElement || selectedElement.type !== 'bpmn:UserTask') {
      setProcessVariableOptions([])
      return
    }
    const processDefId = processId.split(':')[0]
    const activityId = (selectedElement as any).businessObject?.id
    if (!activityId) return

    listProcessVariablesAt(processDefId, activityId)
      .then((vars) => {
        setProcessVariableOptions(vars ?? [])
        // seed combobox cache so Assignee / Candidate Users entries get DTDS data
        const resolvedVars = vars ?? []
        const stringVars = resolvedVars.filter((v) => !v.type || v.type === 'string')
        const dtdsItems = stringVars.map((v) => ({
          id: `\${${v.name}}`,
          name: `\${${v.name}}`,
          meta: v.setByTask ? `from ${v.setByTask}` : v.setByActivity ? `from ${v.setByActivity}` : undefined,
        }))
        // processDefId matches element.businessObject.$parent.id (both are the bare BPMN <process id> attribute)
        const cacheKey = JSON.stringify(['dtds-variables-string', processDefId, activityId])
        seedComboboxCache(cacheKey, dtdsItems.length > 0
          ? [{ key: 'process', label: 'Process Variables · in scope', icon: 'process' as const, items: dtdsItems }]
          : [])
        // Trigger bpmn-js properties panel refresh so VariableComboBoxEntry re-renders
        try { (modeler as any).get('eventBus').fire('propertiesPanel.updated') } catch (_) {}
      })
      .catch(() => setProcessVariableOptions([]))
  }, [selectedElement, processId, modeler])

  // Ctrl+S / Cmd+S keyboard shortcut for save draft
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault()
        if (!saveDraftMutation.isPending) saveDraftMutation.mutate()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [saveDraftMutation])

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

      // Bug 1 fix: morph any base bpmn:Task elements that carry flowable:delegateExpression
      // into bpmn:ServiceTask before export. These arise when a draft was saved before the
      // user explicitly changed the action type, so the auto-morph in setActionType never ran.
      try {
        const elementRegistry = (modeler as any).get('elementRegistry')
        const bpmnReplace = (modeler as any).get('bpmnReplace')
        const baseTasks = (elementRegistry.getAll() as any[]).filter(
          (el: any) =>
            el.type === 'bpmn:Task' &&
            el.businessObject?.get('flowable:delegateExpression')
        )
        for (const task of baseTasks) {
          bpmnReplace.replaceElement(task, { type: 'bpmn:ServiceTask' })
        }
      } catch (err) {
        console.warn('Could not auto-morph base Task elements before deploy:', err)
      }

      // Pre-deploy validation — catch misconfigured action blocks before hitting the engine
      const validationErrors = validateActionBlocks(modeler)
      if (validationErrors.length > 0) {
        throw new Error(`Process validation failed:\n${validationErrors.map(e => `• ${e}`).join('\n')}`)
      }

      const { xml } = await modeler.saveXML({ format: true })
      if (!xml) throw new Error('Failed to generate XML')

      return deployBpmn({
        name: processName || 'New Process',
        bpmnXml: xml
      })
    },
    onSuccess: (data) => {
      setHasChanges(false)
      queryClient.invalidateQueries({ queryKey: ['processDefinitions'] })
      // Surface any referenced decisions that could not be pinned into the bundle —
      // they will resolve to their latest deployed version at runtime (ADR-026).
      if (data.unbundledDecisions.length > 0) {
        toast({
          title: 'Deployed — some decisions not version-pinned',
          description:
            `These decision tables will resolve to their latest version: ${data.unbundledDecisions.join(', ')}. ` +
            'Deploy them, then redeploy this process to pin them.',
        })
      }
      // Clean up draft so it doesn't resurface as "unsaved draft" after deploy
      if (processId) {
        deleteDraft(processId.split(':')[0]).catch(() => {})
      }
      router.push('/processes')
    },
    onError: (error: any) => {
      // Extract the Flowable engine error message from the response body when available
      const responseData = error?.response?.data
      const engineMsg =
        typeof responseData === 'string'
          ? responseData
          : responseData?.message ?? responseData?.error ?? null
      toast({
        title: 'Deploy Failed',
        description: engineMsg || error.message,
        variant: 'destructive',
      })
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

  const capabilitiesUnavailable = !capabilities

  return (
    <div className="flex flex-col h-screen">
      {capabilitiesUnavailable && (
        <div className="bg-amber-50 border-b border-amber-200 px-4 py-2 text-sm text-amber-800">
          Designer capabilities unavailable — some options may be limited
        </div>
      )}
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
            <div className="flex items-center gap-2 border-l pl-2">
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
            </div>

            {/* Save/Deploy */}
            {draftSuccess && (
              <span className="text-sm text-green-600 flex items-center gap-1">
                <CheckCircle className="h-4 w-4" /> {t('saveSuccess')}
              </span>
            )}
            {draftError && (
              <span className="text-sm text-red-600">{t('draftFailedInline', { error: draftError })}</span>
            )}
            <div className="flex items-center gap-2 border-l pl-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => saveDraftMutation.mutate()}
                disabled={saveDraftMutation.isPending}
                title="Save draft (Ctrl+S)"
              >
                <Save className="h-4 w-4 mr-2" />
                {saveDraftMutation.isPending ? t('saving') : t('saveDraft')}
              </Button>
              <Button
                size="sm"
                onClick={() => deployMutation.mutate()}
                disabled={deployMutation.isPending || !processName}
                title="Deploy to engine"
              >
                {deployMutation.isPending ? t('deploying') : t('deploy')}
              </Button>
            </div>
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
        <div ref={containerRef} className="flex-1 bg-gray-50 dark:bg-gray-900" />

        {/* Properties Panel */}
        {showProperties && (
          <div className="w-96 min-w-[320px] border-l bg-background overflow-y-auto werkflow-props-panel">
            {/* Native bpmn-js properties panel — hidden when a fully custom panel takes over */}
            <div
              ref={propertiesPanelRef}
              className={!showNativePanel && isCustomPanelServiceTask(selectedElement) ? 'hidden' : ''}
            />

            {/* Sequence flow: optional Expression Builder toggle */}
            {isSequenceFlow(selectedElement) && modeler && (
              <div className="border-t">
                <button
                  type="button"
                  onClick={() => setShowExprBuilder((v) => !v)}
                  className="w-full flex items-center justify-between px-3 py-2 text-sm font-medium text-left hover:bg-muted/40 transition-colors sticky top-0 z-10 bg-background"
                >
                  <span>Expression Builder</span>
                  <span className="text-xs text-muted-foreground">{showExprBuilder ? '▲' : '▼'}</span>
                </button>
                {showExprBuilder && (
                  <div className="p-3 overflow-auto">
                    <ExpressionBuilder
                      value={readConditionExpression(selectedElement)}
                      onChange={(expr) => writeConditionExpression(selectedElement, modeler, expr)}
                      availableVariables={[...STANDARD_EXPRESSION_VARIABLES, ...formFieldVariables].filter(
                        (v, i, a) => a.indexOf(v) === i  // deduplicate
                      )}
                      catalogValues={{
                        ...(doaLevels.length > 0 && {
                          doaLevel: doaLevels.map((d) => ({
                            value: d.doaLevel,
                            label: d.label ? `${d.doaLevel} — ${d.label}` : d.doaLevel,
                          })),
                        }),
                        ...(custodyMappings.length > 0 && {
                          custodyGroup: custodyMappings.map((m) => ({
                            value: m.custodyOwner,
                            label: `${m.custodyOwner} (${m.candidateGroups.join(', ')})`,
                          })),
                        }),
                      }}
                    />
                  </div>
                )}
              </div>
            )}

            {/* Service task custom panel — covers CONNECTOR_OPERATION, HUMAN_APPROVAL, SEND_NOTIFICATION */}
            {!showNativePanel && isCustomPanelServiceTask(selectedElement) && modeler && (
              <div className="flex-1 overflow-auto wf-panel-custom">
                <ServiceTaskPropertiesPanel
                  element={selectedElement}
                  modeler={modeler}
                  onShowNativePanel={() => setShowNativePanel(true)}
                />
              </div>
            )}

            {/* Artifact Metadata portal — injected into .bio-properties-panel-scroll-container
                so it sits inside the same CSS variable scope as "General" and "Documentation".
                Only rendered when no element is selected (process-level) and after the
                library has created the scroll container in the DOM. */}
            {!selectedElement && scrollContainer && createPortal(
              <div className="bio-properties-panel-group">
                <div
                  className="bio-properties-panel-group-header"
                  onClick={() => setShowMeta((v) => !v)}
                >
                  <span className="bio-properties-panel-group-header-title">Artifact Metadata</span>
                  <div className="bio-properties-panel-group-header-buttons">
                    <button
                      type="button"
                      title="Toggle section"
                      className={`bio-properties-panel-group-header-button bio-properties-panel-arrow${showMeta ? ' wf-open' : ''}`}
                      onClick={(e) => { e.stopPropagation(); setShowMeta((v) => !v) }}
                    >
                      <svg
                        xmlns="http://www.w3.org/2000/svg"
                        width="16"
                        height="16"
                        className="bio-properties-panel-arrow-right"
                      >
                        <path
                          fillRule="evenodd"
                          d="m11.657 8-4.95 4.95a1 1 0 0 1-1.414-1.414L8.828 8 5.293 4.464A1 1 0 1 1 6.707 3.05L11.657 8Z"
                        />
                      </svg>
                    </button>
                  </div>
                </div>
                {showMeta && (
                  <ArtifactMetadataPanel
                    artifactType="process"
                    value={artifactMetadata}
                    onChange={(v) => { setArtifactMetadata(v); setHasChanges(true) }}
                  />
                )}
              </div>,
              scrollContainer
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

function isCustomPanelServiceTask(element: { type: string; businessObject: Record<string, any> } | null): boolean {
  const actionType = element?.businessObject?.get('flowable:actionType')
  const type = element?.type
  // HUMAN_APPROVAL morphs to UserTask; tolerate ServiceTask for legacy/unorphaned elements.
  if (actionType === 'HUMAN_APPROVAL') {
    return type === 'bpmn:UserTask' || type === 'bpmn:ServiceTask'
  }
  // SEND_NOTIFICATION morphs to SendTask (ADR-009); tolerate ServiceTask for legacy.
  if (actionType === 'SEND_NOTIFICATION') {
    return type === 'bpmn:SendTask' || type === 'bpmn:ServiceTask'
  }
  if (actionType === 'CONNECTOR_OPERATION') {
    return type === 'bpmn:ServiceTask'
  }
  if (actionType === 'SET_VARIABLES') {
    return type === 'bpmn:ServiceTask'
  }
  if (actionType === 'MANUAL_STEP') {
    return type === 'bpmn:ManualTask' || type === 'bpmn:UserTask'
  }
  if (actionType === 'CALL_SUBPROCESS') {
    return type === 'bpmn:CallActivity'
  }
  return false
}

function readConditionExpression(element: { type: string; businessObject: Record<string, any> } | null): string {
  return element?.businessObject?.conditionExpression?.body || ''
}

function writeConditionExpression(element: { type: string; businessObject: Record<string, any> } | null, modeler: any, expression: string): void {
  if (!element || !modeler) return
  const moddle = modeler.get('moddle')
  const modeling = modeler.get('modeling')
  if (!expression) {
    modeling.updateProperties(element, { conditionExpression: undefined })
    return
  }
  const formalExpr = moddle.create('bpmn:FormalExpression', { body: expression })
  modeling.updateProperties(element, { conditionExpression: formalExpr })
}
