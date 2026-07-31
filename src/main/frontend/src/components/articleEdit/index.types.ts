import {AdminCommonProps, AIProviderType} from "../../type";
import {AIContent} from "@editor/dist/ai/AIContentItem";
import {AssistantToolPayload} from "./article-ai-assistant/article-ai-assistant.types";

export type ArticleEntry = ChangedContent &
    ThumbnailChanged &
    TitleChanged &
    AliasChanged &
    DigestChanged &
    KeywordsChanged &
    RecommendedChanged &
    PrivacyChanged &
    CanCommentChanged &
    TypeChanged & {
        rubbish: boolean;
        logId?: number;
        lastUpdateDate?: number;
        version: number;
        previewUrl?: string;
        socialPreview?: SocialPreview;
        transparentPublish?: boolean;
        editorType?: string;
    };

export type SocialPreview = {
    type: string;
    title: string;
    description: string;
    url: string;
    siteName: string;
    image: string;
    twitterCard: string;
    author: string;
    publishedTime: string;
    modifiedTime: string;
    metaHtml: string;
};

export type ChangedContent = {
    content?: string;
    markdown?: string;
};

export type ThumbnailChanged = {
    thumbnail?: string;
};

export type TitleChanged = {
    title: string;
};

export type AliasChanged = {
    alias?: string;
};

export type DigestChanged = {
    digest?: string;
};

export type KeywordsChanged = {
    keywords?: string;
};

export type RecommendedChanged = {
    recommended?: boolean;
};

export type TypeChanged = {
    typeId?: number;
};

export type CanCommentChanged = {
    canComment?: boolean;
};
export type PrivacyChanged = {
    privacy?: boolean;
};

export type ArticleChangeableValue =
    | ArticleEntry
    | PrivacyChanged
    | CanCommentChanged
    | AliasChanged
    | TypeChanged
    | TitleChanged
    | KeywordsChanged
    | RecommendedChanged
    | ChangedContent
    | ThumbnailChanged
    | DigestChanged;

export type ArticleEditInfo = {
    tags: any[];
    types: any[];
    article: ArticleEntry;
    aiProvider: AIProviderType;
    aiModel?: string;
    aiConfigured?: boolean;
    aiMessages: AIContent[];
    linkPreviewEnabled?: boolean;
    publishCheckEnabled?: boolean;
    articleCoverAspectRatio?: string;
    articleEditAutoSaveInterval?: number;
};

export type FullScreenProps = {
    onExitFullScreen: () => void;
    onFullScreen: () => void;
    fullScreen: boolean;
};

export type ArticleEditProps = FullScreenProps & AdminCommonProps<ArticleEditInfo> & {};

export type ArticleEditState = {
    typeOptions: any[];
    tags: any[];
    aiProvider: AIProviderType;
    aiModel?: string;
    aiConfigured: boolean;
    aiMessages: AIContent[];
    linkPreviewEnabled: boolean;
    publishCheckEnabled: boolean;
    articleCoverAspectRatio: string;
    articleEditAutoSaveInterval: number;
    rubbish: boolean;
    editorVersion: number;
    contentSource: "server" | "localDraft" | "localEdit";
    contentSourceUpdatedAt?: number;
    contentConflict?: {
        source: "localDraft" | "localEdit";
        localArticle: ArticleEntry;
        localVersion: number;
        localUpdatedAt?: number;
        serverVersion: number;
    };
    article: ArticleEntry;
    saving: ArticleSavingState;
};

export type ArticleSavingState = {
    rubbishSaving: boolean;
    previewIng: boolean;
    autoSaving: boolean;
    releaseSaving: boolean;
};

export type PublishStatusPopoverState = {
    open: boolean;
    visible: boolean;
    updatedAt?: number;
    publishText?: string;
    publishError?: string;
    staticText?: string;
    checkStatus: "idle" | "running" | "success" | "error";
    checkError?: string;
    checkPayload?: Extract<AssistantToolPayload, { tool: "publishCheck" }>;
};

export type PublishCheckTarget = "title" | "alias" | "digest" | "tags" | "cover" | "markdown" | "settings";
