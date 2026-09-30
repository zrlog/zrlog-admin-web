import { useEffect, useReducer, useRef } from "react";
import { AssistantTool } from "./article-ai-assistant.types";

export type QueuedAiMessage = { id: number; input: string; tool?: AssistantTool; selectedText?: string };

// Queue only unsent input. Conversation history and article context are read when a turn starts.
export const useArticleAiQueue = (
    scope: string,
    blocked: boolean,
    send: (message: QueuedAiMessage) => Promise<boolean | undefined>
) => {
    const [, redraw] = useReducer((value: number) => value + 1, 0);
    const nextId = useRef(0);
    const migration = useRef<string>();
    const mounted = useRef(true);
    const state = useRef({ scope, key: scope, items: [] as QueuedAiMessage[], paused: false, running: false });
    if (state.current.scope !== scope) {
        if (migration.current === scope) state.current.scope = scope;
        else state.current = { scope, key: scope, items: [], paused: false, running: false };
        migration.current = undefined;
    }
    const current = state.current;
    const sendRef = useRef(send);
    sendRef.current = send;

    useEffect(() => {
        if (blocked || current.paused || current.running || !current.items.length) return;
        const [message, ...rest] = current.items;
        current.items = rest;
        current.running = true;
        redraw();
        void sendRef
            .current(message)
            .then(
                (success) => {
                    if (!success) current.paused = true;
                    // A save in progress can reject dispatch before any request/message exists.
                    if (success === undefined) current.items = [message, ...current.items];
                },
                () => {
                    current.paused = true;
                }
            )
            .finally(() => {
                current.running = false;
                if (mounted.current && state.current === current) redraw();
            });
    });

    useEffect(() => {
        mounted.current = true;
        return () => {
            mounted.current = false;
        };
    }, []);

    return {
        key: current.key,
        messages: current.items,
        paused: current.paused,
        add: (input: string, tool?: AssistantTool, selectedText?: string) => {
            if (!current.running && !current.items.length) current.paused = false;
            current.items = [...current.items, { id: ++nextId.current, input, tool, selectedText }];
            redraw();
        },
        remove: (id: number) => {
            current.items = current.items.filter((message) => message.id !== id);
            redraw();
        },
        pause: () => {
            current.paused = true;
            redraw();
        },
        resume: () => {
            current.paused = false;
            redraw();
        },
        clear: () => {
            current.items = [];
            current.paused = false;
            redraw();
        },
        migrate: (nextScope: string) => {
            migration.current = nextScope;
        },
    };
};
