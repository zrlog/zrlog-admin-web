package com.zrlog.admin.business.knowledge;

import com.google.gson.JsonElement;
import java.util.List;

public final class McpModels {
    private McpModels() { }
    public static class InitializeResult {
        public String protocolVersion;
        public Capabilities capabilities = new Capabilities();
        public ServerInfo serverInfo = new ServerInfo();
        public String instructions;
    }
    public static class Capabilities { public ToolCapability tools = new ToolCapability(); }
    public static class ToolCapability { public boolean listChanged = false; }
    public static class ServerInfo {
        public String name = "zrlog-knowledge";
        public String title;
        public String description;
        public String version = "1.0.0";
    }
    public static class ToolList {
        public List<KnowledgeModels.Tool> tools;
        public ToolList(List<KnowledgeModels.Tool> tools) { this.tools = tools; }
    }
    public static class TextContent {
        public String type = "text";
        public String text;
        public TextContent(String text) { this.text = text; }
    }
    public static class ToolResult {
        public List<TextContent> content;
        public JsonElement structuredContent;
        public boolean isError;
    }
    public static class Error {
        public int code;
        public String message;
        public Error(int code, String message) { this.code = code; this.message = message; }
    }
}
