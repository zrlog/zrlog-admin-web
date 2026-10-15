import { getRes } from "../../../utils/constants";

export type AiMentionArticle = { id: number; title: string; url: string };
export type AiMention = { type: "article"; article: AiMentionArticle } | { type: "file"; file: File };
export type AiMentionInput = { type: "article"; articleId: number } | { type: "file"; fileIndex: number };

export const MAX_AI_MENTIONS = 8;

export const findAiMention = (input: string, caret: number) => {
    if (caret < 0 || caret > input.length) return undefined;
    const match = /(?:^|[\s([（【])@([^\s@]*)$/.exec(input.slice(0, caret));
    if (!match || match[1].length > 200) return undefined;
    return { start: caret - match[1].length - 1, end: caret, query: match[1] };
};

export const aiMentionLabel = (mention: AiMention) =>
    mention.type === "article" ? mention.article.title : mention.file.name;

export const sameAiMention = (left: AiMention, right: AiMention) =>
    left.type === "article" && right.type === "article"
        ? left.article.id === right.article.id
        : left.type === "file" && right.type === "file" && left.file === right.file;

export const encodeAiMentions = (mentions: AiMention[], files: File[]): AiMentionInput[] =>
    mentions.flatMap((mention): AiMentionInput[] => {
        if (mention.type === "article") return [{ type: "article", articleId: mention.article.id }];
        const fileIndex = files.indexOf(mention.file);
        return fileIndex < 0 ? [] : [{ type: "file", fileIndex }];
    });

// Keep readable references (including article URLs) in the existing conversation history.
// The request also carries typed IDs/attachment indexes; labels never determine what is read.
export const withAiMentionLabels = (message: string, mentions: AiMention[]) => {
    if (!mentions.length) return message;
    const labels = mentions.map((mention) => {
        const label = aiMentionLabel(mention).replace(/([\\`*_[\]<>])/g, "\\$1");
        if (mention.type === "file") return `- @${label}`;
        const url = mention.article.url.replace(/[<>\s]/g, encodeURIComponent);
        return `- [@${label}](<${url}>)`;
    });
    return `${message}\n\n${getRes().articleEdit.assistant.mentions.selected}\n${labels.join("\n")}`;
};
