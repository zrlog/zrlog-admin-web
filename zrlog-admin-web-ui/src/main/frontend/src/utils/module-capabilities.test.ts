import { beforeEach, describe, expect, it } from "@jest/globals";
import { isModuleEnabled, isFeaturePathEnabled } from "./module-capabilities";
import { getRes, setRes } from "./constants";

describe("enabled backend modules", () => {
    beforeEach(() => {
        window.__SS_DATA__ = { key: "modules", pageBuildId: "test", systemNotification: "", user: null };
    });
    it("preserves older server payloads and copies capabilities through runtime resource normalization", () => {
        expect(isModuleEnabled("ai")).toBe(true);
        setRes({ capabilities: { ai: false, access: true, mcp: false } });
        expect(getRes().capabilities).toEqual({ ai: false, access: true, mcp: false });
        expect(isModuleEnabled("ai")).toBe(false);
        expect(isModuleEnabled("access")).toBe(true);
        expect(isModuleEnabled("mcp")).toBe(false);
    });
    it("hides AI pages independently from application access and ordinary account preferences", () => {
        setRes({ capabilities: { ai: false, access: true } });
        expect(isFeaturePathEnabled("website/ai.html")).toBe(false);
        expect(isFeaturePathEnabled("/user/preferences/assistant?v=1")).toBe(false);
        expect(isFeaturePathEnabled("/user/preferences/writing")).toBe(true);
        expect(isFeaturePathEnabled("/user/applications/tokens")).toBe(true);
        setRes({ capabilities: { ai: true, access: false } });
        expect(isFeaturePathEnabled("/website/ai")).toBe(true);
        expect(isFeaturePathEnabled("user/applications/authorize.html?request_id=1")).toBe(false);
        expect(isFeaturePathEnabled("/website/webhook")).toBe(false);
        expect(isFeaturePathEnabled("/user/security")).toBe(true);
    });
});
