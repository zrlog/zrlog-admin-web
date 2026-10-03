import useArticleFieldAi from "../use-article-field-ai";
import { articleContextRevision } from "./article-ai-skill-contract";
import { act, Children, isValidElement, ReactElement, ReactNode, useState } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { AIContent } from "@zrlog/editor/dist/ai/AIContentItem";
import { AIProviderType } from "../../../type";
import { ArticleChangeableValue, ArticleEditState } from "../index.types";
import { createDraftAiSaveGate, DraftAiSaveGate } from "../draft-ai-save-gate";
import { AssistantTool, ToolAwareAIContent } from "./article-ai-assistant.types";
import { useArticleAiAssistantConfig } from "./article-ai-assistant-button";

const mockMessageError = jest.fn(async (content?: unknown): Promise<void> => {
    void content;
});
const mockMessageSuccess = jest.fn(async (content?: unknown): Promise<void> => {
    void content;
});
const mockMessageWarning = jest.fn(async (content?: unknown): Promise<void> => {
    void content;
});

jest.mock("antd", () => {
    const NullComponent = () => null;
    return {
        Alert: NullComponent,
        App: {
            useApp: () => ({
                message: {
                    error: mockMessageError,
                    success: mockMessageSuccess,
                    warning: mockMessageWarning,
                },
            }),
        },
        Button: NullComponent,
        Collapse: NullComponent,
        Drawer: NullComponent,
        Grid: { useBreakpoint: () => ({ md: true, lg: true }) },
        Space: NullComponent,
        Tag: NullComponent,
        Typography: { Paragraph: NullComponent, Text: NullComponent },
    };
});

jest.mock("@ant-design/icons", () => ({ EyeOutlined: () => null }));
jest.mock("antd-style", () => ({
    useTheme: () => ({
        borderRadius: 6,
        borderRadiusLG: 6,
        colorBorderSecondary: "#ddd",
        colorFillQuaternary: "#f5f5f5",
        lineType: "solid",
        lineWidth: 1,
    }),
}));
jest.mock("@zrlog/editor/dist/ai/AIButton", () => ({
    __esModule: true,
    default: () => null,
    getAIButtonDrawerOpen: () => false,
}));
jest.mock("@zrlog/editor/dist/ai/AIIcon", () => ({ __esModule: true, default: () => null }));
jest.mock("@zrlog/editor/dist/editor/html-preview-panel", () => ({ __esModule: true, default: () => null }));
jest.mock("@zrlog/editor/dist/ai/AIDrawer", () => ({ resolveDrawerWidth: (width: unknown) => width }));
jest.mock("@zrlog/editor/dist/editor/utils/marked-utils", () => ({
    markdownToHtmlSyncWithCallback: (markdown: string) => markdown,
}));
jest.mock("../../../base/ConfigProviderApp", () => ({ getAppState: () => ({ dark: false }) }));
jest.mock("../../../common/ImageCropper", () => ({ __esModule: true, default: () => null }));
jest.mock("../../../utils/cache", () => ({
    addToCache: require("@jest/globals").jest.fn(),
    getCacheByKey: require("@jest/globals").jest.fn(),
}));
jest.mock("../../../utils/constants", () => ({
    formatLabelValue: (label: string, value: unknown) => `${label}: ${value}`,
    getLabelValueSeparator: () => ": ",
    getRealRouteUrl: (url: string) => url,
    getRes: () => ({
        articleEdit: {
            interaction: {
                staleResult: "Stale result",
                invalidResult: "Invalid result",
                pendingResult: "Pending result",
            },
            knowledge: {
                thinking: "Thinking",
                permission: "Permissions changed",
                requestFailed: "Request failed",
                saveFailed: "Save failed",
            },
            assistant: {
                articleContextPreviewTitle: "Article context",
                saveInProgress: "Save in progress",
            },
        },
        error: {
            requestError: "Request error",
            unknown: "Unknown error",
        },
        websiteAi: { label: "AI" },
    }),
    tryAppendBackendServerUrl: (url: string) => url,
}));
jest.mock("../../../utils/helpers", () => ({ getEditorUser: () => ({}) }));
jest.mock("../../../utils/crop-image-url", () => ({ resolveBackendCropImageUrl: (url: string) => url }));
jest.mock("../../article/article-preview-snapshot", () => ({ __esModule: true, default: () => null }));
jest.mock("../cover-aspect-ratio", () => ({ parseCoverAspectRatio: () => 16 / 9 }));
jest.mock("../markdown-reference-utils", () => ({
    collectMarkdownReferenceSummary: () => ({
        imageReferenceCount: 0,
        imageReferences: [],
        linkReferenceCount: 0,
        linkReferences: [],
        externalLinkCount: 0,
        externalLinks: [],
    }),
}));
jest.mock("../shortcut-utils", () => ({
    getShortcutTitle: (label: string) => label,
    isTouchLikeDevice: () => false,
}));
jest.mock("./article-ai-assistant-skill-content", () => ({ __esModule: true, default: () => null }));
jest.mock("./article-ai-assistant-drawer", () => ({ __esModule: true, default: () => null }));
jest.mock("./tool/article-ai-assistant-tool-content", () => ({ __esModule: true, default: () => null }));
jest.mock("./tool/article-ai-assistant-tools", () => ({ getAssistantToolLabel: (tool: string) => tool }));

