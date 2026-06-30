'use strict';

/**
 * Rule: task-type-advisory (error)
 *
 * Surfaces at design time the task-type bans that the engine enforces at
 * deploy time or silently ignores at runtime.  References:
 *
 *   bpmn:ScriptTask          → quarantined (ADR-016)
 *   bpmn:BusinessRuleTask    → dead in Flowable 7.2 (ADR-026); use
 *                              serviceTask flowable:type=dmn
 *   bpmn:ManualTask          → confirmationRequired='true' is a silent no-op
 *                              (ADR-017)
 *   Dead flowable:* attrs    → signalName, correlationKey, webhookConnector,
 *                              correlationExpression have no effect (ADR-009)
 *
 * @param {ModdleElement} node
 * @param {Reporter} reporter
 */

/** Dead extension attributes that have been removed / never wired. */
const DEAD_FLOWABLE_ATTRS = [
  'signalName',
  'correlationKey',
  'webhookConnector',
  'correlationExpression'
];

/**
 * Try to read a potentially-namespaced attribute from a moddle element.
 * Checks both typed getters (attribute defined in loaded moddle descriptor)
 * and the $attrs bag (attribute present in XML but not recognised by any
 * loaded descriptor).
 *
 * @param {ModdleElement} node
 * @param {string} qualifiedName  e.g. 'flowable:signalName'
 * @returns {string|undefined}
 */
function getAttrValue(node, qualifiedName) {
  if (typeof node.get === 'function') {
    try {
      const val = node.get(qualifiedName);
      if (val !== undefined && val !== null) return val;
    } catch (_) {
      /* attribute not defined in loaded moddle descriptor — fall through */
    }
  }
  return node.$attrs && node.$attrs[qualifiedName];
}

module.exports = function() {
  const { is } = require('bpmnlint-utils');

  function check(node, reporter) {
    // 1. Quarantined task type: ScriptTask (ADR-016)
    if (is(node, 'bpmn:ScriptTask')) {
      reporter.report(
        node.id,
        'Script tasks are quarantined (ADR-016). Use a service task with an appropriate action type instead.'
      );
    }

    // 2. Dead task type in Flowable 7.2: BusinessRuleTask (ADR-026)
    if (is(node, 'bpmn:BusinessRuleTask')) {
      reporter.report(
        node.id,
        'Business rule tasks are not supported in Flowable 7.2 (ADR-026). ' +
        'Use a service task with flowable:type=dmn and a decisionTableReferenceKey field instead.'
      );
    }

    // 3. ManualTask with confirmationRequired='true' is a silent no-op (ADR-017)
    if (is(node, 'bpmn:ManualTask')) {
      const confirmationRequired = getAttrValue(node, 'flowable:confirmationRequired');
      if (confirmationRequired === 'true') {
        reporter.report(
          node.id,
          'ManualTask confirmationRequired="true" is a silent no-op (ADR-017). Remove the attribute.'
        );
      }
    }

    // 4. Dead flowable:* extension attributes (ADR-009)
    for (const attr of DEAD_FLOWABLE_ATTRS) {
      if (getAttrValue(node, `flowable:${attr}`)) {
        reporter.report(
          node.id,
          `Element carries dead extension attribute "flowable:${attr}" which has no effect (ADR-009). Remove it.`
        );
      }
    }
  }

  return { check };
};
