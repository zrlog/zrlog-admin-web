import { describe, expect, it } from "@jest/globals";
import {
    applyArticlePinningSnapshot,
    canPinArticle,
    finishArticlePinningRequest,
    resolveArticleStickyMap,
    toStickyMap,
    tryBeginArticlePinningRequest,
} from "./article-pinning";

describe("article pinning helpers", () => {
    it("only allows published public articles to be pinned", () => {
        expect(canPinArticle({ id: 1, privacy: false, rubbish: false })).toBe(true);
        expect(canPinArticle({ id: 2, privacy: true, rubbish: false })).toBe(false);
        expect(canPinArticle({ id: 3, privacy: false, rubbish: true })).toBe(false);
    });

    it("builds the latest sticky lookup from the server order", () => {
        const sticky = toStickyMap([
            { logId: 3, title: "Third", sticky: 2 },
            { logId: 1, title: "First", sticky: 1 },
        ]);

        expect(sticky.get(3)).toBe(2);
        expect(sticky.get(1)).toBe(1);
        expect(sticky.has(2)).toBe(false);
    });

    it("uses fresh row values immediately when cached rows are replaced", () => {
        const cachedRows = [{ id: 1, sticky: 0 }];
        const freshRows = [{ id: 1, sticky: 1 }];
        const override = {
            sourceRows: cachedRows,
            stickyById: new Map([[1, 0]]),
        };

        expect(resolveArticleStickyMap(cachedRows, override).get(1)).toBe(0);
        expect(resolveArticleStickyMap(freshRows, override).get(1)).toBe(1);
    });

    it("applies a complete pinning snapshot and clears removed pins", () => {
        const rows = [
            { id: 1, sticky: 1, title: "First" },
            { id: 2, sticky: 2, title: "Second" },
        ];

        expect(
            applyArticlePinningSnapshot(rows, [{ logId: 2, sticky: 1, title: "Second" }]).map((row) => ({
                id: row.id,
                sticky: row.sticky,
            }))
        ).toEqual([
            { id: 1, sticky: 0 },
            { id: 2, sticky: 1 },
        ]);
    });

    it("rejects a second pinning request until the active request finishes", () => {
        const guard = { current: false };

        expect(tryBeginArticlePinningRequest(guard)).toBe(true);
        expect(tryBeginArticlePinningRequest(guard)).toBe(false);

        finishArticlePinningRequest(guard);
        expect(tryBeginArticlePinningRequest(guard)).toBe(true);
    });
});