type AssistantConfig = ReturnType<typeof useArticleAiAssistantConfig>;
type FooterActions = {
    disabled: boolean;
    busy: boolean;
    onStop: () => void;
    queuedMessages: { id: number; input: string }[];
    queuePaused: boolean;
    onResumeQueue: () => void;
    onRemoveQueued: (id: number) => void;
    onClearAiMessages: () => void;
    onSubmit: (message: string, tool?: AssistantTool) => void;
};

type ToolContentActions = {
    applyDisabled: boolean;
    onApplyValues: (values: ArticleChangeableValue) => void;
    onApplyGeneratedCover: (cover: { dataUrl: string }) => Promise<string | undefined>;
    onCropCover: (url: string) => void;
    onUpdateToolPayload: (
        messageIndex: number,
        payload: { tool: "cover"; payload: { url: string } },
        persist?: boolean
    ) => void;
};

type CropperActions = {
    onOk: (dataUrl: string) => Promise<void>;
};

type Deferred<T> = {
    promise: Promise<T>;
    resolve: (value: T) => void;
    reject: (reason: unknown) => void;
};

type AxiosPostConfig = {
    onDownloadProgress?: (event: unknown) => void;
    signal?: AbortSignal;
};

type AxiosPost = (url?: string, body?: unknown, config?: AxiosPostConfig) => Promise<any>;

const deferred = <T,>(): Deferred<T> => {
    let resolve!: (value: T) => void;
    let reject!: (reason: unknown) => void;
    const promise = new Promise<T>((resolvePromise, rejectPromise) => {
        resolve = resolvePromise;
        reject = rejectPromise;
    });
    return { promise, resolve, reject };
};

const createState = (logId?: number, aiMessages: AIContent[] = []): ArticleEditState => ({
    typeOptions: [{ value: 1, label: "General" }],
    tags: [],
    aiProvider: AIProviderType.OPEN_AI,
    aiModel: "test-model",
    aiConfigured: true,
    aiMessages,
    linkPreviewEnabled: false,
    publishCheckEnabled: false,
    articleCoverAspectRatio: "16:9",
    articleEditAutoSaveInterval: 5,
    rubbish: true,
    editorVersion: 1,
    contentSource: "server",
    article: {
        logId,
        title: "Draft title",
        typeId: 1,
        markdown: "Draft body",
        content: "<p>Draft body</p>",
        rubbish: true,
        version: logId ? 1 : -1,
    },
    saving: {
        rubbishSaving: false,
        previewIng: false,
        autoSaving: false,
        releaseSaving: false,
    },
});

const reactActEnvironment = globalThis as typeof globalThis & {
    IS_REACT_ACT_ENVIRONMENT?: boolean;
};

