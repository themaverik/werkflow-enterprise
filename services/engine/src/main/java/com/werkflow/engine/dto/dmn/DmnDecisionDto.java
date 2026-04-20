package com.werkflow.engine.dto.dmn;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

/**
 * Summary of a deployed DMN decision table.
 * Returned by list and single-get endpoints.
 */
@Value
@Builder
public class DmnDecisionDto {

    /** Decision table key (stable across versions) */
    String key;

    /** Human-readable display name */
    String name;

    /** Deployed version number — increments on each redeploy */
    int version;

    /** Flowable deployment ID that contains this decision */
    String deploymentId;

    /** Flowable internal ID for this specific decision table version */
    String id;

    /** Tenant the decision belongs to */
    String tenantId;

    /** When this version was deployed */
    OffsetDateTime deployedAt;
}
