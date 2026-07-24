import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { ArticleChangeableValue, ArticleEntry } from "../index.types";
import useArticleOfflineDraft, { ArticleOfflineChange, ArticleOfflineSyncTask } from "./use-article-offline-draft";

const reactActEnvironment = globalThis as typeof globalThis & {
    IS_REACT_ACT_ENVIRONMENT?: boolean;
};

type HookResult = {
    applyPatch: (patch: ArticleChangeableValue) => ArticleOfflineChange | undefined;
    markCommitted: () => void;
    markSynced: (task: ArticleOfflineSyncTask) => boolean;
};

type HarnessProps = {
    article: ArticleEntry;
    initialDirty: boolean;
    offline: boolean;
    onPersist: (article: ArticleEntry, updatedAt: number) => void;
    onRemove: (article: ArticleEntry) => void;
    onRequestSync: (task: ArticleOfflineSyncTask) => void;
    onSynced: () => void;
};

const validArticle = (article: ArticleEntry) => article.title.length > 0 && (article.typeId || 0) > 0;

describe("useArticleOfflineDraft", () => {
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
        current = useArticleOfflineDraft({
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

    beforeEach(() => {
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = true;
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
    });

    afterEach(() => {
        act(() => {
            root.unmount();
        });
        container.remove();
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = false;
    });

    it("does nothing for a clean online mount or a same-value patch", () => {
        const props = createProps();
        render(props);

        expect(props.onPersist).not.toHaveBeenCalled();
        expect(props.onRequestSync).not.toHaveBeenCalled();

        let change: ArticleOfflineChange | undefined;
        act(() => {
            change = current.applyPatch({ title: baseArticle.title });
        });

        expect(change).toBeUndefined();
        expect(props.onPersist).not.toHaveBeenCalled();
        expect(props.onRequestSync).not.toHaveBeenCalled();
    });

    it("persists an offline change and requests synchronization after reconnecting", () => {
        const props = createProps({ offline: true });
        render(props);

        let change: ArticleOfflineChange | undefined;
        act(() => {
            change = current.applyPatch({ title: "Offline title" });
        });

        expect(change).toEqual({
            article: {
                ...baseArticle,
                title: "Offline title",
            },
            revision: 1,
            updatedAt: 123,
        });
        expect(props.onPersist).toHaveBeenCalledWith(change?.article, 123);
        expect(props.onRequestSync).not.toHaveBeenCalled();

        render({
            ...props,
            article: change!.article,
            offline: false,
        });

        expect(props.onRequestSync).toHaveBeenCalledWith({
            article: change!.article,
            revision: 1,
        });
    });

    it("requests synchronization for a restored dirty draft on an online mount", () => {
        const props = createProps({ initialDirty: true });
        render(props);

        expect(props.onPersist).not.toHaveBeenCalled();
        expect(props.onRequestSync).toHaveBeenCalledWith({
            article: baseArticle,
            revision: 1,
        });
    });

    it("only clears the cache after the latest revision is acknowledged", () => {
        const props = createProps();
        render(props);

        let firstChange: ArticleOfflineChange | undefined;
        let secondChange: ArticleOfflineChange | undefined;
        act(() => {
            firstChange = current.applyPatch({ title: "First title" });
            secondChange = current.applyPatch({ title: "Second title" });
        });

        expect(current.markSynced(firstChange!)).toBe(false);
        expect(props.onRemove).not.toHaveBeenCalled();
        expect(props.onSynced).not.toHaveBeenCalled();

        expect(current.markSynced(secondChange!)).toBe(true);
        expect(props.onRemove).toHaveBeenCalledWith(secondChange!.article);
        expect(props.onSynced).toHaveBeenCalledTimes(1);
    });
});
