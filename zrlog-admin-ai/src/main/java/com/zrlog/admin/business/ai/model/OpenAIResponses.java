package com.zrlog.admin.business.ai.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.List;

/** Provider protocol types. Opaque reasoning items stay in the private run checkpoint. */
public final class OpenAIResponses {
    private OpenAIResponses() { }

    public static class Request {
        public String model;
        public List<Item> input;
        public boolean stream;
        public boolean store = false;
        public List<String> include = List.of("reasoning.encrypted_content");
        public Integer max_output_tokens;
        public Reasoning reasoning;
        public List<Tool> tools;
        public String tool_choice;
    }

    public static class Reasoning {
        public String summary = "auto";
    }

    public static class Tool {
        public String type = "function";
        public String name;
        public String description;
        public JsonElement parameters;
        // Existing tool schemas intentionally contain optional fields.
        public boolean strict = false;
    }

    public static class Item {
        public String type;
        public String id;
        public String role;
        public String status;
        public String phase;
        public List<Content> content;
        public List<Summary> summary;
        public String encrypted_content;
        public String call_id;
        public String name;
        public String arguments;
        public String output;
    }

    public static class Content {
        public String type;
        public String text;
        public String refusal;
        public JsonArray annotations;
    }

    public static class Summary {
        public String type;
        public String text;
    }

    public static class Response {
        public String status;
        public List<Item> output;
        public JsonElement error;
        public IncompleteDetails incomplete_details;
    }

    public static class IncompleteDetails {
        public String reason;
    }

    public static class StreamEvent {
        public String type;
        public String delta;
        public String item_id;
        public Integer output_index;
        public Integer content_index;
        public Integer summary_index;
        public Response response;
    }
}
