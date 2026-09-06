import { act, SetStateAction } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { AIContent } from "@editor/dist/ai/AIContentItem";
import { postRefreshCacheSse, RefreshCacheSseOptions, SseEvent } from "../../utils/sse-utils";
import { ArticleEntry, PublishStatusPopoverState } from "./index.types";
import { ToolAwareAIContent } from "./article-ai-assistant/article-ai-assistant.types";
import useTransparentPublish from "./use-transparent-publish";

let mockSupportSse = true;

jest.mock("../../utils/constants", () => ({
    getRes: () => ({
        articleEdit: {
            actions: { release: "Publish" },
            publishCheck: { failed: "Check failed", finished: "Check finished", running: "Checking" },
            saveFailed: "Publish failed",
        },
        backgroundTask: { title: "Tasks" },
        staticSite: {
            publishComplete: "Published",
            publishStart: "Publishing",
            syncFailed: "Sync failed",
        },
        supportSse: mockSupportSse,
    }),
}));

jest.mock("../../utils/sse-utils", () => ({
    getStaticProgressText: () => "Syncing 1/2",
    postRefreshCacheSse: require("@jest/globals").jest.fn(),
}));

const reactActEnvironment = globalThis as typeof globalThis & {
    IS_REACT_ACT_ENVIRONMENT?: boolean;
};

const postPublish = jest.mocked(postRefreshCacheSse);

