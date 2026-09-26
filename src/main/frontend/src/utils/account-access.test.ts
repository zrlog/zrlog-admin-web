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
        expect(actionForPath("/user/preferences/appearance")).toBe("account.self");
        expect(actionForPath("/user/applications/authorize?request_id=example")).toBe("oauth.grant.manage");
    });
    it("does not reuse another signed-in session's article or grant cache", () => {
        addToCache("/article", { privateText: "first session" });
        addToCache("/user/applications/grants", { grants: ["first session"] });
        window.__SS_DATA__!.key = "two";
        expect(getCacheByKey("/article")).toBeUndefined();
        expect(getCacheByKey("/user/applications/grants")).toBeUndefined();
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
    it("isolates independent page caches and keeps all application pages out of persistent storage", () => {
        for (const page of ["tokens", "grants", "clients"]) {
            const key = `/user/applications/${page}`;
            addToCache(key, { marker: `private-${page}` });
            expect(getCacheByKey(key)).toEqual({ marker: `private-${page}` });
            expect(JSON.stringify(localStorage)).not.toContain(`private-${page}`);
        }
        addToCache("/user/preferences", { userName: "obsolete-profile-cache" });
        expect(getCacheByKey("/user/preferences/appearance")).toBeUndefined();
        addToCache("/user/preferences/appearance", { marker: "appearance-cache" });
        expect(getCacheByKey("/user/preferences/writing")).toBeUndefined();
        expect(actionForPath("/user/applications/clients.html?v=1")).toBe("oauth.client.manage");
        expect(actionForPath("/user/applications/grants")).toBe("oauth.grant.manage");
    });
});
