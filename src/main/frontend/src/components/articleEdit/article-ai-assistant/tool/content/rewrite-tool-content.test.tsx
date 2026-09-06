import { act, useState } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { AssistantToolPayload } from "../../article-ai-assistant.types";
import { SpecificToolContentProps } from "../article-ai-assistant-tool-content.types";
import RewriteToolContent from "./rewrite-tool-content";

jest.mock("antd", () => {
    const React = require("react") as typeof import("react");
    return {
        Button: ({ children, disabled, onClick }: React.ButtonHTMLAttributes<HTMLButtonElement>) =>
            React.createElement("button", { disabled, onClick }, children),
        Space: ({ children }: { children?: React.ReactNode }) => React.createElement("div", null, children),
        Typography: {
            Paragraph: ({ children }: { children?: React.ReactNode }) => React.createElement("p", null, children),
            Text: ({ children }: { children?: React.ReactNode }) => React.createElement("span", null, children),
        },
    };
});

jest.mock("../../../../../utils/constants", () => ({
    getRes: () => ({
        articleEdit: {
            assistant: {
                apply: "Apply",
                rejectCandidate: "Reject Candidate",
                rewriteCandidate: "Candidate body",
                rewriteCurrent: "Current body",
                rewriteRejected: "Candidate rejected. The article body was not changed",
                rewriteUnchanged: "The candidate body is the same as the current body",
            },
        },
    }),
}));

jest.mock("../../../markdown-diff-view", () => ({
    __esModule: true,
    default: ({ afterText }: { afterText: string }) =>
        require("react").createElement("div", { "data-testid": "rewrite-diff" }, afterText),
}));

jest.mock("../article-ai-assistant-tool-refine-actions", () => ({
    __esModule: true,
    default: () => null,
}));

jest.mock("../article-ai-assistant-tool-result-card", () => ({
    __esModule: true,
    default: ({ children }: { children?: import("react").ReactNode }) => children,
}));

const reactActEnvironment = globalThis as typeof globalThis & {
    IS_REACT_ACT_ENVIRONMENT?: boolean;
};

type RewriteToolPayload = Extract<AssistantToolPayload, { tool: "rewrite" }>;

describe("RewriteToolContent", () => {
    let container: HTMLDivElement;
    let root: Root;
    let onApplyValues: SpecificToolContentProps<RewriteToolPayload>["onApplyValues"];
    let onUpdateToolPayload: SpecificToolContentProps<RewriteToolPayload>["onUpdateToolPayload"];

    const candidate: RewriteToolPayload = {
        tool: "rewrite",
        payload: {
            summary: "Clearer wording",
            markdown: "Rewritten body",
        },
    };

    const baseProps = (): SpecificToolContentProps<RewriteToolPayload> => ({
        aiProvider: "test",
        messageIndex: 3,
        offline: false,
        currentMarkdown: "Original body",
        toolPayload: candidate,
        onApplyValues,
        onSelectTitle: jest.fn(),
        onRefine: jest.fn(),
        onUpdateToolPayload,
        onCoverApplyingChange: jest.fn(),
        onCropCover: jest.fn(),
    });

    const clickButton = (label: string) => {
        const button = Array.from(container.querySelectorAll("button")).find((item) => item.textContent === label);
        expect(button).toBeDefined();
        act(() => {
            button?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
        });
    };

    beforeEach(() => {
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = true;
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
        onApplyValues = jest.fn<void, Parameters<typeof onApplyValues>>();
        onUpdateToolPayload = jest.fn<void, Parameters<typeof onUpdateToolPayload>>();
    });

    afterEach(() => {
        act(() => {
            root.unmount();
        });
        container.remove();
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = false;
    });

    it("applies the rewrite only after the user accepts the candidate", () => {
        act(() => {
            root.render(<RewriteToolContent {...baseProps()} />);
        });

        expect(onApplyValues).not.toHaveBeenCalled();
        clickButton("Apply");

        expect(onApplyValues).toHaveBeenCalledTimes(1);
        expect(onApplyValues).toHaveBeenCalledWith({ markdown: "Rewritten body" });
        expect(onUpdateToolPayload).not.toHaveBeenCalled();
    });

    it("discards the candidate without applying it or requesting server persistence", () => {
        const Harness = () => {
            const [toolPayload, setToolPayload] = useState<RewriteToolPayload>(candidate);
            return (
                <RewriteToolContent
                    {...baseProps()}
                    toolPayload={toolPayload}
                    onUpdateToolPayload={(messageIndex, nextToolPayload, persist) => {
                        onUpdateToolPayload(messageIndex, nextToolPayload, persist);
                        if (nextToolPayload.tool === "rewrite") {
                            setToolPayload(nextToolPayload);
                        }
                    }}
                />
            );
        };
        act(() => {
            root.render(<Harness />);
        });

        expect(container.querySelector('[data-testid="rewrite-diff"]')?.textContent).toBe("Rewritten body");
        clickButton("Reject Candidate");

        expect(onApplyValues).not.toHaveBeenCalled();
        expect(onUpdateToolPayload).toHaveBeenCalledWith(
            3,
            {
                tool: "rewrite",
                payload: {
                    summary: "Clearer wording",
                    markdown: "Rewritten body",
                    discarded: true,
                },
            },
            false
        );
        expect(container.querySelector('[data-testid="rewrite-diff"]')).toBeNull();
        expect(container.textContent).toContain("Candidate rejected. The article body was not changed");
        expect(container.textContent).not.toContain("Apply");
    });
});
