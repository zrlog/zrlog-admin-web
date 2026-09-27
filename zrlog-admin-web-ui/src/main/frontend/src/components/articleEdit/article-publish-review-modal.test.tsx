import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { ArticleEntry } from "./index.types";
import ArticlePublishReviewModal from "./article-publish-review-modal";

jest.mock("antd", () => {
    const React = require("react") as typeof import("react");
    return {
        Alert: ({ message }: { message?: React.ReactNode }) => React.createElement("div", null, message),
        Button: ({ children, icon, ...props }: any) => React.createElement("button", props, icon, children),
        Grid: { useBreakpoint: () => ({ md: true, lg: true }) },
        Modal: ({ children, open, title, onCancel, onOk, okText, cancelText, okButtonProps, footer }: any) =>
            open
                ? React.createElement(
                      "div",
                      { role: "dialog" },
                      React.createElement("h1", null, title),
                      children,
                      footer === undefined
                          ? React.createElement(
                                React.Fragment,
                                null,
                                React.createElement("button", { onClick: onCancel }, cancelText),
                                React.createElement("button", { ...okButtonProps, onClick: onOk }, okText)
                            )
                          : footer
                  )
                : null,
        Segmented: ({ onChange, options, value }: any) =>
            React.createElement(
                "div",
                null,
                options.map((option: any) =>
                    React.createElement(
                        "button",
                        {
                            key: option.value,
                            "data-selected": String(option.value === value),
                            onClick: () => onChange(option.value),
                        },
                        option.label
                    )
                )
            ),
        Space: ({ children, direction, ...props }: any) => {
            void direction;
            return React.createElement("div", props, children);
        },
        Tooltip: ({ children }: { children?: React.ReactNode }) => React.createElement(React.Fragment, null, children),
        Typography: {
            Text: ({ children, strong, ...props }: any) => {
                void strong;
                return React.createElement("span", props, children);
            },
            Title: ({ children, level, ...props }: any) => {
                void level;
                return React.createElement("h2", props, children);
            },
        },
    };
});

jest.mock("@ant-design/icons", () => {
    const React = require("react") as typeof import("react");
    const Icon = () => React.createElement("span");
    return {
        CheckCircleOutlined: Icon,
        CloseCircleOutlined: Icon,
        DesktopOutlined: Icon,
        EditOutlined: Icon,
        ExclamationCircleOutlined: Icon,
        MobileOutlined: Icon,
    };
});

jest.mock("antd-style", () => ({
    useTheme: () => ({
        borderRadiusLG: 6,
        colorBgContainer: "#fff",
        colorBorderSecondary: "#ddd",
        colorError: "#f00",
        colorSuccess: "#0a0",
        colorWarning: "#fa0",
        lineType: "solid",
        lineWidth: 1,
        margin: 16,
        marginLG: 24,
        marginSM: 12,
        padding: 16,
        paddingXS: 8,
    }),
}));

jest.mock("@editor/dist/editor/utils/marked-utils", () => ({
    markdownToHtmlSyncWithCallback: (markdown: string, onSuccess: (html: string) => void) => {
        const html = markdown ? `<p>${markdown}</p>` : "";
        onSuccess(html);
        return html;
    },
}));

jest.mock("../../base/ConfigProviderApp", () => ({ getAppState: () => ({ dark: false }) }));
jest.mock("../article/article-preview-snapshot", () => ({
    __esModule: true,
    default: ({ htmlContent }: { htmlContent?: string }) =>
        require("react").createElement("div", { "data-testid": "preview-content" }, htmlContent),
}));

jest.mock("../../utils/constants", () => ({
    getRes: () => ({
        cancel: "Cancel",
        close: "Close",
        edit: "Edit",
        article: { previewSnapshot: { digest: "Summary", empty: "Empty", keywords: "Tags" } },
        articleEdit: {
            publishReview: {
                title: "Pre-publish Review",
                metadata: "Publish Checks",
                contentPreview: "Content Preview",
                desktop: "Desktop",
                mobile: "Mobile",
                confirm: "Confirm Publish",
                ready: "Ready",
                untitled: "Untitled Article",
                blocked: "Add required fields",
                offline: "Reconnect before publishing",
                aiPending: "Wait for the assistant request",
                contentConflict: "Resolve conflict",
                fields: {
                    title: "Title",
                    category: "Category",
                    markdown: "Body",
                    alias: "Alias",
                    digest: "Summary",
                    tags: "Tags",
                    cover: "Cover",
                },
                details: {
                    titleMissing: "Missing title",
                    categoryMissing: "Missing category",
                    markdownMissing: "Missing body",
                    aliasMissing: "Missing alias",
                    digestMissing: "Missing summary",
                    tagsMissing: "Missing tags",
                    coverMissing: "Missing cover",
                },
            },
        },
    }),
}));

