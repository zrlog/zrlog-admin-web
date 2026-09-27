import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import ArticleEditMoreActions from "./article-edit-more-actions";

let mockDropdownProps: any;

jest.mock("antd", () => {
    const React = require("react");
    const mockFn = require("@jest/globals").jest.fn;
    return {
        App: {
            useApp: () => ({ message: { error: mockFn(), warning: mockFn() } }),
        },
        Button: ({ children, icon, ...props }: any) => React.createElement("button", props, icon, children),
        Dropdown: (props: any) => {
            mockDropdownProps = props;
            return React.createElement("div", { "data-open": String(props.open) }, props.children);
        },
        Grid: { useBreakpoint: () => ({ md: true, lg: true }) },
        Modal: () => null,
    };
});

jest.mock("@ant-design/icons", () => {
    const React = require("react");
    const Icon = () => React.createElement("span");
    return {
        EllipsisOutlined: Icon,
        EyeOutlined: Icon,
        FileMarkdownOutlined: Icon,
        FilePdfOutlined: Icon,
        FolderOpenOutlined: Icon,
        FullscreenExitOutlined: Icon,
        FullscreenOutlined: Icon,
        HistoryOutlined: Icon,
        ShareAltOutlined: Icon,
    };
});

jest.mock("antd-style", () => ({
    useTheme: () => ({ colorTextTertiary: "#555" }),
}));

jest.mock("screenfull", () => ({
    __esModule: true,
    default: {
        isEnabled: false,
        exit: require("@jest/globals").jest.fn(),
        request: require("@jest/globals").jest.fn(),
    },
}));

jest.mock("@editor/dist/ai/AIDrawer", () => ({ getAiDrawerOpen: () => false }));
jest.mock("../../base/ConfigProviderApp", () => ({ getAppState: () => ({ compactMode: false }) }));
jest.mock("../../utils/cache", () => ({
    addToCache: require("@jest/globals").jest.fn(),
    getCacheByKey: () => false,
}));
jest.mock("./shortcut-utils", () => ({ getShortcutTitle: (title: string) => title, isTouchLikeDevice: () => false }));
jest.mock("../file-manager/picker", () => () => null);
jest.mock("./article-social-preview-drawer", () => () => null);
jest.mock("./article-version-drawer", () => () => null);
jest.mock("./markdown-import-modal", () => () => null);
jest.mock("../article/ArticlePdfAction", () => ({
    exportArticlePdf: require("@jest/globals").jest.fn(),
}));
jest.mock("./markdown-import", () => ({
    MarkdownImportError: class MarkdownImportError extends Error {},
    readMarkdownImportFile: require("@jest/globals").jest.fn(),
}));
jest.mock("../../utils/constants", () => ({
    getEnterFullscreen: () => "Enter fullscreen",
    getExitFullscreen: () => "Exit fullscreen",
    getRes: () => ({
        preview: "Preview",
        article: { exportPdf: "Export PDF", exportPdfPopupBlocked: "Popup blocked" },
        articleEdit: {
            actions: { chooseFromAssets: "Choose from assets", more: "More actions" },
            markdownImport: {
                menu: "Import Markdown",
                errors: {
                    invalidExtension: "Invalid extension",
                    tooLarge: "Too large",
                    invalidUtf8: "Invalid UTF-8",
                    emptyFile: "Empty file",
                    binaryFile: "Binary file",
                    unclosedFrontMatter: "Unclosed front matter",
                    invalidFrontMatter: "Invalid front matter",
                    frontMatterRoot: "Invalid front matter root",
                    frontMatterTooComplex: "Front matter too complex",
                    unknown: "Unknown error",
                },
            },
            socialPreview: { title: "Social preview" },
            version: { label: "Versions" },
        },
    }),
}));

const reactActEnvironment = globalThis as typeof globalThis & {
    IS_REACT_ACT_ENVIRONMENT?: boolean;
};

describe("ArticleEditMoreActions", () => {
    let container: HTMLDivElement;
    let root: Root;

    beforeEach(() => {
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = true;
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
        mockDropdownProps = undefined;
    });

    afterEach(() => {
        act(() => root.unmount());
        container.remove();
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = false;
    });

    it("opens and highlights the Markdown entry without opening the file picker", () => {
        const filePickerClick = jest.spyOn(HTMLInputElement.prototype, "click");
        act(() => {
            root.render(
                <ArticleEditMoreActions
                    fullScreen={false}
                    offline={false}
                    article={{} as any}
                    contentConflict={false}
                    typeOptions={[]}
                    currentVersion={1}
                    axiosInstance={{}}
                    containerRef={{ current: container }}
                    getFullScreenElement={() => container}
                    stateCacheKey="article/new"
                    versionDrawerOpen={false}
                    onRollback={async () => undefined}
                    onVersionOpenChange={jest.fn()}
                    onInsertMarkdownFromAsset={jest.fn()}
                    getCurrentMarkdown={() => ""}
                    onImportMarkdown={async () => true}
                    importMarkdownIntent
                    onExitFullScreen={jest.fn()}
                    onFullScreen={jest.fn()}
                />
            );
        });

        expect(mockDropdownProps.open).toBe(true);
        expect(mockDropdownProps.menu.selectedKeys).toEqual(["import-markdown"]);
        expect(container.querySelector("button")?.getAttribute("aria-label")).toBe("More actions");
        expect(container.querySelector("button")?.getAttribute("title")).toBe("More actions");
        expect(filePickerClick).not.toHaveBeenCalled();

        act(() => mockDropdownProps.onOpenChange(false));
        expect(mockDropdownProps.open).toBe(false);
        expect(filePickerClick).not.toHaveBeenCalled();
        filePickerClick.mockRestore();
    });
});
