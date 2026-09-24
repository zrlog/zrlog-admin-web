package com.zrlog.admin.business.knowledge;

import com.google.gson.*;
import com.zrlog.admin.business.knowledge.McpModels.*;
import java.sql.SQLException;
import java.util.*;

/** Stateless MCP 2025 tool subset. JSON-RPC envelope fields are validated before dispatch. */
public final class McpService {
    public static final String LATEST = "2025-11-25";
    public static final Set<String> VERSIONS = Set.of("2025-03-26", "2025-06-18", LATEST);
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    public static final class Reply {
        public final int status;
        public final JsonObject body;
        Reply(int status, JsonObject body) { this.status = status; this.body = body; }
    }
    public Reply handle(String body, KnowledgeService knowledge) throws SQLException {
        JsonElement parsed;
        try { parsed = JSON.fromJson(body, JsonElement.class); }
        catch (JsonParseException e) { return error(JsonNull.INSTANCE, -32700, "Parse error", 400); }
        if (parsed == null || !parsed.isJsonObject()) return error(JsonNull.INSTANCE, -32600, "Invalid Request", 400);
        JsonObject rpc = parsed.getAsJsonObject();
        JsonElement id = rpc.get("id");
        if (!stringEquals(rpc.get("jsonrpc"), "2.0") || !isString(rpc.get("method"))
                || id != null && (!id.isJsonPrimitive() || !(id.getAsJsonPrimitive().isString() || id.getAsJsonPrimitive().isNumber()))
                || rpc.has("params") && !rpc.get("params").isJsonObject()) return error(JsonNull.INSTANCE, -32600, "Invalid Request", 400);
        // Notifications have no response and never invoke knowledge tools.
        if (id == null) return new Reply(202, null);
        String method = rpc.get("method").getAsString();
        JsonObject params = rpc.has("params") ? rpc.getAsJsonObject("params") : new JsonObject();
        switch (method) {
            case "initialize":
                if (!isString(params.get("protocolVersion")) || !params.has("capabilities") || !params.get("capabilities").isJsonObject()
                        || !params.has("clientInfo") || !params.get("clientInfo").isJsonObject()) return error(id, -32602, "Invalid initialize parameters", 200);
                InitializeResult initialize = new InitializeResult();
                String version = params.get("protocolVersion").getAsString(); initialize.protocolVersion = VERSIONS.contains(version) ? version : LATEST;
                return result(id, initialize);
            case "ping": return result(id, new JsonObject());
            case "tools/list":
                if (params.has("cursor")) return error(id, -32602, "No more tools", 200);
                return result(id, new ToolList());
            case "tools/call":
                if (!isString(params.get("name")) || params.has("arguments") && !params.get("arguments").isJsonObject()) return error(id, -32602, "Invalid tool call", 200);
                String name = params.get("name").getAsString();
                if (!Set.of("search_articles", "read_article").contains(name)) return error(id, -32602, "Unknown tool", 200);
                ToolResult tool = new ToolResult();
                try { tool.structuredContent = JSON.toJsonTree(knowledge.call(name, params.getAsJsonObject("arguments"))); }
                catch (IllegalArgumentException e) { tool.isError = true; tool.structuredContent = JSON.toJsonTree(new KnowledgeModels.ToolError(e.getMessage())); }
                tool.content = List.of(new TextContent(JSON.toJson(tool.structuredContent)));
                return result(id, tool);
            default: return error(id, -32601, "Method not found", 200);
        }
    }
    private static boolean isString(JsonElement value) { return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(); }
    private static boolean stringEquals(JsonElement value, String expected) { return isString(value) && expected.equals(value.getAsString()); }
    private Reply result(JsonElement id, Object result) {
        JsonObject body = envelope(id); body.add("result", JSON.toJsonTree(result)); return new Reply(200, body);
    }
    public static Reply error(JsonElement id, int code, String message, int status) {
        JsonObject body = envelope(id); body.add("error", JSON.toJsonTree(new McpModels.Error(code, message))); return new Reply(status, body);
    }
    private static JsonObject envelope(JsonElement id) { JsonObject body = new JsonObject(); body.addProperty("jsonrpc", "2.0"); body.add("id", id); return body; }
}
