/**
 * Generate a unique, NCName-valid BPMN process id (key).
 * Convention: Process_<base36 timestamp><random> — unique even for two diagrams
 * created in the same millisecond. New diagrams must NOT default to a shared
 * hardcoded key (e.g. "process"): that collides across processes and tenants and
 * breaks tenant-scoped start-by-key lookups.
 */
export function generateProcessId(): string {
  const rand = Math.random().toString(36).slice(2, 8)
  return `Process_${Date.now().toString(36)}${rand}`
}

/**
 * Generate a blank BPMN 2.0 XML template.
 * When no processId is given, a unique key is generated (never a hardcoded default).
 */
export function generateBlankBpmn(processId: string = generateProcessId(), processName: string = 'New Process'): string {
  const timestamp = new Date().toISOString()

  return `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
             xmlns:omgdc="http://www.omg.org/spec/DD/20100524/DC"
             xmlns:omgdi="http://www.omg.org/spec/DD/20100524/DI"
             xmlns:flowable="http://flowable.org/bpmn"
             typeLanguage="http://www.w3.org/2001/XMLSchema"
             expressionLanguage="http://www.w3.org/1999/XPath"
             targetNamespace="http://www.werkflow.com/bpmn">

  <process id="${processId}" name="${processName}" isExecutable="true">
    <documentation>Created on ${timestamp}</documentation>

    <startEvent id="startEvent" name="Start">
      <outgoing>flow1</outgoing>
    </startEvent>

    <endEvent id="endEvent" name="End">
      <incoming>flow1</incoming>
    </endEvent>

    <sequenceFlow id="flow1" sourceRef="startEvent" targetRef="endEvent" />
  </process>

  <bpmndi:BPMNDiagram id="BPMNDiagram_${processId}">
    <bpmndi:BPMNPlane id="BPMNPlane_${processId}" bpmnElement="${processId}">
      <bpmndi:BPMNShape id="BPMNShape_startEvent" bpmnElement="startEvent">
        <omgdc:Bounds x="100" y="100" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <omgdc:Bounds x="106" y="143" width="24" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_endEvent" bpmnElement="endEvent">
        <omgdc:Bounds x="300" y="100" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <omgdc:Bounds x="308" y="143" width="20" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="BPMNEdge_flow1" bpmnElement="flow1">
        <omgdi:waypoint x="136" y="118" />
        <omgdi:waypoint x="300" y="118" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>`
}

/**
 * Validate BPMN XML
 */
export function validateBpmnXml(xml: string): { valid: boolean; error?: string } {
  try {
    // Basic validation - check if it's valid XML and contains required BPMN elements
    if (!xml || xml.trim().length === 0) {
      return { valid: false, error: 'BPMN XML is empty' }
    }

    if (!xml.includes('<definitions')) {
      return { valid: false, error: 'Missing BPMN definitions element' }
    }

    if (!xml.includes('<process')) {
      return { valid: false, error: 'Missing BPMN process element' }
    }

    return { valid: true }
  } catch (error) {
    return { valid: false, error: error instanceof Error ? error.message : 'Invalid XML' }
  }
}

/**
 * Extract process ID from BPMN XML
 */
export function extractProcessId(xml: string): string | null {
  try {
    const processMatch = xml.match(/<process[^>]*id="([^"]+)"/)
    return processMatch ? processMatch[1] : null
  } catch {
    return null
  }
}

/**
 * Extract process name from BPMN XML
 */
export function extractProcessName(xml: string): string | null {
  try {
    const nameMatch = xml.match(/<process[^>]*name="([^"]+)"/)
    return nameMatch ? nameMatch[1] : null
  } catch {
    return null
  }
}

/**
 * Download BPMN XML as file
 */
export function downloadBpmn(xml: string, filename: string = 'process.bpmn20.xml'): void {
  const blob = new Blob([xml], { type: 'application/xml' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
