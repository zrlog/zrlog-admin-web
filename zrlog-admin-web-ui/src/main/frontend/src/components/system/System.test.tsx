import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { MemoryRouter, NavigateFunction, Route, Routes, useNavigate, useLocation } from "react-router-dom";
import { Grid } from "antd";
import { afterEach, beforeEach, expect, jest, test } from "@jest/globals";
import System from "./index";
import SystemSettingsLayout, { SystemInfoPage } from "./SystemSettingsLayout";
import { SystemData } from "../../type";
import SliderMenu from "../../layout/slider";
import LegacyVersion from "../website/version";

jest.mock("@zrlog/ui/icons/dashboard", () => () => null);
jest.mock("@zrlog/ui/icons/container", () => () => null);
jest.mock("@zrlog/ui/icons/info", () => () => null);
jest.mock("@zrlog/ui/icons/experiment", () => () => null);
jest.mock("@zrlog/ui/icons/link", () => () => null);
jest.mock("@zrlog/ui/icons/database", () => () => null);
jest.mock("@zrlog/ui/icons/time", () => () => null);
jest.mock("@zrlog/ui/icons/cpu", () => () => null);
jest.mock("@zrlog/ui/icons/memory", () => () => null);
jest.mock("icons/ZrLogOutlined", () => () => null);
jest.mock("../admin-dashboard-routes", () => ({ renderAdminDashboardRouteIcon: () => null }));
jest.mock("../../base/ConfigProviderApp", () => ({ getAppState: () => ({ dark: false, compactMode: false }) }));
jest.mock("../../utils/helpers", () => ({
    parseQueryParamsToMap: () => new Map(),
    tryBlock: () => undefined,
}));

const mockAxios = {};
const mockGetCsrData = jest.fn<Promise<any>, any[]>();
let mockDevMode = false;
let mockManage = true;
let mockConfigure = true;
let mockStatic = false;
let mockLang = "en_US";
jest.mock("../../base/AppBase", () => ({ useAxiosBaseInstance: () => mockAxios }));
jest.mock("../../api", () => ({
    getCsrData: (...args: any[]) => mockGetCsrData(...args),
    getTimeInfoBySearchStr: () => ({}),
}));
jest.mock("../../utils/account-access", () => ({
    hasAction: (action: string) => (action === "site.configure" ? mockConfigure : mockManage),
    actionForPath: (path: string) => (path === "/system/version" ? "site.configure" : "system.manage"),
}));
jest.mock("../../utils/constants", () => ({
    getRes: () => require("../../i18n/admin").getAdminI18n(mockLang),
    isServerDevelopmentMode: () => mockDevMode,
    getRealRouteUrl: (path: string) => {
        const [pathname, query] = path.split("?");
        return `/blog/admin${pathname}${mockStatic ? ".html" : ""}${query ? `?${query}` : ""}`;
    },
}));

const entry = (key: string, name: string, value: string) => ({ key, name, value });
const data: SystemData = {
    dockerMode: true,
    nativeImageMode: true,
    serverInfos: [
        entry("programInfo", "Application", "ZrLog 4.0.1"),
        entry("runtime", "Runtime", "GraalVM 25"),
        entry("webServer", "Web server", "simplewebserver/4.1.15"),
        entry("system", "Operating system", "Linux - amd64"),
        entry("runPath", "Runtime path", "/opt/zrlog/long-runtime-path/with-no-spaces"),
        entry("dbInfo", "Database", "SQLite 3.53"),
        entry("timezone", "Time zone", "Asia/Shanghai"),
        entry("locale", "Locale", "en/US"),
        entry("encoding", "Encoding", "UTF-8"),
        entry("cpuInfo", "Processor", "Test processor"),
    ],
    serverInfos2: [
        entry("cpuLoad", "CPU usage", "12.5%"),
        entry("usedMemorySpace", "Process Used Memory", "128M"),
        entry("totalMemorySpace", "System Memory", "2G"),
        entry("dbConnectSize", "Database connections", "2 / 8"),
        entry("uptime", "Uptime", "2 hours"),
        entry("systemLoad", "Load average", "0.10, 0.20, 0.30"),
        entry("usedDiskSpace", "Disk usage", "512M"),
        entry("usedCacheSpace", "Cache usage", "16M"),
    ],
};

