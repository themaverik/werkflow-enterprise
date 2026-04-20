package com.werkflow.engine.dto.dmn;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Result of an ad-hoc decision test.
 * Returned by POST /api/v1/dmn/decisions/{key}/test — not persisted to history.
 */
@Value
@Builder
public class DmnTestResultDto {

    /** Echo of the inputs that were supplied */
    Map<String, Object> inputs;

    /**
     * All output maps produced by matching rules.
     * For hitPolicy=FIRST this will have exactly one entry when a rule matched.
     */
    List<Map<String, Object>> resultList;

    /** Total number of rules that matched under the table's hit policy */
    int matchedRuleCount;
}
