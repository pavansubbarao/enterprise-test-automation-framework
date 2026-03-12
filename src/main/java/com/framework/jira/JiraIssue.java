package com.framework.jira;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Represents a Jira issue fetched via the REST API.
 * Includes standard fields and common custom fields for QA workflows.
 */
@Data
@Builder
public class JiraIssue {

    private String key;
    private String id;
    private String summary;
    private String description;
    private String issueType;
    private String status;
    private String priority;
    private String assignee;
    private List<String> labels;

    /** Acceptance Criteria – typically stored in a custom field */
    private String acceptanceCriteria;

    /** Steps to Reproduce – used for bug tickets */
    private String stepsToReproduce;

    /**
     * Returns a full textual context block suitable for AI/Copilot prompts
     * or test-case generation. Combines all available fields.
     */
    public String toTestContext() {
        return """
                JIRA ISSUE: %s
                Type: %s | Status: %s | Priority: %s
                Summary: %s
                
                Description:
                %s
                
                Acceptance Criteria:
                %s
                
                Steps to Reproduce:
                %s
                
                Labels: %s
                """.formatted(
                key, issueType, status, priority,
                summary,
                orEmpty(description),
                orEmpty(acceptanceCriteria),
                orEmpty(stepsToReproduce),
                labels == null ? "none" : String.join(", ", labels)
        );
    }

    private String orEmpty(String s) {
        return s == null || s.isBlank() ? "(not provided)" : s;
    }
}
