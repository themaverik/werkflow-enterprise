package com.werkflow.engine.action;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.Option;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class ResponseMasker {

    private static final Set<String> DEFAULT_MASK_KEYS = Set.of(
        "password", "passwd", "secret", "token", "api_key", "apikey", "authorization",
        "access_token", "refresh_token", "id_token", "client_secret", "bearer",
        "x-api-key", "session_id", "sessionToken", "sessionid", "jwt", "private_key",
        "credential", "credentials", "auth"
    );

    private final List<String> configuredMaskFields;

    public ResponseMasker(
            @Value("${werkflow.audit.mask-fields:}") String maskFieldsCsv) {
        this.configuredMaskFields = maskFieldsCsv == null || maskFieldsCsv.isBlank()
            ? List.of()
            : List.of(maskFieldsCsv.split(","));
    }

    public String mask(String jsonBody, List<String> designerMaskFields) {
        if (jsonBody == null) return null;

        Configuration conf = Configuration.builder()
            .options(Option.SUPPRESS_EXCEPTIONS)
            .build();

        DocumentContext doc;
        try {
            doc = JsonPath.using(conf).parse(jsonBody);
            // Verify root is a JSON object or array, not a plain string/primitive
            Object root = doc.read("$");
            if (!(root instanceof Map) && !(root instanceof List)) {
                log.debug("Response body is not a JSON object or array — skipping masking");
                return jsonBody;
            }
        } catch (Throwable e) {
            log.debug("Response body is not JSON — skipping masking");
            return jsonBody;
        }

        // 1. Apply default key-name masks recursively
        for (String key : DEFAULT_MASK_KEYS) {
            try {
                doc.set("$.." + key, null);
            } catch (JsonPathException ex) {
                log.debug("Could not mask key '{}': {}", key, ex.getMessage());
            }
        }

        // 2. Apply configured mask fields (from application.yml)
        for (String path : configuredMaskFields) {
            applyPath(doc, path.trim());
        }

        // 3. Apply designer-supplied mask fields
        for (String path : designerMaskFields) {
            applyPath(doc, path.trim());
        }

        return doc.jsonString();
    }

    private void applyPath(DocumentContext doc, String path) {
        if (path.isEmpty()) return;
        try {
            doc.set(path, null);
        } catch (JsonPathException e) {
            log.debug("Could not apply mask path '{}': {}", path, e.getMessage());
        }
    }
}
