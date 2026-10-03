import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { ArticleChangeableValue, ArticleEntry } from "../index.types";
import { ArticleDraftSyncState } from "./article-draft-sync-state-machine";
import useArticleDraftSync, { ArticleDraftChange, ArticleDraftSyncTask } from "./use-article-draft-sync";

const reactActEnvironment = globalThis as typeof globalThis & {
    IS_REACT_ACT_ENVIRONMENT?: boolean;
};

type HookResult = {
    applyPatch: (patch: ArticleChangeableValue) => ArticleDraftChange | undefined;
    discard: () => void;
    markBlocked: (task: ArticleDraftSyncTask, error: unknown) => boolean;
    markCommitted: () => void;
    markConflict: (task: ArticleDraftSyncTask, error: unknown) => boolean;
    markDeferred: (task: ArticleDraftSyncTask) => void;
    markFailed: (task: ArticleDraftSyncTask, error: unknown) => boolean;
    markSynced: (task: ArticleDraftSyncTask, savedArticle?: ArticleEntry) => boolean;
    markSyncing: (task: ArticleDraftSyncTask) => boolean;
    pauseForConflict: (article: ArticleEntry) => ArticleDraftChange;
    resolveConflict: (article: ArticleEntry, requestSync?: boolean) => ArticleDraftChange | undefined;
    receiveServerArticle: (article: ArticleEntry) => ArticleDraftChange | undefined;
    state: ArticleDraftSyncState;
};

type HarnessProps = {
    article: ArticleEntry;
    initialDirty: boolean;
    initialState?: ArticleDraftSyncState;
    offline: boolean;
    onPersist: (article: ArticleEntry, updatedAt: number, state: ArticleDraftSyncState) => void;
    onRemove: (article: ArticleEntry) => void;
    onRequestSync: (task: ArticleDraftSyncTask) => void;
    onSynced: () => void;
    retryDelay?: (retryCount: number) => number;
};

const validArticle = (article: ArticleEntry) => article.title.length > 0 && (article.typeId || 0) > 0;

