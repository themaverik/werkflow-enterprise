package com.werkflow.admin.designtime.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.werkflow.admin.designtime.connector.dto.OpenApiImportRequest;
import com.werkflow.admin.designtime.connector.entity.ConnectorDefinitionV2;
import com.werkflow.common.security.SsrfGuard;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Imports an OpenAPI 3.1 (or 3.0) document and generates a ConnectorDefinition
 * with one operation per path × HTTP method.
 *
 * <p>The generated definition uses REST transport. The caller may supply an optional
 * {@code connectorKey}; if omitted, the key is derived from the OpenAPI {@code info.title}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenApiImportService {

    private final ConnectorCatalogService catalogService;
    private final SsrfGuard ssrfGuard;
    private final ObjectMapper objectMapper;

    /**
     * Parses the OpenAPI document, generates a ConnectorDefinition JSON, validates it,
     * and registers it for the given tenant.
     *
     * @param tenantId resolved tenant identifier
     * @param request  import request containing raw content or URL
     * @return the persisted {@link ConnectorDefinitionV2} entity
     */
    public ConnectorDefinitionV2 importOpenApi(String tenantId, OpenApiImportRequest request) {
        OpenAPI openApi = parse(request);
        String key = resolveKey(openApi, request);
        String displayName = resolveDisplayName(openApi, request, key);
        String baseUrl = resolveBaseUrl(openApi);

        ObjectNode definition = buildDefinition(key, displayName, baseUrl, openApi, request);
        String definitionJson;
        try {
            definitionJson = objectMapper.writeValueAsString(definition);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to serialise generated definition: " + e.getMessage());
        }

        log.info("openapi.import tenantId={} key={} operationCount={}",
                tenantId, key, definition.path("spec").path("operations").size());
        return catalogService.register(tenantId, definitionJson);
    }

    // -------------------------------------------------------------------------
    // OpenAPI parsing
    // -------------------------------------------------------------------------

    private OpenAPI parse(OpenApiImportRequest request) {
        OpenAPIV3Parser parser = new OpenAPIV3Parser();
        ParseOptions opts = new ParseOptions();
        // H-6: disable $ref resolution to prevent SSRF via embedded http:// refs
        opts.setResolve(false);
        opts.setResolveFully(false);

        SwaggerParseResult result;
        if (request.content() != null && !request.content().isBlank()) {
            result = parser.readContents(request.content(), null, opts);
        } else if (request.url() != null && !request.url().isBlank()) {
            ssrfGuard.validate(request.url());
            result = parser.readLocation(request.url(), null, opts);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Either 'content' or 'url' must be provided");
        }

        if (result.getOpenAPI() == null) {
            String messages = result.getMessages() != null
                    ? String.join("; ", result.getMessages()) : "unknown parse error";
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Failed to parse OpenAPI document: " + messages);
        }
        return result.getOpenAPI();
    }

    // -------------------------------------------------------------------------
    // Definition builder
    // -------------------------------------------------------------------------

    private ObjectNode buildDefinition(
            String key, String displayName, String baseUrl,
            OpenAPI openApi, OpenApiImportRequest request) {

        ObjectNode root = objectMapper.createObjectNode();
        root.put("apiVersion", "werkflow.io/connector/v1");
        root.put("kind", "ConnectorDefinition");

        // metadata
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("key", key);
        meta.put("displayName", displayName);
        if (openApi.getInfo() != null && openApi.getInfo().getDescription() != null) {
            meta.put("description", openApi.getInfo().getDescription());
        }
        meta.put("version", openApi.getInfo() != null && openApi.getInfo().getVersion() != null
                ? openApi.getInfo().getVersion() : "1.0.0");
        meta.put("category", "data-source");
        root.set("metadata", meta);

        // spec
        ObjectNode spec = objectMapper.createObjectNode();

        // transport
        ObjectNode transport = objectMapper.createObjectNode();
        transport.put("type", "rest");
        ObjectNode transportConfig = objectMapper.createObjectNode();
        transportConfig.put("baseUrl", baseUrl);
        transport.set("config", transportConfig);
        spec.set("transport", transport);

        // auth (placeholder — none by default for imported connectors)
        ObjectNode auth = objectMapper.createObjectNode();
        ArrayNode profiles = objectMapper.createArrayNode();
        ObjectNode defaultProfile = objectMapper.createObjectNode();
        defaultProfile.put("id", "default");
        defaultProfile.put("type", "none");
        profiles.add(defaultProfile);
        auth.set("profiles", profiles);
        spec.set("auth", auth);

        // operations
        ArrayNode operations = buildOperations(openApi);
        spec.set("operations", operations);

        root.set("spec", spec);
        return root;
    }

    private ArrayNode buildOperations(OpenAPI openApi) {
        ArrayNode operations = objectMapper.createArrayNode();
        if (openApi.getPaths() == null) return operations;

        for (Map.Entry<String, PathItem> pathEntry : openApi.getPaths().entrySet()) {
            String path = pathEntry.getKey();
            PathItem pathItem = pathEntry.getValue();

            Map<PathItem.HttpMethod, Operation> ops = pathItem.readOperationsMap();
            if (ops == null) continue;

            for (Map.Entry<PathItem.HttpMethod, Operation> opEntry : ops.entrySet()) {
                String method = opEntry.getKey().name();
                Operation openApiOp = opEntry.getValue();
                operations.add(buildOperation(path, method, openApiOp));
            }
        }
        return operations;
    }

    @SuppressWarnings("unchecked")
    private ObjectNode buildOperation(String path, String method, Operation openApiOp) {
        ObjectNode op = objectMapper.createObjectNode();

        // id: camelCase from method + path segments
        op.put("id", deriveOperationId(method, path, openApiOp));
        op.put("displayName", openApiOp.getSummary() != null
                ? openApiOp.getSummary() : method + " " + path);

        if (openApiOp.getDescription() != null) {
            op.put("description", openApiOp.getDescription());
        }
        op.put("category", inferCategory(method));

        // input schema from parameters
        op.set("input", buildInputSchema(openApiOp));

        // output schema (basic — empty object; would need full media-type schema extraction)
        op.set("output", objectMapper.createObjectNode().put("type", "object"));

        // transportSpecific
        ObjectNode ts = objectMapper.createObjectNode();
        ts.put("method", method);
        ts.put("path", path);
        op.set("transportSpecific", ts);

        return op;
    }

    private ObjectNode buildInputSchema(Operation openApiOp) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        if (openApiOp.getParameters() == null || openApiOp.getParameters().isEmpty()) {
            return schema;
        }

        ObjectNode properties = objectMapper.createObjectNode();
        List<String> required = new ArrayList<>();

        for (io.swagger.v3.oas.models.parameters.Parameter param : openApiOp.getParameters()) {
            if (param.getName() == null) continue;
            ObjectNode propSchema = objectMapper.createObjectNode();
            if (param.getSchema() != null) {
                Schema<?> s = param.getSchema();
                propSchema.put("type", s.getType() != null ? s.getType() : "string");
                if (s.getDescription() != null) propSchema.put("description", s.getDescription());
            } else {
                propSchema.put("type", "string");
            }
            properties.set(param.getName(), propSchema);
            if (Boolean.TRUE.equals(param.getRequired())) {
                required.add(param.getName());
            }
        }
        schema.set("properties", properties);
        if (!required.isEmpty()) {
            ArrayNode reqArray = objectMapper.createArrayNode();
            required.forEach(reqArray::add);
            schema.set("required", reqArray);
        }
        return schema;
    }

    // -------------------------------------------------------------------------
    // Resolution helpers
    // -------------------------------------------------------------------------

    private String resolveKey(OpenAPI openApi, OpenApiImportRequest request) {
        if (request.connectorKey() != null && !request.connectorKey().isBlank()) {
            return request.connectorKey();
        }
        if (openApi.getInfo() != null && openApi.getInfo().getTitle() != null) {
            return slugify(openApi.getInfo().getTitle());
        }
        return "imported-connector";
    }

    private String resolveDisplayName(OpenAPI openApi, OpenApiImportRequest request, String key) {
        if (request.displayName() != null && !request.displayName().isBlank()) {
            return request.displayName();
        }
        if (openApi.getInfo() != null && openApi.getInfo().getTitle() != null) {
            return openApi.getInfo().getTitle();
        }
        return key;
    }

    private String resolveBaseUrl(OpenAPI openApi) {
        if (openApi.getServers() != null && !openApi.getServers().isEmpty()) {
            String url = openApi.getServers().get(0).getUrl();
            if (url != null && !url.isBlank() && !"/".equals(url)) return url;
        }
        return "https://example.com";
    }

    private static String deriveOperationId(String method, String path, Operation op) {
        if (op.getOperationId() != null && !op.getOperationId().isBlank()) {
            // sanitise: replace non-alphanumeric runs with underscore then camelCase
            String sanitised = op.getOperationId().replaceAll("[^a-zA-Z0-9]+", "_");
            StringBuilder camel = new StringBuilder();
            boolean nextUpper = false;
            for (char c : sanitised.toCharArray()) {
                if (c == '_') {
                    nextUpper = true;
                } else {
                    camel.append(nextUpper ? Character.toUpperCase(c) : c);
                    nextUpper = false;
                }
            }
            String result = camel.toString();
            if (result.isEmpty() || !Character.isLetter(result.charAt(0))) result = "op" + result;
            return result;
        }
        // derive from method + path
        String[] segments = path.replaceAll("\\{[^}]+}", "").split("/");
        StringBuilder sb = new StringBuilder(method.toLowerCase(Locale.ROOT));
        for (String seg : segments) {
            if (!seg.isBlank()) {
                sb.append(Character.toUpperCase(seg.charAt(0)));
                sb.append(seg.substring(1).replaceAll("[^a-zA-Z0-9]", ""));
            }
        }
        return sb.toString();
    }

    private static String inferCategory(String method) {
        return switch (method.toUpperCase(Locale.ROOT)) {
            case "GET"    -> "read";
            case "POST"   -> "write";
            case "PUT", "PATCH" -> "write";
            case "DELETE" -> "action";
            default       -> "action";
        };
    }

    private static String slugify(String title) {
        return title.toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("^-|-$", "")
                    .substring(0, Math.min(64, title.length()));
    }
}
