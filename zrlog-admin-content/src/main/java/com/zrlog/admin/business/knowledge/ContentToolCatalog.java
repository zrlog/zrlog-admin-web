package com.zrlog.admin.business.knowledge;

import com.google.gson.*;
import com.zrlog.admin.business.knowledge.KnowledgeModels.Tool;
import java.util.*;

/** The tool contract shared by the internal assistant and the external MCP adapter. */
public final class ContentToolCatalog {
    // Allows Base64 attachments and JSON-escaped article bodies in model tool calls.
    public static final int MAX_ARGUMENT_LENGTH = 6 * 1024 * 1024;
    public static final int MAX_ATTACHMENT_BYTES = 4 * 1024 * 1024;
    public static final int MAX_BASE64_LENGTH = ((MAX_ATTACHMENT_BYTES + 2) / 3) * 4;
    public static final Set<String> NAMES = Set.of("search_articles", "read_article", "get_article", "list_categories", "list_tags",
            "create_article", "update_article", "publish_article", "upload_attachment");
    private ContentToolCatalog() { }

    public static List<Tool> tools(String language) {
        KnowledgeMessages messages = new KnowledgeMessages(language);
        List<Tool> result = new ArrayList<>(KnowledgeService.tools(language));
        result.add(tool(messages,"get_article", props("id", integer(1,Integer.MAX_VALUE)), List.of("id"),true));
        for (String name : List.of("list_categories","list_tags")) {
            result.add(tool(messages,name,props("offset",integer(0,100000),"limit",integer(1,100)),List.of(),true));
        }
        JsonObject fields = props("title",string(1,1024),"typeId",integer(1,Integer.MAX_VALUE),
                "status",enumeration("draft","private","published"), "markdown",string(0,262144),"content",string(0,524288),
                "alias",string(0,255),"digest",string(0,10000),"keywords",string(0,2000),"thumbnail",string(0,4000),
                "canComment",primitive("boolean"),"recommended",primitive("boolean"),"editorType",enumeration("markdown","html"));
        result.add(tool(messages,"create_article",fields.deepCopy(),List.of("title","typeId","status"),false));
        JsonObject update = fields.deepCopy(); update.add("id",integer(1,Integer.MAX_VALUE)); update.add("version",integer(0,Integer.MAX_VALUE-1));
        result.add(tool(messages,"update_article",update,List.of("id","version"),false));
        result.add(tool(messages,"publish_article",props("id",integer(1,Integer.MAX_VALUE),"version",integer(0,Integer.MAX_VALUE-1)),List.of("id","version"),false));
        result.add(tool(messages,"upload_attachment",props("filename",string(1,255),"data",string(1,MAX_BASE64_LENGTH)),List.of("filename","data"),false));
        return result;
    }
    private static Tool tool(KnowledgeMessages messages, String name, JsonObject properties, List<String> required, boolean readOnly) {
        Tool tool = new Tool(); tool.name = name;
        String key = "admin.mcp.tools." + camel(name);
        tool.description = messages.get(key + ".description"); tool.annotations.title = messages.get(key + ".title");
        tool.annotations.readOnlyHint = readOnly; tool.annotations.idempotentHint = readOnly;
        tool.annotations.destructiveHint = name.equals("update_article") || name.equals("publish_article");
        tool.annotations.openWorldHint = name.equals("upload_attachment");
        tool.inputSchema = primitive("object"); tool.inputSchema.add("properties",properties);
        tool.inputSchema.addProperty("additionalProperties",false);
        JsonArray requiredFields = new JsonArray(); required.forEach(requiredFields::add); tool.inputSchema.add("required",requiredFields);
        properties.entrySet().forEach(entry -> entry.getValue().getAsJsonObject().addProperty("description",messages.get("admin.mcp.parameters." + entry.getKey())));
        return tool;
    }
    public static void validate(Tool tool, JsonObject args, String language) {
        JsonObject schema = tool.inputSchema, properties = schema.getAsJsonObject("properties");
        boolean valid = properties.keySet().containsAll(args.keySet());
        if (schema.has("required")) for (JsonElement field : schema.getAsJsonArray("required")) valid &= args.has(field.getAsString());
        for (var entry : args.entrySet()) {
            if (!properties.has(entry.getKey()) || !entry.getValue().isJsonPrimitive()) { valid = false; continue; }
            JsonObject rule = properties.getAsJsonObject(entry.getKey()); JsonPrimitive value = entry.getValue().getAsJsonPrimitive();
            switch (rule.get("type").getAsString()) {
                case "string":
                    if (!value.isString()) { valid = false; break; }
                    int length = value.getAsString().length();
                    valid &= (!rule.has("minLength") || length >= rule.get("minLength").getAsInt())
                            && (!rule.has("maxLength") || length <= rule.get("maxLength").getAsInt());
                    if (entry.getKey().equals("title")) valid &= !value.getAsString().isBlank();
                    if (rule.has("enum")) valid &= rule.getAsJsonArray("enum").contains(value);
                    break;
                case "integer":
                    try {
                        if (!value.isNumber()) { valid = false; break; }
                        int number = value.getAsBigDecimal().intValueExact();
                        valid &= number >= rule.get("minimum").getAsInt() && number <= rule.get("maximum").getAsInt();
                    } catch (ArithmeticException | NumberFormatException e) { valid = false; }
                    break;
                case "boolean": valid &= value.isBoolean(); break;
                default: valid = false;
            }
        }
        if (tool.name.equals("update_article")) valid &= args.size() > 2;
        if (!valid) throw new IllegalArgumentException(new KnowledgeMessages(language).get("admin.mcp.validation.arguments"));
    }
    private static String camel(String name) {
        StringBuilder result = new StringBuilder(); boolean upper = false;
        for (char c : name.toCharArray()) { if (c == '_') upper = true; else { result.append(upper ? Character.toUpperCase(c) : c); upper = false; } }
        return result.toString();
    }
    private static JsonObject props(Object... fields) { JsonObject result = new JsonObject(); for (int i=0;i<fields.length;i+=2) result.add((String)fields[i],(JsonElement)fields[i+1]); return result; }
    private static JsonObject primitive(String type) { JsonObject result = new JsonObject(); result.addProperty("type",type); return result; }
    private static JsonObject string(int min,int max) { JsonObject result=primitive("string");result.addProperty("minLength",min);result.addProperty("maxLength",max);return result; }
    private static JsonObject integer(int min,int max) { JsonObject result=primitive("integer");result.addProperty("minimum",min);result.addProperty("maximum",max);return result; }
    private static JsonObject enumeration(String... values) { JsonObject result=primitive("string");JsonArray choices=new JsonArray();for(String value:values) choices.add(value);result.add("enum",choices);return result; }
}
