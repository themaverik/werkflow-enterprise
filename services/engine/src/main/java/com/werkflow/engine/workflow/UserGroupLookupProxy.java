package com.werkflow.engine.workflow;

import com.werkflow.engine.dto.JwtUserContext;

import java.util.List;

/**
 * SPI for resolving Flowable candidateGroup identifiers for an authenticated user.
 * Decouples callers from the concrete FlowableGroupResolver implementation.
 */
public interface UserGroupLookupProxy {

    List<String> resolveGroups(JwtUserContext userContext);
}
