package com.werkflow.admin.dto.connector;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class ConnectorTestResponse {
    private int statusCode;
    private Map<String, String> headers;
    private String body;
    private boolean truncated;
    private long durationMs;
}
