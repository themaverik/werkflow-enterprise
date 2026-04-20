package com.werkflow.admin.dto.serviceregistry;

import com.werkflow.admin.entity.serviceregistry.Environment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceResolverResponse {
    private String serviceName;
    private Environment environment;
    private String resolvedUrl;
    private String basePath;
    private String fullUrl;
}
