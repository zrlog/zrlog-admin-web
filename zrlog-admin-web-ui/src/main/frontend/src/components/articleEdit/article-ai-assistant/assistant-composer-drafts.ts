import type { AssistantTool } from "./article-ai-assistant.types";
import type { AiMention } from "./article-ai-mentions";

// Local unsent input only. Never send another conversation's draft or attachments to a model.
export const assistantComposerDrafts = new Map<
    string,
    { input: string; files: File[]; selectedTool?: AssistantTool; mentions: AiMention[] }
>();

export const assistantComposerScope = (
    accountKey: string,
    context: { sessionId?: string; articleId?: number; taskId?: string }
) =>
    `${accountKey}/${
        context.sessionId
            ? `session/${context.sessionId}`
            : context.articleId !== undefined
            ? context.articleId || "draft"
            : context.taskId
            ? `task/${context.taskId}`
            : "general"
    }`;
