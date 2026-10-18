import { act } from "react";
import { createRoot } from "react-dom/client";
import { Simulate } from "react-dom/test-utils";
import { describe, expect, it, jest } from "@jest/globals";
import ConfigProviderApp, { changeAppState, getAppState } from "./ConfigProviderApp";
import { applyUserPreferences } from "../utils/user-preferences";
import { getRes } from "../utils/constants";
import SsData from "./SsData";
import type { AppState } from "../type";

jest.mock("antd/es/locale/zh_CN", () => require("antd/lib/locale/zh_CN"));
jest.mock("antd/es/locale/en_US", () => require("antd/lib/locale/en_US"));
jest.mock("antd-style", () => {
    const { jest: mockJest } = require("@jest/globals");
    Object.defineProperty(globalThis, "matchMedia", {
        writable: true,
        value: (media: string) => ({
            matches: false,
            media,
            addListener: mockJest.fn(),
            removeListener: mockJest.fn(),
            addEventListener: mockJest.fn(),
            removeEventListener: mockJest.fn(),
        }),
    });
    return mockJest.requireActual("antd-style");
});

let mockMounts = 0;
const mockInitialAppearances: AppState[] = [];
jest.mock("./AppInit", () => ({
    __esModule: true,
    getLangByRes: () => require("../utils/constants").getRes().lang ?? "zh_CN",
    getThemeByRes: () => require("../utils/constants").getRes().admin_theme ?? "default",
    isCompactModeByRes: () => require("../utils/constants").getRes().admin_compactMode ?? false,
    isDarkModeByRes: () => require("../utils/constants").getRes().admin_darkMode ?? false,
    getColorPrimaryByRes: () => require("../utils/constants").getRes().admin_color_primary ?? "#1677ff",
    default: () => {
        const React = require("react");
        const { token } = require("antd").theme.useToken();
        const [draft, setDraft] = React.useState("");
        React.useLayoutEffect(() => {
            mockInitialAppearances.push({ ...require("./ConfigProviderApp").getAppState() });
        }, []);
        React.useEffect(() => {
            mockMounts++;
        }, []);
        return React.createElement("input", {
            "aria-label": "draft",
            value: draft,
            "data-primary": token.colorPrimary,
            "data-lang": require("../utils/constants").getRes().lang,
            onChange: (event: any) => setDraft(event.target.value),
        });
    },
}));

describe("global appearance updates", () => {
    it.each(
        (
            [
                { theme: "default", dark: true, color: "#00875a" },
                { theme: "desk", dark: false, color: "#172033" },
                { theme: "antd", dark: true, color: "#00875a" },
            ] as const
        ).flatMap((appearance) => [false, true].map((cached) => ({ ...appearance, cached })))
    )("uses $theme appearance on the first render (cached=$cached)", async ({ theme, dark, color, cached }) => {
        (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
        delete window.__SS_DATA__;
        window.history.replaceState(null, "", "/admin/dev/ui");
        const base = document.createElement("base");
        base.href = "http://localhost/";
        document.head.appendChild(base);
        const injected = document.createElement("pre");
        injected.id = "__SS_DATA__";
        localStorage.clear();
        if (cached) {
            localStorage.setItem(
                "localhost_cache_page_data_session_review",
                JSON.stringify({
                    "/user": {
                        userId: 1,
                        userName: "owner",
                        key: "review",
                        header: "",
                        language: "en_US",
                        appearance: { theme, darkMode: dark, compactMode: true, colorPrimary: color },
                    },
                })
            );
        }
        injected.innerText = JSON.stringify({
            data: true,
            key: "review",
            user: null,
            pageBuildId: "test",
            systemNotification: "",
            resourceInfo: cached
                ? { lang: "zh_CN", staticPage: true, admin_theme: "default", admin_darkMode: false }
                : {
                      lang: "en_US",
                      admin_theme: theme,
                      admin_darkMode: dark,
                      admin_compactMode: true,
                      admin_color_primary: color,
                  },
        });
        document.body.appendChild(injected);
        const container = document.createElement("div");
        document.body.appendChild(container);
        const root = createRoot(container);
        mockInitialAppearances.length = 0;
        try {
            await act(async () =>
                root.render(
                    <SsData>
                        <ConfigProviderApp />
                    </SsData>
                )
            );
            expect(mockInitialAppearances).toHaveLength(1);
            expect(mockInitialAppearances[0]).toMatchObject({
                lang: "en_US",
                theme,
                dark,
                compactMode: true,
                colorPrimary: color,
            });
            expect(container.querySelector("input")!.dataset.lang).toBe("en_US");
            expect(document.body.className).toBe(dark ? "dark" : "light");
        } finally {
            act(() => root.unmount());
            container.remove();
            injected.remove();
            base.remove();
            delete window.__SS_DATA__;
            localStorage.clear();
            (globalThis as any).IS_REACT_ACT_ENVIRONMENT = false;
        }
    });

    it("applies color, dark, density and language immediately without remounting the page", async () => {
        mockMounts = 0;
        (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
        window.__SS_DATA__ = {
            key: "appearance",
            pageBuildId: "test",
            systemNotification: "",
            user: null,
            resourceInfo: { lang: "zh_CN", homeUrl: "http://localhost/" },
        };
        window.history.replaceState(null, "", "/admin/index");
        const base = document.createElement("base");
        base.href = "http://localhost/";
        document.head.appendChild(base);
        const container = document.createElement("div");
        document.body.appendChild(container);
        const root = createRoot(container);
        try {
            await act(async () => root.render(<ConfigProviderApp />));
            const input = container.querySelector("input")!;
            act(() => Simulate.change(input, { target: { value: "unsaved draft" } } as any));
            const appearance = {
                theme: "default" as const,
                darkMode: false,
                compactMode: false,
                colorPrimary: "#1677ff",
            };
            for (const next of [
                { colorPrimary: "#00875a" },
                { darkMode: true },
                { compactMode: true },
                { compactMode: false },
            ]) {
                Object.assign(appearance, next);
                await act(async () => applyUserPreferences({ language: "zh_CN", appearance }));
                expect(getAppState()).toMatchObject({
                    colorPrimary: appearance.colorPrimary,
                    dark: appearance.darkMode,
                    compactMode: appearance.compactMode,
                });
                expect(document.body.className).toBe(appearance.darkMode ? "dark" : "light");
                expect(container.querySelector("input")).toBe(input);
                expect(input.value).toBe("unsaved draft");
            }
            await act(async () => applyUserPreferences({ language: "en_US", appearance }));
            expect(input.dataset.lang).toBe("en_US");
            expect(document.documentElement.lang).toBe("en");
            expect(getRes().lang).toBe(getAppState().lang);
            for (const theme of [undefined, null, "", "unknown-theme"]) {
                await act(async () => changeAppState({ theme: "desk" }));
                expect(container.querySelector("[data-zrlog-desk-style]")).not.toBeNull();
                await act(async () => changeAppState({ theme } as any));
                expect(getAppState().theme).toBe("default");
                expect(container.querySelector("[data-zrlog-material-controls]")).not.toBeNull();
                expect(container.querySelector("[data-zrlog-desk-style]")).toBeNull();
                expect(container.querySelector("input")).toBe(input);
                expect(input.value).toBe("unsaved draft");
            }
            expect(mockMounts).toBe(1);
        } finally {
            act(() => root.unmount());
            container.remove();
            base.remove();
            (globalThis as any).IS_REACT_ACT_ENVIRONMENT = false;
        }
    });
});
