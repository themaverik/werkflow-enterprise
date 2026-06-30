'use strict';

/**
 * bpmnlint-plugin-werkflow
 *
 * Werkflow-specific advisory rules surfaced in the BPMN designer.
 * These rules mirror / extend the deploy-time gate (validateActionBlocks)
 * to give authors early feedback without touching that gate.
 *
 * Resolution contract (bpmnlint >=11):
 *   - configs.recommended.rules  → short rule names, severities
 *   - rules[name]                → relative path strings consumed by
 *                                  bpmnlint-pack-config / rollup
 */
module.exports = {
  configs: {
    recommended: {
      rules: {
        'candidate-group-fallback':       'error',
        'gateway-default-flow':           'error',
        'boundary-timer-sla-coverage':    'warn',
        'action-block-completeness':      'error',
        'task-type-advisory':             'error'
      }
    }
  },

  rules: {
    'candidate-group-fallback':       './rules/candidate-group-fallback',
    'gateway-default-flow':           './rules/gateway-default-flow',
    'boundary-timer-sla-coverage':    './rules/boundary-timer-sla-coverage',
    'action-block-completeness':      './rules/action-block-completeness',
    'task-type-advisory':             './rules/task-type-advisory'
  }
};
