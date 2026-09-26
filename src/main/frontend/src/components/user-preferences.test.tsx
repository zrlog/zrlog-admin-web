import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { Simulate } from "react-dom/test-utils";
import { MemoryRouter, NavigateFunction, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import UserPreferencesForm from "./user-preferences";
import UserSettingsLayout from "./common/UserSettingsLayout";
import { Grid } from "antd";
import { USER_ROUTES, USER_PREFERENCE_PAGES } from "../utils/account-page-routes";
import { getRes } from "../utils/constants";
import { resolveUserPreferences, UserPreferences, UserPreferencesResponse } from "../utils/user-preferences";

const mockGet = jest.fn<Promise<any>, any[]>();
const mockPost = jest.fn<Promise<any>, any[]>();
const mockAxios = { get: mockGet, post: mockPost };
jest.mock("../base/AppBase", () => ({ useAxiosBaseInstance: () => mockAxios }));
const mockChangeAppState = jest.fn();
jest.mock("../base/ConfigProviderApp", () => ({
    changeAppState: (...args: any[]) => mockChangeAppState(...args),
    getAppState: () => ({ compactMode: false }),
}));
jest.mock("../base/AppInit", () => ({
    isSupportDarkMode: (theme: string) => ["default", "antd"].includes(theme),
    isDarkModeByRes: () => require("../utils/constants").getRes().admin_darkMode,
    getColorPrimaryByRes: () => require("../utils/constants").getRes().admin_color_primary,
}));

describe("personal preferences", () => {
    const previousEnv = process.env;
    let root: Root;
    let container: HTMLDivElement;
    let navigate: NavigateFunction;
    const updateCache = jest.fn();
    const Location = () => {
        navigate = useNavigate();
        const location = useLocation();
        return <output>{location.pathname + location.search + location.hash}</output>;
    };
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
        process.env = { ...previousEnv, NODE_ENV: "production" };
        jest.spyOn(Grid, "useBreakpoint").mockReturnValue({ md: true });
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
        updateCache.mockClear();
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
    });
    afterEach(() => {
        process.env = previousEnv;
        act(() => root.unmount());
        container.remove();
        jest.restoreAllMocks();
        delete (globalThis as any).IS_REACT_ACT_ENVIRONMENT;
        delete (globalThis as any).MessageChannel;
    });
    const PreferencePages = ({ data, offline }: { data: UserPreferencesResponse; offline: boolean }) => (
        <Routes>
            {USER_PREFERENCE_PAGES.flatMap((page) =>
                ["", ".html"].map((suffix) => (
                    <Route
                        key={page + suffix}
                        path={USER_ROUTES[page] + suffix}
                        element={
                            <UserSettingsLayout activeKey={page}>
                                <UserPreferencesForm
                                    key={page}
                                    activePage={page}
                                    data={data}
                                    offline={offline}
                                    updateCache={updateCache}
                                />
                            </UserSettingsLayout>
                        }
                    />
                ))
            )}
        </Routes>
    );
    const render = async (
        offline = false,
        entry = USER_ROUTES.appearance as string,
        data: UserPreferencesResponse = response
    ) => {
        await act(async () =>
            root.render(
                <MemoryRouter initialEntries={[entry]}>
                    <Location />
                    <PreferencePages data={data} offline={offline} />
                </MemoryRouter>
            )
        );
    };
    const button = (text: string) =>
        Array.from(container.querySelectorAll("button")).find((item) => item.textContent === text)!;

    const toggle = (id: string) => act(() => container.querySelector<HTMLButtonElement>(`#${id}`)!.click());
    const submit = async () => {
        await act(async () => Simulate.submit(container.querySelector("form")!));
    };

    it.each(["", ".html"])(
        "opens independent preference pages and restores saved values after leaving (%s)",
        async (suffix) => {
            window.__SS_DATA__!.resourceInfo = { lang: "zh_CN", staticPage: suffix === ".html" };
            const url = (page: keyof typeof USER_ROUTES) => USER_ROUTES[page] + suffix + "?v=test";
            await render(false, url("writing"));
            const selected = () => container.querySelector('nav a[aria-current="page"]')?.textContent;
            const item = (title: string) =>
                Array.from(container.querySelectorAll<HTMLElement>("nav a")).find(
                    (link) => link.textContent === title
                )!;
            expect(selected()).toBe(getRes().user.preferences.writingTitle);
            expect(container.querySelector("#articlePageSize")).not.toBeNull();
            expect(container.querySelector("#appearance_compactMode")).toBeNull();
            expect(container.querySelector('[role="tablist"]')).toBeNull();
            await act(async () => item(getRes().user.preferences.appearanceTitle).click());
            toggle("appearance_compactMode");
            await act(async () => item(getRes().user.preferences.assistantTitle).click());
            expect(container.querySelector("output")?.textContent).toBe(url("assistant"));
            expect(container.querySelector("#assistant_knowledgeScope")).not.toBeNull();
            expect(container.querySelector("#appearance_compactMode")).toBeNull();
            expect(getRes().admin_compactMode).toBe(false);
            await act(async () => navigate(-1));
            expect(selected()).toBe(getRes().user.preferences.appearanceTitle);
            expect(container.querySelector("#appearance_compactMode")?.getAttribute("aria-checked")).toBe("false");
            await act(async () => navigate(1));
            expect(selected()).toBe(getRes().user.preferences.assistantTitle);
            expect(mockPost).not.toHaveBeenCalled();
        }
    );

    it("shows effective values and previews edits without pinning untouched defaults", async () => {
        await render();
        expect(container.querySelector("#assistant_knowledgeScope")).toBeNull();
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

    it("accepts refreshed page data but preserves unsaved edits until undo", async () => {
        await render();
        const refreshed = {
            overrides: {},
            defaults: { ...defaults, appearance: { ...defaults.appearance, colorPrimary: "#654321" } },
            effective: {},
        } as UserPreferencesResponse;
        refreshed.effective = resolveUserPreferences(refreshed.defaults, refreshed.overrides);
        await render(false, USER_ROUTES.appearance, refreshed);
        expect(container.querySelector("#appearance_darkMode")?.getAttribute("aria-checked")).toBe("true");
        toggle("appearance_compactMode");
        await render(false, USER_ROUTES.appearance, response);
        expect(container.querySelector("#appearance_compactMode")?.getAttribute("aria-checked")).toBe("true");
        expect(mockGet).not.toHaveBeenCalled();
        act(() => button(getRes().user.preferences.undo).click());
        expect(container.querySelector("#appearance_compactMode")?.getAttribute("aria-checked")).toBe("false");
        expect(container.querySelector("#appearance_darkMode")?.getAttribute("aria-checked")).toBe("false");
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
        expect(updateCache).toHaveBeenCalledWith(
            { overrides: {}, defaults, effective: defaults },
            USER_ROUTES.appearance
        );
        toggle("appearance_darkMode");
        expect(getRes().admin_darkMode).toBe(false);
        act(() => button(getRes().user.preferences.undo).click());
        expect(getRes().admin_darkMode).toBe(true);
        expect(button(getRes().user.preferences.save).disabled).toBe(true);
    });

    it("resets only writing preferences and preserves refreshed values from other pages", async () => {
        const overrides: UserPreferences = {
            ...response.overrides,
            articlePageSize: 50,
            editor: { autoSaveInterval: 10 },
            assistant: { knowledgeScope: "off" },
        };
        const data = { overrides, defaults, effective: resolveUserPreferences(defaults, overrides) };
        await render(false, USER_ROUTES.writing, data);
        act(() => button(getRes().user.preferences.reset).click());
        const latestOverrides: UserPreferences = {
            ...overrides,
            appearance: { darkMode: true },
            assistant: { knowledgeScope: "own_all" },
        };
        await render(false, USER_ROUTES.writing, {
            overrides: latestOverrides,
            defaults,
            effective: resolveUserPreferences(defaults, latestOverrides),
        });
        mockPost.mockResolvedValue({ data: { error: 9012, message: "Invalid" } });
        await submit();
        expect(mockPost).toHaveBeenCalledWith("/api/admin/user/updatePreferences", {
            appearance: { darkMode: true },
            assistant: { knowledgeScope: "own_all" },
        });
        expect(container.querySelector("#appearance_darkMode")).toBeNull();
        expect(container.querySelector("#assistant_knowledgeScope")).toBeNull();
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
        await render(false, USER_ROUTES.assistant);
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
        expect(container.textContent).toContain("Connect to view and edit preferences");
        expect(container.textContent).toContain(getRes().user.preferences.offline);
        expect(mockGet).not.toHaveBeenCalled();
        expect(container.querySelector("form")).toBeNull();
    });
});
