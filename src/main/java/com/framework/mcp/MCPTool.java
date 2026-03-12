package com.framework.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Contract for all MCP tool implementations.
 */
public interface MCPTool {
    String description();
    ObjectNode parametersSchema(ObjectMapper mapper);
    String execute(JsonNode params, ObjectMapper mapper) throws Exception;
}
