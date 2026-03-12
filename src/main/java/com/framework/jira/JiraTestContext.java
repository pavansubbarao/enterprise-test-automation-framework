package com.framework.jira;

import lombok.Builder;
import lombok.Data;

/**
 * Enriched test context derived from a Jira issue.
 * Passed into tests via TestNG DataProvider or injected by JiraTestMapper.
 */
@Data
@Builder
public class JiraTestContext {

    private String issueKey;
    private String summary;
    private String acceptanceCriteria;
    private String fullContext;
    private String generatedTestCode;

    public static JiraTestContext empty(String key) {
        return JiraTestContext.builder()
                .issueKey(key)
                .summary("Not Found")
                .acceptanceCriteria("")
                .fullContext("")
                .generatedTestCode("")
                .build();
    }

    public boolean hasAcceptanceCriteria() {
        return acceptanceCriteria != null && !acceptanceCriteria.isBlank();
    }
}
