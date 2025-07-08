package com.intershop.customization.migration.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MigrationContextTest
{
    private MigrationContext context;
    private static final String TEST_PROJECT = "testProject";
    private static final Path SOURCE_PATH = Path.of("/source/file.txt");
    private static final Path TARGET_PATH = Path.of("/target/file.txt");

    @BeforeEach
    void setUp()
    {
        context = new MigrationContext(); // re-initialize a new MigrationContext before each test
    }

    @Nested
    @DisplayName("Basic operation recording tests")
    class OperationRecordingTests
    {
        @Test
        @DisplayName("Should record a successful operation")
        void testRecordSuccess()
        {
            context.recordSuccess(TEST_PROJECT, MigrationContext.OperationType.MOVE, SOURCE_PATH, TARGET_PATH);

            String report = context.generateSummaryReport();

            assertTrue(report.contains(TEST_PROJECT));
            assertTrue(report.contains("1 successful"));
            assertFalse(report.contains("Failed operations")); // Summary doesn't contain failures
        }

        @Test
        @DisplayName("Should record a skipped operation")
        void testRecordSkipped()
        {
            context.recordSkipped(TEST_PROJECT, MigrationContext.OperationType.MOVE,
                    SOURCE_PATH, TARGET_PATH, "Skip reason");

            String report = context.generateSummaryReport();

            assertTrue(report.contains(TEST_PROJECT));
            assertTrue(report.contains("1 skipped"));
            assertFalse(report.contains("Skip reason")); // Summary doesn't show skipped details
        }

        @Test
        @DisplayName("Should record an unknown operation")
        void testRecordUnknown()
        {
            context.recordUnknown(TEST_PROJECT, MigrationContext.OperationType.CREATE,
                    null, TARGET_PATH, "Unknown reason");

            // Then
            String report = context.generateSummaryReport();

            assertTrue(report.contains(TEST_PROJECT));
            assertTrue(report.contains("1 unknown"));
            assertTrue(report.contains("Unknown reason"));
            assertTrue(report.contains("Unknown operations:"));
        }

        @Test
        @DisplayName("Should record a warning operation")
        void testRecordWarning()
        {
            context.recordWarning(TEST_PROJECT, MigrationContext.OperationType.MODIFY,
                    SOURCE_PATH, SOURCE_PATH, "Warning message");

            String report = context.generateSummaryReport();

            assertTrue(report.contains(TEST_PROJECT));
            assertTrue(report.contains("1 warnings"));
            assertTrue(report.contains("Warning message"));
            assertTrue(report.contains("Warnings:"));
        }

        @Test
        @DisplayName("Should record a failed operation")
        void testRecordFailure()
        {
            context.recordFailure(TEST_PROJECT, MigrationContext.OperationType.DELETE,
                    SOURCE_PATH, null, "Error message");

            String report = context.generateSummaryReport();

            assertTrue(report.contains(TEST_PROJECT));
            assertTrue(report.contains("1 failed"));
            assertTrue(report.contains("Error message"));
            assertTrue(report.contains("Failed operations:"));
        }
    }

    @Nested
    @DisplayName("Multiple operations tests")
    class MultipleOperationsTests
    {
        @Test
        @DisplayName("Should count operations correctly")
        void testOperationCounting()
        {
            context.recordSuccess(TEST_PROJECT, MigrationContext.OperationType.MOVE, SOURCE_PATH, TARGET_PATH);
            context.recordSuccess(TEST_PROJECT, MigrationContext.OperationType.CREATE, null, TARGET_PATH);
            context.recordFailure(TEST_PROJECT, MigrationContext.OperationType.DELETE, SOURCE_PATH, null, "Error");

            String report = context.generateSummaryReport();

            assertTrue(report.contains("3 operations"));
            assertTrue(report.contains("2 successful"));
            assertTrue(report.contains("1 failed"));
        }

        @Test
        @DisplayName("Should handle multiple projects correctly")
        void testMultipleProjects()
        {
            final String project1 = "project1";
            final String project2 = "project2";

            context.recordSuccess(project1, MigrationContext.OperationType.MOVE, SOURCE_PATH, TARGET_PATH);
            context.recordSkipped(project2, MigrationContext.OperationType.CREATE, null, TARGET_PATH, "Skipped");

            String report = context.generateSummaryReport();

            assertTrue(report.contains(project1));
            assertTrue(report.contains(project2));
            assertTrue(report.contains("Project 'project1': 1 operations (1 successful"));
            assertTrue(report.contains("Project 'project2': 1 operations (0 successful, 1 skipped"));
        }

        @Test
        @DisplayName("Should deduplicate identical operations")
        void testDeduplication()
        {
            context.recordSuccess(TEST_PROJECT, MigrationContext.OperationType.MOVE, SOURCE_PATH, TARGET_PATH);
            context.recordSuccess(TEST_PROJECT, MigrationContext.OperationType.MOVE, SOURCE_PATH, TARGET_PATH);

            String report = context.generateSummaryReport();

            assertTrue(report.contains("1 operations"));
            assertTrue(report.contains("1 successful"));
        }
    }

    @Nested
    @DisplayName("Critical errors tests")
    class CriticalErrorTests
    {
        @Test
        @DisplayName("Should record and retrieve critical errors")
        void testCriticalErrors()
        {
            final String errorMsg1 = "Critical error 1";
            final String errorMsg2 = "Critical error 2";

            context.recordCriticalError(errorMsg1);
            context.recordCriticalError(errorMsg2);

            assertTrue(context.hasCriticalError());
            List<String> errors = context.getCriticalErrors();
            assertEquals(2, errors.size());
            assertTrue(errors.contains(errorMsg1));
            assertTrue(errors.contains(errorMsg2));
        }

        @Test
        @DisplayName("Should not have critical errors by default")
        void testNoCriticalErrorsByDefault()
        {
            assertFalse(context.hasCriticalError());
            assertTrue(context.getCriticalErrors().isEmpty());
        }
    }

    @Nested
    @DisplayName("Additional attributes tests")
    class AdditionalAttributesTests
    {

        @Test
        @DisplayName("Should add and retrieve additional attributes")
        void testAdditionalAttributes()
        {
            final String key = "testKey";
            final Object value = "testValue";

            Object result = context.addAdditionalAttribute(key, value);
            assertNull(result);

            Map<String, Object> attributes = context.getAdditionalAttributes();
            assertEquals(1, attributes.size());
            assertEquals(value, attributes.get(key));
        }

        @Test
        @DisplayName("Should replace existing attributes")
        void testReplaceAttribute()
        {
            final String key = "testKey";
            final Object value1 = "testValue1";
            final Object value2 = "testValue2";

            context.addAdditionalAttribute(key, value1);
            Object result = context.addAdditionalAttribute(key, value2);

            assertEquals(value1, result); // Previous value
            assertEquals(value2, context.getAdditionalAttributes().get(key));
        }

        @Test
        @DisplayName("Should remove attributes")
        void testRemoveAttribute()
        {
            final String key = "testKey";
            final Object value = "testValue";
            context.addAdditionalAttribute(key, value);

            Object result = context.removeAdditionalAttribute(key);

            assertEquals(value, result); // Removed value
            assertTrue(context.getAdditionalAttributes().isEmpty());
        }

        @Test
        @DisplayName("Should throw exception for null key")
        void testNullKeyThrowsException()
        {
            assertThrows(NullPointerException.class, () ->
                context.addAdditionalAttribute(null, "value"));

            assertThrows(NullPointerException.class, () ->
                context.removeAdditionalAttribute(null));
        }

        @Test
        @DisplayName("Should throw exception for null value")
        void testNullValueThrowsException()
        {
            assertThrows(NullPointerException.class, () ->
                context.addAdditionalAttribute("key", null));
        }
    }

    @Nested
    @DisplayName("Operation record tests")
    class OperationRecordTests
    {
        @Test
        @DisplayName("Should correctly format operation toString")
        void testOperationToString()
        {
            final MigrationContext.Operation op1 = new MigrationContext.Operation(
                MigrationContext.OperationType.MOVE,
                SOURCE_PATH,
                TARGET_PATH,
                MigrationContext.OperationStatus.SUCCESS,
                "Test message"
            );

            final MigrationContext.Operation op2 = new MigrationContext.Operation(
                MigrationContext.OperationType.CREATE,
                null,
                TARGET_PATH,
                MigrationContext.OperationStatus.SUCCESS,
                "Test message"
            );

            String op1String = op1.toString();
            assertTrue(op1String.contains("SUCCESS MOVE"));
            assertTrue(op1String.contains("/source/file.txt -> /target/file.txt"));
            assertTrue(op1String.contains("Test message"));

            String op2String = op2.toString();
            assertTrue(op2String.contains("N/A -> /target/file.txt"));
        }

        @Test
        @DisplayName("Should handle equals correctly")
        void testOperationEquals()
        {
            final MigrationContext.Operation op1 = new MigrationContext.Operation(
                MigrationContext.OperationType.MOVE,
                SOURCE_PATH,
                TARGET_PATH,
                MigrationContext.OperationStatus.SUCCESS,
                "Message 1"
            );

            final MigrationContext.Operation op2 = new MigrationContext.Operation(
                MigrationContext.OperationType.MOVE,
                SOURCE_PATH,
                TARGET_PATH,
                MigrationContext.OperationStatus.SUCCESS,
                "Message 2" // Message is intentionally different
            );

            final MigrationContext.Operation op3 = new MigrationContext.Operation(
                MigrationContext.OperationType.DELETE,
                SOURCE_PATH,
                null,
                MigrationContext.OperationStatus.SUCCESS,
                "Message 1"
            );

            assertEquals(op1, op2); // Equal despite different messages
            assertNotEquals(op1, op3); // Different type and target
        }
    }
}
