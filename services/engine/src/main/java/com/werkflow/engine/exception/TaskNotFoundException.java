package com.werkflow.engine.exception;

/**
 * Exception thrown when a requested task cannot be found
 */
public class TaskNotFoundException extends RuntimeException {

    /**
     * Create exception with task ID
     * @param taskId ID of the task that was not found
     */
    public TaskNotFoundException(String taskId) {
        super("Task not found with ID: " + taskId);
    }

    /**
     * Create exception with custom message
     * @param message Custom error message
     * @param cause Root cause exception
     */
    public TaskNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
