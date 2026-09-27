import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import FirstUseChecklist from "./FirstUseChecklist";

jest.mock("antd", () => {
    const React = require("react");
    return {
        Button: ({ children, href, icon, ...props }: any) =>
            React.createElement(href ? "a" : "button", href ? { ...props, href } : props, icon, children),
        Typography: {
            Text: ({ children, ...props }: any) => React.createElement("span", props, children),
        },
    };
});

jest.mock("antd-style", () => ({
    useTheme: () => ({
        borderRadius: 6,
        colorBorderSecondary: "#ddd",
        colorFillSecondary: "#eee",
        colorTextSecondary: "#555",
        lineType: "solid",
        lineWidth: 1,
    }),
}));

jest.mock("../../utils/constants", () => ({
    getRealRouteUrl: (url: string) => url,
    getRes: () => ({
        buildId: "400",
        homeUrl: "https://example.test/blog/",
        index: {
            firstUse: {
                title: "Complete first publish",
                dismiss: "Skip",
                viewSite: "View public site",
                openSite: "Open site",
                createOrImport: "Create or import",
                createArticle: "Create article",
                importMarkdown: "Import Markdown",
                previewAndPublish: "Preview and publish",
                previewAndPublishTip: "Preview before publishing",
            },
        },
    }),
}));

const reactActEnvironment = globalThis as typeof globalThis & {
    IS_REACT_ACT_ENVIRONMENT?: boolean;
};

describe("FirstUseChecklist", () => {
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

    it("renders continuous first-use actions and invokes dismiss", () => {
        const onDismiss = jest.fn();
        act(() => {
            root.render(
                <MemoryRouter>
                    <FirstUseChecklist dismissing={false} onDismiss={onDismiss} />
                </MemoryRouter>
            );
        });

        expect(container.querySelectorAll('[role="listitem"]')).toHaveLength(3);
        expect(container.querySelector<HTMLAnchorElement>('a[href="/article-edit"]')).not.toBeNull();
        expect(
            container.querySelector<HTMLAnchorElement>('a[href="/article-edit?intent=import-markdown"]')
        ).not.toBeNull();
        expect(container.querySelector<HTMLAnchorElement>('a[target="_blank"]')?.href).toBe(
            "https://example.test/blog/?spm=admin-first-use&buildId=400"
        );

        const skipButton = Array.from(container.querySelectorAll("button")).find(
            (button) => button.textContent?.trim() === "Skip"
        );
        expect(skipButton).toBeDefined();
        act(() => skipButton?.dispatchEvent(new MouseEvent("click", { bubbles: true })));
        expect(onDismiss).toHaveBeenCalledTimes(1);
    });
});
