package com.framework.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framework.jira.JiraClient;
import com.framework.mcp.MCPTool;

/**
 * MCP tools for test plan operations — fetching test plans from Jira
 * and recording test execution results back to Jira.
 */
public class TestPlanTool {

    private static final JiraClient jira = new JiraClient();

    // ------------------------------------------------------------------ //

    public static class GetPlan implements MCPTool {
        @Override
        public String description() {
            return "Retrieve a test plan from Jira. Returns the issue summary, " +
                   "acceptance criteria, and linked test cases as structured context.";
        }

        @Override
        public ObjectNode parametersSchema(ObjectMapper m) {
            ObjectNode schema = m.createObjectNode();
            schema.putObject("properties")
                    .putObject("issueKey").put("type", "string");
            schema.putArray("required").add("issueKey");
            return schema;
        }

        @Override
        public String execute(JsonNode params, ObjectMapper mapper) throws Exception {
            String key = params.path("issueKey").asText();
            var issue = jira.getIssue(key);
            if (issue == null) return "{\"error\":\"Not found\"}";
            ObjectNode result = mapper.createObjectNode();
            result.put("issueKey", issue.getKey());
            result.put("summary", issue.getSummary());
            result.put("status", issue.getStatus());
            result.put("acceptanceCriteria", issue.getAcceptanceCriteria());
            result.put("context", issue.toTestContext());
            return result.toString();
        }
    }

    // ------------------------------------------------------------------ //

    public static class UpdateResult implements MCPTool {
        @Override
        public String description() {
            return "Record a test execution result back to Jira as a comment " +
                   "and optionally transition the issue status.";
        }

        @Override
        public ObjectNode parametersSchema(ObjectMapper m) {
            ObjectNode schema = m.createObjectNode();
            ObjectNode props = schema.putObject("properties");
            props.putObject("issueKey").put("type", "string");
            props.putObject("passed").put("type", "boolean");
            props.putObject("summary").put("type", "string").put("description", "Short result summary");
            props.putObject("details").put("type", "string").put("description", "Full test output details");
            schema.putArray("required").add("issueKey").add("passed");
            return schema;
        }

        @Override
        public String execute(JsonNode params, ObjectMapper mapper) {
            String key = params.path("issueKey").asText();
            boolean passed = params.path("passed").asBoolean();
            String summary = params.path("summary").asText("Automated test result");
            String details = params.path("details").asText("");

            String comment = String.format("[AUTOMATION] %s: %s\n\n%s",
                    passed ? "PASSED ✓" : "FAILED ✗", summary, details);
            jira.addComment(key, comment);

            return "{\"success\": true, \"issueKey\": \"" + key + "\", \"result\": \"" +
                   (passed ? "PASSED" : "FAILED") + "\"}";
        }
    }
}
