import { useCallback, useLayoutEffect, useRef, useState } from "react";
import type { EditorView } from "@uiw/react-codemirror";

// The public editor height sizes its content, excluding its toolbar. Measure that
// difference in our own wrappers so density, language and wrapped actions can vary.
const useArticleEditorHeight = () => {
    const slotRef = useRef<HTMLDivElement>(null);
    const contentRef = useRef<HTMLDivElement>(null);
    const [view, setView] = useState<EditorView | null>(null);
    const [height, setHeight] = useState(0);
    const onLoad = useCallback((editor: EditorView) => setView(editor), []);

    useLayoutEffect(() => {
        const slot = slotRef.current;
        const content = contentRef.current;
        if (!slot || !content || !view) return;
        let frame = 0;
        const measure = () => {
            const chrome = content.getBoundingClientRect().height - view.dom.getBoundingClientRect().height;
            const next = Math.max(0, Math.floor(slot.getBoundingClientRect().height - chrome));
            setHeight((previous) => (previous === next ? previous : next));
        };
        const observer = new ResizeObserver(() => {
            cancelAnimationFrame(frame);
            frame = requestAnimationFrame(measure);
        });
        observer.observe(slot);
        observer.observe(content);
        observer.observe(view.dom);
        measure();
        return () => {
            cancelAnimationFrame(frame);
            observer.disconnect();
        };
    }, [view]);

    return { slotRef, contentRef, height: `${height}px`, onLoad };
};

export default useArticleEditorHeight;
