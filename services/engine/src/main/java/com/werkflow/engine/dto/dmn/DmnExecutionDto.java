package com.werkflow.engine.dto.dmn;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * A single recorded decision evaluation from Flowable history.
 * Maps to ACT_HI_DEC_INSTANCE + ACT_HI_DEC_IN/OUT tables.
 */
@Value
@Builder
public class DmnExecutionDto {

    /** Flowable historic decision instance ID */
    String id;

    /** Decision table key that was evaluated */
    String decisionKey;

    /** Decision table display name at time of execution */
    String decisionName;

    /** Input variable snapshot passed to the decision */
    Map<String, Object> inputs;

    /** Output variable snapshot produced by the decision */
    Map<String, Object> outputs;

    /** Number of rules that matched */
    int matchedRuleCount;

    /** BPMN process instance that triggered this evaluation (may be null for ad-hoc test calls) */
    String processInstanceId;

    /** When the decision was evaluated */
    OffsetDateTime executedAt;
}
