import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it } from "@jest/globals";
import { getCacheByKey } from "../../utils/cache";
import { PublishStatusPopoverState } from "./index.types";
import useArticleEditUiState from "./use-article-edit-ui-state";

type HarnessProps = {
    logId?: number;
    search: string;
};

const publishedStatus: PublishStatusPopoverState = {
    open: true,
    visible: true,
    publishState: "success",
    publishText: "Published",
    publicUrl: "//blog.example.com/new-article?v=1",
    staticStatus: "running",
    staticText: "Syncing",
    checkStatus: "running",
};
const completedCheckPayload: NonNullable<PublishStatusPopoverState["checkPayload"]> = {
    tool: "publishCheck",
    payload: { score: 92 },
};

const reactActEnvironment = globalThis as typeof globalThis & {
    IS_REACT_ACT_ENVIRONMENT?: boolean;
};

describe("useArticleEditUiState scope migration", () => {
    let container: HTMLDivElement;
    let root: Root;
    let state: ReturnType<typeof useArticleEditUiState>;

    const Harness = ({ logId, search }: HarnessProps) => {
        state = useArticleEditUiState(logId, search);
        return null;
    };

    const render = (props: HarnessProps) => {
        act(() => {
            root.render(<Harness {...props} />);
        });
    };

    beforeEach(() => {
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = true;
        localStorage.clear();
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
    });

    afterEach(() => {
        act(() => root.unmount());
        container.remove();
        localStorage.clear();
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = false;
    });

    it("moves the publish result to a created article and keeps later stale-callback updates in the new scope", () => {
        render({ search: "" });
        const updateFromStartedPublish = state.updatePublishStatus;
        act(() => updateFromStartedPublish(publishedStatus));

        act(() => state.migrateToArticle(42));
        render({ logId: 42, search: "?id=42" });

        expect(state.publishStatus).toMatchObject(publishedStatus);
        expect(getCacheByKey("articleEdit/ui/article/42")).toMatchObject({ publishStatus: publishedStatus });

        act(() => root.unmount());
        root = createRoot(container);
        render({ logId: 42, search: "?id=42" });

        act(() =>
            updateFromStartedPublish((previousState) => ({
                ...previousState,
                staticStatus: "success",
                staticText: undefined,
                checkStatus: "success",
                checkPayload: completedCheckPayload,
            }))
        );

        expect(state.publishStatus).toMatchObject({
            publicUrl: publishedStatus.publicUrl,
            staticStatus: "success",
            checkStatus: "success",
            checkPayload: completedCheckPayload,
        });
        expect(
            getCacheByKey<{ publishStatus: PublishStatusPopoverState }>("articleEdit/ui/article/42").publishStatus
        ).toMatchObject({
            publicUrl: publishedStatus.publicUrl,
            staticStatus: "success",
            checkStatus: "success",
            checkPayload: completedCheckPayload,
        });
        expect(
            getCacheByKey<{ publishStatus: PublishStatusPopoverState } | undefined>("articleEdit/ui/draft")
        ).toBeUndefined();

        act(() => root.unmount());
        root = createRoot(container);
        render({ logId: 42, search: "?id=42" });

        expect(state.publishStatus).toMatchObject({
            publicUrl: publishedStatus.publicUrl,
            staticStatus: "success",
            checkStatus: "success",
            checkPayload: completedCheckPayload,
        });
    });

    it("does not leak a migrated publish result into the next new draft", () => {
        render({ search: "" });
        act(() => state.updatePublishStatus(publishedStatus));
        act(() => state.migrateToArticle(42));

        act(() => root.unmount());
        root = createRoot(container);
        render({ search: "" });

        expect(state.publishStatus).toMatchObject({
            visible: false,
            publishState: "idle",
            staticStatus: "idle",
            checkStatus: "idle",
        });
        expect(state.publishStatus.publicUrl).toBeUndefined();
        expect(getCacheByKey("articleEdit/ui/draft")).toBeUndefined();
        expect(
            getCacheByKey<{ publishStatus: PublishStatusPopoverState }>("articleEdit/ui/article/42").publishStatus
        ).toMatchObject(publishedStatus);
    });

    it("keeps ordinary article scopes isolated", () => {
        render({ logId: 7, search: "?id=7" });
        act(() => state.updatePublishStatus(publishedStatus));

        render({ logId: 8, search: "?id=8" });
        expect(state.publishStatus).toMatchObject({
            visible: false,
            publishState: "idle",
            staticStatus: "idle",
            checkStatus: "idle",
        });

        act(() =>
            state.updatePublishStatus({
                ...publishedStatus,
                publicUrl: "//blog.example.com/article-8?v=1",
            })
        );
        render({ logId: 7, search: "?id=7" });

        expect(state.publishStatus.publicUrl).toBe(publishedStatus.publicUrl);
        expect(
            getCacheByKey<{ publishStatus: PublishStatusPopoverState }>("articleEdit/ui/article/8").publishStatus
                .publicUrl
        ).toBe("//blog.example.com/article-8?v=1");
    });
});
