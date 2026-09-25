import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { MemoryRouter, NavigateFunction, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import User from "./user";
import { getRes } from "../utils/constants";
import { USER_ROUTES } from "../utils/user-page-routes";

jest.mock("antd/es/divider", () => require("antd").Divider);
jest.mock("antd/es/form", () => require("antd").Form);
jest.mock("antd/es/grid/row", () => require("antd").Row);
jest.mock("antd/es/grid/col", () => require("antd").Col);
jest.mock("../base/AppBase", () => ({ useAxiosBaseInstance: () => ({}) }));
jest.mock("../common/ResourceDragger", () => () => null);
jest.mock("../common/ImageCropper", () => () => null);
jest.mock("../common/BackendImage", () => () => null);
jest.mock("./user-preferences", () => () => <div>Settings content</div>);
jest.mock("./oauth", () => ({ UserApplications: () => <div>Applications content</div> }));

describe("personal page URLs", () => {
    let root: Root;
    let container: HTMLDivElement;
    let navigate: NavigateFunction;
    const previousEnv = process.env;
    beforeEach(() => {
        process.env = { ...previousEnv, NODE_ENV: "production" };
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
    });

    it.each([false, true])("opens a deep link and follows back/forward navigation (static=%s)", async (staticPage) => {
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
                        {(["profile", "preferences", "applications"] as const).map((tab) => (
                            <Route
                                key={tab}
                                path={USER_ROUTES[tab] + suffix}
                                element={
                                    <User
                                        data={{ userName: "writer", email: "", header: "" }}
                                        offline={false}
                                        activeTab={tab}
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
        const selected = () => container.querySelector('[role="tab"][aria-selected="true"]')?.textContent;
        expect(selected()).toBe(getRes().oauth.title);
        const settingsTab = Array.from(container.querySelectorAll<HTMLElement>('[role="tab"]')).find(
            (tab) => tab.textContent === getRes().user.preferences.title
        )!;
        await act(async () => settingsTab.click());
        expect(container.querySelector("output")?.textContent).toBe(USER_ROUTES.preferences + suffix);
        expect(selected()).toBe(getRes().user.preferences.title);
        await act(async () => navigate(-1));
        expect(selected()).toBe(getRes().oauth.title);
        await act(async () => navigate(1));
        expect(selected()).toBe(getRes().user.preferences.title);
    });
});
