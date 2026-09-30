import { act, Suspense } from "react";
import { createRoot, Root } from "react-dom/client";
import { Location, MemoryRouter, NavigateFunction, useLocation, useNavigate } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { BasicUserInfo } from "../type";
import AdminDashboardRouter from "./admin-dashboard-router";

const mockGetCsrData = jest.fn<Promise<any>, [string, number, unknown]>();
const mockUpdateDocumentTitle = jest.fn();
const mockAxios = {};
let mockCache: Record<string, any>;
let mockSsData: Record<string, any>;
let mockDeserializeCache = false;
const mockPageDataChanged = jest.fn();
const mockEditorMounted = jest.fn();
const mockEditorUnmounted = jest.fn();
const mockLayoutRender = jest.fn<void, [{ loading: boolean; fullScreen: boolean }]>();
let mockFullScreenCache: Record<string, boolean>;

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
    getCacheByKey: (key: string) =>
        mockDeserializeCache && mockCache[key] !== undefined
            ? JSON.parse(JSON.stringify(mockCache[key]))
            : mockCache[key],
    getLastOpenedPage: () => null,
    getPageBuildId: () => "400",
    getPageDataCacheKey: (location: { pathname: string; search: string }) => location.pathname + location.search,
    getPageDataCacheKeyByPath: (pathname: string, search: string) => pathname + search,
    getPageFullState: (path: string) => mockFullScreenCache[path] === true,
    savePageFullState: (path: string, fullScreen: boolean) => {
        mockFullScreenCache[path] = fullScreen;
    },
}));
jest.mock("layout", () => ({
    __esModule: true,
    default: ({
        children,
        loading,
        fullScreen,
    }: {
        children?: import("react").ReactNode;
        loading: boolean;
        fullScreen: boolean;
    }) => {
        mockLayoutRender({ loading, fullScreen });
        return require("react").createElement("div", { "data-loading": String(loading) }, children);
    },
}));
jest.mock("./my-loading-component", () => () => null);
jest.mock("components/not-found-page", () => () => null);
jest.mock("./admin-dashboard-routes", () => {
    const Page = ({ data }: { data: { label: string } }) => {
        require("react").useEffect(() => {
            mockPageDataChanged(data);
        }, [data]);
        return require("react").createElement("p", null, data.label);
    };
    const ArticleEditor = ({ data, onFullScreen }: { data: { label: string }; onFullScreen: () => void }) => {
        require("react").useEffect(() => {
            mockEditorMounted();
            return () => mockEditorUnmounted();
        }, []);
        return require("react").createElement(
            "div",
            null,
            require("react").createElement("p", null, data.label),
            require("react").createElement("textarea"),
            require("react").createElement("button", { onClick: onFullScreen }, "Fullscreen")
        );
    };
    return {
        createAdminDashboardRoutes: (articleEditProps: { onFullScreen: () => void }) => [
            {
                paths: [
                    "/index",
                    "/article",
                    "/website/members",
                    "/user/applications/tokens",
                    "/user/applications/grants",
                    "/user/applications/clients",
                    "/user/preferences/appearance",
                    "/user/preferences/writing",
                    "/user/preferences/assistant",
                ],
                lazy: Page,
                fallback: Page,
            },
            {
                paths: ["/article-edit", "/article-edit.html"],
                lazy: ArticleEditor,
                fallback: ArticleEditor,
                props: articleEditProps,
            },
        ],
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
    let location: Location;
    let initialEntry: Partial<Location>;

    const Navigation = () => {
        navigate = useNavigate();
        location = useLocation();
        return null;
    };
    const render = async (offline = false, mounted = true) => {
        await act(async () => {
            root.render(
                <MemoryRouter
                    initialEntries={[initialEntry]}
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
        mockDeserializeCache = false;
        mockPageDataChanged.mockClear();
        mockEditorMounted.mockClear();
        mockEditorUnmounted.mockClear();
        mockLayoutRender.mockClear();
        mockFullScreenCache = {};
        initialEntry = { pathname: "/index" };
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

    it("does not load or render disabled module pages even with cached data", async () => {
        mockSsData.resourceInfo = { capabilities: { ai: false, access: false } };
        mockCache["/user/preferences/assistant"] = { label: "cached assistant" };
        mockCache["/user/applications/tokens"] = { label: "cached tokens" };
        mockGetCsrData.mockResolvedValue({ data: { label: "index" } });
        await render();
        mockGetCsrData.mockClear();
        for (const path of ["/user/preferences/assistant", "/user/applications/tokens"]) {
            await act(async () => {
                navigate(path);
            });
            expect(mockGetCsrData).not.toHaveBeenCalled();
            expect(container.textContent).not.toContain("cached");
        }
        await act(async () => {
            navigate("/user/preferences/writing");
        });
        expect(mockGetCsrData).toHaveBeenCalled();
    });

    it.each(["/article-edit", "/article-edit.html"])(
        "keeps the editor, focus, and fullscreen when a created draft acquires its URL on %s",
        async (path) => {
            initialEntry = { pathname: path, search: "?typeId=3" };
            mockSsData.data = { label: "New draft", article: { version: -1 } };
            await render();
            const editor = container.querySelector("textarea")!;
            editor.value = "Unsent assistant prompt";
            editor.focus();
            act(() => container.querySelector("button")!.click());
            mockLayoutRender.mockClear();
            const saved = { label: "AI draft", article: { logId: 18, version: 0 } };
            const target = `${path}?typeId=3&id=18`;
            mockCache[target] = saved;
            delete mockCache[`${path}?typeId=3`];

            await act(async () => navigate(target, { replace: true, state: { articleCreatedFrom: location.key } }));

            expect(mockGetCsrData).not.toHaveBeenCalled();
            expect(container.textContent).toContain("AI draft");
            expect(container.querySelector("textarea")).toBe(editor);
            expect(editor.value).toBe("Unsent assistant prompt");
            expect(document.activeElement).toBe(editor);
            expect(mockEditorMounted).toHaveBeenCalledTimes(1);
            expect(mockEditorUnmounted).not.toHaveBeenCalled();
            expect(mockSsData.data).toEqual(saved);
            expect(mockLayoutRender.mock.calls.every(([props]) => !props.loading && props.fullScreen)).toBe(true);
            expect(mockFullScreenCache[target]).toBe(true);

            mockCache[target] = { label: "Updated draft", article: { logId: 18, version: 1 } };
            await render();
            expect(container.textContent).toContain("Updated draft");
            expect(container.querySelector("textarea")).toBe(editor);
            expect(mockGetCsrData).not.toHaveBeenCalled();

            await render(true);
            mockGetCsrData.mockResolvedValue({ data: mockCache[target] });
            await render();
            expect(mockGetCsrData).toHaveBeenCalledTimes(1);
            expect(container.querySelector("textarea")).toBe(editor);
        }
    );

    it("ignores an in-flight draft response after adopting a created article", async () => {
        initialEntry = { pathname: "/article-edit" };
        mockCache["/article-edit"] = { label: "New draft", article: { version: -1 } };
        const request = deferred();
        mockGetCsrData.mockReturnValue(request.promise);
        await render();
        const saved = { label: "Created draft", article: { logId: 18, version: 0 } };
        mockCache["/article-edit?id=18"] = saved;
        delete mockCache["/article-edit"];
        await act(async () =>
            navigate("/article-edit?id=18", { replace: true, state: { articleCreatedFrom: location.key } })
        );
        await act(async () => request.resolve({ data: { label: "Stale empty draft" } }));
        expect(mockGetCsrData).toHaveBeenCalledTimes(1);
        expect(mockSsData.data).toEqual(saved);
        expect(container.textContent).toContain("Created draft");
        expect(loading()).toBe("false");
        expect(mockEditorMounted).toHaveBeenCalledTimes(1);
    });

    it("remounts and requests data when switching articles or opening a new draft", async () => {
        initialEntry = { pathname: "/article-edit", search: "?id=7" };
        mockSsData.data = { label: "Article 7", article: { logId: 7, version: 1 } };
        await render();
        for (const [target, article] of [
            ["/article-edit?id=8", { logId: 8, version: 0 }],
            ["/article-edit", { version: -1 }],
        ] as const) {
            const editor = container.querySelector("textarea");
            mockGetCsrData.mockResolvedValue({ data: { label: target, article } });
            await act(async () => navigate(target));
            expect(container.querySelector("textarea")).not.toBe(editor);
            expect(container.textContent).toContain(target);
        }
        expect(mockGetCsrData).toHaveBeenCalledTimes(2);
        expect(mockEditorMounted).toHaveBeenCalledTimes(3);
    });

    it.each(["missing-cache", "different-id", "different-session", "stale-marker"])(
        "does not bypass loading on a creation navigation with %s",
        async (boundary) => {
            initialEntry = { pathname: "/article-edit" };
            mockSsData.data = { label: "New draft", article: { version: -1 } };
            await render();
            if (boundary !== "missing-cache") {
                mockCache["/article-edit?id=18"] = {
                    label: "Cached",
                    article: { logId: boundary === "different-id" ? 19 : 18 },
                };
            }
            if (boundary === "different-session") mockSsData.key = "new-session";
            mockGetCsrData.mockResolvedValue({ data: { label: "Fresh", article: { logId: 18, version: 1 } } });
            await act(async () =>
                navigate("/article-edit?id=18", {
                    replace: true,
                    state: { articleCreatedFrom: boundary === "stale-marker" ? "old-location" : location.key },
                })
            );
            expect(mockGetCsrData).toHaveBeenCalledTimes(1);
            expect(container.textContent).toContain("Fresh");
            expect(mockEditorUnmounted).toHaveBeenCalled();
        }
    );

    it("loads a created article again on history navigation or a fresh router mount", async () => {
        initialEntry = { pathname: "/article-edit" };
        mockSsData.data = { label: "New draft", article: { version: -1 } };
        await render();
        const saved = { label: "Created draft", article: { logId: 18, version: 0 } };
        mockCache["/article-edit?id=18"] = saved;
        await act(async () =>
            navigate("/article-edit?id=18", { replace: true, state: { articleCreatedFrom: location.key } })
        );
        mockGetCsrData.mockResolvedValueOnce({ data: { label: "Home" } }).mockResolvedValue({ data: saved });
        await act(async () => navigate("/index"));
        await act(async () => navigate(-1));
        expect(mockGetCsrData).toHaveBeenCalledTimes(2);
        expect(container.textContent).toContain("Created draft");
        await render(false, false);
        delete mockSsData.data;
        await render();
        expect(mockGetCsrData).toHaveBeenCalledTimes(3);
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

    it("retains loaded page data when only the settings tab fragment changes", async () => {
        mockSsData.data = { label: "Loaded page" };
        await render();
        await act(async () => navigate("/index#writing"));
        expect(container.textContent).toBe("Loaded page");
        expect(loading()).toBe("false");
        await act(async () => navigate(-1));
        expect(container.textContent).toBe("Loaded page");
        expect(mockGetCsrData).not.toHaveBeenCalled();
    });

    it("does not rehydrate forms on appearance renders, but accepts changed data and session boundaries", async () => {
        mockDeserializeCache = true;
        mockSsData.data = { label: "Saved data" };
        await render();
        const initialSnapshot = mockPageDataChanged.mock.calls[0][0];
        await render();
        await render();
        expect(mockPageDataChanged).toHaveBeenCalledTimes(1);
        expect(mockPageDataChanged.mock.calls[0][0]).toBe(initialSnapshot);
        mockCache["/index"] = { label: "Updated on server" };
        await render();
        expect(container.textContent).toBe("Updated on server");
        expect(mockPageDataChanged).toHaveBeenCalledTimes(2);
        mockSsData.key = "another-session";
        await render();
        expect(mockPageDataChanged).toHaveBeenCalledTimes(3);
    });

    it.each([
        "/website/members",
        "/user/applications/tokens",
        "/user/applications/grants",
        "/user/applications/clients",
        "/user/preferences/appearance",
        "/user/preferences/writing",
        "/user/preferences/assistant",
    ])("shows the cached %s page immediately on a second visit and replaces it with the response", async (path) => {
        mockSsData.data = { label: "Home" };
        const first = deferred();
        const second = deferred();
        mockGetCsrData
            .mockReturnValueOnce(first.promise)
            .mockResolvedValueOnce({ error: 0, data: { label: "Home" }, pageBuildId: "400" })
            .mockReturnValueOnce(second.promise);
        await render();
        await act(async () => navigate(path));
        await act(async () => first.resolve({ error: 0, data: { label: "First snapshot" }, pageBuildId: "400" }));
        await act(async () => navigate("/index"));
        await act(async () => navigate(path));
        expect(container.textContent).toBe("First snapshot");
        expect(loading()).toBe("true");
        await act(async () => second.resolve({ error: 0, data: { label: "Latest snapshot" }, pageBuildId: "400" }));
        expect(container.textContent).toBe("Latest snapshot");
        expect(loading()).toBe("false");
        expect(mockCache[path]).toEqual({ label: "Latest snapshot" });
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
