import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { Simulate } from "react-dom/test-utils";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import UserPreferencesForm from "./user-preferences";
import { getRes } from "../utils/constants";
import { resolveUserPreferences, UserPreferences } from "../utils/user-preferences";

const mockGet = jest.fn<Promise<any>, any[]>();
const mockPost = jest.fn<Promise<any>, any[]>();
const mockAxios = { get: mockGet, post: mockPost };
jest.mock("../base/AppBase", () => ({ useAxiosBaseInstance: () => mockAxios }));
const mockChangeAppState = jest.fn();
jest.mock("../base/ConfigProviderApp", () => ({ changeAppState: (...args: any[]) => mockChangeAppState(...args) }));
jest.mock("../base/AppInit", () => ({
    isSupportDarkMode: (theme: string) => ["default", "antd"].includes(theme),
    isDarkModeByRes: () => require("../utils/constants").getRes().admin_darkMode,
    getColorPrimaryByRes: () => require("../utils/constants").getRes().admin_color_primary,
}));

describe("personal preferences", () => {
    let root: Root;
    let container: HTMLDivElement;
    const defaults: UserPreferences = {
        language: "zh_CN",
        appearance: { theme: "default", darkMode: true, compactMode: false, colorPrimary: "#1677ff" },
        articlePageSize: 20,
        editor: { autoSaveInterval: 5 },
        assistant: { knowledgeScope: "own_public" },
    };
    const response = {
        overrides: { appearance: { darkMode: false, colorPrimary: "#123456" } },
        defaults,
        effective: resolveUserPreferences(defaults, { appearance: { darkMode: false, colorPrimary: "#123456" } }),
    };

    beforeEach(() => {
        (globalThis as any).MessageChannel = class {
            port1 = { onmessage: () => undefined };
            port2 = { postMessage: () => Promise.resolve().then(() => this.port1.onmessage()) };
        };
        (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
        global.ResizeObserver = class {
            observe() {
                return undefined;
            }
            unobserve() {
                return undefined;
            }
            disconnect() {
                return undefined;
            }
        };
        window.matchMedia = (() => ({
            matches: false,
            addListener: () => undefined,
            removeListener: () => undefined,
            addEventListener: () => undefined,
            removeEventListener: () => undefined,
            dispatchEvent: () => false,
        })) as any;
        const computedStyle = window.getComputedStyle;
        jest.spyOn(window, "getComputedStyle").mockImplementation((element) => computedStyle(element));
        window.__SS_DATA__ = {
            key: "preferences-test",
            pageBuildId: "test",
            systemNotification: "",
            user: null,
            resourceInfo: { lang: "zh_CN" },
        };
        mockGet.mockReset().mockResolvedValue({ data: { error: 0, data: response } });
        mockPost.mockReset();
        mockChangeAppState.mockClear();
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
    });
    afterEach(() => {
        act(() => root.unmount());
        container.remove();
        jest.restoreAllMocks();
        delete (globalThis as any).IS_REACT_ACT_ENVIRONMENT;
        delete (globalThis as any).MessageChannel;
    });
    const render = async (offline = false) => {
        await act(async () => {
            root.render(<UserPreferencesForm offline={offline} />);
        });
    };
    const button = (text: string) =>
        Array.from(container.querySelectorAll("button")).find((item) => item.textContent === text)!;

    const toggle = (id: string) => act(() => container.querySelector<HTMLButtonElement>(`#${id}`)!.click());
    const submit = async () => {
        await act(async () => Simulate.submit(container.querySelector("form")!));
    };

    it("shows effective values and previews edits without pinning untouched defaults", async () => {
        await render();
        expect(container.textContent).toContain(getRes().user.preferences.scopeOwnPublic);
        expect(container.querySelector("#appearance_darkMode")?.getAttribute("aria-checked")).toBe("false");
        toggle("appearance_compactMode");
        expect(mockChangeAppState).toHaveBeenLastCalledWith(
            expect.objectContaining({ compactMode: true, dark: false, colorPrimary: "#123456" })
        );
        expect(getRes().admin_compactMode).toBe(true);
        expect(mockPost).not.toHaveBeenCalled();
        mockPost.mockResolvedValue({ data: { error: 9012, message: "Invalid" } });
        await submit();
        expect(mockPost).toHaveBeenCalledWith("/api/admin/user/updatePreferences", {
            appearance: { darkMode: false, colorPrimary: "#123456", compactMode: true },
        });
        expect(container.querySelector("#appearance_compactMode")?.getAttribute("aria-checked")).toBe("true");
        act(() => root.render(null));
        expect(getRes().admin_compactMode).toBe(false);
    });

    it("previews reset, persists only on save, and uses the saved result for later undo", async () => {
        await render();
        act(() => button(getRes().user.preferences.reset).click());
        expect(getRes().admin_darkMode).toBe(true);
        expect(getRes().admin_color_primary).toBe("#1677ff");
        expect(mockPost).not.toHaveBeenCalled();
        mockPost.mockResolvedValue({ data: { error: 0, data: { overrides: {}, defaults, effective: defaults } } });
        await submit();
        expect(mockPost).toHaveBeenCalledWith("/api/admin/user/updatePreferences", {});
        toggle("appearance_darkMode");
        expect(getRes().admin_darkMode).toBe(false);
        act(() => button(getRes().user.preferences.undo).click());
        expect(getRes().admin_darkMode).toBe(true);
        expect(button(getRes().user.preferences.save).disabled).toBe(true);
    });

    it("previews language immediately and rolls back on leaving", async () => {
        await render();
        await act(async () => {
            Simulate.mouseDown(container.querySelector("#language")!);
        });
        await act(async () => {
            Array.from(document.querySelectorAll<HTMLElement>(".ant-select-item-option"))
                .find((item) => item.getAttribute("title") === getRes().websiteAdmin.language.english)!
                .click();
        });
        expect(document.documentElement.lang).toBe("en");
        expect(mockChangeAppState).toHaveBeenLastCalledWith(expect.objectContaining({ lang: "en_US" }));
        expect(container.textContent).toContain("Appearance");
        act(() => root.render(null));
        expect(document.documentElement.lang).toBe("zh");
    });

    it("uses one knowledge scope selector and saves it without exposing permission toggles", async () => {
        await render();
        expect(container.querySelector("#assistant_allArticles")).toBeNull();
        await act(async () => Simulate.mouseDown(container.querySelector("#assistant_knowledgeScope")!));
        expect(document.body.textContent).not.toContain(getRes().user.preferences.scopeAccessibleAll);
        await act(async () => {
            Array.from(document.querySelectorAll<HTMLElement>(".ant-select-item-option"))
                .find((item) => item.getAttribute("title") === getRes().user.preferences.scopeOwnAll)!
                .click();
        });
        mockPost.mockResolvedValue({ data: { error: 9012, message: "Invalid" } });
        await submit();
        expect(mockPost).toHaveBeenCalledWith("/api/admin/user/updatePreferences", {
            appearance: response.overrides.appearance,
            assistant: { knowledgeScope: "own_all" },
        });
    });

    it("supports English and prevents offline writes", async () => {
        window.__SS_DATA__!.resourceInfo = { lang: "en_US" };
        await render(true);
        expect(container.textContent).toContain("Connect to view and edit personal settings");
        expect(container.textContent).toContain(getRes().user.preferences.offline);
        expect(mockGet).not.toHaveBeenCalled();
        expect(container.querySelector("form")).toBeNull();
    });
});
