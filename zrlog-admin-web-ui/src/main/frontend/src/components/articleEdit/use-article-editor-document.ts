import { useCallback, useLayoutEffect, useRef, useState } from "react";
import { Transaction } from "@codemirror/state";
import type { EditorView } from "@uiw/react-codemirror";
import { getArticleEditorChange } from "./article-editor-change";
import type { ChangedContent } from "./index.types";

// The editor package reads value only on mount. Apply later article replacements
// through its public EditorView without remounting the editor and its assistant.
export default function useArticleEditorDocument(
    markdown: string | undefined,
    restoreRevision: number,
    onChange: (change: ChangedContent) => void
) {
    const viewRef = useRef<EditorView | null>(null);
    const [view, setView] = useState<EditorView | null>(null);
    const latest = useRef({ markdown, onChange });
    latest.current = { markdown, onChange };
    const submitted = useRef<string>();
    const previousRevision = useRef(restoreRevision);
    const onLoad = useCallback((editor: EditorView) => {
        viewRef.current = editor;
        setView(editor);
    }, []);

    useLayoutEffect(() => {
        const value = markdown ?? "";
        const typingAcknowledged = submitted.current === value && previousRevision.current === restoreRevision;
        submitted.current = undefined;
        previousRevision.current = restoreRevision;
        // Accepting an earlier keystroke must not replace newer typing still being rendered.
        if (!view || typingAcknowledged || view.state.doc.toString() === value) return;
        view.dispatch({
            changes: { from: 0, to: view.state.doc.length, insert: value },
            annotations: Transaction.addToHistory.of(false),
        });
    }, [markdown, restoreRevision, view]);

    const handleChange = useCallback((content: Parameters<typeof getArticleEditorChange>[0]) => {
        const change = getArticleEditorChange(content, latest.current.markdown, viewRef.current?.state.doc.toString());
        if (change) {
            submitted.current = change.markdown;
            latest.current.onChange(change);
        }
    }, []);

    return { viewRef, onLoad, onChange: handleChange };
}
