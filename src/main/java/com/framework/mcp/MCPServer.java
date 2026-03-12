package com.framework.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framework.config.FrameworkConfig;
import com.framework.mcp.tools.DatabaseTool;
import com.framework.mcp.tools.JiraTool;
import com.framework.mcp.tools.TestPlanTool;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Model Context Protocol (MCP) server implementation.
 *
 * Exposes test framework capabilities as MCP tools so AI agents
 * (Copilot, Claude, GPT-4, custom agents) can dynamically:
 *  - Query Jira for test context
 *  - Run database queries
 *  - Fetch and update test plans
 *  - Generate test scenarios
 *
 * Starts an HTTP server on the configured port.
 * MCP tool calls are handled via JSON-RPC 2.0 style requests.
 *
 * Usage: MCPServer.start()  (call once in test suite setup)
 */
@Slf4j
public class MCPServer {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static HttpServer server;

    private static final Map<String, MCPTool> tools = new HashMap<>();

    static {
        tools.put("jira_get_issue", new JiraTool.GetIssue());
        tools.put("jira_search", new JiraTool.Search());
        tools.put("jira_add_comment", new JiraTool.AddComment());
        tools.put("db_query", new DatabaseTool.Query());
        tools.put("db_count", new DatabaseTool.Count());
        tools.put("db_assert_exists", new DatabaseTool.AssertExists());
        tools.put("test_plan_get", new TestPlanTool.GetPlan());
        tools.put("test_plan_update", new TestPlanTool.UpdateResult());
    }

    public static synchronized void start() {
        if (!FrameworkConfig.getBoolean("mcp.server.enabled", true)) {
            log.info("MCP server disabled via config");
            return;
        }
        int port = FrameworkConfig.getInt("mcp.server.port", 8765);
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/mcp/tools", MCPServer::handleToolsList);
            server.createContext("/mcp/call", MCPServer::handleToolCall);
            server.createContext("/mcp/health", MCPServer::handleHealth);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            log.info("MCP server started on port {}", port);
        } catch (IOException e) {
            log.error("Failed to start MCP server: {}", e.getMessage());
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            log.info("MCP server stopped");
        }
    }

    // ------------------------------------------------------------------ //
    //  Handlers                                                            //
    // ------------------------------------------------------------------ //

    private static void handleToolsList(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        var toolList = mapper.createArrayNode();
        tools.forEach((name, tool) -> {
            ObjectNode t = toolList.addObject();
            t.put("name", name);
            t.put("description", tool.description());
            t.set("parameters", tool.parametersSchema(mapper));
        });
        ObjectNode resp = mapper.createObjectNode();
        resp.set("tools", toolList);
        sendJson(exchange, 200, resp.toString());
    }

    private static void handleToolCall(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try {
            var request = mapper.readTree(requestBody);
            String toolName = request.path("tool").asText();
            var params = request.path("parameters");

            MCPTool tool = tools.get(toolName);
            if (tool == null) {
                sendJson(exchange, 404, "{\"error\":\"Tool not found: " + toolName + "\"}");
                return;
            }

            log.info("MCP tool call: {} with params: {}", toolName, params);
            String result = tool.execute(params, mapper);

            ObjectNode resp = mapper.createObjectNode();
            resp.put("tool", toolName);
            resp.put("result", result);
            sendJson(exchange, 200, resp.toString());

        } catch (Exception e) {
            log.error("MCP tool call failed: {}", e.getMessage());
            sendJson(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private static void handleHealth(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, "{\"status\":\"ok\",\"tools\":" + tools.size() + "}");
    }

    private static void sendJson(com.sun.net.httpserver.HttpExchange exchange,
                                 int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
