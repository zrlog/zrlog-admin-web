import { describe, expect, it } from "@jest/globals";
import {
    ArticleDraftSyncState,
    createArticleDraftSyncState,
    reduceArticleDraftSyncState,
    restoreArticleDraftSyncState,
} from "./article-draft-sync-state-machine";

const dirtyOnlineState = (): ArticleDraftSyncState => ({
    connectivity: "online",
    document: "dirty",
    sync: "queued",
    revision: 1,
    retryCount: 0,
});

describe("articleDraftSyncStateMachine", () => {
    it("models offline as connectivity instead of replacing document state", () => {
        const dirty = reduceArticleDraftSyncState(createArticleDraftSyncState(false), {
            type: "edit",
            revision: 1,
            syncable: true,
        });
        const offline = reduceArticleDraftSyncState(dirty, {
            type: "networkLost",
        });

        expect(offline).toEqual({
            connectivity: "offline",
            document: "dirty",
            sync: "idle",
            revision: 1,
            retryCount: 0,
            nextRetryAt: undefined,
        });

        expect(
            reduceArticleDraftSyncState(offline, {
                type: "networkRestored",
                syncable: true,
            })
        ).toEqual({
            connectivity: "online",
            document: "dirty",
            sync: "queued",
            revision: 1,
            retryCount: 0,
            nextRetryAt: undefined,
        });
    });

    it("moves through queued, syncing and clean for the current revision", () => {
        const dirty = dirtyOnlineState();
        const syncing = reduceArticleDraftSyncState(dirty, {
            type: "syncStarted",
            revision: 1,
        });
        const clean = reduceArticleDraftSyncState(syncing, {
            type: "syncSucceeded",
            revision: 1,
        });

        expect(syncing.sync).toBe("syncing");
        expect(clean).toEqual({
            connectivity: "online",
            document: "clean",
            sync: "idle",
            revision: 1,
            retryCount: 0,
        });
    });

    it("ignores stale responses after a newer edit", () => {
        const syncing = reduceArticleDraftSyncState(dirtyOnlineState(), {
            type: "syncStarted",
            revision: 1,
        });
        const newerEdit = reduceArticleDraftSyncState(syncing, {
            type: "edit",
            revision: 2,
            syncable: true,
        });

        expect(
            reduceArticleDraftSyncState(newerEdit, {
                type: "syncSucceeded",
                revision: 1,
            })
        ).toBe(newerEdit);
    });

    it("waits before retrying a transient failure", () => {
        const syncing = reduceArticleDraftSyncState(dirtyOnlineState(), {
            type: "syncStarted",
            revision: 1,
        });
        const failed = reduceArticleDraftSyncState(syncing, {
            type: "syncFailed",
            revision: 1,
            error: "network error",
            nextRetryAt: 2000,
        });

        expect(failed).toEqual({
            connectivity: "online",
            document: "dirty",
            sync: "retryWaiting",
            revision: 1,
            retryCount: 1,
            nextRetryAt: 2000,
            lastError: "network error",
        });
        expect(
            reduceArticleDraftSyncState(failed, {
                type: "retryReady",
            }).sync
        ).toBe("queued");
    });

    it("keeps conflicts terminal until explicit resolution", () => {
        const syncing = reduceArticleDraftSyncState(dirtyOnlineState(), {
            type: "syncStarted",
            revision: 1,
        });
        const conflict = reduceArticleDraftSyncState(syncing, {
            type: "syncConflict",
            revision: 1,
            error: "version expired",
        });

        expect(conflict).toMatchObject({
            connectivity: "online",
            document: "dirty",
            sync: "conflict",
        });
        expect(
            reduceArticleDraftSyncState(conflict, {
                type: "networkRestored",
                syncable: true,
            })
        ).toBe(conflict);
        expect(
            reduceArticleDraftSyncState(conflict, {
                type: "edit",
                revision: 2,
                syncable: true,
            })
        ).toBe(conflict);
        expect(
            reduceArticleDraftSyncState(conflict, {
                type: "resolveConflict",
                revision: 2,
                syncable: true,
            })
        ).toEqual({
            connectivity: "online",
            document: "dirty",
            sync: "queued",
            revision: 2,
            retryCount: 0,
        });
    });

    it("keeps permanent failures blocked until the document changes", () => {
        const syncing = reduceArticleDraftSyncState(dirtyOnlineState(), {
            type: "syncStarted",
            revision: 1,
        });
        const blocked = reduceArticleDraftSyncState(syncing, {
            type: "syncBlocked",
            revision: 1,
            error: "invalid article",
        });
        const offline = reduceArticleDraftSyncState(blocked, {
            type: "networkLost",
        });
        const online = reduceArticleDraftSyncState(offline, {
            type: "networkRestored",
            syncable: true,
        });

        expect(offline).toMatchObject({
            connectivity: "offline",
            document: "dirty",
            sync: "blocked",
        });
        expect(online).toMatchObject({
            connectivity: "online",
            document: "dirty",
            sync: "blocked",
        });
        expect(
            reduceArticleDraftSyncState(online, {
                type: "edit",
                revision: 2,
                syncable: true,
            })
        ).toEqual({
            connectivity: "online",
            document: "dirty",
            sync: "queued",
            revision: 2,
            retryCount: 0,
        });
    });

    it("restores interrupted work according to current connectivity", () => {
        const interrupted: ArticleDraftSyncState = {
            connectivity: "online",
            document: "dirty",
            sync: "syncing",
            revision: 4,
            retryCount: 2,
        };

        expect(restoreArticleDraftSyncState(true, true, interrupted, 1000)).toEqual({
            connectivity: "offline",
            document: "dirty",
            sync: "idle",
            revision: 4,
            retryCount: 2,
            lastError: undefined,
            nextRetryAt: undefined,
        });
        expect(restoreArticleDraftSyncState(false, true, interrupted, 1000)).toEqual({
            connectivity: "online",
            document: "dirty",
            sync: "queued",
            revision: 4,
            retryCount: 2,
            lastError: undefined,
            nextRetryAt: undefined,
        });
    });

    it("restores a detected server-version conflict without queuing synchronization", () => {
        expect(restoreArticleDraftSyncState(false, true, undefined, 1000, true)).toEqual({
            connectivity: "online",
            document: "dirty",
            sync: "conflict",
            revision: 1,
            retryCount: 0,
            lastError: undefined,
        });
    });
});
