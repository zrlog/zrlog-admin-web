import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { ArticleEditState, PublishStatusPopoverState } from "./index.types";
import PublishStatusBar from "./publish-status-bar";

jest.mock("antd", () => {
    const React = require("react") as typeof import("react");
    return {
        Button: ({ children, onClick }: React.ButtonHTMLAttributes<HTMLButtonElement>) =>
            React.createElement("button", { onClick }, children),
        Grid: { useBreakpoint: () => ({ md: true }) },
        Popover: ({ children, content }: { children?: React.ReactNode; content?: React.ReactNode }) =>
            React.createElement("div", null, children, content),
        Space: ({ children }: { children?: React.ReactNode }) => React.createElement("div", null, children),
        Tag: ({ children }: { children?: React.ReactNode }) => React.createElement("span", null, children),
        Typography: {
            Link: ({ children, href, rel, target }: React.AnchorHTMLAttributes<HTMLAnchorElement>) =>
                React.createElement("a", { href, rel, target }, children),
            Text: ({ children }: { children?: React.ReactNode }) => React.createElement("span", null, children),
        },
    };
});

jest.mock("@ant-design/icons", () => {
    const React = require("react") as typeof import("react");
    const Icon = () => React.createElement("span");
    return {
        CheckCircleOutlined: Icon,
        CloseOutlined: Icon,
        ExclamationCircleOutlined: Icon,
        ExportOutlined: Icon,
        InfoCircleOutlined: Icon,
        LoadingOutlined: Icon,
        MessageOutlined: Icon,
    };
});

jest.mock("antd-style", () => ({
    useTheme: () => ({
        colorTextSecondary: "#666",
        marginXS: 4,
        marginXXS: 2,
    }),
}));

jest.mock("@editor/dist/editor/TimeAgo", () => ({
    __esModule: true,
    default: () => null,
}));

jest.mock("./article-ai-assistant/tool/content/publish-check-result", () => ({
    __esModule: true,
    default: () => null,
}));

jest.mock("../../utils/constants", () => ({
    getLabelValueSeparator: () => ": ",
    getRes: () => ({
        articleEdit: {
            publishCheck: {
                failed: "Check failed",
                finished: "Check finished",
                notBlocking: "Checks do not block publishing",
                openAssistantHistory: "View assistant history",
                reportHint: "Report saved",
                running: "Checking",
            },
            publishStatus: {
                checkFailed: "Published, Check Failed",
                checking: "Checking",
                completed: "Published",
                failed: "Publish Failed",
                notRequired: "Not Required",
                publicUrl: "Public Article URL",
                publishing: "Publishing",
                staticFailed: "Published, Sync Failed",
                syncing: "Syncing",
                title: "Publish Status",
            },
            saveFailed: "Publish failed",
        },
        backgroundTask: {
            status: { error: "Failed", running: "Running", success: "Completed" },
            updatedAt: "Updated",
        },
        staticSite: {
            publishComplete: "Publish completed",
            publishStart: "Publishing article",
            syncComplete: "Sync completed",
            syncFailed: "Sync failed",
            syncNotRequired: "Static generation is disabled; no sync is required",
            syncing: "Syncing static pages",
        },
    }),
}));

const reactActEnvironment = globalThis as typeof globalThis & {
    IS_REACT_ACT_ENVIRONMENT?: boolean;
};

describe("PublishStatusBar", () => {
    let container: HTMLDivElement;
    let root: Root;

    const saving: ArticleEditState["saving"] = {
        autoSaving: false,
        previewIng: false,
        releaseSaving: false,
        rubbishSaving: false,
    };

    const renderStatus = (staticStatus: PublishStatusPopoverState["staticStatus"], staticError?: string) => {
        const publishStatus: PublishStatusPopoverState = {
            open: true,
            visible: true,
            publishState: "success",
            publishText: "Article published",
            publicUrl: "//blog.example.com/article?v=1",
            staticStatus,
            staticError,
            checkStatus: "idle",
        };
        act(() => {
            root.render(
                <PublishStatusBar
                    saving={saving}
                    publishStatus={publishStatus}
                    onOpenChange={jest.fn()}
                    onClose={jest.fn()}
                />
            );
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

    it.each([
        ["success", undefined, "Sync completed"],
        ["failed", "CDN upload failed", "CDN upload failed"],
        ["not-required", undefined, "Static generation is disabled; no sync is required"],
    ] as const)("renders the %s static result distinctly", (staticStatus, staticError, expectedText) => {
        renderStatus(staticStatus, staticError);

        expect(container.textContent).toContain("Article published");
        expect(container.textContent).toContain(expectedText);
        const link = container.querySelector("a");
        expect(link?.getAttribute("href")).toBe("//blog.example.com/article?v=1");
        expect(link?.getAttribute("target")).toBe("_blank");
        expect(link?.getAttribute("rel")).toBe("noopener noreferrer");
    });

    it("does not present a static failure as a publish failure", () => {
        renderStatus("failed", "CDN upload failed");

        expect(container.textContent).toContain("Published, Sync Failed");
        expect(container.textContent).not.toContain("Publish Failed");
    });
});