const completeArticle: ArticleEntry = {
    title: "Release notes",
    typeId: 1,
    markdown: "Release body",
    content: "<p>Release body</p>",
    alias: "release-notes",
    digest: "Release summary",
    keywords: "release",
    thumbnail: "/release.png",
    rubbish: true,
    version: 1,
};

const typeOptions = [{ value: 1, label: "Engineering" }];

const reactActEnvironment = globalThis as typeof globalThis & {
    IS_REACT_ACT_ENVIRONMENT?: boolean;
};

describe("ArticlePublishReviewModal", () => {
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

    const renderModal = (overrides: Partial<Parameters<typeof ArticlePublishReviewModal>[0]> = {}) => {
        const props: Parameters<typeof ArticlePublishReviewModal>[0] = {
            open: true,
            article: completeArticle,
            typeOptions,
            offline: false,
            draftAiPending: false,
            saving: false,
            contentConflict: false,
            onOpenChange: jest.fn(),
            onLocate: jest.fn(),
            onConfirm: jest.fn(async () => true),
            ...overrides,
        };
        act(() => root.render(<ArticlePublishReviewModal {...props} />));
        return props;
    };

    it("blocks confirmation until the required title and category are present", () => {
        const onConfirm = jest.fn(async () => true);
        renderModal({
            article: { ...completeArticle, title: "", typeId: 9 },
            onConfirm,
        });

        const confirmButton = Array.from(container.querySelectorAll("button")).find(
            (button) => button.textContent === "Confirm Publish"
        ) as HTMLButtonElement;
        expect(confirmButton.disabled).toBe(true);
        act(() => confirmButton.click());
        expect(onConfirm).not.toHaveBeenCalled();
    });

    it("keeps warnings non-blocking and stays open when publishing fails", async () => {
        const onOpenChange = jest.fn();
        const onConfirm = jest.fn(async () => false);
        renderModal({
            article: {
                ...completeArticle,
                markdown: "",
                content: "",
                alias: "",
                digest: "",
                keywords: "",
                thumbnail: "",
            },
            onOpenChange,
            onConfirm,
        });

        const confirmButton = Array.from(container.querySelectorAll("button")).find(
            (button) => button.textContent === "Confirm Publish"
        ) as HTMLButtonElement;
        expect(confirmButton.disabled).toBe(false);
        await act(async () => confirmButton.click());
        expect(onConfirm).toHaveBeenCalledTimes(1);
        expect(onOpenChange).not.toHaveBeenCalled();
        expect(container.querySelector('[role="dialog"]')).not.toBeNull();
    });

    it("blocks publish confirmation while a draft assistant request is pending", () => {
        const onConfirm = jest.fn(async () => true);
        renderModal({ draftAiPending: true, onConfirm });

        const confirmButton = Array.from(container.querySelectorAll("button")).find(
            (button) => button.textContent === "Confirm Publish"
        ) as HTMLButtonElement;
        expect(confirmButton.disabled).toBe(true);
        expect(container.textContent).toContain("Wait for the assistant request");
        act(() => confirmButton.click());
        expect(onConfirm).not.toHaveBeenCalled();
    });

    it("switches to a stable mobile preview and closes only after successful confirmation", async () => {
        const onOpenChange = jest.fn();
        const onConfirm = jest.fn(async () => true);
        renderModal({ onOpenChange, onConfirm });

        const frame = container.querySelector('[data-testid="article-publish-preview-frame"]') as HTMLDivElement;
        expect(frame.dataset.previewMode).toBe("desktop");
        const mobileButton = Array.from(container.querySelectorAll("button")).find(
            (button) => button.textContent === "Mobile"
        ) as HTMLButtonElement;
        act(() => mobileButton.click());
        expect(frame.dataset.previewMode).toBe("mobile");
        expect(frame.style.maxWidth).toBe("390px");

        const confirmButton = Array.from(container.querySelectorAll("button")).find(
            (button) => button.textContent === "Confirm Publish"
        ) as HTMLButtonElement;
        await act(async () => confirmButton.click());
        expect(onConfirm).toHaveBeenCalledTimes(1);
        expect(onOpenChange).toHaveBeenCalledWith(false);
    });

    it("uses a side-effect-free close action in content preview mode", () => {
        const onOpenChange = jest.fn();
        const onConfirm = jest.fn(async () => true);
        renderModal({ previewOnly: true, onOpenChange, onConfirm });

        expect(container.querySelector('[role="dialog"] h1')?.textContent).toBe("Content Preview");
        const closeButton = Array.from(container.querySelectorAll("button")).find(
            (button) => button.textContent === "Close"
        ) as HTMLButtonElement;
        act(() => closeButton.click());

        expect(onConfirm).not.toHaveBeenCalled();
        expect(onOpenChange).toHaveBeenCalledWith(false);
    });
});
