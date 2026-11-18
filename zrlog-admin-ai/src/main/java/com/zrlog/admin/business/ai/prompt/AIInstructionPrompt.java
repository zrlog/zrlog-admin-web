package com.zrlog.admin.business.ai.prompt;

import com.zrlog.admin.business.ai.exception.AIPromptResourceException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum AIInstructionPrompt {
    BLOG_ASSISTANT("blog-assistant"),
    BLOG_TOOLS_ENABLED("blog-tools-enabled"),
    BLOG_TOOLS_DISABLED("blog-tools-disabled"),
    WRITING_ASSISTANT("writing-assistant"),
    WRITING_INPUT_TOOL("writing-input-tool"),
    TASK_CONTEXT("assistant-task-context"),
    REFERENCE_CONTEXT("assistant-reference-context"),
    AUTOMATION_PROPOSAL("automation-proposal"),
    SCHEDULED_WRITING("scheduled-writing"),
    SCHEDULED_WRITING_DRAFTS_ALLOWED("scheduled-writing-drafts-allowed"),
    SCHEDULED_WRITING_DRAFTS_DISABLED("scheduled-writing-drafts-disabled");

    private final String resource;

    AIInstructionPrompt(String name) {
        this.resource = "/ai-prompts/" + name + ".md";
    }

    public String text() {
        try (var input = AIInstructionPrompt.class.getResourceAsStream(resource)) {
            if (input == null) throw new AIPromptResourceException(resource);
            // Resource lines wrap for review. The instructions are one paragraph on the wire.
            String text =
                    new String(input.readAllBytes(), StandardCharsets.UTF_8)
                            .lines()
                            .map(String::strip)
                            .filter(line -> !line.isEmpty())
                            .collect(Collectors.joining(" "));
            if (text.isEmpty()) throw new AIPromptResourceException(resource);
            return text;
        } catch (IOException error) {
            var failure = new AIPromptResourceException(resource);
            failure.initCause(error);
            throw failure;
        }
    }

    public static List<String> resources() {
        return Arrays.stream(values()).map(prompt -> prompt.resource).collect(Collectors.toList());
    }
}
