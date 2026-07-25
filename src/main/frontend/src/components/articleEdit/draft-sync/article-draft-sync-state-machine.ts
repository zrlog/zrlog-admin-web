export type ArticleConnectivity = "online" | "offline";
export type ArticleDocumentStatus = "clean" | "dirty";
export type ArticleSyncActivity = "idle" | "queued" | "syncing" | "retryWaiting" | "conflict" | "blocked";

export type ArticleDraftSyncState = {
    connectivity: ArticleConnectivity;
    document: ArticleDocumentStatus;
    sync: ArticleSyncActivity;
    revision: number;
    retryCount: number;
    nextRetryAt?: number;
    lastError?: string;
};

export type ArticleDraftSyncEvent =
    | {
          type: "edit";
          revision: number;
          syncable: boolean;
      }
    | {
          type: "networkLost";
      }
    | {
          type: "networkRestored";
          syncable: boolean;
      }
    | {
          type: "syncStarted";
          revision: number;
      }
    | {
          type: "syncDeferred";
          revision: number;
      }
    | {
          type: "syncFailed";
          revision: number;
          error: string;
          nextRetryAt: number;
      }
    | {
          type: "syncConflict";
          revision: number;
          error: string;
      }
    | {
          type: "syncBlocked";
          revision: number;
          error: string;
      }
    | {
          type: "retryReady";
      }
    | {
          type: "syncSucceeded";
          revision: number;
      }
    | {
          type: "commit";
      }
    | {
          type: "resolveConflict";
          revision: number;
          syncable: boolean;
      };

export const createArticleDraftSyncState = (offline: boolean): ArticleDraftSyncState => ({
    connectivity: offline ? "offline" : "online",
    document: "clean",
    sync: "idle",
    revision: 0,
    retryCount: 0,
});

export const restoreArticleDraftSyncState = (
    offline: boolean,
    dirty: boolean,
    storedState?: ArticleDraftSyncState,
    now: number = Date.now(),
    conflict: boolean = false
): ArticleDraftSyncState => {
    if (!dirty) {
        return createArticleDraftSyncState(offline);
    }
    const connectivity: ArticleConnectivity = offline ? "offline" : "online";
    const revision = Math.max(1, storedState?.revision || 1);
    const retryCount = Math.max(0, storedState?.retryCount || 0);
    if (conflict || storedState?.sync === "conflict") {
        return {
            connectivity,
            document: "dirty",
            sync: "conflict",
            revision,
            retryCount,
            lastError: storedState?.lastError,
        };
    }
    if (storedState?.sync === "blocked") {
        return {
            connectivity,
            document: "dirty",
            sync: "blocked",
            revision,
            retryCount,
            lastError: storedState.lastError,
        };
    }
    const canRestoreRetry =
        connectivity === "online" &&
        storedState?.sync === "retryWaiting" &&
        storedState.nextRetryAt !== undefined &&
        storedState.nextRetryAt > now;
    return {
        connectivity,
        document: "dirty",
        sync: connectivity === "offline" ? "idle" : canRestoreRetry ? "retryWaiting" : "queued",
        revision,
        retryCount,
        nextRetryAt: canRestoreRetry ? storedState.nextRetryAt : undefined,
        lastError: storedState?.lastError,
    };
};

const isCurrentRevision = (state: ArticleDraftSyncState, revision: number) => state.revision === revision;

export const reduceArticleDraftSyncState = (
    state: ArticleDraftSyncState,
    event: ArticleDraftSyncEvent
): ArticleDraftSyncState => {
    switch (event.type) {
        case "edit":
            if (state.sync === "conflict") {
                return state;
            }
            return {
                connectivity: state.connectivity,
                document: "dirty",
                sync: state.connectivity === "online" && event.syncable ? "queued" : "idle",
                revision: event.revision,
                retryCount: 0,
            };
        case "networkLost":
            if (state.connectivity === "offline") {
                return state;
            }
            return {
                ...state,
                connectivity: "offline",
                sync: state.sync === "conflict" || state.sync === "blocked" ? state.sync : "idle",
                nextRetryAt: undefined,
            };
        case "networkRestored":
            if (state.connectivity === "online") {
                return state;
            }
            return {
                ...state,
                connectivity: "online",
                sync:
                    state.sync === "conflict" || state.sync === "blocked"
                        ? state.sync
                        : state.document === "dirty" && event.syncable
                        ? "queued"
                        : "idle",
                nextRetryAt: undefined,
            };
        case "syncStarted":
            if (
                state.connectivity !== "online" ||
                state.document !== "dirty" ||
                state.sync !== "queued" ||
                !isCurrentRevision(state, event.revision)
            ) {
                return state;
            }
            return {
                ...state,
                sync: "syncing",
            };
        case "syncDeferred":
            if (state.document !== "dirty" || !isCurrentRevision(state, event.revision)) {
                return state;
            }
            return {
                ...state,
                connectivity: "offline",
                sync: "idle",
                nextRetryAt: undefined,
            };
        case "syncFailed":
            if (
                state.connectivity !== "online" ||
                state.document !== "dirty" ||
                state.sync !== "syncing" ||
                !isCurrentRevision(state, event.revision)
            ) {
                return state;
            }
            return {
                ...state,
                sync: "retryWaiting",
                retryCount: state.retryCount + 1,
                nextRetryAt: event.nextRetryAt,
                lastError: event.error,
            };
        case "syncConflict":
            if (state.document !== "dirty" || state.sync !== "syncing" || !isCurrentRevision(state, event.revision)) {
                return state;
            }
            return {
                ...state,
                sync: "conflict",
                nextRetryAt: undefined,
                lastError: event.error,
            };
        case "syncBlocked":
            if (state.document !== "dirty" || state.sync !== "syncing" || !isCurrentRevision(state, event.revision)) {
                return state;
            }
            return {
                ...state,
                sync: "blocked",
                nextRetryAt: undefined,
                lastError: event.error,
            };
        case "retryReady":
            if (state.connectivity !== "online" || state.document !== "dirty" || state.sync !== "retryWaiting") {
                return state;
            }
            return {
                ...state,
                sync: "queued",
                nextRetryAt: undefined,
            };
        case "syncSucceeded":
            if (state.document !== "dirty" || state.sync !== "syncing" || !isCurrentRevision(state, event.revision)) {
                return state;
            }
            return {
                connectivity: state.connectivity,
                document: "clean",
                sync: "idle",
                revision: state.revision,
                retryCount: 0,
            };
        case "commit":
            return {
                connectivity: state.connectivity,
                document: "clean",
                sync: "idle",
                revision: state.revision,
                retryCount: 0,
            };
        case "resolveConflict":
            if (state.sync !== "conflict") {
                return state;
            }
            return {
                connectivity: state.connectivity,
                document: "dirty",
                sync: state.connectivity === "online" && event.syncable ? "queued" : "idle",
                revision: event.revision,
                retryCount: 0,
            };
    }
};
