package com.zrlog.admin.business.knowledge;

import com.google.gson.JsonObject;
import java.util.List;

/** Tool definitions and execution shared by the read service and the external MCP adapter. */
public interface ToolProvider {
    List<KnowledgeModels.Tool> definitions(String language) throws Exception;
    Object call(String name, JsonObject arguments) throws Exception;
}
