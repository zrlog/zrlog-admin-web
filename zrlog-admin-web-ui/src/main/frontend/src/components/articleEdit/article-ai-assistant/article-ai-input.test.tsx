import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { getRes } from "../../../utils/constants";
import ArticleAiInput from "./article-ai-input";
import { ChatRun } from "./use-article-chat";
import { canApplySkillValues, editorContextRevision } from "./article-ai-skill-contract";

const revision = "0123456789abcdef0123456789abcdef";
const paused = (): ChatRun => ({
    runId: "run",
    articleId: 7,
    input: "Titles",
    status: "awaiting_input",
    interaction: {
        id: "input",
        kind: "select",
        question: "Choose a title",
        options: ["First", '<img src="bad" onerror="alert(1)">'],
        contextRevision: revision,
        expiresAt: Date.now() + 60000,
    },
});
describe("writing input", () => {
    let root: Root;
    let container: HTMLDivElement;
    const decide = jest.fn();
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
    });
    afterEach(() => {
        act(() => root.unmount());
        container.remove();
        delete environment.IS_REACT_ACT_ENVIRONMENT;
    });
    const render = (run = paused(), contextRevision = revision, disabled = false) =>
        act(() =>
            root.render(
                <ArticleAiInput run={run} contextRevision={contextRevision} disabled={disabled} onRespond={decide} />
            )
        );
    const button = (label: string) =>
        Array.from(container.querySelectorAll("button")).find(
            (node) => node.textContent?.replace(/\s/g, "") === label.replace(/\s/g, "")
        );
    it.each(["zh_CN", "en_US"] as const)("requires an explicit choice and submits it as text in %s", (lang) => {
        window.__SS_DATA__ = { resourceInfo: { lang } } as NonNullable<typeof window.__SS_DATA__>;
        const res = getRes().articleEdit.interaction;
        render();
        expect(button(res.continue)?.disabled).toBe(true);
        expect(container.querySelector("img")).toBeNull();
        act(() => (container.querySelectorAll('input[type="radio"]')[1] as HTMLInputElement).click());
        act(() => button(res.continue)?.click());
        expect(decide).toHaveBeenCalledWith("submit", '<img src="bad" onerror="alert(1)">');
        act(() => button(res.cancel)?.click());
        expect(decide).toHaveBeenCalledWith("cancel");
    });
    it("blocks expired, changed and in-flight submissions", () => {
        const res = getRes().articleEdit.interaction;
        const run = paused();
        run.interaction!.expiresAt = 1;
        render(run);
        expect(button(res.continue)).toBeUndefined();
        expect(container.textContent).toContain(res.expired);
        render(paused(), "changed");
        expect(button(res.continue)).toBeUndefined();
        expect(button(res.end)).toBeDefined();
        render(paused(), revision, true);
        expect(button(res.continue)?.disabled).toBe(true);
    });
    it("refreshes the task when the input expires so queued messages can continue", () => {
        jest.useFakeTimers();
        const expired = jest.fn();
        try {
            const run = paused();
            run.interaction!.expiresAt = Date.now() + 100;
            act(() =>
                root.render(
                    <ArticleAiInput
                        run={run}
                        contextRevision={revision}
                        disabled={false}
                        onRespond={decide}
                        onExpired={expired}
                    />
                )
            );
            act(() => jest.advanceTimersByTime(101));
            expect(expired).toHaveBeenCalledTimes(1);
            expect(button(getRes().articleEdit.interaction.continue)).toBeUndefined();
        } finally {
            jest.useRealTimers();
        }
    });
    it("only applies contracted fields to the same article content", () => {
        const context = {
            title: "Original",
            alias: "",
            markdown: "正文",
            digest: "",
            keywords: "",
            thumbnail: "",
            selectedText: "",
        };
        const original = editorContextRevision(context);
        const message = {
            role: "assistant" as const,
            thinking: false,
            content: "Candidate",
            messageType: "writingSkill",
            skillContract: {
                version: 1,
                contextRevision: original,
                applicableFields: ["title"],
            },
        };
        expect(canApplySkillValues(message, original, { title: "New" })).toBe(true);
        expect(canApplySkillValues(message, original, { markdown: "Wrong field" })).toBe(false);
        expect(
            canApplySkillValues(message, editorContextRevision({ ...context, markdown: "Updated" }), { title: "New" })
        ).toBe(false);
        expect(canApplySkillValues({ ...message, skillContract: undefined }, original, { title: "New" })).toBe(false);
        expect(editorContextRevision({ ...context, selectedText: "selection" })).toBe(original);
    });
});
