package com.framework.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framework.jira.JiraClient;
import com.framework.jira.JiraIssue;
import com.framework.mcp.MCPTool;

import java.util.List;

/**
 * MCP tools for Jira operations.
 * Each inner class is one tool exposed via the MCP server.
 */
public class JiraTool {

    private static final JiraClient client = new JiraClient();

    // ------------------------------------------------------------------ //

    public static class GetIssue implements MCPTool {
        @Override
        public String description() {
            return "Fetch a Jira issue by key and return full context including " +
                   "acceptance criteria, steps, status, and description.";
        }

        @Override
        public ObjectNode parametersSchema(ObjectMapper m) {
            ObjectNode schema = m.createObjectNode();
            ObjectNode props = schema.putObject("properties");
            props.putObject("issueKey").put("type", "string").put("description", "Jira issue key e.g. QA-123");
            schema.putArray("required").add("issueKey");
            return schema;
        }

        @Override
        public String execute(JsonNode params, ObjectMapper mapper) throws Exception {
            String key = params.path("issueKey").asText();
            JiraIssue issue = client.getIssue(key);
            if (issue == null) return "{\"error\": \"Issue not found: " + key + "\"}";
            return mapper.writeValueAsString(issue);
        }
    }

    // ------------------------------------------------------------------ //

    public static class Search implements MCPTool {
        @Override
        public String description() {
            return "Search Jira using JQL and return matching issues with full context.";
        }

        @Override
        public ObjectNode parametersSchema(ObjectMapper m) {
            ObjectNode schema = m.createObjectNode();
            ObjectNode props = schema.putObject("properties");
            props.putObject("jql").put("type", "string").put("description", "Jira Query Language expression");
            props.putObject("maxResults").put("type", "integer").put("description", "Maximum number of results (default 25)");
            schema.putArray("required").add("jql");
            return schema;
        }

        @Override
        public String execute(JsonNode params, ObjectMapper mapper) throws Exception {
            String jql = params.path("jql").asText();
            int max = params.path("maxResults").asInt(25);
            List<JiraIssue> issues = client.searchByJql(jql, 0, max);
            return mapper.writeValueAsString(issues);
        }
    }

    // ------------------------------------------------------------------ //

    public static class AddComment implements MCPTool {
        @Override
        public String description() {
            return "Add a comment to a Jira issue (e.g. test result summary).";
        }

        @Override
        public ObjectNode parametersSchema(ObjectMapper m) {
            ObjectNode schema = m.createObjectNode();
            ObjectNode props = schema.putObject("properties");
            props.putObject("issueKey").put("type", "string");
            props.putObject("comment").put("type", "string");
            schema.putArray("required").add("issueKey").add("comment");
            return schema;
        }

        @Override
        public String execute(JsonNode params, ObjectMapper mapper) {
            client.addComment(params.path("issueKey").asText(), params.path("comment").asText());
            return "{\"success\": true}";
        }
    }
}
