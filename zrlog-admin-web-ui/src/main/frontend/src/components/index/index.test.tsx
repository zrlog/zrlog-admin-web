import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import { IndexData } from "../../type";
import Index from "./index";

const mockAxios = {
    get: jest.fn<Promise<unknown>, [string]>(),
    post: jest.fn<Promise<unknown>, [string, unknown]>(),
};
const mockMessageError = jest.fn();

jest.mock("../../base/AppBase", () => ({ useAxiosBaseInstance: () => mockAxios }));
jest.mock("../../utils/article-cache", () => ({ getLocalArticleCaches: () => [] }));
jest.mock("../../utils/cache", () => ({
    getPageDataCacheKeyByPath: (pathname: string, search: string) => pathname + search,
}));
jest.mock("../../utils/constants", () => ({
    getRealRouteUrl: (url: string) => url,
    getRes: () => ({
        ...require("../../i18n/admin").getAdminI18n("en_US"),
        homeUrl: "https://example.test/blog/",
    }),
}));
jest.mock("antd-style", () => ({ useTheme: () => ({}) }));
jest.mock("@ant-design/icons", () => ({
    BarChartOutlined: () => null,
    CloseOutlined: () => null,
    EditOutlined: () => null,
    EyeOutlined: () => null,
    FileMarkdownOutlined: () => null,
    HomeOutlined: () => null,
    InfoCircleOutlined: () => null,
    SmileOutlined: () => null,
}));
jest.mock("antd", () => {
    const React = require("react") as typeof import("react");
    const Container = ({ children }: { children?: import("react").ReactNode }) =>
        React.createElement("div", null, children);
    return {
        App: { useApp: () => ({ message: { error: mockMessageError } }) },
        Card: Container,
        Col: Container,
        Grid: { useBreakpoint: () => ({}) },
        Row: Container,
        Button: ({ children, href, loading, onClick }: any) =>
            React.createElement(href ? "a" : "button", { href, disabled: loading, onClick }, children),
        Typography: { Text: Container },
    };
});
jest.mock("./ActivityGraph", () => ({ __esModule: true, default: () => null, generateCompleteData: () => [] }));
jest.mock("./StatisticsInfo", () => () => null);
jest.mock("./QuickAction", () => () => null);
jest.mock("./DataInsights", () => () => null);
jest.mock("./AuditTrail", () => () => null);
jest.mock("./PluginSurfacePanels", () => () => null);
jest.mock("./DashboardConfigDrawer", () => () => null);
jest.mock("./LocalDraftCard", () => () => null);
jest.mock("./DashboardCardAction", () => () => null);

const reactActEnvironment = globalThis as typeof globalThis & {
    IS_REACT_ACT_ENVIRONMENT?: boolean;
};

const initialData = (): IndexData => ({
    dashboardConfig: {
        autoRefreshEnabled: true,
        autoRefreshIntervalSeconds: 10,
        cards: [{ kind: "card", id: "welcome", enabled: true }],
    },
    firstUseChecklist: { version: 1, status: "pending" },
});

