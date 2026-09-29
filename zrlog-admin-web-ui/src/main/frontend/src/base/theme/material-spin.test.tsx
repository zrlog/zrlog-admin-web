import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { ConfigProvider, Spin } from "antd";
import type { SpinProps } from "antd";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import type { AppState } from "../../type";
import { useThemeConfig } from "../../utils/theme-utils";

jest.mock("antd-style", () => {
    const { jest: mockJest } = require("@jest/globals");
    // antd-style reads the browser preference when the module is loaded.
    Object.defineProperty(globalThis, "matchMedia", {
        writable: true,
        value: (query: string) => ({
            matches: false,
            media: query,
            addListener: mockJest.fn(),
            removeListener: mockJest.fn(),
            addEventListener: mockJest.fn(),
            removeEventListener: mockJest.fn(),
        }),
    });
    return mockJest.requireActual("antd-style");
});

const ThemePreview = ({ theme, ...spinProps }: SpinProps & { theme: AppState["theme"] }) => {
    const config = useThemeConfig({
        theme,
        colorPrimary: "#00875a",
        dark: false,
        compactMode: false,
        lang: "zh_CN",
        offline: false,
    });
    return (
        <ConfigProvider {...config}>
            <Spin {...spinProps} />
        </ConfigProvider>
    );
};

const actEnvironment = globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean };

describe("default-theme Spin integration", () => {
    let container: HTMLDivElement;
    let root: Root;

    beforeEach(() => {
        actEnvironment.IS_REACT_ACT_ENVIRONMENT = true;
        jest.useFakeTimers();
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
    });

    afterEach(() => {
        act(() => root.unmount());
        container.remove();
        jest.useRealTimers();
        actEnvironment.IS_REACT_ACT_ENVIRONMENT = false;
    });

    it("restores each independent theme's indicator and supports switching back without remounting", () => {
        for (const theme of [
            "default",
            "desk",
            "antd",
            "glass",
            "shadcn",
            "geek",
            "cartoon",
            "illustration",
            "bootstrap",
            "default",
        ] as const) {
            act(() => root.render(<ThemePreview theme={theme} />));
            expect(container.querySelector(".zrlog-material-spin") !== null).toBe(theme === "default");
            expect(container.querySelectorAll(".ant-spin-dot-item").length).toBe(theme === "default" ? 0 : 4);
        }
    });

    it("preserves Spin delay, aria-busy and completion behavior", () => {
        act(() =>
            root.render(
                <ThemePreview theme="default" delay={300} spinning>
                    <div>Content</div>
                </ThemePreview>
            )
        );
        expect(container.querySelector(".zrlog-material-spin")).toBeNull();
        act(() => jest.advanceTimersByTime(300));
        expect(container.querySelector(".zrlog-material-spin")).not.toBeNull();
        expect(container.querySelector(".ant-spin")?.getAttribute("aria-busy")).toBe("true");
        act(() =>
            root.render(
                <ThemePreview theme="default" spinning={false}>
                    <div>Content</div>
                </ThemePreview>
            )
        );
        expect(container.querySelector(".zrlog-material-spin")).toBeNull();
        expect(container.querySelector(".ant-spin")?.getAttribute("aria-busy")).toBe("false");
        expect(container.textContent).toContain("Content");
    });

    it("keeps explicit indicators and semantic indicator styles usable", () => {
        act(() => root.render(<ThemePreview theme="default" indicator={<span data-custom-indicator />} />));
        expect(container.querySelector("[data-custom-indicator]")).not.toBeNull();
        expect(container.querySelector(".zrlog-material-spin")).toBeNull();
        act(() => root.render(<ThemePreview theme="default" styles={{ indicator: { fontSize: 32 } }} />));
        expect(container.querySelector<HTMLElement>(".zrlog-material-spin")?.style.fontSize).toBe("32px");
    });

    it("renders supplied progress instead of an indefinite animation", () => {
        for (const percent of [0, 42, 100]) {
            act(() => root.render(<ThemePreview theme="default" percent={percent} />));
            expect(container.querySelector("[role=progressbar]")?.getAttribute("aria-valuenow")).toBe(String(percent));
            expect(container.querySelector(".zrlog-material-spin")?.getAttribute("data-indeterminate")).toBe("false");
        }
    });
});
