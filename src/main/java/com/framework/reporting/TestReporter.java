package com.framework.reporting;

import com.framework.config.FrameworkConfig;
import com.framework.jira.JiraClient;
import io.qameta.allure.Allure;
import io.qameta.allure.model.Status;
import lombok.extern.slf4j.Slf4j;
import org.testng.ITestResult;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Centralised test reporter.
 *
 * - Attaches structured context to Allure reports
 * - Logs test results with timing information
 * - Publishes results back to Jira (if configured)
 * - Captures API request/response bodies as Allure attachments
 */
@Slf4j
public class TestReporter {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final JiraClient jira = FrameworkConfig.getBoolean("jira.update.on.result", true)
            ? new JiraClient() : null;

    private TestReporter() {}

    // ------------------------------------------------------------------ //
    //  TestNG result reporting                                             //
    // ------------------------------------------------------------------ //

    public static void reportSuccess(ITestResult result) {
        long durationMs = result.getEndMillis() - result.getStartMillis();
        log.info("[PASS] {} ({} ms)", result.getName(), durationMs);
        attachText("Test Result", buildResultText(result, "PASSED", durationMs));
        publishToJira(result, true);
    }

    public static void reportFailure(ITestResult result) {
        long durationMs = result.getEndMillis() - result.getStartMillis();
        Throwable throwable = result.getThrowable();
        String error = throwable != null ? throwable.getMessage() : "Unknown error";
        log.error("[FAIL] {} ({} ms) → {}", result.getName(), durationMs, error);
        attachText("Test Result", buildResultText(result, "FAILED", durationMs));
        if (throwable != null) {
            attachText("Stack Trace", stackTrace(throwable));
        }
        publishToJira(result, false);
    }

    public static void reportSkipped(ITestResult result) {
        log.warn("[SKIP] {}", result.getName());
        attachText("Test Result", buildResultText(result, "SKIPPED", 0));
    }

    // ------------------------------------------------------------------ //
    //  Allure attachment helpers                                           //
    // ------------------------------------------------------------------ //

    public static void attachText(String name, String content) {
        Allure.addAttachment(name, "text/plain",
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), "txt");
    }

    public static void attachJson(String name, String json) {
        Allure.addAttachment(name, "application/json",
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "json");
    }

    public static void attachHtml(String name, String html) {
        Allure.addAttachment(name, "text/html",
                new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)), "html");
    }

    public static void attachBytes(String name, byte[] data, String extension) {
        Allure.addAttachment(name, "image/png",
                new ByteArrayInputStream(data), extension);
    }

    public static void step(String description) {
        Allure.step(description);
    }

    public static void step(String description, Runnable action) {
        Allure.step(description, action::run);
    }

    public static void addLabel(String name, String value) {
        Allure.label(name, value);
    }

    public static void linkJira(String issueKey) {
        String jiraUrl = FrameworkConfig.get("jira.base.url", "") + "/browse/" + issueKey;
        Allure.link("Jira: " + issueKey, jiraUrl);
        Allure.issue(issueKey, jiraUrl);
    }

    // ------------------------------------------------------------------ //
    //  Internal                                                            //
    // ------------------------------------------------------------------ //

    private static void publishToJira(ITestResult result, boolean passed) {
        if (jira == null) return;
        String issueKey = extractJiraKey(result);
        if (issueKey == null) return;
        try {
            String comment = buildJiraComment(result, passed);
            jira.addComment(issueKey, comment);
        } catch (Exception e) {
            log.warn("Failed to publish result to Jira for {}: {}", issueKey, e.getMessage());
        }
    }

    private static String extractJiraKey(ITestResult result) {
        // Looks for @JiraIssue annotation or test name pattern "QA-123_..."
        String name = result.getName();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[A-Z]+-\\d+").matcher(name);
        return m.find() ? m.group() : null;
    }

    private static String buildResultText(ITestResult result, String status, long durationMs) {
        return String.format("""
                Status:    %s
                Test:      %s
                Class:     %s
                Started:   %s
                Duration:  %d ms
                """,
                status, result.getName(),
                result.getTestClass().getName(),
                FMT.format(Instant.ofEpochMilli(result.getStartMillis())),
                durationMs);
    }

    private static String buildJiraComment(ITestResult result, boolean passed) {
        long durationMs = result.getEndMillis() - result.getStartMillis();
        String error = "";
        if (!passed && result.getThrowable() != null) {
            error = "\n\nError: " + result.getThrowable().getMessage();
        }
        return String.format("[AUTOMATION] Test %s: %s (%d ms)%s",
                passed ? "PASSED ✓" : "FAILED ✗",
                result.getName(), durationMs, error);
    }

    private static String stackTrace(Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.toString()).append("\n");
        for (StackTraceElement el : t.getStackTrace()) {
            sb.append("  at ").append(el).append("\n");
        }
        if (t.getCause() != null) {
            sb.append("Caused by: ").append(t.getCause()).append("\n");
        }
        return sb.toString();
    }
}
