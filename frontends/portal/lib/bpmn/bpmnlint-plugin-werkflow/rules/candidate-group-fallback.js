'use strict';

/**
 * Rule: candidate-group-fallback (error)
 *
 * A HUMAN_APPROVAL user task must have an assignee OR candidate groups.
 * Mirrors the HUMAN_APPROVAL branch in validateActionBlocks().
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

    const hasAssignee = !!(node.get('assignee') || node.get('candidateGroups'));
    if (!hasAssignee) {
      reporter.report(node.id, 'User task has no assignee or candidate groups set.');
    }
  }

  return { check };
};
