import { getSsDate } from "../base/SsData";

export type TabArticleDraft = {
    article: unknown;
    meta: unknown;
    source?: { key: string; article: unknown };
};

// Separate durable slots prevent one tab's save or cleanup from erasing another tab's offline work.
const prefix = () => `${window.location.host}_article_drafts_${getSsDate().key || "anonymous"}:`;
const owner = () => {
    const key = `${prefix()}owner`;
    let id = sessionStorage.getItem(key);
    if (!id) {
        id = `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
        sessionStorage.setItem(key, id);
    }
    return id;
};
export const articleTabStorageKey = (articleKey: string) => `${prefix()}${owner()}:${articleKey}`;

export const readStoredArticleDraft = (key: string): TabArticleDraft | undefined => {
    if (!key.startsWith(prefix())) return;
    try {
        const value = JSON.parse(localStorage.getItem(key) || "null");
        if (value && typeof value === "object" && value.article) return value;
    } catch (error) {
        console.error(error);
    }
};

export const readTabArticleDraft = (articleKey: string) => readStoredArticleDraft(articleTabStorageKey(articleKey));

export const writeTabArticleDraft = (articleKey: string, entry: TabArticleDraft) => {
    try {
        localStorage.setItem(articleTabStorageKey(articleKey), JSON.stringify(entry));
    } catch (error) {
        console.error(error);
    }
};

export const listTabArticleDrafts = () => {
    const entries: Array<{ key: string; articleKey: string; entry: TabArticleDraft }> = [];
    for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i)!;
        if (!key.startsWith(prefix())) continue;
        const entry = readStoredArticleDraft(key);
        if (entry) entries.push({ key, articleKey: key.slice(key.lastIndexOf(":") + 1), entry });
    }
    return entries;
};

export const selectTabArticleDraft = (key: string, articleKey: string) => {
    const entry = readStoredArticleDraft(key);
    if (!entry || key === articleTabStorageKey(articleKey)) return;
    writeTabArticleDraft(articleKey, { ...entry, source: { key, article: entry.article } });
};

export const removeStoredArticleDraft = (key: string) => {
    if (key.startsWith(prefix())) localStorage.removeItem(key);
};

export const removeTabArticleDraft = (articleKey: string) => {
    const entry = readTabArticleDraft(articleKey);
    if (entry?.source) {
        const source = readStoredArticleDraft(entry.source.key);
        if (JSON.stringify(source?.article) === JSON.stringify(entry.source.article)) {
            removeStoredArticleDraft(entry.source.key);
        }
    }
    removeStoredArticleDraft(articleTabStorageKey(articleKey));
    return entry;
};
