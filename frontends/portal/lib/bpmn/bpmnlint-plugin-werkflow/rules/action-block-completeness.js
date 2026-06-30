'use strict';

/**
 * Rule: action-block-completeness (error)
 *
 * Ports the non-HUMAN_APPROVAL branches of validateActionBlocks() into a
 * bpmnlint rule so authors see errors in the designer rather than only at
 * deploy-click time.  The deploy gate (validateActionBlocks) is unchanged —
 * this rule is additive defense-in-depth.
 *
 * Checks covered:
 *   SEND_NOTIFICATION  → requires 'recipient' and 'templateKey' flowable:Field
 *   CONNECTOR_OPERATION → requires 'connector' flowable:Field
 *   CALL_SUBPROCESS    → requires calledElement
 *   GROOVY_SCRIPT      → requires flowable:script
 *   ServiceTask[dmn]   → requires 'decisionTableReferenceKey' flowable:Field
 *
 * @param {ModdleElement} node
 * @param {Reporter} reporter
 */

/**
 * Mirror of getFlowableField from BpmnDesigner.tsx.
 * Reads a <flowable:field> string or expression value from a moddle element.
 *
 * @param {ModdleElement} node
 * @param {string} fieldName
 * @returns {string}
 */
function getFlowableField(node, fieldName) {
  const extensionElements = node.extensionElements;
  if (!extensionElements) return '';

  const values = extensionElements.get ? extensionElements.get('values') : (extensionElements.values || []);
  if (!values) return '';

  const field = values.find(v => v.$type === 'flowable:Field' && v.name === fieldName);
  return (field && (field.expression || field.string)) || '';
}

module.exports = function() {
  const { is } = require('bpmnlint-utils');

  function check(node, reporter) {
    const actionType = (node.get && node.get('flowable:actionType')) || '';

    if (actionType === 'SEND_NOTIFICATION') {
      if (!getFlowableField(node, 'recipient')) {
        reporter.report(node.id, 'Notification task is missing a recipient.');
      }
      if (!getFlowableField(node, 'templateKey')) {
        reporter.report(node.id, 'Notification task is missing a template key.');
      }
    }

    if (actionType === 'CONNECTOR_OPERATION') {
      if (!getFlowableField(node, 'connector')) {
        reporter.report(node.id, 'Connector Operation task requires a connector to be selected.');
      }
    }

    if (actionType === 'CALL_SUBPROCESS') {
      if (!node.get('calledElement')) {
        reporter.report(node.id, 'Call Subprocess task is missing a process key (calledElement).');
      }
    }

    if (actionType === 'GROOVY_SCRIPT') {
      if (!node.get('flowable:script')) {
        reporter.report(node.id, 'Groovy Script task has no script content.');
      }
    }

    if (is(node, 'bpmn:ServiceTask')) {
      const flowableType = node.get && node.get('flowable:type');
      if (flowableType === 'dmn') {
        if (!getFlowableField(node, 'decisionTableReferenceKey')) {
          reporter.report(node.id, 'DMN Decision task is missing a decision key (decisionTableReferenceKey).');
        }
      }
    }
  }

  return { check };
};
