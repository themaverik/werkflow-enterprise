package com.werkflow.engine.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schema-level assertions for shipped form-js form JSON files (ADR-028 Phase 2).
 *
 * <p>Loads a form JSON from the classpath (main or test resources) and extracts the
 * {@code key} fields from the top-level {@code components} array. Components without a
 * {@code key} (e.g. display-only {@code type:"text"} blocks) are silently skipped.
 *
 * <p>The contract enforced here is that every process variable written by a form submission
 * is backed by a declared component key — preventing silent field-name drift between the
 * form schema and the BPMN variable expectations.
 */
public final class FormTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FormTestSupport() {}

    /**
     * Returns the {@code key} values from all keyed components in the given form JSON resource.
     * Components that lack a {@code key} field (e.g. static text, headers) are excluded.
     */
    public static List<String> fieldKeys(String classpathResource) throws IOException {
        JsonNode root = load(classpathResource);
        JsonNode components = root.path("components");
        List<String> keys = new ArrayList<>();
        for (JsonNode component : components) {
            JsonNode key = component.get("key");
            if (key != null && !key.isNull()) {
                keys.add(key.asText());
            }
        }
        return List.copyOf(keys);
    }

    /**
     * Asserts that the form at {@code classpathResource} declares all of the given
     * {@code expectedKeys} as component keys. Fails with a descriptive message on any gap.
     */
    public static void assertHasFields(String classpathResource, String... expectedKeys)
            throws IOException {
        List<String> actual = fieldKeys(classpathResource);
        assertThat(actual)
            .as("Form '%s' field keys", classpathResource)
            .contains(expectedKeys);
    }

    private static JsonNode load(String classpathResource) throws IOException {
        InputStream is = FormTestSupport.class.getClassLoader()
            .getResourceAsStream(classpathResource);
        if (is == null) {
            throw new IllegalArgumentException("Classpath resource not found: " + classpathResource);
        }
        return MAPPER.readTree(is);
    }
}
