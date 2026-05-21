package com.werkflow.engine.action.credential;

/**
 * Describes a single field within a {@link CredentialType} schema.
 *
 * <p>Instances are used by the admin UI (Phase B.3) to render typed form inputs,
 * and by {@link CredentialType#validate} implementations to check required presence.
 *
 * @param name         machine-readable key (e.g. {@code "password"})
 * @param displayName  label shown in the UI (e.g. {@code "Password"})
 * @param type         input type hint for the UI and validation layer
 * @param required     whether this field must have a non-blank value
 * @param defaultValue optional default value; {@code null} means no default
 */
public record CredentialField(
    String name,
    String displayName,
    FieldType type,
    boolean required,
    Object defaultValue
) {

    /**
     * Hints how the UI and validation layer should treat this field's value.
     */
    public enum FieldType {
        /** Plain text — rendered as a text input. */
        STRING,
        /** Sensitive text — rendered masked; never logged. */
        SECRET,
        /** Integer — rendered as a number input. */
        INT,
        /** Boolean — rendered as a toggle/checkbox. */
        BOOL
    }
}
