/**
 * BPMN Layout Generator
 * Generates BPMNShape and BPMNEdge elements for capex-approval-process
 *
 * Usage: node generate-bpmn-layout.js
 * Output: Complete BPMNDiagram section to paste into XML
 */

const layoutConfig = {
  startX: 100,
  startY: 200,
  horizontalSpacing: 180,
  verticalSpacing: 120,
  taskWidth: 100,
  taskHeight: 80,
  gatewaySize: 40,
  eventSize: 30
};

// Define process flow with visual layout
const processLayout = {
  // Main flow (top path)
  mainPath: [
    { id: 'startEvent', type: 'startEvent', x: 100, y: 200 },
    { id: 'createCapExRequest', type: 'serviceTask', x: 180, y: 160 },
    { id: 'checkBudget', type: 'serviceTask', x: 330, y: 160 },
    { id: 'budgetGateway', type: 'gateway', x: 480, y: 180 },
    { id: 'managerApproval', type: 'userTask', x: 570, y: 160 },
    { id: 'amountGateway', type: 'gateway', x: 720, y: 180 },
  ],

  // VP approval path
  vpPath: [
    { id: 'vpApproval', type: 'userTask', x: 810, y: 80 },
    { id: 'cfoGateway', type: 'gateway', x: 960, y: 100 },
  ],

  // CFO approval path
  cfoPath: [
    { id: 'cfoApproval', type: 'userTask', x: 1050, y: 20 },
  ],

  // Merge and decision
  mergeDecision: [
    { id: 'mergeGateway', type: 'gateway', x: 870, y: 180 },
    { id: 'decisionGateway', type: 'gateway', x: 1020, y: 180 },
  ],

  // Approval path
  approvalPath: [
    { id: 'updateApproved', type: 'serviceTask', x: 1110, y: 140 },
    { id: 'reserveBudget', type: 'serviceTask', x: 1260, y: 140 },
    { id: 'sendApprovalNotification', type: 'serviceTask', x: 1410, y: 140 },
    { id: 'endEventApproved', type: 'endEvent', x: 1560, y: 165 },
  ],

  // Rejection path
  rejectionPath: [
    { id: 'updateRejected', type: 'serviceTask', x: 1110, y: 240 },
    { id: 'sendRejectionNotification', type: 'serviceTask', x: 1260, y: 240 },
    { id: 'endEventRejected', type: 'endEvent', x: 1410, y: 265 },
  ],

  // No budget path
  noBudgetPath: [
    { id: 'endEventNoBudget', type: 'endEvent', x: 480, y: 280 },
  ]
};

// Sequence flows with waypoints
const flows = [
  { id: 'flow1', source: 'startEvent', target: 'createCapExRequest', waypoints: [[130, 215], [180, 200]] },
  { id: 'flow2', source: 'createCapExRequest', target: 'checkBudget', waypoints: [[280, 200], [330, 200]] },
  { id: 'flow3', source: 'checkBudget', target: 'budgetGateway', waypoints: [[430, 200], [480, 200]] },
  { id: 'budgetYes', source: 'budgetGateway', target: 'managerApproval', waypoints: [[520, 200], [570, 200]] },
  { id: 'budgetNo', source: 'budgetGateway', target: 'endEventNoBudget', waypoints: [[500, 220], [500, 280]] },
  { id: 'flow4', source: 'managerApproval', target: 'amountGateway', waypoints: [[670, 200], [720, 200]] },
  { id: 'amountLow', source: 'amountGateway', target: 'mergeGateway', waypoints: [[740, 200], [870, 200]] },
  { id: 'amountHigh', source: 'amountGateway', target: 'vpApproval', waypoints: [[740, 180], [740, 120], [810, 120]] },
  { id: 'flow5', source: 'vpApproval', target: 'cfoGateway', waypoints: [[910, 120], [960, 120]] },
  { id: 'cfoNotNeeded', source: 'cfoGateway', target: 'mergeGateway', waypoints: [[980, 140], [980, 200], [890, 200]] },
  { id: 'cfoNeeded', source: 'cfoGateway', target: 'cfoApproval', waypoints: [[980, 100], [980, 60], [1050, 60]] },
  { id: 'flow6', source: 'cfoApproval', target: 'mergeGateway', waypoints: [[1150, 60], [1180, 60], [1180, 200], [890, 200]] },
  { id: 'flow7', source: 'mergeGateway', target: 'decisionGateway', waypoints: [[890, 200], [1020, 200]] },
  { id: 'approved', source: 'decisionGateway', target: 'updateApproved', waypoints: [[1040, 180], [1110, 180]] },
  { id: 'rejected', source: 'decisionGateway', target: 'updateRejected', waypoints: [[1040, 220], [1110, 280]] },
  { id: 'flow8', source: 'updateApproved', target: 'reserveBudget', waypoints: [[1210, 180], [1260, 180]] },
  { id: 'flow9', source: 'reserveBudget', target: 'sendApprovalNotification', waypoints: [[1360, 180], [1410, 180]] },
  { id: 'flow10', source: 'sendApprovalNotification', target: 'endEventApproved', waypoints: [[1510, 180], [1560, 180]] },
  { id: 'flow11', source: 'updateRejected', target: 'sendRejectionNotification', waypoints: [[1210, 280], [1260, 280]] },
  { id: 'flow12', source: 'sendRejectionNotification', target: 'endEventRejected', waypoints: [[1360, 280], [1410, 295]] },
];

function generateBPMNShape(element) {
  const { id, type, x, y } = element;
  let width, height;

  switch(type) {
    case 'startEvent':
    case 'endEvent':
      width = height = layoutConfig.eventSize;
      break;
    case 'gateway':
      width = height = layoutConfig.gatewaySize;
      break;
    case 'userTask':
    case 'serviceTask':
      width = layoutConfig.taskWidth;
      height = layoutConfig.taskHeight;
      break;
    default:
      width = layoutConfig.taskWidth;
      height = layoutConfig.taskHeight;
  }

  return `      <bpmndi:BPMNShape bpmnElement="${id}" id="BPMNShape_${id}">
        <omgdc:Bounds height="${height}.0" width="${width}.0" x="${x}.0" y="${y}.0"/>
      </bpmndi:BPMNShape>`;
}

function generateBPMNEdge(flow) {
  const waypoints = flow.waypoints.map(wp =>
    `        <omgdi:waypoint x="${wp[0]}.0" y="${wp[1]}.0"/>`
  ).join('\n');

  return `      <bpmndi:BPMNEdge bpmnElement="${flow.id}" id="BPMNEdge_${flow.id}">
${waypoints}
      </bpmndi:BPMNEdge>`;
}

// Generate complete diagram
function generateCompleteDiagram() {
  const shapes = [];
  const edges = [];

  // Collect all elements
  Object.values(processLayout).forEach(path => {
    path.forEach(element => {
      shapes.push(generateBPMNShape(element));
    });
  });

  // Generate edges
  flows.forEach(flow => {
    edges.push(generateBPMNEdge(flow));
  });

  return `  <!-- BPMN Diagram (Visual Layout) -->
  <bpmndi:BPMNDiagram id="BPMNDiagram_capex-approval-process">
    <bpmndi:BPMNPlane bpmnElement="capex-approval-process" id="BPMNPlane_capex-approval-process">
${shapes.join('\n')}
${edges.join('\n')}
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>`;
}

console.log(generateCompleteDiagram());