describe("dashboard first-use dismissal", () => {
    let container: HTMLDivElement;
    let root: Root;
    let cachedData: IndexData;
    const updateCache = jest.fn<void, [IndexData, string]>();

    const render = (data: IndexData, key = "initial") => {
        act(() => {
            root.render(
                <MemoryRouter
                    initialEntries={["/index"]}
                    future={{ v7_relativeSplatPath: true, v7_startTransition: true }}
                >
                    <Index key={key} data={data} offline={false} offlineData={false} updateCache={updateCache} />
                </MemoryRouter>
            );
        });
    };
    const checklist = () => container.querySelector('section[aria-label="Complete Your First Publish"]');
    const skip = async () => {
        const button = Array.from(container.querySelectorAll("button")).find((item) => item.textContent === "Skip");
        expect(button).toBeDefined();
        await act(async () => button?.dispatchEvent(new MouseEvent("click", { bubbles: true })));
    };

    beforeEach(() => {
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = true;
        jest.useFakeTimers();
        jest.spyOn(document, "visibilityState", "get").mockReturnValue("visible");
        mockAxios.get.mockReset();
        mockAxios.post.mockReset();
        mockMessageError.mockClear();
        updateCache.mockClear();
        updateCache.mockImplementation((data) => {
            cachedData = data;
        });
        cachedData = initialData();
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
    });

    afterEach(() => {
        act(() => root.unmount());
        container.remove();
        jest.useRealTimers();
        jest.restoreAllMocks();
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = false;
    });

    it("keeps a late pending refresh out of the cache after skipping and remounting", async () => {
        let finishRefresh!: (response: unknown) => void;
        mockAxios.get.mockReturnValue(
            new Promise((resolve) => {
                finishRefresh = resolve;
            })
        );
        mockAxios.post.mockResolvedValue({ data: { error: 0, data: { version: 1, status: "dismissed" } } });
        render(cachedData);
        expect(checklist()).not.toBeNull();

        act(() => jest.advanceTimersByTime(10_000));
        expect(mockAxios.get).toHaveBeenCalledWith("/api/admin/index");
        await skip();
        expect(mockAxios.post).toHaveBeenCalledWith("/api/admin/index/first-use/dismiss", { version: 1 });
        expect(checklist()).toBeNull();
        expect(cachedData.firstUseChecklist).toBeNull();

        const staleData = initialData();
        staleData.dashboardConfig.autoRefreshIntervalSeconds = 30;
        await act(async () => finishRefresh({ data: { error: 0, data: staleData } }));
        expect(checklist()).toBeNull();
        expect(cachedData.firstUseChecklist).toBeNull();
        expect(cachedData.dashboardConfig.autoRefreshIntervalSeconds).toBe(30);
        expect(staleData.firstUseChecklist?.status).toBe("pending");
        expect(updateCache).toHaveBeenLastCalledWith(cachedData, "/index");

        render(cachedData, "returned-to-dashboard");
        expect(checklist()).toBeNull();
    });

    it("repairs a late route response already written to the cache after skipping", async () => {
        mockAxios.post.mockResolvedValue({ data: { error: 0, data: { version: 1, status: "dismissed" } } });
        render(cachedData);
        await skip();
        expect(cachedData.firstUseChecklist).toBeNull();

        const staleData = initialData();
        staleData.dashboardConfig.cards[0].data = { tips: ["A different welcome tip"] };
        // The router persists a changed response before passing it to the mounted page.
        cachedData = staleData;
        render(staleData);

        expect(checklist()).toBeNull();
        expect(cachedData.firstUseChecklist).toBeNull();
        expect(cachedData.dashboardConfig).toEqual(staleData.dashboardConfig);
        expect(staleData.firstUseChecklist?.status).toBe("pending");
        expect(updateCache).toHaveBeenLastCalledWith(cachedData, "/index");

        render(cachedData, "returned-after-route-refresh");
        expect(checklist()).toBeNull();
    });

    it.each(["business", "transport"])(
        "keeps the checklist and cache when dismissal has a %s failure",
        async (failure) => {
            if (failure === "business") {
                mockAxios.post.mockResolvedValue({ data: { error: 1, message: "Unable to save" } });
            } else {
                mockAxios.post.mockRejectedValue(new Error("Network unavailable"));
            }
            render(cachedData);
            await skip();

            expect(checklist()).not.toBeNull();
            expect(cachedData.firstUseChecklist?.status).toBe("pending");
            expect(updateCache).not.toHaveBeenCalled();
            if (failure === "business") {
                expect(mockMessageError).toHaveBeenCalledWith("Unable to save");
            }

            cachedData = initialData();
            render(cachedData);
            expect(checklist()).not.toBeNull();
            expect(cachedData.firstUseChecklist?.status).toBe("pending");
            expect(updateCache).not.toHaveBeenCalled();

            render(cachedData, "returned-after-failure");
            expect(checklist()).not.toBeNull();
        }
    );
});
