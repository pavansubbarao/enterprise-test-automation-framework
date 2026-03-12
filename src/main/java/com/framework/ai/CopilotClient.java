package com.framework.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framework.config.FrameworkConfig;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * GitHub Copilot (Enterprise) client for AI-assisted test generation.
 *
 * Uses the GitHub Copilot Chat completions API compatible with enterprise instances.
 * Set via config:
 *   copilot.api.url=https://api.githubcopilot.com
 *   copilot.api.token=${GITHUB_COPILOT_TOKEN}
 *   copilot.model=gpt-4o
 *
 * Usage:
 *   CopilotClient ai = new CopilotClient();
 *   String code = ai.complete("Generate a TestNG test for login functionality");
 *   String analysis = ai.chat(List.of(
 *       Message.system("You are a QA engineer"),
 *       Message.user("What test cases should I write for this API?")
 *   ));
 */
@Slf4j
public class CopilotClient {

    private final OkHttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiUrl;
    private final String model;

    public CopilotClient() {
        this.apiUrl = FrameworkConfig.get("copilot.api.url", "https://api.githubcopilot.com");
        String token = FrameworkConfig.get("copilot.api.token", "");
        this.model = FrameworkConfig.get("copilot.model", "gpt-4o");

        this.http = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request req = chain.request().newBuilder()
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json")
                            .header("Copilot-Integration-Id", "vscode-chat")
                            .header("Editor-Version", "vscode/1.85.0")
                            .build();
                    return chain.proceed(req);
                })
                .build();

        log.info("CopilotClient initialised [model={}, endpoint={}]", model, apiUrl);
    }

    /**
     * Single-turn completion from a user prompt.
     * Returns the assistant's response text.
     */
    public String complete(String userPrompt) {
        return complete(userPrompt, "You are an expert Java test automation engineer.");
    }

    public String complete(String userPrompt, String systemPrompt) {
        return chat(List.of(
                new Message("system", systemPrompt),
                new Message("user", userPrompt)
        ));
    }

    /**
     * Multi-turn chat completion.
     */
    public String chat(List<Message> messages) {
        String endpoint = apiUrl + "/chat/completions";

        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.put("max_tokens", 4096);

        ArrayNode msgs = body.putArray("messages");
        for (Message msg : messages) {
            ObjectNode m = msgs.addObject();
            m.put("role", msg.role());
            m.put("content", msg.content());
        }

        RequestBody rb = RequestBody.create(
                body.toString(), MediaType.parse("application/json"));
        Request req = new Request.Builder().url(endpoint).post(rb).build();

        try (Response resp = http.newCall(req).execute()) {
            String responseBody = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) {
                log.error("Copilot API error {}: {}", resp.code(), responseBody);
                throw new RuntimeException("Copilot API returned " + resp.code());
            }
            JsonNode root = mapper.readTree(responseBody);
            String content = root.path("choices").get(0)
                    .path("message").path("content").asText();
            log.debug("Copilot response: {} chars", content.length());
            return content;
        } catch (IOException e) {
            log.error("Copilot API call failed: {}", e.getMessage());
            throw new RuntimeException("Copilot API call failed", e);
        }
    }

    /**
     * Ask Copilot to analyse a failing test and suggest a fix.
     */
    public String suggestFix(String testCode, String errorMessage) {
        String prompt = """
                The following Java test is failing. Analyse the error and suggest a fix.
                
                === TEST CODE ===
                %s
                
                === ERROR ===
                %s
                
                Provide:
                1. Root cause analysis
                2. Corrected test code
                3. Explanation of the fix
                """.formatted(testCode, errorMessage);
        return complete(prompt, "You are a senior Java QA engineer specialising in debugging test failures.");
    }

    /**
     * Generate test cases from natural language requirements.
     */
    public String generateTestCases(String requirements) {
        String prompt = """
                Generate comprehensive TestNG test cases for the following requirements.
                Output as a valid Java class using REST Assured and Selenium as appropriate.
                
                Requirements:
                %s
                """.formatted(requirements);
        return complete(prompt);
    }

    /**
     * Analyse Jira acceptance criteria and return a structured list of test scenarios.
     */
    public String analyseAcceptanceCriteria(String acceptanceCriteria) {
        String prompt = """
                Analyse these acceptance criteria and list all test scenarios that should be automated.
                For each scenario, specify: Scenario Name, Test Type (API/UI/DB), Steps, Expected Result.
                
                Acceptance Criteria:
                %s
                """.formatted(acceptanceCriteria);
        return complete(prompt, "You are a QA architect specialising in test design.");
    }

    public record Message(String role, String content) {
        public static Message system(String content) { return new Message("system", content); }
        public static Message user(String content) { return new Message("user", content); }
        public static Message assistant(String content) { return new Message("assistant", content); }
    }
}