describe("useArticleDraftSync", () => {
    let container: HTMLDivElement;
    let root: Root;
    let current!: HookResult;

    const baseArticle: ArticleEntry = {
        version: -1,
        title: "Initial title",
        typeId: 1,
        rubbish: true,
    };

    const createProps = (overrides: Partial<HarnessProps> = {}): HarnessProps => ({
        article: baseArticle,
        initialDirty: false,
        offline: false,
        onPersist: jest.fn(),
        onRemove: jest.fn(),
        onRequestSync: jest.fn(),
        onSynced: jest.fn(),
        ...overrides,
    });

    const Harness = (props: HarnessProps) => {
        current = useArticleDraftSync({
            ...props,
            isSyncable: validArticle,
            now: () => 123,
        });
        return null;
    };

    const render = (props: HarnessProps) => {
        act(() => {
            root.render(<Harness {...props} />);
        });
    };

    it("pauses queued local edits when a tool changes the server and resumes from the chosen version", () => {
        const article = { ...baseArticle, logId: 7, version: 3 };
        const props = createProps({ article });
        render(props);
        let local!: ArticleDraftChange;
        act(() => {
            local = current.applyPatch({ title: "Local title" })!;
        });
        const serverArticle = { ...article, title: "AI title", version: 4 };
        act(() => {
            expect(current.receiveServerArticle(serverArticle)?.article.title).toBe("Local title");
        });
        render({ ...props, article: serverArticle });
        expect(current.state.sync).toBe("conflict");
        expect(current.markSyncing(local)).toBe(false);
        expect(props.onPersist).toHaveBeenLastCalledWith(
            local.article,
            123,
            expect.objectContaining({ sync: "conflict" })
        );
        act(() => current.discard());
        let next!: ArticleDraftChange;
        act(() => {
            next = current.applyPatch({ digest: "Next edit" })!;
        });
        expect(next.article).toEqual({ ...serverArticle, digest: "Next edit" });
        expect(current.state.sync).toBe("queued");
    });

    it("moves unsaved local edits to the created article and preserves them as a conflict", () => {
        const props = createProps();
        render(props);
        act(() => {
            current.applyPatch({ title: "Local title" });
        });
        const serverArticle = { ...baseArticle, logId: 18, title: "AI title", version: 0 };
        act(() => {
            expect(current.receiveServerArticle(serverArticle)?.article).toMatchObject({
                logId: 18,
                title: "Local title",
                version: 0,
            });
        });
        expect(props.onRemove).toHaveBeenCalledWith(expect.objectContaining({ title: "Local title" }));
        expect(props.onPersist).toHaveBeenLastCalledWith(
            expect.objectContaining({ logId: 18, title: "Local title" }),
            123,
            expect.objectContaining({ sync: "conflict" })
        );
        render({ ...props, article: serverArticle });
        act(() => current.discard());
        let next!: ArticleDraftChange;
        act(() => {
            next = current.applyPatch({ digest: "Next edit" })!;
        });
        expect(next.article).toEqual({ ...serverArticle, digest: "Next edit" });
    });

    it.each([false, true])(
        "acknowledges matching AI-saved content and clears its local draft (created: %s)",
        (created) => {
            const article = {
                ...baseArticle,
                logId: created ? undefined : 7,
                version: created ? -1 : 3,
                markdown: "Body",
                content: "<p>Body</p>",
            };
            const props = createProps({ article });
            render(props);
            let local!: ArticleDraftChange;
            act(() => {
                local = current.applyPatch({
                    title: "AI title",
                    markdown: "Updated body",
                    content: "<p>Updated body</p>",
                })!;
            });
            const serverArticle = {
                ...local.article,
                logId: 7,
                version: created ? 0 : 4,
                content: "<p>Updated body</p>\n",
                lastUpdateDate: 456,
                previewUrl: "/preview/7",
                digest: "",
                recommended: false,
            };
            act(() => {
                expect(current.receiveServerArticle(serverArticle)).toBeUndefined();
            });
            expect(current.state).toMatchObject({ document: "clean", sync: "idle" });
            expect(props.onRemove).toHaveBeenCalledWith(local.article);
            expect(props.onSynced).toHaveBeenCalledTimes(1);
            expect(current.markSyncing(local)).toBe(false);
            render({ ...props, article: serverArticle });
            let next!: ArticleDraftChange;
            act(() => {
                next = current.applyPatch({ digest: "Next edit" })!;
            });
            expect(next.article).toEqual({ ...serverArticle, digest: "Next edit" });
            expect(current.state.sync).toBe("queued");
        }
    );

    beforeEach(() => {
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = true;
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
    });

    afterEach(() => {
        jest.useRealTimers();
        act(() => {
            root.unmount();
        });
        container.remove();
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = false;
    });

    it("does nothing for a clean online mount or a same-value patch", () => {
        const props = createProps();
        render(props);

        expect(current.state).toEqual({
            connectivity: "online",
            document: "clean",
            sync: "idle",
            revision: 0,
            retryCount: 0,
        });
        expect(props.onPersist).not.toHaveBeenCalled();
        expect(props.onRequestSync).not.toHaveBeenCalled();

        expect(current.applyPatch({ title: baseArticle.title })).toBeUndefined();
        expect(props.onPersist).not.toHaveBeenCalled();
    });

    it("keeps an offline edit dirty and queues it after reconnecting", () => {
        const props = createProps({ offline: true });
        render(props);

        let change: ArticleDraftChange | undefined;
        act(() => {
            change = current.applyPatch({ title: "Offline title" });
        });

        expect(current.state).toMatchObject({
            connectivity: "offline",
            document: "dirty",
            sync: "idle",
            revision: 1,
        });
        expect(props.onPersist).toHaveBeenLastCalledWith(change?.article, 123, current.state);
        expect(props.onRequestSync).not.toHaveBeenCalled();

        render({
            ...props,
            article: change!.article,
            offline: false,
        });

        expect(current.state).toMatchObject({
            connectivity: "online",
            document: "dirty",
            sync: "queued",
            revision: 1,
        });
        expect(props.onRequestSync).toHaveBeenCalledWith({
            article: change!.article,
            revision: 1,
        });
    });

    it("queues a restored dirty draft on an online mount", () => {
        const props = createProps({ initialDirty: true });
        render(props);

        expect(props.onPersist).not.toHaveBeenCalled();
        expect(props.onRequestSync).toHaveBeenCalledWith({
            article: baseArticle,
            revision: 1,
        });
    });

    it("only acknowledges the newest revision", () => {
        const props = createProps();
        render(props);

        let firstChange: ArticleDraftChange | undefined;
        let secondChange: ArticleDraftChange | undefined;
        act(() => {
            firstChange = current.applyPatch({ title: "First title" });
            secondChange = current.applyPatch({ title: "Second title" });
        });

        expect(current.markSyncing(firstChange!)).toBe(false);
        expect(current.markSynced(firstChange!)).toBe(false);
        expect(props.onRemove).not.toHaveBeenCalled();

        let started = false;
        let synced = false;
        act(() => {
            started = current.markSyncing(secondChange!);
            synced = current.markSynced(secondChange!);
        });
        expect(started).toBe(true);
        expect(synced).toBe(true);
        expect(props.onRemove).toHaveBeenCalledWith(secondChange!.article);
        expect(props.onSynced).toHaveBeenCalledTimes(1);
    });

    it("persists newer local content under the server identity after an older response", () => {
        const props = createProps();
        render(props);

        let firstChange: ArticleDraftChange | undefined;
        let secondChange: ArticleDraftChange | undefined;
        act(() => {
            firstChange = current.applyPatch({ title: "First title" });
            expect(current.markSyncing(firstChange!)).toBe(true);
            secondChange = current.applyPatch({ title: "Second title" });
        });

        act(() => {
            expect(
                current.markSynced(firstChange!, {
                    ...firstChange!.article,
                    logId: 42,
                    version: 3,
                    lastUpdateDate: 456,
                })
            ).toBe(false);
        });

        expect(props.onRemove).toHaveBeenCalledWith(firstChange!.article);
        expect(props.onPersist).toHaveBeenLastCalledWith(
            {
                ...secondChange!.article,
                logId: 42,
                version: 3,
                lastUpdateDate: 456,
            },
            123,
            current.state
        );

        render({
            ...props,
            article: {
                ...firstChange!.article,
                logId: 42,
                version: 3,
                lastUpdateDate: 456,
            },
        });

        let thirdChange: ArticleDraftChange | undefined;
        act(() => {
            thirdChange = current.applyPatch({ digest: "Latest digest" });
        });

        expect(thirdChange?.article).toMatchObject({
            title: "Second title",
            digest: "Latest digest",
            logId: 42,
            version: 3,
            lastUpdateDate: 456,
        });
        expect(thirdChange?.revision).toBe(secondChange!.revision + 1);
    });

    it("retries a transient synchronization failure", () => {
        jest.useFakeTimers();
        const props = createProps({ retryDelay: () => 100 });
        render(props);

        let change: ArticleDraftChange | undefined;
        act(() => {
            change = current.applyPatch({ title: "Retry title" });
        });
        let started = false;
        act(() => {
            started = current.markSyncing(change!);
            expect(current.markFailed(change!, "temporary failure")).toBe(true);
        });
        expect(started).toBe(true);

        expect(current.state).toMatchObject({
            connectivity: "online",
            document: "dirty",
            sync: "retryWaiting",
            retryCount: 1,
            nextRetryAt: 223,
            lastError: "temporary failure",
        });

        act(() => {
            jest.advanceTimersByTime(100);
        });

        expect(current.state.sync).toBe("queued");
        expect(props.onRequestSync).toHaveBeenLastCalledWith({
            article: change!.article,
            revision: 1,
        });
    });

    it("stops automatic synchronization on conflict and can resolve it explicitly", () => {
        const props = createProps();
        render(props);

        let change: ArticleDraftChange | undefined;
        act(() => {
            change = current.applyPatch({ title: "Conflicting title" });
        });
        let started = false;
        act(() => {
            started = current.markSyncing(change!);
            expect(current.markConflict(change!, "version expired")).toBe(true);
        });
        expect(started).toBe(true);

        expect(current.state).toMatchObject({
            document: "dirty",
            sync: "conflict",
            lastError: "version expired",
        });
        expect(current.applyPatch({ title: "Ignored until resolution" })).toBeUndefined();

        let resolved: ArticleDraftChange | undefined;
        act(() => {
            resolved = current.resolveConflict({
                ...change!.article,
                version: 2,
            });
        });

        expect(current.state).toMatchObject({
            connectivity: "online",
            document: "dirty",
            sync: "queued",
            revision: 2,
        });
        expect(props.onRequestSync).toHaveBeenLastCalledWith({
            article: resolved!.article,
            revision: 2,
        });
    });

    it("blocks permanent failures and retries only after a new edit", () => {
        const props = createProps();
        render(props);

        let change: ArticleDraftChange | undefined;
        act(() => {
            change = current.applyPatch({ title: "Invalid title" });
            expect(current.markSyncing(change!)).toBe(true);
            expect(current.markBlocked(change!, "invalid article")).toBe(true);
        });

        expect(current.state).toMatchObject({
            document: "dirty",
            sync: "blocked",
            lastError: "invalid article",
        });

        act(() => {
            change = current.applyPatch({ title: "Corrected title" });
        });
        expect(current.state).toMatchObject({
            document: "dirty",
            sync: "queued",
            revision: 2,
        });
        expect(props.onRequestSync).toHaveBeenLastCalledWith({
            article: change!.article,
            revision: change!.revision,
        });
    });
    it("pauses the newest input when an older manual save conflicts, without scheduling another write", () => {
        const article = { ...baseArticle, logId: 7, version: 3 };
        const props = createProps({ article });
        render(props);
        act(() => current.applyPatch({ title: "Newest input", markdown: "Keep this" }));
        const requests = jest.mocked(props.onRequestSync).mock.calls.length;
        act(() => {
            const local = current.pauseForConflict({ ...article, title: "Submitted earlier", rubbish: false });
            expect(local.article).toMatchObject({
                title: "Newest input",
                markdown: "Keep this",
                rubbish: false,
                version: 3,
            });
        });
        expect(current.state.sync).toBe("conflict");
        expect(props.onRequestSync).toHaveBeenCalledTimes(requests);
        const merged = { ...article, title: "Merged", version: 4 };
        act(() => current.resolveConflict(merged, false));
        expect(props.onPersist).toHaveBeenLastCalledWith(merged, 123, expect.objectContaining({ document: "dirty" }));
        expect(props.onRequestSync).toHaveBeenCalledTimes(requests);
    });

    it("persists a clean manual save candidate when it conflicts", () => {
        const props = createProps({ article: { ...baseArticle, logId: 7, version: 3 } });
        render(props);
        const candidate = { ...props.article, title: "Publish candidate", rubbish: false };
        act(() => current.pauseForConflict(candidate));
        expect(current.state.sync).toBe("conflict");
        expect(props.onPersist).toHaveBeenLastCalledWith(candidate, 123, expect.objectContaining({ sync: "conflict" }));
        expect(props.onRequestSync).not.toHaveBeenCalled();
    });
});
