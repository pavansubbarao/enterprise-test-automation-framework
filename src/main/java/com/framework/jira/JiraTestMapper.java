package com.framework.jira;

import com.framework.ai.CopilotClient;
import com.framework.config.FrameworkConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Maps Jira issues to test execution context and optionally generates
 * test method skeletons via GitHub Copilot.
 *
 * Typical flow:
 *   1. Fetch issue(s) from Jira
 *   2. Build context prompt
 *   3. Call Copilot to generate test code skeleton (optional)
 *   4. Return enriched context for the running test
 */
@Slf4j
public class JiraTestMapper {

    private final JiraClient jiraClient = new JiraClient();
    private final CopilotClient copilot;

    public JiraTestMapper() {
        boolean copilotEnabled = FrameworkConfig.getBoolean("copilot.enabled", false);
        this.copilot = copilotEnabled ? new CopilotClient() : null;
    }

    /**
     * Fetch a single Jira issue and return full test context.
     */
    public JiraTestContext getContext(String issueKey) {
        log.info("Fetching Jira context for: {}", issueKey);
        JiraIssue issue = jiraClient.getIssue(issueKey);
        if (issue == null) {
            log.warn("Issue not found: {}", issueKey);
            return JiraTestContext.empty(issueKey);
        }
        return buildContext(issue);
    }

    /**
     * Fetch and build context for all issues configured in config.properties.
     */
    public List<JiraTestContext> getConfiguredContexts() {
        return jiraClient.getConfiguredIssues().stream()
                .map(this::buildContext)
                .toList();
    }

    /**
     * Get all automation-labelled issues and build contexts.
     */
    public List<JiraTestContext> getAutomationContexts() {
        return jiraClient.getAutomationCandidates().stream()
                .map(this::buildContext)
                .toList();
    }

    /**
     * Generate a Java test method skeleton using Copilot for the given issue.
     * Returns empty string if Copilot is disabled.
     */
    public String generateTestSkeleton(String issueKey, String testType) {
        if (copilot == null) {
            log.info("Copilot disabled – skipping test skeleton generation");
            return "";
        }
        JiraIssue issue = jiraClient.getIssue(issueKey);
        if (issue == null) return "";

        String prompt = buildSkeletonPrompt(issue, testType);
        log.info("Generating {} test skeleton for {}", testType, issueKey);
        return copilot.complete(prompt);
    }

    /**
     * After a test run, report results back to Jira.
     */
    public void reportResult(String issueKey, boolean passed, String details) {
        if (!FrameworkConfig.getBoolean("jira.update.on.result", true)) return;
        String status = passed ? "PASSED" : "FAILED";
        String comment = String.format("[Automation Result] Test %s\n\n%s", status, details);
        jiraClient.addComment(issueKey, comment);
        log.info("Reported {} result to {}", status, issueKey);
    }

    // ------------------------------------------------------------------ //

    private JiraTestContext buildContext(JiraIssue issue) {
        String generatedCode = "";
        if (copilot != null) {
            try {
                generatedCode = copilot.complete(buildSkeletonPrompt(issue, "api"));
            } catch (Exception e) {
                log.warn("Copilot generation failed for {}: {}", issue.getKey(), e.getMessage());
            }
        }
        return JiraTestContext.builder()
                .issueKey(issue.getKey())
                .summary(issue.getSummary())
                .acceptanceCriteria(issue.getAcceptanceCriteria())
                .fullContext(issue.toTestContext())
                .generatedTestCode(generatedCode)
                .build();
    }

    private String buildSkeletonPrompt(JiraIssue issue, String testType) {
        return """
                You are an expert Java test automation engineer using TestNG and REST Assured.
                Generate a single TestNG test class skeleton for the following Jira issue.
                Test type: %s
                
                %s
                
                Requirements:
                - Class name: %sTest
                - Include @Test method(s) for each acceptance criterion
                - Add descriptive @Description annotations from Allure
                - Use REST Assured for API tests, Selenium for UI tests
                - Include //TODO comments for assertion logic
                - Do NOT include import statements – just the class body
                """.formatted(testType, issue.toTestContext(),
                issue.getKey().replace("-", "_"));
    }
}
