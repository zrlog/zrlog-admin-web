import { describe, expect, it } from "@jest/globals";
import {
    canPinArticle,
    finishArticlePinningRequest,
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

    it("rejects a second pinning request until the active request finishes", () => {
        const guard = { current: false };

        expect(tryBeginArticlePinningRequest(guard)).toBe(true);
        expect(tryBeginArticlePinningRequest(guard)).toBe(false);

        finishArticlePinningRequest(guard);
        expect(tryBeginArticlePinningRequest(guard)).toBe(true);
    });
});
