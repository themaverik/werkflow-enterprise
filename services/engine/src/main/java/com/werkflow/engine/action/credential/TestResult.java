package com.werkflow.engine.action.credential;

/**
 * Outcome of a {@link CredentialType#validate} call.
 *
 * @param success {@code true} if all required fields are present and non-blank
 * @param message {@code "OK"} on success; a human-readable description of the
 *                first validation failure otherwise
 */
public record TestResult(boolean success, String message) {

    /** Returns a successful result with the standard {@code "OK"} message. */
    public static TestResult ok() {
        return new TestResult(true, "OK");
    }

    /**
     * Returns a failure result with the given error message.
     *
     * @param msg human-readable description of the failure (e.g. which field is missing)
     */
    public static TestResult error(String msg) {
        return new TestResult(false, msg);
    }
}
