package com.zrlog.admin.business.ai.service;

import com.google.gson.*;
import com.zrlog.admin.business.ai.exception.AIResponseException;
import com.zrlog.admin.business.ai.exception.UnsupportedAIToolException;
import com.zrlog.admin.business.knowledge.KnowledgeModels.Tool;
import com.zrlog.admin.business.rest.request.GenerateArticleFieldRequest;
import com.zrlog.common.exception.ArgsException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Trusted Markdown instructions paired with code-owned executors and result contracts. */
public final class AIWritingSkillCatalog {
    public static final List<String> KEYS = List.of("title", "alias", "digest", "tags", "rewrite", "score",
            "publishCheck", "seo", "proofread", "structure", "questions", "cover");
    public static final String INPUT_TOOL = "request_writing_input";
    private static final Map<String, String> DOCUMENTS = loadDocuments();
    private AIWritingSkillCatalog() { }

    public static String resource(String key) { return "/ai/skills/" + key + "/SKILL.md"; }
    public static String instructions(String key) {
        if (!KEYS.contains(key)) throw new UnsupportedAIToolException(key);
        return DOCUMENTS.get(key);
    }
    private static Map<String, String> loadDocuments() {
        Map<String, String> documents = new LinkedHashMap<>();
        for (String key : KEYS) try (InputStream in = AIWritingSkillCatalog.class.getResourceAsStream(resource(key))) {
            if (in == null) throw new IOException("Missing skill: " + key);
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (!text.startsWith("---\nname: " + key + "\ndescription: ") || !text.contains("\n---\n"))
                throw new IOException("Invalid skill metadata: " + key);
            documents.put(key, text);
        } catch (IOException e) { throw new IllegalStateException(e); }
        return Map.copyOf(documents);
    }
    public static List<String> applicableFields(String key) {
        switch (key) {
            case "title": case "alias": case "digest": return List.of(key);
            case "tags": return List.of("keywords");
            case "rewrite": return List.of("markdown");
            case "cover": return List.of("thumbnail");
            default: return List.of();
        }
    }
    public static List<Tool> tools() {
        List<Tool> result = new ArrayList<>();
        for (String key : KEYS) result.add(tool("writing_" + key, instructions(key),
                "{\"instruction\":{\"type\":\"string\",\"maxLength\":8000,\"description\":\"Task-specific requirements and chosen preferences. "
                        + "May be empty when no extra requirements are needed. The editor snapshot is supplied by the runtime.\"}}",
                List.of("instruction")));
        result.add(tool(INPUT_TOOL, "Ask for essential missing information, or a user choice required before subsequent work. "
                        + "Do not ask for optional preferences when the task is already clear, or ask users to repeat a choice they delegated to you. "
                        + "For title selection, supply resultId from a writing_title result; options are taken from that validated result. "
                        + "Omit resultId for a free text answer. Selection does not apply or save anything. Never repeat a cancelled request.",
                "{\"question\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":1000},\"resultId\":{\"type\":\"string\",\"maxLength\":300}}",
                List.of("question")));
        return result;
    }
    private static Tool tool(String name, String description, String properties, List<String> required) {
        Tool tool = new Tool(); tool.name = name; tool.description = description;
        tool.inputSchema = new JsonObject(); tool.inputSchema.addProperty("type", "object");
        tool.inputSchema.add("properties", JsonParser.parseString(properties));
        tool.inputSchema.addProperty("additionalProperties", false);
        tool.inputSchema.add("required", new Gson().toJsonTree(required));
        return tool;
    }
    public static void validateSnapshot(GenerateArticleFieldRequest context) {
        if (context == null) return;
        List<String> values = Arrays.asList(context.getTitle(), context.getMarkdown(), context.getDigest(),
                context.getKeywords(), context.getSelectedText(), context.getAlias(), context.getThumbnail());
        int total = 0;
        for (String value : values) if (value != null) total += value.length();
        if (total > 180_000 || length(context.getTitle()) > 1024 || length(context.getKeywords()) > 2000
                || length(context.getDigest()) > 10000 || length(context.getAlias()) > 255 || length(context.getThumbnail()) > 4000)
            throw new ArgsException("editorContext");
    }
    private static int length(String value) { return value == null ? 0 : value.length(); }

    /** Validate the normalized DTO before it can become an actionable card, including manual invocations. */
    public static void validateResult(String key, Object value) {
        instructions(key);
        JsonElement json = new Gson().toJsonTree(value);
        if (!json.isJsonObject() || json.toString().length() > 1_500_000) throw invalid();
        JsonObject result = json.getAsJsonObject();
        switch (key) {
            case "title": strings(result, "titles", 3, 1024); break;
            case "tags": strings(result, "tags", 6, 255); break;
            case "alias": text(result, "alias", 255, false); break;
            case "digest": text(result, "digest", 10000, false); break;
            case "rewrite": text(result, "markdown", 262144, false); text(result, "summary", 10000, true); break;
            case "cover": {
                String url = text(result, "url", 1_400_000, false);
                if (!(url.startsWith("/") && !url.startsWith("//")) && !url.startsWith("https://")
                        && !url.startsWith("http://") && !url.matches("(?s)data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=\\s]+")) throw invalid();
                break;
            }
            default:
                text(result, "summary", 10000, false);
                if (Set.of("score", "publishCheck", "seo").contains(key)) score(result);
                JsonElement items = result.get("items");
                if (items == null || !items.isJsonArray() || items.getAsJsonArray().size() > 100) throw invalid();
                for (JsonElement item : items.getAsJsonArray()) {
                    if (!item.isJsonObject()) throw invalid();
                    JsonObject entry = item.getAsJsonObject();
                    if (Set.of("score", "publishCheck").contains(key)) score(entry);
                    List<String> fields;
                    switch (key) {
                        case "proofread": fields = List.of("original", "issue", "suggestion"); break;
                        case "questions": fields = List.of("question", "reason", "suggestion"); break;
                        case "seo": case "structure": fields = List.of("name", "status", "suggestion"); break;
                        default: fields = List.of("name", "suggestion");
                    }
                    for (String field : fields) text(entry, field, 10000, true);
                }
        }
    }
    private static void score(JsonObject object) {
        JsonElement value = object.get("score");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !Double.isFinite(value.getAsDouble()) || value.getAsDouble() < 0 || value.getAsDouble() > 100) throw invalid();
    }
    private static void strings(JsonObject object, String field, int max, int length) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().isEmpty() || value.getAsJsonArray().size() > max) throw invalid();
        for (JsonElement item : value.getAsJsonArray()) {
            JsonObject wrapper = new JsonObject(); wrapper.add(field, item); text(wrapper, field, length, false);
        }
    }
    private static String text(JsonObject object, String field, int max, boolean empty) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid();
        String text = value.getAsString();
        if (text.length() > max || !empty && text.isBlank()) throw invalid();
        return text;
    }
    private static AIResponseException invalid() { return new AIResponseException("Invalid writing skill result"); }
}
