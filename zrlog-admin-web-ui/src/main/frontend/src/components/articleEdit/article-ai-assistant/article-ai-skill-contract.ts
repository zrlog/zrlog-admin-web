import { ArticleEntry } from "../index.types";
import { EditorSnapshot, ToolAwareAIContent } from "./article-ai-assistant.types";

const md5: (value: string) => string = require("md5");

// A content identity, not an authorization token. Selection is transient and does not change the article.
export const editorContextRevision = (context: EditorSnapshot["editorContext"]) =>
    md5(
        JSON.stringify([
            context.title,
            context.alias,
            context.markdown,
            context.digest,
            context.keywords,
            context.thumbnail,
        ])
    );

export const canApplySkillValues = (message: ToolAwareAIContent, revision: string, values: object) => {
    const contract = message.skillContract;
    if (!contract) return message.messageType !== "writingSkill";
    return (
        contract.version === 1 &&
        contract.contextRevision === revision &&
        Array.isArray(contract.applicableFields) &&
        Object.keys(values).every((field) => contract.applicableFields.includes(field))
    );
};

export const articleContextRevision = (article: ArticleEntry) =>
    editorContextRevision({
        title: article.title || "",
        alias: article.alias || "",
        markdown: article.markdown || "",
        digest: article.digest || "",
        keywords: article.keywords || "",
        thumbnail: article.thumbnail || "",
        selectedText: "",
    });

const skillRunId = (message: ToolAwareAIContent) => message.messageId?.match(/^(.+):skill:\d+:\d+$/)?.[1];

// Only explicit applications from the same run extend its original content baseline.
// Keep the server contract immutable, and never infer a run from the content hash alone.
export const createSkillApplicationSession = () => {
    let accepted: { runId: string; original: string; revision: string; articleId?: number } | undefined;
    return {
        getRevision: (message: ToolAwareAIContent, article: ArticleEntry) => {
            const revision = articleContextRevision(article);
            if (
                accepted &&
                (!accepted.articleId || accepted.articleId === article.logId) &&
                accepted.runId === skillRunId(message) &&
                accepted.original === message.skillContract?.contextRevision &&
                accepted.revision === revision
            ) {
                // A new draft may acquire its server ID while these results are being applied.
                accepted.articleId = article.logId;
                return accepted.original;
            }
            return revision;
        },
        record: (message: ToolAwareAIContent | undefined, article: ArticleEntry) => {
            const runId = message && skillRunId(message);
            accepted =
                runId && message?.skillContract
                    ? {
                          runId,
                          original: message.skillContract.contextRevision,
                          revision: articleContextRevision(article),
                          articleId: article.logId,
                      }
                    : undefined;
        },
    };
};
