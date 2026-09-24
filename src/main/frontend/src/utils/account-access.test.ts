import { beforeEach, describe, expect, it } from "@jest/globals";
import { hasAction, actionForPath } from "./account-access";
import { addToCache, getCacheByKey } from "./cache";
import { BasicUserInfo } from "../type";

describe("account permission and cache boundary", () => {
    beforeEach(() => { localStorage.clear(); window.__SS_DATA__ = { user: { actions: ["article.read"] } as BasicUserInfo, key: "one", pageBuildId: "test", systemNotification: "" }; });
    it("denies missing capabilities and uses the backend action catalogue", () => {
        expect(hasAction("article.read")).toBe(true);
        expect(hasAction("article.publish")).toBe(false);
        expect(actionForPath("/members")).toBe("member.manage");
        expect(actionForPath("/oauth/authorize?request_id=example")).toBe("oauth.grant.manage");
    });
    it("does not reuse another signed-in session's article or grant cache", () => {
        addToCache("/article", { privateText: "first session" });
        addToCache("/oauth", { grants: ["first session"] });
        window.__SS_DATA__!.key = "two";
        expect(getCacheByKey("/article")).toBeUndefined();
        expect(getCacheByKey("/oauth")).toBeUndefined();
    });
    it("keeps consent state out of persistent browser storage", () => {
        addToCache("/oauth/authorize?request_id=example", { csrf: "csrf-example" });
        expect(getCacheByKey("/oauth/authorize?request_id=example").csrf).toBe("csrf-example");
        expect(JSON.stringify(localStorage)).not.toContain("csrf-example");
    });
});
