'use strict';

/**
 * Rule: boundary-timer-sla-coverage (warn)
 *
 * Advisory nudge: a HUMAN_APPROVAL user task should have at least one
 * timer boundary event attached to it for SLA coverage. Without it, stalled
 * approvals have no automatic escalation path.
 *
 * This is a recommendation, not a hard requirement — severity is 'warn'.
 *
 * @param {ModdleElement} node
 * @param {Reporter} reporter
 */
module.exports = function() {
  const { is } = require('bpmnlint-utils');

  function check(node, reporter) {
    if (!is(node, 'bpmn:UserTask')) return;

    const actionType = (node.get && node.get('flowable:actionType')) || '';
    if (actionType !== 'HUMAN_APPROVAL') return;

    // Traverse parent process/sub-process flowElements for a timer boundary
    // event whose attachedToRef points back to this task.
    const parent = node.$parent;
    if (!parent) return;

    const flowElements = parent.flowElements || [];
    const hasTimerBoundary = flowElements.some(el => {
      if (!is(el, 'bpmn:BoundaryEvent')) return false;
      if (el.attachedToRef !== node) return false;
      const eventDefs = el.eventDefinitions || [];
      return eventDefs.some(def => is(def, 'bpmn:TimerEventDefinition'));
    });

    if (!hasTimerBoundary) {
      reporter.report(
        node.id,
        'Human approval task has no timer boundary event. ' +
        'Add a timer boundary for SLA escalation coverage.'
      );
    }
  }

  return { check };
};
