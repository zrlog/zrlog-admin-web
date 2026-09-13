import { act, Suspense } from "react";
import { createRoot, Root } from "react-dom/client";
import { MemoryRouter, NavigateFunction, useNavigate } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { BasicUserInfo } from "../type";
import AdminDashboardRouter from "./admin-dashboard-router";

const mockGetCsrData = jest.fn<Promise<any>, [string, number, unknown]>();
const mockUpdateDocumentTitle = jest.fn();
const mockAxios = {};
let mockCache: Record<string, any>;
let mockSsData: Record<string, any>;

jest.mock("../api", () => ({
    getCsrData: (uri: string, time: number, axios: unknown) => mockGetCsrData(uri, time, axios),
    getTimeInfoBySearchStr: () => 0,
}));
jest.mock("../base/AppBase", () => ({ useAxiosBaseInstance: () => mockAxios }));
jest.mock("../base/SsData", () => ({
    getSsDate: () => mockSsData,
    getWindowPageBuildId: () => "400",
}));
jest.mock("../utils/env-utils", () => ({ isPWA: () => false }));
jest.mock("../utils/helpers", () => ({
    deepEqualWithSpecialJSON: (first: unknown, second: unknown) => JSON.stringify(first) === JSON.stringify(second),
    getFullPath: (location: { pathname: string; search: string }) => location.pathname + location.search,
    updateDocumentTitle: (title: string) => mockUpdateDocumentTitle(title),
}));
jest.mock("../utils/cache", () => ({
    addToCache: (key: string, value: unknown) => {
        mockCache[key] = value;
    },
    getCacheByKey: (key: string) => mockCache[key],
    getLastOpenedPage: () => null,
    getPageBuildId: () => "400",
    getPageDataCacheKey: (location: { pathname: string; search: string }) => location.pathname + location.search,
    getPageDataCacheKeyByPath: (pathname: string, search: string) => pathname + search,
    getPageFullState: () => false,
    savePageFullState: require("@jest/globals").jest.fn(),
}));
jest.mock("layout", () => ({
    __esModule: true,
    default: ({ children, loading }: { children?: import("react").ReactNode; loading: boolean }) =>
        require("react").createElement("div", { "data-loading": String(loading) }, children),
}));
jest.mock("./my-loading-component", () => () => null);
jest.mock("components/not-found-page", () => () => null);
jest.mock("./admin-dashboard-routes", () => {
    const Page = ({ data }: { data: { label: string } }) => require("react").createElement("p", null, data.label);
    return {
        createAdminDashboardRoutes: () => [{ paths: ["/index", "/article"], lazy: Page, fallback: Page }],
    };
});

const reactActEnvironment = globalThis as typeof globalThis & {
    IS_REACT_ACT_ENVIRONMENT?: boolean;
};

const deferred = () => {
    let resolve!: (response: unknown) => void;
    let reject!: (error: Error) => void;
    const promise = new Promise((onResolve, onReject) => {
        resolve = onResolve;
        reject = onReject;
    });
    return { promise, resolve, reject };
};

describe("dashboard route request lifecycle", () => {
    let container: HTMLDivElement;
    let root: Root;
    let navigate: NavigateFunction;

    const Navigation = () => {
        navigate = useNavigate();
        return null;
    };
    const render = async (offline = false, mounted = true) => {
        await act(async () => {
            root.render(
                <MemoryRouter
                    initialEntries={["/index"]}
                    future={{ v7_relativeSplatPath: true, v7_startTransition: true }}
                >
                    <Navigation />
                    <Suspense fallback={null}>
                        {mounted && <AdminDashboardRouter offline={offline} userInfo={{} as BasicUserInfo} />}
                    </Suspense>
                </MemoryRouter>
            );
        });
    };
    const loading = () => container.querySelector("[data-loading]")?.getAttribute("data-loading");

    beforeEach(() => {
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = true;
        mockGetCsrData.mockReset();
        mockUpdateDocumentTitle.mockClear();
        mockCache = {};
        mockSsData = { pageBuildId: "400" };
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
    });

    afterEach(() => {
        act(() => root.unmount());
        container.remove();
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = false;
    });

    it("applies the current response to the visible page, cache, title, and shared data", async () => {
        const current = deferred();
        mockGetCsrData.mockReturnValue(current.promise);
        mockCache["/index"] = { label: "Cached home" };
        await render();
        expect(loading()).toBe("true");

        const data = { label: "Current home", firstUseChecklist: null };
        await act(async () => current.resolve({ error: 0, data, documentTitle: "Current title", pageBuildId: "400" }));

        expect(mockGetCsrData).toHaveBeenCalledTimes(1);
        expect(mockGetCsrData).toHaveBeenCalledWith("/index", 0, mockAxios);
        expect(mockCache["/index"]).toEqual(data);
        expect(mockSsData.data).toEqual(data);
        expect(mockUpdateDocumentTitle).toHaveBeenCalledWith("Current title");
        expect(container.textContent).toBe("Current home");
        expect(loading()).toBe("false");
    });

    it.each(["offline", "offline-navigation", "unmount"])("ignores a late success after %s", async (transition) => {
        const previous = deferred();
        mockGetCsrData.mockReturnValue(previous.promise);
        mockCache["/index"] = { label: "Cached home", firstUseChecklist: { version: 1, status: "pending" } };
        mockCache["/article"] = { label: "Cached articles" };
        await render();

        const dismissedData = { label: "Cached home", firstUseChecklist: null };
        mockCache["/index"] = dismissedData;
        if (transition === "unmount") {
            await render(false, false);
        } else {
            await render(true);
            if (transition === "offline-navigation") {
                await act(async () => navigate("/article"));
            }
        }

        await act(async () =>
            previous.resolve({
                error: 0,
                data: { label: "Stale home", firstUseChecklist: { version: 1, status: "pending" } },
                documentTitle: "Stale title",
                pageBuildId: "400",
            })
        );

        expect(mockGetCsrData).toHaveBeenCalledTimes(1);
        expect(mockCache["/index"]).toEqual(dismissedData);
        expect(mockSsData.data).toBeUndefined();
        expect(mockUpdateDocumentTitle).not.toHaveBeenCalled();
        if (transition === "offline-navigation") {
            expect(container.textContent).toBe("Cached articles");
        }
    });

    it.each(["business", "transport"])(
        "does not stop the current loading state for an old %s error",
        async (failure) => {
            mockSsData.data = { label: "Initial home" };
            mockCache["/article"] = { label: "Cached articles" };
            const previous = deferred();
            const current = deferred();
            mockGetCsrData.mockReturnValueOnce(previous.promise).mockReturnValueOnce(current.promise);
            await render();
            expect(mockGetCsrData).not.toHaveBeenCalled();

            await act(async () => navigate("/article"));
            await act(async () => navigate("/index"));
            expect(mockGetCsrData).toHaveBeenCalledTimes(2);
            expect(loading()).toBe("true");

            await act(async () => {
                if (failure === "business") {
                    previous.resolve({ error: 1, message: "Old request failed" });
                } else {
                    previous.reject(new Error("Old connection failed"));
                }
            });
            expect(loading()).toBe("true");
            expect(container.textContent).toBe("Initial home");

            await act(async () => current.resolve({ error: 0, data: { label: "Current home" }, pageBuildId: "400" }));
            expect(loading()).toBe("false");
            expect(container.textContent).toBe("Current home");
            expect(mockSsData.data).toEqual({ label: "Current home" });
        }
    );
});
