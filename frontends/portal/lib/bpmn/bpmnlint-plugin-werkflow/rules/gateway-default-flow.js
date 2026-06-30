'use strict';

/**
 * Rule: gateway-default-flow (error)
 *
 * A diverging exclusive gateway that has at least one conditional outgoing
 * flow MUST also have a default flow set. The Flowable stock validator does
 * not check this, so authors discover the gap at runtime.
 *
 * "Diverging" = more than one outgoing sequence flow.
 * "Conditional" = the flow has a conditionExpression.
 * "Default" = node.default references one of the outgoing flows (BPMN standard attr).
 *
 * @param {ModdleElement} node
 * @param {Reporter} reporter
 */
module.exports = function() {
  const { is } = require('bpmnlint-utils');

  function check(node, reporter) {
    if (!is(node, 'bpmn:ExclusiveGateway')) return;

    const outgoing = node.outgoing || [];
    if (outgoing.length <= 1) return;

    const hasConditionalFlow = outgoing.some(flow => !!flow.conditionExpression);
    if (!hasConditionalFlow) return;

    if (!node.default) {
      reporter.report(
        node.id,
        'Exclusive gateway has conditional flows but no default flow set. ' +
        'A default flow is required to handle unmatched conditions.'
      );
    }
  }

  return { check };
};
