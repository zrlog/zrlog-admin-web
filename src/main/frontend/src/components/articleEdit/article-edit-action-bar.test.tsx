import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import ArticleEditActionBar from "./article-edit-action-bar";
import { ArticleEditState } from "./index.types";
import { AIProviderType } from "../../type";
import { createDraftAiSaveGate } from "./draft-ai-save-gate";

jest.mock("antd", () => {
    const React = require("react") as typeof import("react");
    return {
        Button: React.forwardRef(({ children, icon, ...props }: any, ref: any) =>
            React.createElement("button", { ...props, ref }, icon, children)
        ),
        Grid: { useBreakpoint: () => ({ sm: true }) },
    };
});

jest.mock("@ant-design/icons", () => {
    const React = require("react") as typeof import("react");
    const Icon = () => React.createElement("span");
    return { SaveOutlined: Icon, SendOutlined: Icon };
});

jest.mock("../../base/AppBase", () => ({ useAxiosBaseInstance: () => ({}) }));
jest.mock("@editor/dist/ai/AIDrawer", () => ({ getAiDrawerOpen: () => false }));
jest.mock("./article-ai-assistant/article-ai-assistant-button", () => ({
    __esModule: true,
    default: () => null,
    getArticleAiAssistantDrawerOpen: () => false,
}));
jest.mock("./shortcut-utils", () => ({
    getShortcutTitle: (title: string) => title,
    isMacLikeDevice: () => false,
    isTouchLikeDevice: () => false,
}));
jest.mock("../../utils/constants", () => ({
    getRes: () => ({
        articleEdit: {
            actions: { release: "Publish", save: "Save", saveAsDraft: "Save Draft" },
            saving: "Saving",
        },
    }),
}));

const state: ArticleEditState = {
    typeOptions: [],
    tags: [],
    aiProvider: AIProviderType.OPEN_AI,
    aiConfigured: false,
    aiMessages: [],
    linkPreviewEnabled: false,
    publishCheckEnabled: false,
    articleCoverAspectRatio: "16:9",
    articleEditAutoSaveInterval: 5,
    rubbish: true,
    editorVersion: 1,
    contentSource: "server",
    article: {
        title: "Release notes",
        typeId: 1,
        rubbish: true,
        version: 1,
        privacy: false,
    },
    saving: {
        rubbishSaving: false,
        previewIng: false,
        autoSaving: false,
        releaseSaving: false,
    },
};

const reactActEnvironment = globalThis as typeof globalThis & {
    IS_REACT_ACT_ENVIRONMENT?: boolean;
};

describe("ArticleEditActionBar", () => {
    let container: HTMLDivElement;
    let root: Root;

    beforeEach(() => {
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = true;
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
    });

    afterEach(() => {
        act(() => root.unmount());
        container.remove();
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = false;
    });

    const renderActionBar = (
        data: ArticleEditState,
        onSubmit = jest.fn(async () => true),
        onPreview = jest.fn(async () => undefined),
        shortcutsDisabled = false,
        draftAiPending = false
    ) => {
        const onRequestPublish = jest.fn();
        act(() => {
            root.render(
                <ArticleEditActionBar
                    data={data}
                    draftAiPending={draftAiPending}
                    draftAiSaveGate={createDraftAiSaveGate()}
                    fullScreen={false}
                    offline={false}
                    shortcutsDisabled={shortcutsDisabled}
                    onSubmit={onSubmit}
                    onRequestPublish={onRequestPublish}
                    onPreview={onPreview}
                    onApplyAiValues={jest.fn()}
                />
            );
        });
        return { onPreview, onRequestPublish, onSubmit };
    };

    it("opens the review without submitting when the public publish button is clicked", async () => {
        const { onRequestPublish, onSubmit } = renderActionBar(state);
        const publishButton = Array.from(container.querySelectorAll("button")).find(
            (button) => button.textContent === "Publish"
        ) as HTMLButtonElement;

        await act(async () => publishButton.click());

        expect(onRequestPublish).toHaveBeenCalledTimes(1);
        expect(onSubmit).not.toHaveBeenCalled();
    });

    it("routes Ctrl+Enter through the same public review entry", () => {
        const { onRequestPublish, onSubmit } = renderActionBar(state);

        act(() => window.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", ctrlKey: true })));

        expect(onRequestPublish).toHaveBeenCalledTimes(1);
        expect(onSubmit).not.toHaveBeenCalled();
    });

    it("routes Ctrl+Shift+Enter to content preview without submitting", () => {
        const { onPreview, onRequestPublish, onSubmit } = renderActionBar(state);

        act(() => window.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", ctrlKey: true, shiftKey: true })));

        expect(onPreview).toHaveBeenCalledTimes(1);
        expect(onRequestPublish).not.toHaveBeenCalled();
        expect(onSubmit).not.toHaveBeenCalled();
    });

    it("ignores editor shortcuts while the publish review or content preview is open", () => {
        const onSubmit = jest.fn(async () => true);
        const onPreview = jest.fn(async () => undefined);
        const { onRequestPublish } = renderActionBar(state, onSubmit, onPreview, true);

        act(() => {
            window.dispatchEvent(new KeyboardEvent("keydown", { key: "s", ctrlKey: true }));
            window.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", ctrlKey: true }));
            window.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", ctrlKey: true, shiftKey: true }));
        });

        expect(onSubmit).not.toHaveBeenCalled();
        expect(onPreview).not.toHaveBeenCalled();
        expect(onRequestPublish).not.toHaveBeenCalled();
    });

    it("disables first save and publish actions while draft AI requests are pending", () => {
        const onSubmit = jest.fn(async () => true);
        const { onRequestPublish } = renderActionBar(state, onSubmit, undefined, false, true);
        const buttons = Array.from(container.querySelectorAll("button"));
        const saveButton = buttons.find((button) => button.textContent === "Save Draft") as HTMLButtonElement;
        const publishButton = buttons.find((button) => button.textContent === "Publish") as HTMLButtonElement;

        expect(saveButton.disabled).toBe(true);
        expect(publishButton.disabled).toBe(true);
        act(() => {
            saveButton.click();
            publishButton.click();
            window.dispatchEvent(new KeyboardEvent("keydown", { key: "s", ctrlKey: true }));
            window.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", ctrlKey: true }));
        });

        expect(onSubmit).not.toHaveBeenCalled();
        expect(onRequestPublish).not.toHaveBeenCalled();
    });

    it("keeps private article saves on the existing direct save path", async () => {
        const privateState = {
            ...state,
            article: { ...state.article, privacy: true },
        };
        const onSubmit = jest.fn(async () => true);
        const { onRequestPublish } = renderActionBar(privateState, onSubmit);
        const saveButton = Array.from(container.querySelectorAll("button")).find(
            (button) => button.textContent === "Save"
        ) as HTMLButtonElement;

        await act(async () => saveButton.click());

        expect(onRequestPublish).not.toHaveBeenCalled();
        expect(onSubmit).toHaveBeenCalledWith(privateState.article, true, false, false);
    });
});
