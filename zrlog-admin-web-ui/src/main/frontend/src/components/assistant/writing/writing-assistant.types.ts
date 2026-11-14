import type { ReactNode } from "react";

export type WritingSkill =
    | "rewrite"
    | "title"
    | "alias"
    | "digest"
    | "tags"
    | "score"
    | "publishCheck"
    | "seo"
    | "proofread"
    | "structure"
    | "questions"
    | "cover";

export type ArticleScoreItem = {
    name: string;
    score: number;
    suggestion: string;
};

export type ArticleScoreResult = {
    score: number;
    summary: string;
    items: ArticleScoreItem[];
};

export type ArticleSeoItem = {
    name: string;
    status: "good" | "warning" | "bad" | string;
    suggestion: string;
};

export type ArticleSeoResult = {
    score: number;
    summary: string;
    items: ArticleSeoItem[];
};

export type ArticleProofreadItem = {
    original: string;
    issue: string;
    suggestion: string;
};

export type ArticleProofreadResult = {
    summary: string;
    items: ArticleProofreadItem[];
};

export type ArticleStructureItem = {
    name: string;
    status: "good" | "warning" | "bad" | string;
    suggestion: string;
};

export type ArticleStructureResult = {
    summary: string;
    items: ArticleStructureItem[];
};

export type ArticleReaderQuestionItem = {
    question: string;
    reason: string;
    suggestion: string;
};

export type ArticleReaderQuestionsResult = {
    summary: string;
    items: ArticleReaderQuestionItem[];
};

export type WritingSkillPayload =
    | {
          tool: "rewrite";
          payload: { summary?: string; markdown?: string; discarded?: boolean };
      }
    | {
          tool: "title";
          payload: { titles?: string[]; selectedTitle?: string };
      }
    | {
          tool: "alias";
          payload: { alias?: string };
      }
    | {
          tool: "digest";
          payload: { digest?: string };
      }
    | {
          tool: "tags";
          payload: { tags?: string[] };
      }
    | {
          tool: "score";
          payload: Partial<ArticleScoreResult>;
      }
    | {
          tool: "publishCheck";
          payload: Partial<ArticleScoreResult>;
      }
    | {
          tool: "seo";
          payload: Partial<ArticleSeoResult>;
      }
    | {
          tool: "proofread";
          payload: Partial<ArticleProofreadResult>;
      }
    | {
          tool: "structure";
          payload: Partial<ArticleStructureResult>;
      }
    | {
          tool: "questions";
          payload: Partial<ArticleReaderQuestionsResult>;
      }
    | {
          tool: "cover";
          payload: { url?: string };
      };

export type EditorSnapshot = {
    editorContext: {
        title: string;
        alias: string;
        markdown: string;
        digest: string;
        keywords: string;
        thumbnail: string;
        selectedText: string;
    };
    contextRevision: string;
};

export type ArticleContextMeta = {
    title?: string;
    articleVersion?: number;
    markdownLength?: number;
    createdAt?: number;
};

export type WritingSkillButton = {
    key: WritingSkill;
    command: string;
    icon: ReactNode;
    label: string;
    prompt: string;
    group: "ideation" | "editing" | "publishCheck";
    disabled?: boolean;
    disabledReason?: string;
};

export type WritingSkillGroup = {
    key: WritingSkillButton["group"];
    label: string;
};

export const writingSkills: WritingSkill[] = [
    "rewrite",
    "title",
    "alias",
    "digest",
    "tags",
    "score",
    "publishCheck",
    "seo",
    "proofread",
    "structure",
    "questions",
    "cover",
];

export const isWritingSkill = (tool: unknown): tool is WritingSkill => writingSkills.includes(tool as WritingSkill);
export type ArticleUpdatedEvent = { articleId: number; version: number; created?: boolean };
