import { act, useLayoutEffect, useRef } from "react";
import { createRoot, Root } from "react-dom/client";
import { EditorState } from "@codemirror/state";
import { EditorView } from "@codemirror/view";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import useArticleEditorDocument from "./use-article-editor-document";

// CRA's Jest resolver ignores this package's CommonJS export. Use its real CJS build.
jest.mock("@marijn/find-cluster-break", () => require("@marijn/find-cluster-break/dist/index.cjs"));

const environment = globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean };

describe("article body replacement in the mounted editor", () => {
    let root: Root;
    let container: HTMLDivElement;
    let editor: ReturnType<typeof useArticleEditorDocument>;
    let renderedChanges: (() => void)[];
    const onChange = jest.fn();

    function Harness({ markdown, revision = 0 }: { markdown?: string; revision?: number }) {
        editor = useArticleEditorDocument(markdown, revision, onChange);
        const { onLoad, onChange: onEditorChange } = editor;
        const parent = useRef<HTMLDivElement>(null);
        const initialValue = useRef(markdown);
        useLayoutEffect(() => {
            // Match the package's initialization-only value and asynchronous Markdown rendering.
            const view = new EditorView({
                parent: parent.current!,
                state: EditorState.create({
                    doc: initialValue.current,
                    extensions: [
                        EditorView.updateListener.of((update) => {
                            if (!update.docChanged) return;
                            const value = update.state.doc.toString();
                            renderedChanges.push(() => onEditorChange({ value, previewContent: `<p>${value}</p>` }));
                        }),
                    ],
                }),
            });
            onLoad(view);
            return () => view.destroy();
        }, [onLoad, onEditorChange]);
        return <div ref={parent} />;
    }

    const render = (markdown?: string, revision = 0) =>
        act(() => root.render(<Harness markdown={markdown} revision={revision} />));
    const replaceByTyping = (value: string) =>
        act(() => {
            const view = editor.viewRef.current!;
            view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: value } });
        });
    const finishRendering = () => act(() => renderedChanges.splice(0).forEach((callback) => callback()));

    beforeEach(() => {
        environment.IS_REACT_ACT_ENVIRONMENT = true;
        // These tests inspect document updates, not browser layout measurements.
        jest.spyOn(window, "requestAnimationFrame").mockReturnValue(0);
        onChange.mockClear();
        renderedChanges = [];
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
        render("Original body");
    });
    afterEach(() => {
        act(() => root.unmount());
        container.remove();
        jest.restoreAllMocks();
        environment.IS_REACT_ACT_ENVIRONMENT = false;
    });

    it("displays a refreshed server body without remounting or triggering another save", () => {
        const originalView = editor.viewRef.current;
        const earlierCallback = editor.onChange;
        render("AI updated body", 1);
        expect(editor.viewRef.current).toBe(originalView);
        expect(originalView!.state.doc.toString()).toBe("AI updated body");
        expect(container.querySelector(".cm-content")?.textContent).toBe("AI updated body");
        finishRendering();
        act(() => earlierCallback({ value: "Original body", previewContent: "<p>Old preview</p>" }));
        expect(onChange).not.toHaveBeenCalled();
        replaceByTyping("AI updated body with local edits");
        finishRendering();
        expect(onChange).toHaveBeenCalledWith({
            markdown: "AI updated body with local edits",
            content: "<p>AI updated body with local edits</p>",
        });
    });

    it.each(["", undefined])("clears the visible body when the server supplies %j", (value) => {
        render(value, 1);
        expect(editor.viewRef.current!.state.doc.toString()).toBe("");
        expect(container.querySelector(".cm-content")?.textContent).toBe("");
        finishRendering();
        expect(onChange).not.toHaveBeenCalled();
    });

    it("leaves selection and pending typing alone during unrelated page updates", () => {
        const view = editor.viewRef.current!;
        act(() => view.dispatch({ selection: { anchor: 4 } }));
        render("Original body");
        expect(view.state.selection.main.anchor).toBe(4);
        replaceByTyping("First keystroke");
        finishRendering();
        replaceByTyping("Newer typing waiting for HTML");
        render("First keystroke");
        expect(view.state.doc.toString()).toBe("Newer typing waiting for HTML");
        finishRendering();
        expect(onChange).toHaveBeenLastCalledWith({
            markdown: "Newer typing waiting for HTML",
            content: "<p>Newer typing waiting for HTML</p>",
        });
    });

    it("applies explicit server restoration and then restores the user's conflict copy", () => {
        replaceByTyping("Unsaved local body");
        render("Original body", 1);
        finishRendering();
        expect(editor.viewRef.current!.state.doc.toString()).toBe("Original body");
        expect(onChange).not.toHaveBeenCalled();
        render("Unsaved local body", 2);
        finishRendering();
        expect(editor.viewRef.current!.state.doc.toString()).toBe("Unsaved local body");
        expect(onChange).not.toHaveBeenCalled();
    });
});
