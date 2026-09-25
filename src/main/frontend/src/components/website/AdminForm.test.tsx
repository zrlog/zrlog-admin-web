import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { Simulate } from "react-dom/test-utils";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import AdminForm from "./AdminForm";
import { Admin } from "./index";

jest.mock("antd/es/form", () => require("antd/lib/form"));
jest.mock("antd/es/input", () => require("antd/lib/input"));
jest.mock("antd/es/switch", () => require("antd/lib/switch"));
jest.mock("antd/es/select", () => require("antd/lib/select"));
jest.mock("antd/es/button", () => require("antd/lib/button"));
jest.mock("antd/es/locale/zh_CN", () => require("antd/lib/locale/zh_CN"));
jest.mock("antd/es/locale/en_US", () => require("antd/lib/locale/en_US"));

jest.mock("antd-style", () => ({ useTheme: () => require("antd").theme.useToken().token }));

const mockChangeAppState = jest.fn();
jest.mock("../../base/ConfigProviderApp", () => ({
    getAppState: () => ({ theme: "geek", dark: true, compactMode: true, colorPrimary: "#39ff14", lang: "en_US" }),
    changeAppState: (...args: any[]) => mockChangeAppState(...args),
}));
jest.mock("../../base/AppInit", () => ({
    isSupportDarkMode: (theme: string) => theme === "default" || theme === "antd",
}));
jest.mock("./FaviconUpload", () => () => null);

describe("site admin defaults", () => {
    let root: Root;
    let container: HTMLDivElement;
    const submit = jest.fn();
    const site: Admin = {
        session_timeout: 60,
        disable_comment_status: false,
        article_thumbnail_status: false,
        admin_static_resource_base_url: "",
        language: "zh_CN",
        admin_theme: "antd",
        admin_darkMode: false,
        admin_compactMode: false,
        admin_color_primary: "#123456",
        favicon_png_pwa_192_base64: "",
        favicon_png_pwa_512_base64: "",
    };
    beforeEach(() => {
        (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
        (globalThis as any).MessageChannel = class {
            port1 = { onmessage: () => undefined };
            port2 = { postMessage: () => Promise.resolve().then(() => this.port1.onmessage()) };
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
            key: "site-defaults",
            pageBuildId: "test",
            systemNotification: "",
            user: null,
            resourceInfo: { lang: "zh_CN" },
        };
        submit.mockClear();
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
    const render = async (data = site) => {
        await act(async () => {
            root.render(<AdminForm data={data} offline={false} offlineData={false} onSubmit={submit} />);
        });
    };
    it("keeps site appearance when a different personal appearance is active", async () => {
        await render();
        expect(container.querySelector("#admin_darkMode")?.getAttribute("aria-checked")).toBe("false");
        expect(container.querySelector("#admin_compactMode")?.getAttribute("aria-checked")).toBe("false");
        await act(async () => {
            Simulate.submit(container.querySelector("form")!);
        });
        expect(submit).toHaveBeenCalledWith(
            expect.objectContaining({
                language: "zh_CN",
                admin_theme: "antd",
                admin_darkMode: false,
                admin_compactMode: false,
                admin_color_primary: "#123456",
            })
        );
        expect(mockChangeAppState).not.toHaveBeenCalled();
    });
    it("changing session duration does not copy personal appearance into the site", async () => {
        await render();
        const input = container.querySelector("#session_timeout") as HTMLInputElement;
        act(() => {
            Simulate.change(input, { target: { value: "90" } } as any);
        });
        await act(async () => {
            Simulate.submit(container.querySelector("form")!);
        });
        expect(submit).toHaveBeenCalledWith(
            expect.objectContaining({ session_timeout: 90, admin_theme: "antd", admin_color_primary: "#123456" })
        );
    });
    it("uses the site theme to decide whether dark mode can be configured", async () => {
        await render({ ...site, admin_theme: "geek" });
        expect(container.querySelector("#admin_darkMode")).toBeNull();
        await render(site);
        expect(container.querySelector("#admin_darkMode")).not.toBeNull();
    });
});
