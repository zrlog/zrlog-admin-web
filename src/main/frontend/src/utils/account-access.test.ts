import { beforeEach, describe, expect, it } from "@jest/globals";
import { hasAction, actionForPath } from "./account-access";
import { addToCache, getCacheByKey } from "./cache";
import { BasicUserInfo } from "../type";

describe("account permission and cache boundary", () => {
    beforeEach(() => {
        localStorage.clear();
        window.__SS_DATA__ = {
            user: { actions: ["article.read"] } as BasicUserInfo,
            key: "one",
            pageBuildId: "test",
            systemNotification: "",
        };
    });
    it("denies missing capabilities and uses the backend action catalogue", () => {
        expect(hasAction("article.read")).toBe(true);
        expect(hasAction("article.publish")).toBe(false);
        expect(actionForPath("/website/members")).toBe("member.manage");
        expect(actionForPath("/website/members.html?v=1")).toBe("member.manage");
        expect(actionForPath("/user/security")).toBe("account.self");
        expect(actionForPath("/user/preferences")).toBe("account.self");
        expect(actionForPath("/user/applications/authorize?request_id=example")).toBe("oauth.grant.manage");
    });
    it("does not reuse another signed-in session's article or grant cache", () => {
        addToCache("/article", { privateText: "first session" });
        addToCache("/user/applications", { grants: ["first session"] });
        window.__SS_DATA__!.key = "two";
        expect(getCacheByKey("/article")).toBeUndefined();
        expect(getCacheByKey("/user/applications")).toBeUndefined();
    });
    it("keeps consent state out of persistent browser storage", () => {
        addToCache("/user/applications/authorize?request_id=example", { csrf: "csrf-example" });
        expect(getCacheByKey("/user/applications/authorize?request_id=example").csrf).toBe("csrf-example");
        expect(JSON.stringify(localStorage)).not.toContain("csrf-example");
    });
    it("keeps site member data in the current session only", () => {
        addToCache("/website/members", { members: ["member-cache-example"] });
        expect(getCacheByKey("/website/members").members).toEqual(["member-cache-example"]);
        expect(JSON.stringify(localStorage)).not.toContain("member-cache-example");
        window.__SS_DATA__!.key = "two";
        expect(getCacheByKey("/website/members")).toBeUndefined();
    });
    it("ignores the old profile-shaped preferences cache until preferences arrive", () => {
        const key = "/user/preferences";
        addToCache(key, { userId: 1, userName: "old profile" });
        expect(getCacheByKey(key)).toBeUndefined();
        const preferences = { overrides: {}, defaults: { language: "zh_CN" }, effective: { language: "zh_CN" } };
        addToCache(key, preferences);
        expect(getCacheByKey(key)).toEqual(preferences);
    });
});