describe("useArticleAiAssistantConfig draft request gate", () => {
    const mountedRoots: Array<{ root: Root; container: HTMLDivElement }> = [];

    const mountHook = (
        gate: DraftAiSaveGate,
        post: AxiosPost,
        onAiMessagesChange = jest.fn((messages?: AIContent[], articleId?: number) => {
            void messages;
            void articleId;
        }),
        initialLogId?: number,
        initialMessages: AIContent[] = [],
        followMessages = false,
        onApplyGeneratedCover?: (cover: { dataUrl: string }) => Promise<string | undefined>
    ) => {
        let data = createState(initialLogId, initialMessages);
        let config!: AssistantConfig;
        let secondConfig!: AssistantConfig;
        const onApplied = jest.fn();
        const container = document.createElement("div");
        document.body.appendChild(container);
        const root = createRoot(container);
        mountedRoots.push({ root, container });

        const Harness = () => {
            const [, redraw] = useState(0);
            const fieldAi = useArticleFieldAi({
                getCurrentArticle: () => data.article,
                onValuesChange: (values) => {
                    const article = { ...data.article, ...values };
                    if (JSON.stringify(article) === JSON.stringify(data.article)) return;
                    data = { ...data, article };
                    redraw((revision) => revision + 1);
                    return { article };
                },
                onApplied,
            });
            const props = {
                data,
                draftAiSaveGate: gate,
                offline: false,
                axiosInstance: { get: jest.fn(), post } as never,
                onAiMessagesChange: (messages: AIContent[], articleId?: number) => {
                    onAiMessagesChange(messages, articleId);
                    if (followMessages && articleId === (data.article.logId || 0)) {
                        data = { ...data, aiMessages: messages };
                        redraw((revision) => revision + 1);
                    }
                },
                onApplyValues: fieldAi.applyGeneratedValues,
                getSkillContextRevision: fieldAi.getSkillContextRevision,
                onApplyGeneratedCover,
            };
            config = useArticleAiAssistantConfig(props);
            secondConfig = useArticleAiAssistantConfig(props);
            return null;
        };

        const render = () => {
            act(() => root.render(<Harness />));
        };
        render();

        return {
            getConfig: () => config,
            getFooter: () => ({
                ...(config.renderFooter() as ReactElement<FooterActions>).props,
                onClearAiMessages: () => {
                    void config.conversationActions.onClear();
                },
            }),
            getToolContent: (content: AIContent, index = 0, second = false) => {
                const findTool = (node: ReactNode): ReactElement<ToolContentActions> | undefined => {
                    if (!isValidElement(node)) return;
                    const element = node as ReactElement<Partial<ToolContentActions> & { children?: ReactNode }>;
                    if (element.props.onApplyValues) return element as ReactElement<ToolContentActions>;
                    return Children.toArray(element.props.children).map(findTool).find(Boolean);
                };
                return findTool(
                    (second ? secondConfig : config).renderMessage({
                        content,
                        index,
                        defaultNode: null,
                    } as never) as ReactNode
                )!.props;
            },
            getArticle: () => data.article,
            onApplied,
            edit: (values: ArticleChangeableValue) => {
                data = { ...data, article: { ...data.article, ...values } };
                render();
            },
            getCropper: () => {
                const overlay = config.overlays as ReactElement<{ children: ReactElement<CropperActions> }>;
                return overlay.props.children.props;
            },
            onAiMessagesChange,
            rerender: (logId?: number, aiMessages = data.aiMessages) => {
                data = createState(logId, aiMessages);
                render();
            },
        };
    };

    const skillMessage = (field: "title" | "digest" | "keywords" | "thumbnail", run = "run-1"): ToolAwareAIContent => ({
        role: "assistant",
        content: "",
        thinking: false,
        messageId: `${run}:skill:1:${field === "title" ? 1 : field === "digest" ? 2 : field === "keywords" ? 3 : 4}`,
        messageType: "writingSkill",
        skillContract: {
            version: 1,
            contextRevision: articleContextRevision(createState(7).article),
            applicableFields: [field],
        },
        tool: field === "keywords" ? "tags" : field === "thumbnail" ? "cover" : field,
        payload:
            field === "title"
                ? { titles: ["AI title"] }
                : field === "digest"
                ? { digest: "AI digest" }
                : field === "keywords"
                ? { tags: ["AI", "writing"] }
                : { url: "data:image/png;base64,cover" },
    });

    it("keeps sibling results applicable across both assistant views and rapid clicks", () => {
        const hook = mountHook(
            createDraftAiSaveGate(),
            jest.fn(async () => undefined),
            undefined,
            7
        );
        const title = skillMessage("title");
        const digest = skillMessage("digest");
        const tags = skillMessage("keywords");
        const titleActions = hook.getToolContent(title);
        const digestActions = hook.getToolContent(digest, 1, true);
        act(() => {
            titleActions.onApplyValues({ title: "AI title" });
            digestActions.onApplyValues({ digest: "AI digest" });
        });
        expect(hook.getToolContent(tags).applyDisabled).toBe(false);
        act(() => hook.getToolContent(tags).onApplyValues({ keywords: "AI,writing" }));
        expect(hook.getArticle()).toMatchObject({ title: "AI title", digest: "AI digest", keywords: "AI,writing" });
        expect(hook.onApplied).toHaveBeenCalledTimes(3);
        act(() => hook.getToolContent(tags).onApplyValues({ keywords: "AI,writing" }));
        expect(hook.onApplied).toHaveBeenCalledTimes(3);
        expect(title.skillContract!.contextRevision).toBe(articleContextRevision(createState(7).article));
    });

    it("still rejects another run, forbidden fields, and manual edits after accepting a sibling", () => {
        const hook = mountHook(
            createDraftAiSaveGate(),
            jest.fn(async () => undefined),
            undefined,
            7
        );
        const title = skillMessage("title");
        const digest = skillMessage("digest");
        const staleClick = hook.getToolContent(digest).onApplyValues;
        act(() => hook.getToolContent(title).onApplyValues({ markdown: "Forbidden body" }));
        expect(hook.getArticle().markdown).toBe("Draft body");
        act(() => hook.getToolContent(title).onApplyValues({ title: "AI title" }));
        expect(hook.getToolContent(skillMessage("digest", "other-run")).applyDisabled).toBe(true);
        hook.edit({ markdown: "Manual body" });
        expect(hook.getToolContent(digest).applyDisabled).toBe(true);
        act(() => staleClick({ digest: "AI digest" }));
        expect(hook.getArticle().digest).toBeUndefined();
        expect(hook.onApplied).toHaveBeenCalledTimes(1);
    });

    it("allows a cover to join the same run but rechecks edits and article scope after upload", async () => {
        const upload = deferred<string>();
        const prepareCover = jest.fn(async () => upload.promise);
        const hook = mountHook(
            createDraftAiSaveGate(),
            jest.fn(async () => undefined),
            undefined,
            7,
            [],
            false,
            prepareCover
        );
        act(() => hook.getToolContent(skillMessage("title")).onApplyValues({ title: "AI title" }));
        const cover = skillMessage("thumbnail");
        let prepared!: Promise<string | undefined>;
        act(() => {
            prepared = hook.getToolContent(cover).onApplyGeneratedCover({ dataUrl: "cover" });
        });
        await act(async () => {
            upload.resolve("/attached/cover.png");
            expect(await prepared).toBe("/attached/cover.png");
        });
        act(() => hook.getToolContent(cover).onApplyValues({ thumbnail: "/attached/cover.png" }));
        expect(hook.getToolContent(skillMessage("digest")).applyDisabled).toBe(false);
        const nextUpload = deferred<string>();
        prepareCover.mockImplementationOnce(async () => nextUpload.promise);
        act(() => {
            prepared = hook.getToolContent(cover).onApplyGeneratedCover({ dataUrl: "cover" });
        });
        hook.edit({ title: "Manual title" });
        await act(async () => {
            nextUpload.resolve("/attached/stale.png");
            expect(await prepared).toBeUndefined();
        });
        expect(hook.getArticle().thumbnail).toBe("/attached/cover.png");

        hook.rerender(7);
        const switchedUpload = deferred<string>();
        prepareCover.mockImplementationOnce(async () => switchedUpload.promise);
        const oldActions = hook.getToolContent(cover);
        act(() => {
            prepared = oldActions.onApplyGeneratedCover({ dataUrl: "cover" });
        });
        hook.rerender(8);
        await act(async () => {
            switchedUpload.resolve("/attached/other.png");
            expect(await prepared).toBeUndefined();
        });
        act(() => oldActions.onApplyValues({ thumbnail: "/attached/other.png" }));
        expect(hook.getArticle().thumbnail).toBeUndefined();
    });

    const flushRequest = async () => {
        await Promise.resolve();
        await Promise.resolve();
        await Promise.resolve();
    };

    beforeEach(() => {
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = true;
        mockMessageError.mockImplementation(async () => undefined);
        mockMessageSuccess.mockImplementation(async () => undefined);
        mockMessageWarning.mockImplementation(async () => undefined);
        mockMessageError.mockClear();
        mockMessageSuccess.mockClear();
        mockMessageWarning.mockClear();
    });

    afterEach(() => {
        mountedRoots.splice(0).forEach(({ root, container }) => {
            act(() => root.unmount());
            container.remove();
        });
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = false;
    });

    it("queues chat and skills while awaiting confirmation and keeps input and clear available", async () => {
        const gate = createDraftAiSaveGate();
        const post = jest.fn(async () => ({ data: "" }));
        const pending = {
            role: "assistant" as const,
            thinking: false,
            content: "",
            messageType: "knowledge",
            run: { runId: "run", articleId: 7, input: "Update", status: "awaiting_approval" },
        };
        const mounted = mountHook(gate, post, undefined, 7, [pending]);
        expect(mounted.getFooter().disabled).toBe(false);
        expect(mounted.getConfig().conversationActions.disabled).toBe(false);
        await act(async () => {
            mounted.getFooter().onSubmit("Another question");
            mounted.getFooter().onSubmit("Rewrite", "rewrite");
            await flushRequest();
        });
        expect(post).not.toHaveBeenCalled();
        expect(mounted.getFooter().queuedMessages.map((entry) => entry.input)).toEqual(["Another question", "Rewrite"]);
        const executing = { ...pending, run: { ...pending.run, status: "executing" } };
        mounted.rerender(7, [executing]);
        expect(mounted.getConfig().conversationActions.disabled).toBe(true);
    });
    it("blocks ordinary draft creation between tool approvals until the saved article is adopted", () => {
        const gate = createDraftAiSaveGate();
        const pending = {
            role: "assistant" as const,
            thinking: false,
            content: "",
            messageType: "knowledge",
            run: { runId: "run", articleId: 0, input: "Write", status: "awaiting_approval" },
        };
        const mounted = mountHook(
            gate,
            jest.fn(async () => ({ data: "" })),
            undefined,
            undefined,
            [pending]
        );
        expect(gate.getPendingAiCount()).toBe(2);
        expect(gate.tryBeginCreate()).toBeUndefined();
        mounted.rerender(18, []);
        expect(gate.getPendingAiCount()).toBe(0);
        const release = gate.tryBeginCreate();
        expect(release).toBeDefined();
        release?.();
    });

    it("restores saved ordinary chat through the same article state as writing skills", async () => {
        const gate = createDraftAiSaveGate();
        const saved = [
            { role: "user" as const, content: "Find a related article", messageType: "knowledge", messageId: "q1" },
            { role: "assistant" as const, content: "Source based answer", messageType: "knowledge", messageId: "a1" },
        ];
        const post = jest.fn(
            async (): Promise<any> => ({
                data: `data: ${JSON.stringify({
                    type: "answer",
                    content: "Source based answer",
                    messages: saved,
                })}\n\ndata: {"type":"done"}\n\n`,
            })
        );
        const mounted = mountHook(gate, post);
        await act(async () => {
            mounted.getFooter().onSubmit("Find a related article");
            await flushRequest();
        });
        expect(post).toHaveBeenCalledWith(
            "/api/admin/article/ai",
            expect.objectContaining({
                input: "Find a related article",
                articleId: 0,
                editorContext: expect.objectContaining({ markdown: "Draft body" }),
                contextRevision: expect.any(String),
            }),
            expect.anything()
        );
        expect(mounted.onAiMessagesChange).toHaveBeenLastCalledWith(
            saved.map((entry) => ({ ...entry, thinking: false })),
            0
        );
        act(() =>
            mounted.rerender(
                7,
                saved.map((entry) => ({ ...entry, thinking: false }))
            )
        );
        expect(mounted.getConfig().messages.map((m) => m.content)).toEqual([
            "Find a related article",
            "Source based answer",
        ]);
        expect(post).toHaveBeenCalledTimes(1);
        expect(gate.getPendingAiCount()).toBe(0);
    });

    const answer = (question: string, reply: string) => ({
        data: `data: ${JSON.stringify({
            type: "answer",
            messages: [
                { role: "user", content: question, messageType: "knowledge", messageId: `${question}:user` },
                { role: "assistant", content: reply, messageType: "knowledge", messageId: `${question}:assistant` },
            ],
        })}\n\ndata: {"type":"done"}\n\n`,
    });

    it("serializes rapid submissions and uses completed history for each queued turn", async () => {
        const first = deferred<any>(),
            second = deferred<any>();
        const post = jest
            .fn<ReturnType<AxiosPost>, Parameters<AxiosPost>>()
            .mockReturnValueOnce(first.promise)
            .mockReturnValueOnce(second.promise);
        const mounted = mountHook(createDraftAiSaveGate(), post, undefined, 7, [], true);
        act(() => {
            mounted.getFooter().onSubmit("First");
            mounted.getFooter().onSubmit("Second");
            mounted.getFooter().onSubmit("Remove me");
        });
        expect(post).toHaveBeenCalledTimes(1);
        expect(mounted.getFooter().disabled).toBe(false);
        expect(mounted.getFooter().busy).toBe(true);
        act(() => mounted.getFooter().onRemoveQueued(mounted.getFooter().queuedMessages[1].id));
        await act(async () => {
            first.resolve(answer("First", "First answer"));
            await flushRequest();
        });
        expect(post).toHaveBeenCalledTimes(2);
        expect(post.mock.calls[1][1]).toMatchObject({
            input: "Second",
            articleId: 7,
            editorContext: { markdown: "Draft body" },
        });
        expect(mounted.getConfig().messages.map((m) => m.content)).toEqual(["First", "First answer", "Second", ""]);
        await act(async () => {
            second.resolve(answer("Second", "Second answer"));
            await flushRequest();
        });
        expect(mounted.getConfig().messages.map((m) => m.content)).toEqual([
            "First",
            "First answer",
            "Second",
            "Second answer",
        ]);
        expect(mounted.getFooter().queuedMessages).toHaveLength(0);
    });

    it("stops the stream, preserves queued input, and requires an explicit resume", async () => {
        const request = deferred<any>();
        const post = jest
            .fn<ReturnType<AxiosPost>, Parameters<AxiosPost>>()
            .mockImplementationOnce((_url, _body, config) => {
                config?.signal?.addEventListener("abort", () => request.reject(new Error("cancelled")));
                return request.promise;
            })
            .mockResolvedValueOnce(answer("Next", "Next answer"));
        const mounted = mountHook(createDraftAiSaveGate(), post, undefined, 7, [], true);
        act(() => {
            mounted.getFooter().onSubmit("First");
            mounted.getFooter().onSubmit("Next");
        });
        await act(async () => {
            mounted.getFooter().onStop();
            await flushRequest();
        });
        expect(post.mock.calls[0][2]?.signal?.aborted).toBe(true);
        expect(post).toHaveBeenCalledTimes(1);
        expect(mounted.getFooter().queuePaused).toBe(true);
        expect(mounted.getFooter().queuedMessages[0].input).toBe("Next");
        expect(mounted.getConfig().messages).toHaveLength(0);
        await act(async () => {
            mounted.getFooter().onResumeQueue();
            await flushRequest();
        });
        expect(post).toHaveBeenCalledTimes(2);
        expect(mounted.getConfig().messages.map((m) => m.content)).toEqual(["Next", "Next answer"]);
    });

    it("preserves new input when the server returns an existing run instead of accepting it", async () => {
        const run = { runId: "old-run", articleId: 7, input: "Earlier question", status: "running" };
        const post = jest
            .fn<ReturnType<AxiosPost>, Parameters<AxiosPost>>()
            .mockResolvedValueOnce({ data: `data: ${JSON.stringify({ type: "run-state", run })}\n\n` });
        const mounted = mountHook(createDraftAiSaveGate(), post, undefined, 7, [], true);
        await act(async () => {
            mounted.getFooter().onSubmit("New question");
            await flushRequest();
        });
        expect(mounted.getFooter().queuedMessages[0].input).toBe("New question");
        expect(post).toHaveBeenCalledTimes(1);
    });

    it("aborts writing skills without showing a request error or consuming the next prompt", async () => {
        const request = deferred<any>();
        const post = jest
            .fn<ReturnType<AxiosPost>, Parameters<AxiosPost>>()
            .mockImplementationOnce((_url, _body, config) => {
                config?.signal?.addEventListener("abort", () => request.reject(new Error("cancelled")));
                return request.promise;
            });
        const mounted = mountHook(createDraftAiSaveGate(), post, undefined, 7, [], true);
        act(() => {
            mounted.getFooter().onSubmit("Title", "title");
            mounted.getFooter().onSubmit("Next", "digest");
        });
        await act(async () => {
            mounted.getFooter().onStop();
            await flushRequest();
        });
        expect(post.mock.calls[0][2]?.signal?.aborted).toBe(true);
        expect(mounted.getFooter().queuedMessages[0].input).toBe("Next");
        expect(mounted.getFooter().queuePaused).toBe(true);
        expect(mounted.getConfig().messages).toHaveLength(0);
        expect(mockMessageError).not.toHaveBeenCalled();
    });

    it("pauses queued skills on failure and discards them when changing articles", async () => {
        const request = deferred<any>();
        const post = jest.fn<ReturnType<AxiosPost>, Parameters<AxiosPost>>().mockReturnValueOnce(request.promise);
        const mounted = mountHook(createDraftAiSaveGate(), post, undefined, 7, [], true);
        act(() => {
            mounted.getFooter().onSubmit("First", "title");
            mounted.getFooter().onSubmit("Next", "digest");
        });
        await act(async () => {
            request.reject(new Error("Unavailable"));
            await flushRequest();
        });
        expect(mounted.getFooter().queuePaused).toBe(true);
        expect(mounted.getFooter().queuedMessages).toHaveLength(1);
        expect(post).toHaveBeenCalledTimes(1);
        mounted.rerender(8, []);
        expect(mounted.getFooter().queuedMessages).toHaveLength(0);
        expect(post).toHaveBeenCalledTimes(1);
    });

    it("holds one shared lease per overlapping send until success or failure settles", async () => {
        const gate = createDraftAiSaveGate();
        const firstRequest = deferred<any>();
        const secondRequest = deferred<any>();
        const firstPost = jest.fn(async (): Promise<any> => firstRequest.promise);
        const secondPost = jest.fn(async (): Promise<any> => secondRequest.promise);
        const first = mountHook(gate, firstPost);
        const second = mountHook(gate, secondPost);

        act(() => {
            first.getFooter().onSubmit("First request", "title");
            second.getFooter().onSubmit("Second request", "title");
        });

        expect(gate.getPendingAiCount()).toBe(2);
        expect(firstPost).toHaveBeenCalledTimes(1);
        expect(secondPost).toHaveBeenCalledTimes(1);

        await act(async () => {
            firstRequest.resolve({
                status: 200,
                data: 'data: {"content":"First answer","messageId":"first"}\n\n',
            });
            await flushRequest();
        });
        expect(gate.getPendingAiCount()).toBe(1);

        await act(async () => {
            secondRequest.reject(new Error("Second request failed"));
            await flushRequest();
        });
        expect(gate.getPendingAiCount()).toBe(0);
        expect(mockMessageError).toHaveBeenCalledWith("Second request failed");
    });

    it("does not start a draft request while first create owns the gate", async () => {
        const gate = createDraftAiSaveGate();
        const releaseCreate = gate.tryBeginCreate(0);
        const post = jest.fn(async (): Promise<any> => undefined);
        const mounted = mountHook(gate, post);

        await act(async () => {
            mounted.getFooter().onSubmit("Blocked request");
            await flushRequest();
        });

        expect(post).not.toHaveBeenCalled();
        expect(gate.getPendingAiCount()).toBe(0);
        expect(mockMessageWarning).toHaveBeenCalledWith("Save in progress");
        releaseCreate?.();
    });

    it("keeps a draft clear request on id zero and holds its lease until completion", async () => {
        const gate = createDraftAiSaveGate();
        const request = deferred<any>();
        const post = jest.fn(async (url?: string, body?: unknown, config?: unknown): Promise<any> => {
            void url;
            void body;
            void config;
            return request.promise;
        });
        const draftMessage = {
            role: "user",
            content: "Draft question",
            thinking: false,
            messageId: "draft-question",
        } as ToolAwareAIContent;
        const mounted = mountHook(gate, post, undefined, undefined, [draftMessage]);

        act(() => mounted.getFooter().onClearAiMessages());

        expect(post).toHaveBeenCalledWith("/api/admin/article/ai/messages/clear?id=0");
        expect(gate.getPendingAiCount()).toBe(1);
        expect(gate.tryBeginCreate(0)).toBeUndefined();
        mounted.rerender(42, [draftMessage]);

        await act(async () => {
            request.resolve({ data: { error: 0, data: true } });
            await flushRequest();
        });

        expect(mounted.onAiMessagesChange).toHaveBeenCalledWith([], 0);
        expect(gate.getPendingAiCount()).toBe(0);
    });

    it("releases a draft clear lease when the request fails", async () => {
        const gate = createDraftAiSaveGate();
        const request = deferred<any>();
        const post = jest.fn(async (): Promise<any> => request.promise);
        const draftMessage = {
            role: "user",
            content: "Draft question",
            thinking: false,
            messageId: "draft-question",
        } as ToolAwareAIContent;
        const mounted = mountHook(gate, post, undefined, undefined, [draftMessage]);

        act(() => mounted.getFooter().onClearAiMessages());
        await act(async () => {
            request.reject(new Error("Clear failed"));
            await flushRequest();
        });

        expect(mockMessageError).toHaveBeenCalledWith("Clear failed");
        expect(gate.getPendingAiCount()).toBe(0);
        const releaseCreate = gate.tryBeginCreate(0);
        expect(releaseCreate).toBeDefined();
        releaseCreate?.();
    });

    it("keeps a draft payload update on id zero and releases its lease after failure", async () => {
        const gate = createDraftAiSaveGate();
        const request = deferred<any>();
        const post = jest.fn(async (): Promise<any> => request.promise);
        const coverMessage = {
            role: "assistant",
            content: "Generated cover",
            thinking: false,
            messageId: "cover-message",
            tool: "cover",
            payload: { url: "/temporary/original.png" },
        } as ToolAwareAIContent;
        const mounted = mountHook(gate, post, undefined, undefined, [coverMessage]);

        act(() => {
            mounted.getToolContent(coverMessage).onUpdateToolPayload(0, {
                tool: "cover",
                payload: { url: "/temporary/updated.png" },
            });
        });

        expect(post).toHaveBeenCalledWith("/api/admin/article/ai/message?id=0", {
            messageId: "cover-message",
            tool: "cover",
            payload: { url: "/temporary/updated.png" },
        });
        expect(mounted.onAiMessagesChange).toHaveBeenCalledWith(
            [expect.objectContaining({ messageId: "cover-message", payload: { url: "/temporary/updated.png" } })],
            0
        );
        expect(gate.tryBeginCreate(0)).toBeUndefined();
        mounted.rerender(42, [coverMessage]);

        await act(async () => {
            request.reject(new Error("Payload update failed"));
            await flushRequest();
        });

        expect(gate.getPendingAiCount()).toBe(0);
        const releaseCreate = gate.tryBeginCreate(0);
        expect(releaseCreate).toBeDefined();
        releaseCreate?.();
    });

    it("rejects draft clear and payload writes while first create owns the gate", () => {
        const gate = createDraftAiSaveGate();
        const releaseCreate = gate.tryBeginCreate(0);
        const post = jest.fn(async (): Promise<any> => undefined);
        const coverMessage = {
            role: "assistant",
            content: "Generated cover",
            thinking: false,
            messageId: "cover-message",
            tool: "cover",
            payload: { url: "/temporary/original.png" },
        } as ToolAwareAIContent;
        const mounted = mountHook(gate, post, undefined, undefined, [coverMessage]);

        act(() => {
            mounted.getFooter().onClearAiMessages();
            mounted.getToolContent(coverMessage).onUpdateToolPayload(0, {
                tool: "cover",
                payload: { url: "/temporary/updated.png" },
            });
        });

        expect(post).not.toHaveBeenCalled();
        expect(mounted.onAiMessagesChange).not.toHaveBeenCalled();
        expect(mockMessageWarning).toHaveBeenCalledTimes(2);
        expect(mockMessageWarning).toHaveBeenNthCalledWith(1, "Save in progress");
        expect(mockMessageWarning).toHaveBeenNthCalledWith(2, "Save in progress");
        releaseCreate?.();
    });

    it("rejects a crop upload while first create owns the draft gate", async () => {
        const gate = createDraftAiSaveGate();
        const releaseCreate = gate.tryBeginCreate(0);
        const post = jest.fn(async (): Promise<any> => undefined);
        const coverMessage = {
            role: "assistant",
            content: "Generated cover",
            thinking: false,
            messageId: "cover-message",
            tool: "cover",
            payload: { url: "/temporary/original.png" },
        } as ToolAwareAIContent;
        const mounted = mountHook(gate, post, undefined, undefined, [coverMessage]);

        await act(async () => {
            await mounted.getCropper().onOk("data:image/png;base64,cropped");
        });

        expect(post).not.toHaveBeenCalled();
        expect(mockMessageWarning).toHaveBeenCalledWith("Save in progress");
        releaseCreate?.();
    });

    it("holds the crop lease across upload and the id-zero payload update", async () => {
        const originalFetch = globalThis.fetch;
        const gate = createDraftAiSaveGate();
        const uploadRequest = deferred<any>();
        const payloadRequest = deferred<any>();
        const post = jest.fn(async (url?: string): Promise<any> => {
            if (url?.startsWith("/api/admin/upload")) {
                return uploadRequest.promise;
            }
            return payloadRequest.promise;
        });
        const coverMessage = {
            role: "assistant",
            content: "Generated cover",
            thinking: false,
            messageId: "cover-message",
            tool: "cover",
            payload: { url: "/temporary/original.png" },
        } as ToolAwareAIContent;
        const mounted = mountHook(gate, post, undefined, undefined, [coverMessage]);
        globalThis.fetch = jest.fn(async () => ({
            blob: async () => new Blob(["cover"], { type: "image/png" }),
        })) as never;

        try {
            act(() => mounted.getToolContent(coverMessage).onCropCover("/temporary/original.png"));
            let cropPromise: Promise<void>;
            act(() => {
                cropPromise = mounted.getCropper().onOk("data:image/png;base64,cropped");
            });
            await act(async () => flushRequest());

            expect(post.mock.calls[0][0]).toBe("/api/admin/upload?dir=ai-cover&temporary=true");
            expect(gate.tryBeginCreate(0)).toBeUndefined();
            mounted.rerender(42, [coverMessage]);

            await act(async () => {
                uploadRequest.resolve({ data: { error: 0, data: { url: "/temporary/cropped.png" } } });
                await flushRequest();
            });

            expect(post.mock.calls[1]).toEqual([
                "/api/admin/article/ai/message?id=0",
                {
                    messageId: "cover-message",
                    tool: "cover",
                    payload: { url: "/temporary/cropped.png" },
                },
            ]);
            expect(gate.getPendingAiCount()).toBe(1);
            expect(gate.tryBeginCreate(0)).toBeUndefined();

            await act(async () => {
                payloadRequest.resolve({ data: { error: 0, data: true } });
                await cropPromise!;
                await flushRequest();
            });

            expect(gate.getPendingAiCount()).toBe(0);
            const releaseCreate = gate.tryBeginCreate(0);
            expect(releaseCreate).toBeDefined();
            releaseCreate?.();
        } finally {
            globalThis.fetch = originalFetch;
        }
    });

    it("aborts skills and ignores late progress and results after changing articles", async () => {
        const gate = createDraftAiSaveGate();
        const request = deferred<any>();
        const post = jest.fn(async (_url?: string, _body?: unknown, _config?: AxiosPostConfig): Promise<any> => {
            void _url;
            void _body;
            void _config;
            return request.promise;
        });
        const mounted = mountHook(gate, post);

        act(() => mounted.getFooter().onSubmit("Route-bound request", "title"));
        expect(mounted.onAiMessagesChange).toHaveBeenCalledTimes(1);
        mounted.rerender(42);

        const requestConfig = post.mock.calls[0][2];
        expect(requestConfig).toBeDefined();
        expect(requestConfig?.signal?.aborted).toBe(true);
        act(() => {
            requestConfig?.onDownloadProgress?.({
                event: {
                    target: {
                        responseText: 'data: {"content":"Streaming","messageId":"route-message"}\n\n',
                    },
                },
            });
        });
        await act(async () => {
            request.resolve({
                status: 200,
                data: 'data: {"content":"Final","messageId":"route-message"}\n\n',
            });
            await flushRequest();
        });

        expect(mounted.onAiMessagesChange.mock.calls.map(([, articleId]) => articleId)).toEqual([0]);
        expect(gate.getPendingAiCount()).toBe(0);
    });

    it("ignores a late skill failure after changing articles", async () => {
        const gate = createDraftAiSaveGate();
        const request = deferred<any>();
        const post = jest.fn(async (): Promise<any> => request.promise);
        const mounted = mountHook(gate, post);

        act(() => mounted.getFooter().onSubmit("Fail after navigation", "title"));
        mounted.rerender(42);
        await act(async () => {
            request.resolve({
                status: 503,
                data: 'event: ai-error\ndata: {"message":"Provider unavailable"}\n\n',
            });
            await flushRequest();
        });

        expect(mounted.onAiMessagesChange.mock.calls.map(([, articleId]) => articleId)).toEqual([0]);
        expect(mockMessageError).not.toHaveBeenCalled();
        expect(gate.getPendingAiCount()).toBe(0);
    });
});
