import type { AIContent } from "@zrlog/editor/core/ai/AIContentItem";
import type { ToolAwareAIContent } from "../articleEdit/article-ai-assistant/article-ai-assistant.types";

/** A streaming reply and a separately synchronized task result must preserve each other. */
export const mergeTaskFeedback = (
    messages: AIContent[] | undefined,
    incoming: AIContent[] | undefined
): AIContent[] => {
    const result = [...(messages || [])] as ToolAwareAIContent[];
    for (const message of incoming || []) {
        const feedback = message as ToolAwareAIContent;
        if (!feedback.taskResult || !feedback.messageId) continue;
        const index = result.findIndex((entry) => entry.messageId === feedback.messageId);
        if (index < 0) result.push(feedback);
        else if ((result[index].taskResult?.version ?? -1) <= feedback.taskResult.version) result[index] = feedback;
    }
    return result.sort((a, b) => (a.createdAt ?? Number.MAX_SAFE_INTEGER) - (b.createdAt ?? Number.MAX_SAFE_INTEGER));
};
