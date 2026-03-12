package com.framework.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framework.database.PostgreSQLManager;
import com.framework.mcp.MCPTool;

import java.util.List;
import java.util.Map;

/**
 * MCP tools for database operations.
 */
public class DatabaseTool {

    private static final PostgreSQLManager db = PostgreSQLManager.getInstance();

    // ------------------------------------------------------------------ //

    public static class Query implements MCPTool {
        @Override
        public String description() {
            return "Execute a read-only SQL SELECT query against the test database " +
                   "and return results as JSON. Use for data validation assertions.";
        }

        @Override
        public ObjectNode parametersSchema(ObjectMapper m) {
            ObjectNode schema = m.createObjectNode();
            ObjectNode props = schema.putObject("properties");
            props.putObject("sql").put("type", "string").put("description", "SQL SELECT query");
            schema.putArray("required").add("sql");
            return schema;
        }

        @Override
        public String execute(JsonNode params, ObjectMapper mapper) throws Exception {
            String sql = params.path("sql").asText();
            if (!sql.trim().toUpperCase().startsWith("SELECT")) {
                return "{\"error\": \"Only SELECT queries are allowed\"}";
            }
            List<Map<String, Object>> results = db.query(sql);
            return mapper.writeValueAsString(results);
        }
    }

    // ------------------------------------------------------------------ //

    public static class Count implements MCPTool {
        @Override
        public String description() {
            return "Count rows in a database table, optionally with a WHERE filter.";
        }

        @Override
        public ObjectNode parametersSchema(ObjectMapper m) {
            ObjectNode schema = m.createObjectNode();
            ObjectNode props = schema.putObject("properties");
            props.putObject("table").put("type", "string");
            props.putObject("where").put("type", "string").put("description", "Optional WHERE clause");
            schema.putArray("required").add("table");
            return schema;
        }

        @Override
        public String execute(JsonNode params, ObjectMapper mapper) throws Exception {
            String table = params.path("table").asText();
            String where = params.path("where").asText(null);
            String sql = "SELECT COUNT(*) FROM " + table + (where != null ? " WHERE " + where : "");
            Object count = db.querySingle(sql);
            return "{\"count\": " + count + "}";
        }
    }

    // ------------------------------------------------------------------ //

    public static class AssertExists implements MCPTool {
        @Override
        public String description() {
            return "Assert that a row exists in the database matching the given table and WHERE condition.";
        }

        @Override
        public ObjectNode parametersSchema(ObjectMapper m) {
            ObjectNode schema = m.createObjectNode();
            ObjectNode props = schema.putObject("properties");
            props.putObject("table").put("type", "string");
            props.putObject("where").put("type", "string");
            schema.putArray("required").add("table").add("where");
            return schema;
        }

        @Override
        public String execute(JsonNode params, ObjectMapper mapper) {
            String table = params.path("table").asText();
            String where = params.path("where").asText();
            boolean exists = db.exists(table, where);
            return "{\"exists\": " + exists + "}";
        }
    }
}