describe("useTransparentPublish", () => {
    let container: HTMLDivElement;
    let root: Root;
    let publish: ReturnType<typeof useTransparentPublish>;
    let publishStatus: PublishStatusPopoverState;
    let aiMessages: AIContent[];
    let aiMessagesArticleId: number | undefined;

    const article: ArticleEntry = {
        rubbish: false,
        title: "Article",
        typeId: 1,
        version: 0,
    };
    const articleResponse = {
        error: 0,
        message: "Article published",
        data: {
            article: {
                logId: 42,
                previewUrl: "//blog.example.com/article?v=1",
            },
        },
    };

    const updatePublishStatus = (
        action: PublishStatusPopoverState | ((previousState: PublishStatusPopoverState) => PublishStatusPopoverState)
    ) => {
        publishStatus = typeof action === "function" ? action(publishStatus) : action;
    };

    const Harness = () => {
        publish = useTransparentPublish({
            messageApi: {} as never,
            onAiMessagesChange: (action: SetStateAction<AIContent[]>, articleId?: number) => {
                aiMessages = typeof action === "function" ? action(aiMessages) : action;
                aiMessagesArticleId = articleId;
            },
            updatePublishStatus,
        });
        return null;
    };

    const publishWithEvents = async (events: SseEvent[]) => {
        postPublish.mockImplementationOnce(async (_uri, options: RefreshCacheSseOptions<unknown> = {}) => {
            events.forEach((event) => options.onEvent?.(event));
            options.onResponse?.(articleResponse);
            return articleResponse;
        });
        let response: unknown;
        await act(async () => {
            response = await publish("/api/admin/article/create", article);
        });
        return response;
    };

    beforeEach(() => {
        mockSupportSse = true;
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = true;
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
        publishStatus = {
            open: false,
            visible: false,
            publishState: "idle",
            staticStatus: "idle",
            checkStatus: "idle",
        };
        aiMessages = [];
        aiMessagesArticleId = undefined;
        postPublish.mockReset();
        act(() => {
            root.render(<Harness />);
        });
    });

    afterEach(() => {
        act(() => {
            root.unmount();
        });
        container.remove();
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = false;
    });

    it("records a successful static sync and the public article URL", async () => {
        const response = await publishWithEvents([
            { event: "publish-start", data: { message: "Publishing" } },
            { event: "article", data: articleResponse },
            { event: "static-sync-start", data: { siteTypes: ["BLOG"] } },
            { event: "static-sync-complete", data: { siteTypes: ["BLOG"] } },
            { event: "publish-complete", data: { message: "Article published" } },
        ]);

        expect(response).toBe(articleResponse);
        expect(publishStatus).toMatchObject({
            publishState: "success",
            publishText: "Article published",
            publicUrl: "//blog.example.com/article?v=1",
            staticStatus: "success",
            staticError: undefined,
        });
    });

    it("keeps the article published when static sync fails", async () => {
        await publishWithEvents([
            { event: "publish-start", data: { message: "Publishing" } },
            { event: "article", data: articleResponse },
            { event: "publish-check-start", data: {} },
            { event: "static-sync-start", data: { siteTypes: ["BLOG"] } },
            { event: "static-error", data: { message: "CDN upload failed" } },
        ]);

        expect(publishStatus).toMatchObject({
            publishState: "success",
            publicUrl: "//blog.example.com/article?v=1",
            staticStatus: "failed",
            staticError: "CDN upload failed",
            checkStatus: "idle",
        });
        expect(publishStatus.publishError).toBeUndefined();
    });

    it("records that static sync is not required", async () => {
        await publishWithEvents([
            { event: "publish-start", data: { message: "Publishing" } },
            { event: "article", data: articleResponse },
            { event: "static-sync-skipped", data: { siteTypes: ["BLOG"] } },
            { event: "publish-complete", data: { message: "Article published" } },
        ]);

        expect(publishStatus).toMatchObject({
            publishState: "success",
            publicUrl: "//blog.example.com/article?v=1",
            staticStatus: "not-required",
            staticError: undefined,
        });
    });

    it("appends publish-check messages to the latest state and ignores duplicate message ids", async () => {
        const existingResult = {
            role: "assistant",
            content: "Existing check",
            messageId: "check-existing",
        } as ToolAwareAIContent;
        const messageAddedAfterPublishStarted = {
            role: "user",
            content: "A later question",
            messageId: "later-user",
        } as ToolAwareAIContent;
        const newResult = {
            role: "assistant",
            content: "New check",
            messageId: "check-new",
        } as ToolAwareAIContent;
        aiMessages = [existingResult, messageAddedAfterPublishStarted];

        await publishWithEvents([
            { event: "article", data: articleResponse },
            {
                event: "publish-check-complete",
                data: {
                    aiMessages: [{ ...existingResult }, newResult],
                    toolPayload: { tool: "publishCheck", payload: { score: 90 } },
                },
            },
        ]);

        expect(aiMessages.map((message) => (message as ToolAwareAIContent).messageId)).toEqual([
            "check-existing",
            "later-user",
            "check-new",
        ]);
        expect(aiMessagesArticleId).toBe(42);
        expect(publishStatus).toMatchObject({
            checkStatus: "success",
            checkPayload: { tool: "publishCheck", payload: { score: 90 } },
        });
    });

    it("marks an interrupted background stream without reverting the published article", async () => {
        await publishWithEvents([
            { event: "publish-start", data: { message: "Publishing" } },
            { event: "article", data: articleResponse },
            { event: "publish-check-start", data: {} },
            { event: "static-sync-start", data: { siteTypes: ["BLOG"] } },
        ]);

        const options = postPublish.mock.calls[0][1];
        act(() => {
            options?.onBackgroundError?.(new Error("connection closed"));
        });

        expect(options?.requiredCompletionEvent).toBe("publish-complete");
        expect(publishStatus).toMatchObject({
            publishState: "success",
            publicUrl: "//blog.example.com/article?v=1",
            staticStatus: "failed",
            staticError: "connection closed",
            checkStatus: "idle",
        });
        expect(publishStatus.publishError).toBeUndefined();
    });

    it("records a failure before the article is persisted", async () => {
        postPublish.mockRejectedValueOnce(new Error("database unavailable"));

        await act(async () => {
            await expect(publish("/api/admin/article/create", article)).rejects.toThrow("database unavailable");
        });

        expect(publishStatus).toMatchObject({
            publishState: "failed",
            publishError: "database unavailable",
            staticStatus: "idle",
        });
    });

    it("uses a successful JSON fallback response", async () => {
        mockSupportSse = false;
        postPublish.mockResolvedValueOnce(articleResponse);

        let response: unknown;
        await act(async () => {
            response = await publish("/api/admin/article/create", article);
        });

        expect(response).toBe(articleResponse);
        expect(postPublish).toHaveBeenCalledWith(
            "/api/admin/article/create",
            expect.objectContaining({
                body: expect.objectContaining({ transparentPublish: false }),
            })
        );
        expect(publishStatus).toMatchObject({
            publishState: "success",
            publicUrl: "//blog.example.com/article?v=1",
            staticStatus: "idle",
        });
    });

    it("records a JSON fallback business error", async () => {
        mockSupportSse = false;
        postPublish.mockResolvedValueOnce({ error: 9026, message: "Title is required" });

        await act(async () => {
            await publish("/api/admin/article/create", article);
        });

        expect(publishStatus).toMatchObject({
            publishState: "failed",
            publishError: "Title is required",
            staticStatus: "idle",
        });
    });
});
