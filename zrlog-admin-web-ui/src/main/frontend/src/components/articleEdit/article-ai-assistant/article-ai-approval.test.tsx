import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { getRes } from "../../../utils/constants";
import ArticleAiApproval from "./article-ai-approval";
import { ChatRun } from "./use-article-chat";

jest.mock("antd-style", () => ({ useTheme: () => ({ paddingXS: 8, colorFillQuaternary: "#eee", borderRadiusSM: 4 }) }));

const paused = (): ChatRun => ({
    runId: "run",
    articleId: 7,
    input: "Update",
    status: "awaiting_approval",
    approval: {
        id: "approval",
        tool: "update_article",
        title: "Article",
        articleId: 7,
        version: 2,
        publicImpact: true,
        expiresAt: Date.now() + 60000,
        changes: [
            { field: "content", before: "Old body", after: '<img src="bad" onerror="alert(1)">', truncated: false },
        ],
    },
});

describe("article operation approval", () => {
    let root: Root;
    let container: HTMLDivElement;
    const decide = jest.fn();
    const refresh = jest.fn();
    const environment = globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean };
    beforeEach(() => {
        environment.IS_REACT_ACT_ENVIRONMENT = true;
        window.matchMedia = jest.fn(() => ({
            matches: false,
            addListener: jest.fn(),
            removeListener: jest.fn(),
            addEventListener: jest.fn(),
            removeEventListener: jest.fn(),
        })) as unknown as typeof window.matchMedia;
        global.ResizeObserver = class {
            observe() {
                return undefined;
            }
            unobserve() {
                return undefined;
            }
            disconnect() {
                return undefined;
            }
        };
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
        decide.mockClear();
        refresh.mockClear();
    });
    afterEach(() => {
        act(() => root.unmount());
        container.remove();
        delete environment.IS_REACT_ACT_ENVIRONMENT;
    });
    const render = (run = paused(), disabled = false) =>
        act(() =>
            root.render(<ArticleAiApproval run={run} disabled={disabled} onDecide={decide} onRefresh={refresh} />)
        );
    const button = (label: string) =>
        Array.from(container.querySelectorAll("button")).find(
            (node) => node.textContent?.replace(/\s/g, "") === label.replace(/\s/g, "")
        )!;
    it.each(["zh_CN", "en_US"] as const)("reviews changes as text and allows or rejects once in %s", (lang) => {
        window.__SS_DATA__ = { resourceInfo: { lang } } as NonNullable<typeof window.__SS_DATA__>;
        const res = getRes().articleEdit.approval;
        render();
        expect(container.textContent).toContain(res.publicImpact);
        act(() => (container.querySelector(".ant-collapse-header") as HTMLElement).click());
        expect(container.textContent).toContain("Old body");
        expect(container.textContent).toContain('<img src="bad" onerror="alert(1)">');
        expect(container.querySelector("img")).toBeNull();
        act(() => button(res.approve).click());
        act(() => button(res.reject).click());
        expect(decide.mock.calls).toEqual([["approve"], ["reject"]]);
        render(paused(), true);
        act(() => button(res.approve).click());
        expect(decide).toHaveBeenCalledTimes(2);
    });
    it("never offers execution for expired or uncertain tasks and refreshes running tasks manually", () => {
        const view = paused();
        view.approval!.expiresAt = 1;
        render(view);
        expect(container.textContent).toContain(getRes().articleEdit.approval.expired);
        expect(button(getRes().articleEdit.approval.approve)).toBeUndefined();
        render({ ...paused(), status: "uncertain" });
        expect(button(getRes().articleEdit.approval.approve)).toBeUndefined();
        render({ ...paused(), status: "running" });
        expect(refresh).not.toHaveBeenCalled();
        act(() => button(getRes().articleEdit.approval.refresh).click());
        expect(refresh).toHaveBeenCalledTimes(1);
        render({ ...paused(), status: "failed", error: "permission" });
        expect(container.textContent).toContain(getRes().articleEdit.knowledge.permission);
    });
});
