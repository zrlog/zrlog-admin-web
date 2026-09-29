import { act } from "react";
import { createRoot } from "react-dom/client";
import { Simulate } from "react-dom/test-utils";
import { describe, expect, it, jest } from "@jest/globals";
import ConfigProviderApp, { changeAppState, getAppState } from "./ConfigProviderApp";
import { applyUserPreferences } from "../utils/user-preferences";
import { getRes } from "../utils/constants";

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
jest.mock("./AppInit", () => ({
    __esModule: true,
    getLangByRes: () => "zh_CN",
    getThemeByRes: () => "default",
    isCompactModeByRes: () => false,
    isDarkModeByRes: () => require("../utils/constants").getRes().admin_darkMode ?? false,
    getColorPrimaryByRes: () => require("../utils/constants").getRes().admin_color_primary ?? "#1677ff",
    default: () => {
        const React = require("react");
        const { token } = require("antd").theme.useToken();
        const [draft, setDraft] = React.useState("");
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
    it("applies color, dark, density and language immediately without remounting the page", async () => {
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
