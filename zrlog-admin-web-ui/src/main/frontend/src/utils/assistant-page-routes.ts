export const ASSISTANT_ROUTES = {
    tasks: "/assistant",
    system: "/assistant/system",
    history: "/assistant/history",
} as const;

export type AssistantPage = keyof typeof ASSISTANT_ROUTES;

export const isAssistantPage = (raw: string) =>
    Object.values(ASSISTANT_ROUTES).some((path) => path === raw.split(/[?#]/)[0].replace(/\.html$/, ""));
