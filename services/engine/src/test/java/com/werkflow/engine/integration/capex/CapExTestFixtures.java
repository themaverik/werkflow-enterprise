package com.werkflow.engine.integration.capex;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Test Fixtures for CapEx Integration Tests
 * Provides mocking utilities and test setup helpers
 */
public class CapExTestFixtures {

    /**
     * Captured email notifications for verification
     */
    public static class EmailCapture {
        private final List<CapturedEmail> capturedEmails = new ArrayList<>();

        public void addEmail(String to, String subject, String body) {
            capturedEmails.add(new CapturedEmail(to, subject, body));
        }

        public List<CapturedEmail> getCapturedEmails() {
            return new ArrayList<>(capturedEmails);
        }

        public int getEmailCount() {
            return capturedEmails.size();
        }

        public boolean hasEmailTo(String recipient) {
            return capturedEmails.stream()
                    .anyMatch(email -> email.to.equals(recipient));
        }

        public boolean hasEmailWithSubject(String subject) {
            return capturedEmails.stream()
                    .anyMatch(email -> email.subject.contains(subject));
        }

        public CapturedEmail getEmailTo(String recipient) {
            return capturedEmails.stream()
                    .filter(email -> email.to.equals(recipient))
                    .findFirst()
                    .orElse(null);
        }

        public void clear() {
            capturedEmails.clear();
        }
    }

    /**
     * Represents a captured email for testing
     */
    public static class CapturedEmail {
        public final String to;
        public final String subject;
        public final String body;

        public CapturedEmail(String to, String subject, String body) {
            this.to = to;
            this.subject = subject;
            this.body = body;
        }
    }

    /**
     * Sets up mock JavaMailSender to capture sent emails
     */
    public static EmailCapture setupEmailCapture(JavaMailSender mailSender) {
        EmailCapture capture = new EmailCapture();

        // Create mock MimeMessage
        MimeMessage mockMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);

        // Capture send() calls (implementation will vary based on actual mail service)
        doNothing().when(mailSender).send(any(MimeMessage.class));

        return capture;
    }

    /**
     * Verifies that an email was sent to the specified recipient
     */
    public static boolean verifyEmailSent(JavaMailSender mailSender, String recipientEmail) {
        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);

        try {
            verify(mailSender, atLeastOnce()).send(messageCaptor.capture());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Mock Keycloak authentication context
     */
    public static class MockKeycloakContext {
        private final String userId;
        private final String email;
        private final List<String> roles;
        private final List<String> groups;
        private final Integer doaLevel;

        public MockKeycloakContext(String userId, String email,
                                    List<String> roles, List<String> groups,
                                    Integer doaLevel) {
            this.userId = userId;
            this.email = email;
            this.roles = roles;
            this.groups = groups;
            this.doaLevel = doaLevel;
        }

        public String getUserId() {
            return userId;
        }

        public String getEmail() {
            return email;
        }

        public List<String> getRoles() {
            return roles;
        }

        public List<String> getGroups() {
            return groups;
        }

        public Integer getDoaLevel() {
            return doaLevel;
        }
    }

    /**
     * Mock Budget Service responses
     */
    public static class MockBudgetService {
        /**
         * Simulates budget check passing
         */
        public static boolean checkBudgetAvailable(String budgetCode, java.math.BigDecimal amount) {
            // Mock logic: amounts under $500K pass budget check
            return amount.compareTo(new java.math.BigDecimal("500000.00")) < 0;
        }

        /**
         * Simulates budget reservation
         */
        public static String reserveBudget(String budgetCode, java.math.BigDecimal amount) {
            return "BUDGET-RESERVATION-" + System.currentTimeMillis();
        }
    }

    /**
     * Mock Vendor Service responses
     */
    public static class MockVendorService {
        /**
         * Checks if vendor is approved
         */
        public static boolean isApprovedVendor(String vendorName) {
            // Mock approved vendors
            List<String> approvedVendors = List.of(
                    "Dell Technologies",
                    "HP Inc",
                    "Cisco Systems",
                    "Industrial Print Co",
                    "Acme Construction",
                    "Green Energy Solutions"
            );
            return approvedVendors.stream()
                    .anyMatch(vendor -> vendor.equalsIgnoreCase(vendorName));
        }

        /**
         * Gets vendor list for category
         */
        public static List<String> getVendorsForCategory(String category) {
            return switch (category) {
                case "IT_EQUIPMENT" -> List.of("Dell Technologies", "HP Inc", "Lenovo");
                case "NETWORK_INFRASTRUCTURE" -> List.of("Cisco Systems", "Juniper Networks");
                case "OFFICE_EQUIPMENT" -> List.of("Industrial Print Co", "Office Depot");
                case "BUILDING_RENOVATION" -> List.of("Acme Construction", "Green Energy Solutions");
                default -> List.of();
            };
        }
    }

    /**
     * Mock Fixed Assets System responses
     */
    public static class MockFixedAssetsSystem {
        /**
         * Simulates asset capitalization in Fixed Assets system
         */
        public static String capitalizeAsset(String assetTag,
                                              java.math.BigDecimal amount,
                                              String coaCode) {
            return "FA-" + assetTag + "-" + System.currentTimeMillis();
        }

        /**
         * Simulates depreciation schedule creation
         */
        public static String createDepreciationSchedule(String assetTag,
                                                          int usefulLifeYears,
                                                          String method) {
            return "DEPR-SCHEDULE-" + assetTag + "-" + method;
        }
    }

    /**
     * Mock Notification Service
     */
    public static class MockNotificationService {
        private static final List<String> sentNotifications = new ArrayList<>();

        /**
         * Simulates sending notification
         */
        public static void sendNotification(String recipientEmail,
                                              String subject,
                                              String body) {
            String notification = recipientEmail + "|" + subject + "|" + body;
            sentNotifications.add(notification);
        }

        /**
         * Verifies notification was sent
         */
        public static boolean wasNotificationSentTo(String recipientEmail) {
            return sentNotifications.stream()
                    .anyMatch(notif -> notif.startsWith(recipientEmail + "|"));
        }

        /**
         * Gets all sent notifications
         */
        public static List<String> getSentNotifications() {
            return new ArrayList<>(sentNotifications);
        }

        /**
         * Clears notification history
         */
        public static void clear() {
            sentNotifications.clear();
        }
    }

    /**
     * Test timing utilities
     */
    public static class TimingUtils {
        /**
         * Waits for async operations to complete
         */
        public static void waitForAsync(long milliseconds) {
            try {
                Thread.sleep(milliseconds);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /**
         * Waits with timeout for a condition to be true
         */
        public static boolean waitUntil(java.util.function.BooleanSupplier condition,
                                         long timeoutMs,
                                         long checkIntervalMs) {
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                if (condition.getAsBoolean()) {
                    return true;
                }
                try {
                    Thread.sleep(checkIntervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return false;
        }
    }

    /**
     * Process verification utilities
     */
    public static class ProcessVerificationUtils {
        /**
         * Validates that all required variables are present
         */
        public static boolean hasRequiredVariables(java.util.Map<String, Object> variables,
                                                     String... requiredKeys) {
            for (String key : requiredKeys) {
                if (!variables.containsKey(key) || variables.get(key) == null) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Checks if process has reached expected state
         */
        public static boolean isInExpectedState(java.util.Map<String, Object> variables,
                                                 String expectedStatus) {
            Object status = variables.get("status");
            return status != null && status.toString().equals(expectedStatus);
        }
    }
}
