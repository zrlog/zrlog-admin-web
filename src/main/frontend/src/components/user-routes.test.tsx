import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { MemoryRouter, NavigateFunction, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { Grid } from "antd";
import User from "./user";
import { getRes } from "../utils/constants";
import { USER_ROUTES } from "../utils/account-page-routes";

jest.mock("antd/es/divider", () => require("antd").Divider);
jest.mock("antd/es/form", () => require("antd").Form);
jest.mock("antd/es/grid/row", () => require("antd").Row);
jest.mock("antd/es/grid/col", () => require("antd").Col);
const mockGet = jest.fn<Promise<any>, any[]>();
const mockApi = { get: mockGet };
jest.mock("../base/AppBase", () => ({ useAxiosBaseInstance: () => mockApi }));
jest.mock("../base/ConfigProviderApp", () => ({ getAppState: () => ({ compactMode: false }) }));
jest.mock("../common/ResourceDragger", () => () => null);
jest.mock("../common/ImageCropper", () => () => null);
jest.mock("../common/BackendImage", () => () => null);
jest.mock("./user-preferences", () => () => <div>Settings content</div>);
jest.mock("./oauth", () => ({
    UserApplications: () => (
        <div>
            Applications content
            <input aria-label="Application name" />
        </div>
    ),
}));

describe("personal page URLs", () => {
    let root: Root;
    let container: HTMLDivElement;
    let navigate: NavigateFunction;
    const previousEnv = process.env;
    beforeEach(() => {
        mockGet
            .mockReset()
            .mockResolvedValue({ data: { error: 0, data: { currentRole: "author", roles: ["author"], actions: [] } } });
        process.env = { ...previousEnv, NODE_ENV: "production" };
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
            addListener() {
                return undefined;
            },
            removeListener() {
                return undefined;
            },
            addEventListener() {
                return undefined;
            },
            removeEventListener() {
                return undefined;
            },
            dispatchEvent: () => false,
        })) as any;
        const computedStyle = window.getComputedStyle;
        jest.spyOn(window, "getComputedStyle").mockImplementation((element) => computedStyle(element));
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

    it.each([
        [false, false],
        [false, true],
        [true, false],
        [true, true],
    ])("opens a deep link and follows back/forward navigation (static=%s, mobile=%s)", async (staticPage, mobile) => {
        jest.spyOn(Grid, "useBreakpoint").mockReturnValue({ md: !mobile });
        window.__SS_DATA__ = {
            user: null,
            key: "routes",
            pageBuildId: "test",
            systemNotification: "",
            resourceInfo: { lang: "zh_CN", staticPage },
        };
        const suffix = staticPage ? ".html" : "";
        function Pages() {
            navigate = useNavigate();
            const location = useLocation();
            return (
                <>
                    <output>{location.pathname}</output>
                    <Routes>
                        {(["profile", "preferences", "applications"] as const).map((page) => (
                            <Route
                                key={page}
                                path={USER_ROUTES[page] + suffix}
                                element={
                                    <User
                                        data={{ userName: "writer", email: "", header: "" }}
                                        offline={false}
                                        activeKey={page}
                                    />
                                }
                            />
                        ))}
                    </Routes>
                </>
            );
        }
        await act(async () =>
            root.render(
                <MemoryRouter initialEntries={[USER_ROUTES.applications + suffix]}>
                    <Pages />
                </MemoryRouter>
            )
        );
        expect(container.textContent).toContain("Applications content");
        const selected = () =>
            container.querySelector(mobile ? ".ant-select-content" : 'nav a[aria-current="page"]')?.textContent;
        expect(selected()).toBe(getRes().oauth.title);
        if (mobile) {
            await act(async () => {
                container
                    .querySelector('[role="combobox"]')!
                    .dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
            });
            const options = Array.from(container.querySelectorAll<HTMLElement>(".ant-select-item-option"));
            expect(options.map((option) => option.textContent)).toContain(getRes().accountSecurity.title);
            await act(async () =>
                options.find((option) => option.textContent === getRes().user.preferences.title)!.click()
            );
        } else {
            const links = Array.from(container.querySelectorAll<HTMLAnchorElement>("nav a"));
            expect(
                links.find((link) => link.textContent === getRes().accountSecurity.title)?.getAttribute("href")
            ).toBe(USER_ROUTES.security + suffix + "?v=test");
            await act(async () => links.find((link) => link.textContent === getRes().user.preferences.title)!.click());
        }
        expect(container.querySelector("output")?.textContent).toBe(USER_ROUTES.preferences + suffix);
        expect(selected()).toBe(getRes().user.preferences.title);
        await act(async () => navigate(-1));
        expect(selected()).toBe(getRes().oauth.title);
        await act(async () => navigate(1));
        expect(selected()).toBe(getRes().user.preferences.title);
        await act(async () => navigate(-1));
        const applicationName = container.querySelector<HTMLInputElement>('input[aria-label="Application name"]')!;
        applicationName.value = "Unsaved application";
        const help = Array.from(container.querySelectorAll<HTMLButtonElement>("button")).find(
            (button) => button.textContent === getRes().access.title
        )!;
        await act(async () => help.click());
        expect(container.querySelector("output")?.textContent).toBe(USER_ROUTES.applications + suffix);
        const drawer = document.querySelector('[role="dialog"]')!;
        expect(drawer.querySelector('[role="tab"][aria-selected="true"]')?.textContent).toBe(
            getRes().access.applicationScopes
        );
        expect(drawer.textContent).toContain(getRes().oauth.scopeLabels["articles:read_private"]);
        await act(async () => drawer.querySelector<HTMLButtonElement>(".ant-drawer-close")!.click());
        expect(container.querySelector("output")?.textContent).toBe(USER_ROUTES.applications + suffix);
        expect(container.querySelector<HTMLInputElement>('input[aria-label="Application name"]')?.value).toBe(
            "Unsaved application"
        );
    });
});
