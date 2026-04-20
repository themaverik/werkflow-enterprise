package com.werkflow.delegates.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Generic Validation Delegate for validating form data and process variables
 *
 * Configurable via BPMN process variables:
 * - validationRules: Map of field → validation rules (required)
 * - variables: Map of variables to validate (required)
 * - failOnError: Whether to throw exception on validation failure (default: true)
 * - validationResultVariable: Variable to store validation result (default: "validationResult")
 *
 * Supported validation rules:
 * - required: Field must be present and not empty
 * - email: Must be valid email format
 * - minLength:N: Minimum string length
 * - maxLength:N: Maximum string length
 * - min:N: Minimum numeric value
 * - max:N: Maximum numeric value
 * - pattern:REGEX: Must match regex pattern
 * - date:FORMAT: Must be valid date in format (default: yyyy-MM-dd)
 * - in:val1,val2,val3: Must be one of the listed values
 *
 * Example BPMN configuration:
 * <serviceTask id="validateForm" flowable:delegateExpression="${validationDelegate}">
 *   <extensionElements>
 *     <flowable:field name="validationRules">
 *       <flowable:expression>#{
 *         'email': 'required,email',
 *         'age': 'required,min:18,max:100',
 *         'department': 'required,in:HR,IT,Finance',
 *         'startDate': 'required,date:yyyy-MM-dd'
 *       }</flowable:expression>
 *     </flowable:field>
 *     <flowable:field name="variables">
 *       <flowable:expression>#{
 *         'email': email,
 *         'age': age,
 *         'department': department,
 *         'startDate': startDate
 *       }</flowable:expression>
 *     </flowable:field>
 *   </extensionElements>
 * </serviceTask>
 */
@Slf4j
@Component("validationDelegate")
@RequiredArgsConstructor
public class ValidationDelegate implements JavaDelegate {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private final ObjectMapper objectMapper;

    @Override
    public void execute(DelegateExecution execution) {
        log.info("Executing ValidationDelegate for process: {}", execution.getProcessInstanceId());

        // Get configuration
        Map<String, String> validationRules = getRequiredVariable(execution, "validationRules");
        Map<String, Object> variables = getRequiredVariable(execution, "variables");
        Boolean failOnError = getVariable(execution, "failOnError", true);
        String resultVariable = getVariable(execution, "validationResultVariable", "validationResult");

        log.debug("Validating {} fields with {} rules", variables.size(), validationRules.size());

        // Perform validation
        Map<String, List<String>> errors = new HashMap<>();
        boolean isValid = true;

        for (Map.Entry<String, String> entry : validationRules.entrySet()) {
            String fieldName = entry.getKey();
            String rules = entry.getValue();
            Object value = variables.get(fieldName);

            List<String> fieldErrors = validateField(fieldName, value, rules);
            if (!fieldErrors.isEmpty()) {
                errors.put(fieldName, fieldErrors);
                isValid = false;
            }
        }

        // Store validation result
        Map<String, Object> result = new HashMap<>();
        result.put("isValid", isValid);
        result.put("errors", errors);
        result.put("validatedFields", validationRules.keySet().size());

        execution.setVariable(resultVariable, result);

        log.info("Validation completed. Valid: {}, Errors: {}", isValid, errors.size());

        if (!isValid && failOnError) {
            String errorMessage = buildErrorMessage(errors);
            throw new RuntimeException("Validation failed: " + errorMessage);
        }
    }

    private List<String> validateField(String fieldName, Object value, String rules) {
        List<String> errors = new ArrayList<>();
        String[] ruleArray = rules.split(",");

        for (String rule : ruleArray) {
            rule = rule.trim();

            if (rule.equals("required")) {
                if (value == null || value.toString().trim().isEmpty()) {
                    errors.add(fieldName + " is required");
                }
            } else if (rule.equals("email")) {
                if (value != null && !EMAIL_PATTERN.matcher(value.toString()).matches()) {
                    errors.add(fieldName + " must be a valid email");
                }
            } else if (rule.startsWith("minLength:")) {
                int minLength = Integer.parseInt(rule.substring(10));
                if (value != null && value.toString().length() < minLength) {
                    errors.add(fieldName + " must be at least " + minLength + " characters");
                }
            } else if (rule.startsWith("maxLength:")) {
                int maxLength = Integer.parseInt(rule.substring(10));
                if (value != null && value.toString().length() > maxLength) {
                    errors.add(fieldName + " must be at most " + maxLength + " characters");
                }
            } else if (rule.startsWith("min:")) {
                BigDecimal min = new BigDecimal(rule.substring(4));
                if (value != null) {
                    BigDecimal numValue = new BigDecimal(value.toString());
                    if (numValue.compareTo(min) < 0) {
                        errors.add(fieldName + " must be at least " + min);
                    }
                }
            } else if (rule.startsWith("max:")) {
                BigDecimal max = new BigDecimal(rule.substring(4));
                if (value != null) {
                    BigDecimal numValue = new BigDecimal(value.toString());
                    if (numValue.compareTo(max) > 0) {
                        errors.add(fieldName + " must be at most " + max);
                    }
                }
            } else if (rule.startsWith("pattern:")) {
                String pattern = rule.substring(8);
                if (value != null && !Pattern.matches(pattern, value.toString())) {
                    errors.add(fieldName + " does not match required pattern");
                }
            } else if (rule.startsWith("date:")) {
                String format = rule.substring(5);
                if (value != null) {
                    try {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
                        LocalDate.parse(value.toString(), formatter);
                    } catch (DateTimeParseException e) {
                        errors.add(fieldName + " must be a valid date in format " + format);
                    }
                }
            } else if (rule.startsWith("in:")) {
                String allowedValues = rule.substring(3);
                List<String> allowed = Arrays.asList(allowedValues.split("\\|"));
                if (value != null && !allowed.contains(value.toString())) {
                    errors.add(fieldName + " must be one of: " + allowedValues.replace("|", ", "));
                }
            }
        }

        return errors;
    }

    private String buildErrorMessage(Map<String, List<String>> errors) {
        StringBuilder message = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : errors.entrySet()) {
            for (String error : entry.getValue()) {
                message.append(error).append("; ");
            }
        }
        return message.toString();
    }

    @SuppressWarnings("unchecked")
    private <T> T getRequiredVariable(DelegateExecution execution, String variableName) {
        Object value = execution.getVariable(variableName);
        if (value == null) {
            throw new IllegalArgumentException("Required variable '" + variableName + "' is not set");
        }
        return (T) value;
    }

    @SuppressWarnings("unchecked")
    private <T> T getVariable(DelegateExecution execution, String variableName, T defaultValue) {
        Object value = execution.getVariable(variableName);
        return value != null ? (T) value : defaultValue;
    }
}
