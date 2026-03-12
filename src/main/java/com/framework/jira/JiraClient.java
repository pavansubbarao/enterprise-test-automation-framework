package com.framework.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.config.FrameworkConfig;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.StreamSupport;

/**
 * Jira REST API v3 client.
 * Supports fetching issues, test cases, acceptance criteria, and updating issue status.
 *
 * Authentication: Basic Auth using email + API token (Jira Cloud).
 * For Jira Server/DC: set jira.auth.type=bearer and provide a PAT via jira.api.token.
 */
@Slf4j
public class JiraClient {

    private final OkHttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;
    private final String credentials;
    private final String authType;

    public JiraClient() {
        this.baseUrl = FrameworkConfig.get("jira.base.url");
        String token = FrameworkConfig.get("jira.api.token");
        String email = FrameworkConfig.get("jira.user.email", "");
        this.authType = FrameworkConfig.get("jira.auth.type", "basic");

        if ("bearer".equalsIgnoreCase(authType)) {
            this.credentials = "Bearer " + token;
        } else {
            String raw = email + ":" + token;
            this.credentials = "Basic " + Base64.getEncoder().encodeToString(raw.getBytes());
        }

        this.http = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request req = chain.request().newBuilder()
                            .header("Authorization", this.credentials)
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json")
                            .build();
                    return chain.proceed(req);
                })
                .build();

        log.info("JiraClient initialised for: {}", baseUrl);
    }

    /**
     * Fetch a single Jira issue with all fields.
     */
    public JiraIssue getIssue(String issueKey) {
        String url = baseUrl + "/rest/api/3/issue/" + issueKey;
        JsonNode root = get(url);
        return parseIssue(root);
    }

    /**
     * Fetch all issues matching a JQL query.
     */
    public List<JiraIssue> searchByJql(String jql) {
        return searchByJql(jql, 0, 50);
    }

    public List<JiraIssue> searchByJql(String jql, int startAt, int maxResults) {
        String url = baseUrl + "/rest/api/3/search?jql="
                + HttpUrl.encode(jql, "UTF-8")
                + "&startAt=" + startAt + "&maxResults=" + maxResults
                + "&expand=renderedFields,names";
        JsonNode root = get(url);
        List<JiraIssue> issues = new ArrayList<>();
        if (root != null && root.has("issues")) {
            root.get("issues").forEach(node -> issues.add(parseIssue(node)));
        }
        log.info("JQL '{}' returned {} issues", jql, issues.size());
        return issues;
    }

    /**
     * Fetch all issues labelled for automation in the configured project.
     */
    public List<JiraIssue> getAutomationCandidates() {
        String project = FrameworkConfig.get("jira.project.key", "QA");
        String label   = FrameworkConfig.get("jira.test.label", "automated");
        String jql = String.format(
                "project = %s AND labels = %s AND issuetype in (Test, Story, Bug) ORDER BY created DESC",
                project, label);
        return searchByJql(jql);
    }

    /**
     * Fetch issues by explicit comma-separated keys from config.
     */
    public List<JiraIssue> getConfiguredIssues() {
        String raw = FrameworkConfig.get("jira.issue.keys", "");
        if (raw == null || raw.isBlank()) return List.of();
        List<JiraIssue> issues = new ArrayList<>();
        for (String key : raw.split(",")) {
            key = key.trim();
            if (!key.isEmpty()) {
                try { issues.add(getIssue(key)); }
                catch (Exception e) { log.warn("Could not fetch {}: {}", key, e.getMessage()); }
            }
        }
        return issues;
    }

    /**
     * Add a comment to a Jira issue (Atlassian Document Format).
     */
    public void addComment(String issueKey, String text) {
        String url = baseUrl + "/rest/api/3/issue/" + issueKey + "/comment";
        String body = """
                {
                  "body": {
                    "type": "doc",
                    "version": 1,
                    "content": [{
                      "type": "paragraph",
                      "content": [{"type": "text", "text": "%s"}]
                    }]
                  }
                }
                """.formatted(escapeJson(text));
        post(url, body);
        log.info("Comment added to {}", issueKey);
    }

    /**
     * Transition a Jira issue to a new status (e.g. "In Progress", "Done").
     * Looks up the transition ID by name automatically.
     */
    public void transitionIssue(String issueKey, String targetStatus) {
        String transitionId = findTransitionId(issueKey, targetStatus);
        if (transitionId == null) {
            log.warn("No transition found for status '{}' on {}", targetStatus, issueKey);
            return;
        }
        String url = baseUrl + "/rest/api/3/issue/" + issueKey + "/transitions";
        String body = String.format("{\"transition\":{\"id\":\"%s\"}}", transitionId);
        post(url, body);
        log.info("Transitioned {} → {}", issueKey, targetStatus);
    }

    /**
     * Update a custom field on an issue (useful for storing test result metadata).
     */
    public void updateField(String issueKey, String fieldId, String value) {
        String url = baseUrl + "/rest/api/3/issue/" + issueKey;
        String body = String.format("{\"fields\":{\"%s\":\"%s\"}}", fieldId, escapeJson(value));
        put(url, body);
    }

    // ------------------------------------------------------------------ //
    //  Internal helpers                                                     //
    // ------------------------------------------------------------------ //

    private JiraIssue parseIssue(JsonNode node) {
        if (node == null) return null;
        JsonNode fields = node.path("fields");
        return JiraIssue.builder()
                .key(node.path("key").asText())
                .id(node.path("id").asText())
                .summary(fields.path("summary").asText())
                .description(extractText(fields.path("description")))
                .issueType(fields.path("issuetype").path("name").asText())
                .status(fields.path("status").path("name").asText())
                .priority(fields.path("priority").path("name").asText("Medium"))
                .assignee(fields.path("assignee").path("emailAddress").asText(""))
                .labels(extractLabels(fields.path("labels")))
                .acceptanceCriteria(extractText(fields.path("customfield_10016")))
                .stepsToReproduce(extractText(fields.path("customfield_10034")))
                .build();
    }

    private String extractText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        if (node.isTextual()) return node.asText();
        StringBuilder sb = new StringBuilder();
        extractAdfText(node, sb);
        return sb.toString().trim();
    }

    private void extractAdfText(JsonNode node, StringBuilder sb) {
        if (node.isTextual()) { sb.append(node.asText()).append(" "); return; }
        if (node.has("text")) { sb.append(node.get("text").asText()).append(" "); }
        if (node.has("content")) node.get("content").forEach(c -> extractAdfText(c, sb));
    }

    private List<String> extractLabels(JsonNode node) {
        if (node == null || node.isMissingNode()) return List.of();
        return StreamSupport.stream(node.spliterator(), false)
                .map(JsonNode::asText).toList();
    }

    private String findTransitionId(String issueKey, String targetStatus) {
        String url = baseUrl + "/rest/api/3/issue/" + issueKey + "/transitions";
        JsonNode root = get(url);
        if (root == null || !root.has("transitions")) return null;
        for (JsonNode t : root.get("transitions")) {
            if (targetStatus.equalsIgnoreCase(t.path("name").asText())) {
                return t.path("id").asText();
            }
        }
        return null;
    }

    private JsonNode get(String url) {
        Request req = new Request.Builder().url(url).get().build();
        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) {
                log.error("Jira GET {} → {} : {}", url, resp.code(), body);
                return null;
            }
            return mapper.readTree(body);
        } catch (IOException e) {
            log.error("Jira GET failed: {}", e.getMessage());
            return null;
        }
    }

    private void post(String url, String json) {
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        Request req = new Request.Builder().url(url).post(body).build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) log.error("Jira POST {} → {}", url, resp.code());
        } catch (IOException e) {
            log.error("Jira POST failed: {}", e.getMessage());
        }
    }

    private void put(String url, String json) {
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        Request req = new Request.Builder().url(url).put(body).build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) log.error("Jira PUT {} → {}", url, resp.code());
        } catch (IOException e) {
            log.error("Jira PUT failed: {}", e.getMessage());
        }
    }

    private String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String encode(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }
}