let root: Root;
let container: HTMLDivElement;
beforeEach(() => {
    (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
    (globalThis as any).ResizeObserver = class {
        observe = jest.fn();
        unobserve = jest.fn();
        disconnect = jest.fn();
    };
    (globalThis as any).MessageChannel = class {
        port1 = { onmessage: () => undefined };
        port2 = { postMessage: () => Promise.resolve().then(() => this.port1.onmessage()) };
    };
    window.matchMedia = (() => ({
        matches: false,
        addListener: jest.fn(),
        removeListener: jest.fn(),
        addEventListener: jest.fn(),
        removeEventListener: jest.fn(),
    })) as any;
    const computedStyle = window.getComputedStyle;
    jest.spyOn(window, "getComputedStyle").mockImplementation((element) => computedStyle(element));
    jest.spyOn(Grid, "useBreakpoint").mockReturnValue({ md: true, sm: true });
    mockDevMode = false;
    mockManage = true;
    mockConfigure = true;
    mockStatic = false;
    mockLang = "en_US";
    mockGetCsrData.mockReset();
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
});
afterEach(() => {
    act(() => root.unmount());
    container.remove();
    jest.useRealTimers();
    jest.restoreAllMocks();
    delete (globalThis as any).IS_REACT_ACT_ENVIRONMENT;
    delete (globalThis as any).MessageChannel;
});

const renderSystem = async (value = data, page: SystemInfoPage = "overview", offline = false) => {
    await act(async () =>
        root.render(
            <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
                <System data={value} offline={offline} activePage={page} />
            </MemoryRouter>
        )
    );
};

test("overview shows key live readings without the environment and resource detail lists", async () => {
    await renderSystem();
    const overview = container.querySelector('a[href="/blog/admin/system"]');
    expect(overview?.getAttribute("aria-current")).toBe("page");
    expect(container.querySelector('a[href="/blog/admin/system/proxy"]')).not.toBeNull();
    for (const value of ["13%", "128M", "2 / 8", "2 hours"]) expect(container.textContent).toContain(value);
    expect(container.querySelectorAll('[role="meter"]')).toHaveLength(2);
    expect(container.textContent).not.toContain("128M / 2G");
    expect(container.textContent).not.toContain("GraalVM 25");
    expect(container.textContent).not.toContain("512M");
    expect(container.querySelector('a[href="/blog/admin/dev"]')).toBeNull();
    expect(container.querySelector('a[href="/blog/admin/system/version"]')?.textContent).toBe("Version Info");
});

test("runtime and resource pages separate their details and preserve external documentation links", async () => {
    await renderSystem(data, "runtime");
    for (const item of data.serverInfos.filter((item) => item.key !== "cpuInfo"))
        expect(container.textContent).toContain(item.value);
    expect(container.querySelector('a[href="https://www.graalvm.org"]')?.textContent).toBe("GraalVM 25");
    expect(container.textContent).not.toContain("128M");
    await renderSystem(data, "resources");
    for (const value of ["Test processor", "2G", "512M", "16M", "0.10", "0.20", "0.30"])
        expect(container.textContent).toContain(value);
    expect(container.textContent).not.toContain("ZrLog 4.0.1");
    expect(container.querySelectorAll('[role="meter"]')).toHaveLength(0);
});

test("developer tools appear only while server development mode is enabled", async () => {
    await renderSystem();
    expect(container.querySelector('a[href="/blog/admin/dev"]')).toBeNull();
    mockDevMode = true;
    await renderSystem();
    expect(container.querySelector('a[href="/blog/admin/dev"]')?.textContent).toBe("Developer tools");
    mockDevMode = false;
    await renderSystem();
    expect(container.querySelector('a[href="/blog/admin/dev"]')).toBeNull();
});

test.each([false, true])(
    "legacy version links redirect without re-encoding query parameters (static=%s)",
    async (staticPage) => {
        mockStatic = staticPage;
        const suffix = staticPage ? ".html" : "";
        let navigate: NavigateFunction;
        const Location = () => {
            navigate = useNavigate();
            const location = useLocation();
            return <output>{location.pathname + location.search + location.hash}</output>;
        };
        await act(async () =>
            root.render(
                <MemoryRouter
                    initialEntries={["/blog/admin/system", `/blog/admin/website/version${suffix}?value=a%2Bb#build`]}
                    future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
                >
                    <Location />
                    <Routes>
                        <Route path={`/blog/admin/website/version${suffix}`} element={<LegacyVersion />} />
                        <Route path="*" element={<div />} />
                    </Routes>
                </MemoryRouter>
            )
        );
        expect(container.querySelector("output")?.textContent).toBe(
            `/blog/admin/system/version${suffix}?value=a%2Bb#build`
        );
        await act(async () => navigate(-1));
        expect(container.querySelector("output")?.textContent).toBe("/blog/admin/system");
    }
);

test("navigation respects system and site permissions and selects the current page", async () => {
    mockManage = false;
    mockDevMode = true;
    await renderSystem();
    expect(container.querySelector('a[href="/blog/admin/system/proxy"]')).toBeNull();
    expect(container.querySelector('a[href="/blog/admin/dev"]')).toBeNull();
    expect(container.querySelector('a[href="/blog/admin/system/version"]')).not.toBeNull();
    mockManage = true;
    mockConfigure = false;
    mockLang = "zh_CN";
    await act(async () =>
        root.render(
            <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
                <SystemSettingsLayout activeKey="proxy">代理表单</SystemSettingsLayout>
            </MemoryRouter>
        )
    );
    expect(container.querySelector('a[href="/blog/admin/system/proxy"]')?.getAttribute("aria-current")).toBe("page");
    expect(container.querySelector('a[href="/blog/admin/system"]')?.textContent).toBe("系统概览");
    expect(container.querySelector('a[href="/blog/admin/system/version"]')).toBeNull();
});

test("unavailable metrics remain raw readings without invented percentages", async () => {
    await renderSystem({
        ...data,
        serverInfos2: [entry("cpuLoad", "CPU usage", "-"), entry("dbConnectSize", "Database connections", "0 / 0")],
    });
    expect(container.textContent).toContain("0 / 0");
    expect(container.querySelectorAll('[role="meter"]')).toHaveLength(0);
});

test.each(["overview", "resources"] as SystemInfoPage[])(
    "%s refreshes live readings and stops polling on unmount",
    async (page) => {
        jest.useFakeTimers();
        jest.spyOn(document, "visibilityState", "get").mockReturnValue("visible");
        mockGetCsrData.mockResolvedValue({
            data: {
                ...data,
                serverInfos2: [entry("uptime", "Uptime", "3 hours"), entry("usedDiskSpace", "Disk usage", "600M")],
            },
        });
        await renderSystem(data, page);
        expect(mockGetCsrData).not.toHaveBeenCalled();
        await act(async () => {
            jest.advanceTimersByTime(5000);
        });
        expect(mockGetCsrData).toHaveBeenCalledTimes(1);
        expect(mockGetCsrData.mock.calls[0][0]).toBe(page === "resources" ? "/system/resources" : "/system");
        expect(container.textContent).toContain(page === "overview" ? "3 hours" : "600M");
        await act(async () => root.render(<div />));
        await act(async () => {
            jest.advanceTimersByTime(10000);
        });
        expect(mockGetCsrData).toHaveBeenCalledTimes(1);
    }
);

test("runtime, offline, and hidden pages do not poll", async () => {
    jest.useFakeTimers();
    jest.spyOn(document, "visibilityState", "get").mockReturnValue("visible");
    await renderSystem(data, "runtime");
    await act(async () => {
        jest.advanceTimersByTime(10000);
    });
    await renderSystem(data, "overview", true);
    await act(async () => {
        jest.advanceTimersByTime(10000);
    });
    jest.spyOn(document, "visibilityState", "get").mockReturnValue("hidden");
    await renderSystem(data, "overview");
    await act(async () => {
        jest.advanceTimersByTime(10000);
    });
    expect(mockGetCsrData).not.toHaveBeenCalled();
});

test.each([
    [false, false],
    [false, true],
    [true, false],
    [true, true],
])("system deep links and browser history work (static=%s, mobile=%s)", async (staticPage, mobile) => {
    mockStatic = staticPage;
    mockDevMode = true;
    jest.mocked(Grid.useBreakpoint).mockReturnValue({ md: !mobile, sm: !mobile });
    const suffix = staticPage ? ".html" : "";
    let navigate: NavigateFunction;
    const Pages = () => {
        navigate = useNavigate();
        return (
            <Routes>
                {(["overview", "runtime", "resources"] as SystemInfoPage[]).map((page) => (
                    <Route
                        key={page}
                        path={`/blog/admin/system${page === "overview" ? "" : `/${page}`}${suffix}`}
                        element={<System data={data} offline={false} activePage={page} />}
                    />
                ))}
                <Route
                    path={`/blog/admin/dev${suffix}`}
                    element={<SystemSettingsLayout activeKey="dev">Developer content</SystemSettingsLayout>}
                />
                <Route
                    path={`/blog/admin/system/version${suffix}`}
                    element={<SystemSettingsLayout activeKey="version">Version content</SystemSettingsLayout>}
                />
            </Routes>
        );
    };
    await act(async () =>
        root.render(
            <MemoryRouter
                initialEntries={[`/blog/admin/system/runtime${suffix}`]}
                future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
            >
                <Pages />
            </MemoryRouter>
        )
    );
    const selected = () =>
        container.querySelector(mobile ? ".ant-select-content" : 'nav a[aria-current="page"]')?.textContent;
    const choose = async (label: string) => {
        if (mobile) {
            await act(async () =>
                container
                    .querySelector('[role="combobox"]')!
                    .dispatchEvent(new MouseEvent("mousedown", { bubbles: true }))
            );
            await act(async () =>
                Array.from(container.querySelectorAll<HTMLElement>(".ant-select-item-option"))
                    .find((item) => item.textContent === label)!
                    .click()
            );
        } else {
            await act(async () =>
                Array.from(container.querySelectorAll<HTMLAnchorElement>("nav a"))
                    .find((item) => item.textContent === label)!
                    .click()
            );
        }
    };
    expect(selected()).toBe("Runtime environment");
    expect(container.textContent).toContain("GraalVM 25");
    await choose("Resource status");
    expect(selected()).toBe("Resource status");
    expect(container.textContent).toContain("512M");
    await act(async () => navigate(-1));
    expect(selected()).toBe("Runtime environment");
    await act(async () => navigate(1));
    expect(selected()).toBe("Resource status");
    await choose("Developer tools");
    expect(selected()).toBe("Developer tools");
    expect(container.textContent).toContain("Developer content");
    await choose("Version Info");
    expect(selected()).toBe("Version Info");
    expect(container.textContent).toContain("Version content");
});

test.each([false, true])(
    "global navigation keeps all system pages under their own entry (expanded=%s)",
    async (expanded) => {
        for (const path of [
            "/system",
            "/system/runtime.html",
            "/system/resources",
            "/system/proxy",
            "/dev.html",
            "/system/version",
        ]) {
            await act(async () =>
                root.render(
                    <MemoryRouter
                        key={path}
                        initialEntries={[path]}
                        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
                    >
                        <SliderMenu expanded={expanded} />
                    </MemoryRouter>
                )
            );
            expect(container.querySelector(".ant-menu-item-selected a")?.getAttribute("href")).toBe(
                "/blog/admin/system"
            );
        }
        mockManage = false;
        await act(async () =>
            root.render(
                <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
                    <SliderMenu expanded={expanded} />
                </MemoryRouter>
            )
        );
        expect(container.querySelector('a[href="/blog/admin/system"]')).toBeNull();
    }
);
