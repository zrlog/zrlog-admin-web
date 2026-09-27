export type DraftArticleOperationRelease = () => void;

export type DraftAiSaveGate = {
    getPendingAiCount: () => number;
    subscribe: (listener: () => void) => () => void;
    tryBeginAiRequest: (articleId?: number) => DraftArticleOperationRelease | undefined;
    tryBeginCreate: (articleId?: number) => DraftArticleOperationRelease | undefined;
};

const NOOP_RELEASE: DraftArticleOperationRelease = () => undefined;

const isPersistedArticle = (articleId?: number) => articleId !== undefined && articleId > 0;

export const createDraftAiSaveGate = (): DraftAiSaveGate => {
    let pendingAiCount = 0;
    let createInProgress = false;
    const listeners = new Set<() => void>();

    const notify = () => listeners.forEach((listener) => listener());
    const releaseOnce = (release: () => void): DraftArticleOperationRelease => {
        let released = false;
        return () => {
            if (released) {
                return;
            }
            released = true;
            release();
        };
    };

    return {
        getPendingAiCount: () => pendingAiCount,
        subscribe: (listener) => {
            listeners.add(listener);
            return () => listeners.delete(listener);
        },
        tryBeginAiRequest: (articleId) => {
            if (isPersistedArticle(articleId)) {
                return NOOP_RELEASE;
            }
            if (createInProgress) {
                return undefined;
            }
            pendingAiCount += 1;
            notify();
            return releaseOnce(() => {
                pendingAiCount = Math.max(0, pendingAiCount - 1);
                notify();
            });
        },
        tryBeginCreate: (articleId) => {
            if (isPersistedArticle(articleId)) {
                return NOOP_RELEASE;
            }
            if (createInProgress || pendingAiCount > 0) {
                return undefined;
            }
            createInProgress = true;
            return releaseOnce(() => {
                createInProgress = false;
            });
        },
    };
};
