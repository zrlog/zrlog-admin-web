import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { Link, MemoryRouter, NavigateFunction, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { Grid } from "antd";
import { afterEach, beforeEach, expect, jest, test } from "@jest/globals";
import System from "./index";
import Version from "./Version";
import { markdownToHtmlSyncWithCallback } from "@zrlog/editor/dist/editor/utils/marked-utils";
import AdminSettingsLayout from "../common/AdminSettingsLayout";
import { SystemData } from "../../type";
import SliderMenu from "../../layout/slider";
import SystemSettingsRedirect from "../website/system-settings-redirect";

jest.mock("@zrlog/ui/icons/dashboard", () => () => null);
jest.mock("@zrlog/ui/icons/container", () => () => null);
jest.mock("@zrlog/ui/icons/info", () => () => null);
jest.mock("@zrlog/ui/icons/experiment", () => () => null);
jest.mock("@zrlog/ui/icons/link", () => () => null);
jest.mock("@zrlog/ui/icons/database", () => () => null);
jest.mock("@zrlog/ui/icons/arrow-right", () => () => null);
jest.mock("@zrlog/ui/icons/code", () => () => null);
jest.mock("@zrlog/ui/icons/disk", () => () => null);
jest.mock("@zrlog/ui/icons/globe", () => () => null);
jest.mock("@zrlog/ui/icons/partition", () => () => null);
jest.mock("@zrlog/ui/icons/server", () => () => null);
jest.mock("@zrlog/ui/icons/time", () => () => null);
jest.mock("@zrlog/ui/icons/cpu", () => () => null);
jest.mock("@zrlog/ui/icons/memory", () => () => null);
jest.mock("icons/ZrLogOutlined", () => () => null);
jest.mock("../admin-dashboard-routes", () => ({ renderAdminDashboardRouteIcon: () => null }));
jest.mock("@zrlog/editor/dist/editor/utils/marked-utils", () => ({
    markdownToHtmlSyncWithCallback: require("@jest/globals").jest.fn(),
}));
jest.mock("@zrlog/editor/core/editor/html-preview-panel", () => ({
    __esModule: true,
    default: ({ htmlContent }: { htmlContent: string }) => require("react").createElement("div", null, htmlContent),
}));
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
jest.mock("../../utils/module-capabilities", () => ({ isFeaturePathEnabled: () => true }));
jest.mock("../../utils/account-access", () => {
    const actual = require("@jest/globals").jest.requireActual(
        "../../utils/account-access"
    ) as typeof import("../../utils/account-access");
    return {
        hasAction: (action: string) => (action === "site.configure" ? mockConfigure : mockManage),
        actionForPath: actual.actionForPath,
        canAccessPage: (path: string) => (actual.actionForPath(path) === "site.configure" ? mockConfigure : mockManage),
    };
});
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
        entry("programInfo", "Application version", "4.0.1"),
        entry("buildInfo", "Build information", "a1b2c3d"),
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
const versionData = {
    version: "4.0.1",
    buildId: "a1b2c3d",
    changelog: "Current release changes",
    buildSystemInfo: "Package build environment",
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
    jest.mocked(markdownToHtmlSyncWithCallback)
        .mockReset()
        .mockImplementation((markdown) => markdown);
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

const renderSystem = async (value = data, offline = false) => {
    await act(async () =>
        root.render(
            <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
                <System data={value} offline={offline} />
            </MemoryRouter>
        )
    );
};

test("system information shows live readings, environment and resource details on one page", async () => {
    await renderSystem();
    const overview = container.querySelector('a[href="/blog/admin/system"]');
    expect(overview?.getAttribute("aria-current")).toBe("page");
    expect(container.querySelector('a[href="/blog/admin/system/proxy"]')).not.toBeNull();
    for (const value of ["13%", "128M", "2 / 8", "2 hours"]) expect(container.textContent).toContain(value);
    expect(container.querySelectorAll('[role="meter"]')).toHaveLength(2);
    expect(container.textContent).not.toContain("128M / 2G");
    for (const item of data.serverInfos) expect(container.textContent).toContain(item.value);
    for (const value of ["2G", "512M", "16M", "0.10", "0.20", "0.30"]) expect(container.textContent).toContain(value);
    const runtimeLink = container.querySelector('a[href="https://www.graalvm.org"]');
    expect(runtimeLink?.textContent).toContain("Runtime");
    expect(runtimeLink?.textContent).toContain("GraalVM 25");
    expect(runtimeLink?.getAttribute("target")).toBe("_blank");
    expect(runtimeLink?.getAttribute("rel")).toBe("noopener noreferrer");
    expect(runtimeLink?.querySelector("a")).toBeNull();
    expect(container.querySelector('a[href="/blog/admin/website"]')).not.toBeNull();
    expect(container.querySelector('a[href="/blog/admin/system/upgrade"]')).not.toBeNull();
    expect(container.querySelector('a[href="/blog/admin/system/runtime"]')).toBeNull();
    expect(container.querySelector('a[href="/blog/admin/system/resources"]')).toBeNull();
    expect(container.querySelector('a[href="/blog/admin/dev"]')).toBeNull();
    const buildLink = container.querySelector('a[href="/blog/admin/system/version"]');
    for (const value of ["Build information", "a1b2c3d"]) {
        expect(buildLink?.textContent).toContain(value);
    }
    expect(buildLink?.getAttribute("aria-label")).toContain("View build details");
    expect(buildLink?.querySelector("a")).toBeNull();
    expect(container.querySelector('nav a[href="/blog/admin/system/version"]')).toBeNull();
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

test.each([
    [false, "version"],
    [true, "version"],
    [false, "upgrade"],
    [true, "upgrade"],
] as const)(
    "existing settings links redirect with encoded queries and history preserved (static=%s, page=%s)",
    async (staticPage, page) => {
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
                    initialEntries={["/blog/admin/system", `/blog/admin/website/${page}${suffix}?value=a%2Bb#build`]}
                    future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
                >
                    <Location />
                    <Routes>
                        <Route
                            path={`/blog/admin/website/${page}${suffix}`}
                            element={<SystemSettingsRedirect page={page} />}
                        />
                        <Route path="*" element={<div />} />
                    </Routes>
                </MemoryRouter>
            )
        );
        expect(container.querySelector("output")?.textContent).toBe(
            `/blog/admin/system/${page}${suffix}?value=a%2Bb#build`
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
                <AdminSettingsLayout activeKey="proxy">代理表单</AdminSettingsLayout>
            </MemoryRouter>
        )
    );
    expect(container.querySelector('a[href="/blog/admin/system/proxy"]')?.getAttribute("aria-current")).toBe("page");
    expect(container.querySelector('a[href="/blog/admin/system"]')?.textContent).toBe("系统信息");
    expect(container.querySelector('a[href="/blog/admin/system/version"]')).toBeNull();
    await renderSystem();
    expect(container.textContent).toContain("a1b2c3d");
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

test("refreshes all system readings together and stops polling on unmount", async () => {
    jest.useFakeTimers();
    jest.spyOn(document, "visibilityState", "get").mockReturnValue("visible");
    mockGetCsrData.mockResolvedValue({
        data: {
            ...data,
            serverInfos2: [entry("uptime", "Uptime", "3 hours"), entry("usedDiskSpace", "Disk usage", "600M")],
        },
    });
    await renderSystem();
    expect(mockGetCsrData).not.toHaveBeenCalled();
    await act(async () => {
        jest.advanceTimersByTime(5000);
    });
    expect(mockGetCsrData).toHaveBeenCalledTimes(1);
    expect(mockGetCsrData.mock.calls[0][0]).toBe("/system");
    expect(container.textContent).toContain("3 hours");
    expect(container.textContent).toContain("600M");
    expect(container.textContent).toContain("GraalVM 25");
    await act(async () => root.render(<div />));
    await act(async () => {
        jest.advanceTimersByTime(10000);
    });
    expect(mockGetCsrData).toHaveBeenCalledTimes(1);
});

test("offline and hidden pages do not poll", async () => {
    jest.useFakeTimers();
    jest.spyOn(document, "visibilityState", "get").mockReturnValue("visible");
    await renderSystem(data, true);
    await act(async () => {
        jest.advanceTimersByTime(10000);
    });
    jest.spyOn(document, "visibilityState", "get").mockReturnValue("hidden");
    await renderSystem(data);
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
                <Route path={`/blog/admin/system${suffix}`} element={<System data={data} offline={false} />} />
                <Route
                    path={`/blog/admin/website${suffix}`}
                    element={<AdminSettingsLayout activeKey="basic">Site content</AdminSettingsLayout>}
                />
                <Route
                    path={`/blog/admin/system/upgrade${suffix}`}
                    element={<AdminSettingsLayout activeKey="upgrade">Update content</AdminSettingsLayout>}
                />
                <Route
                    path={`/blog/admin/upgrade${suffix}`}
                    element={
                        <AdminSettingsLayout
                            activeKey="upgrade"
                            title="Upgrade Wizard"
                            summary={null}
                            extra={<Link to={`/blog/admin/system/upgrade${suffix}`}>Back to Update Settings</Link>}
                        >
                            Update execution
                        </AdminSettingsLayout>
                    }
                />
                <Route
                    path={`/blog/admin/dev${suffix}`}
                    element={<AdminSettingsLayout activeKey="dev">Developer content</AdminSettingsLayout>}
                />
                <Route path={`/blog/admin/system/version${suffix}`} element={<Version data={versionData} />} />
            </Routes>
        );
    };
    await act(async () =>
        root.render(
            <MemoryRouter
                initialEntries={[`/blog/admin/system${suffix}`]}
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
    expect(selected()).toBe("System information");
    expect(container.textContent).toContain("GraalVM 25");
    expect(container.textContent).toContain("512M");
    await choose("Basic Settings");
    expect(selected()).toBe("Basic Settings");
    expect(container.textContent).toContain("Site content");
    await act(async () => navigate(-1));
    expect(selected()).toBe("System information");
    await act(async () => navigate(1));
    expect(selected()).toBe("Basic Settings");
    await choose("Developer tools");
    expect(selected()).toBe("Developer tools");
    expect(container.textContent).toContain("Developer content");
    await choose("System information");
    await act(async () =>
        container.querySelector<HTMLAnchorElement>(`a[href="/blog/admin/system/version${suffix}"]`)!.click()
    );
    expect(selected()).toBe("System information");
    expect(container.textContent).toContain("Build ID");
    expect(container.textContent).toContain("a1b2c3d");
    expect(container.textContent).toContain("Package build environment");
    expect(container.textContent).toContain("Current release changes");
    expect(container.querySelector(`nav a[href="/blog/admin/system/version${suffix}"]`)).toBeNull();
    expect(container.querySelector("h2")?.textContent).toBe("Build Information");
    await act(async () =>
        Array.from(container.querySelectorAll<HTMLAnchorElement>("a"))
            .find((item) => item.textContent === "Back to System Information")!
            .click()
    );
    expect(selected()).toBe("System information");
    expect(container.textContent).toContain("GraalVM 25");
    await choose("Update Settings");
    expect(selected()).toBe("Update Settings");
    await act(async () => navigate(`/blog/admin/upgrade${suffix}`));
    expect(selected()).toBe("Update Settings");
    expect(container.textContent).toContain("Update execution");
    expect(container.textContent).not.toContain(
        "Set the update check interval and whether preview versions are included"
    );
    expect(container.querySelector('nav a[href^="/blog/admin/upgrade"]')).toBeNull();
    expect(container.querySelector("h2")?.textContent).toBe("Upgrade Wizard");
    await act(async () =>
        Array.from(container.querySelectorAll<HTMLAnchorElement>("a"))
            .find((item) => item.textContent === "Back to Update Settings")!
            .click()
    );
    expect(selected()).toBe("Update Settings");
    expect(container.textContent).toContain("Update content");
});

test("build details keep a permitted return path for accounts without system access", async () => {
    mockManage = false;
    await act(async () =>
        root.render(
            <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
                <Version data={versionData} />
            </MemoryRouter>
        )
    );
    expect(container.querySelector('nav a[aria-current="page"]')?.textContent).toBe("Basic Settings");
    expect(container.querySelector('a[href="/blog/admin/system"]')).toBeNull();
    expect(
        Array.from(container.querySelectorAll("a"))
            .find((item) => item.textContent === "Back to Settings")
            ?.getAttribute("href")
    ).toBe("/blog/admin/website");
});

test("build details render markdown once per document and ignore replaced document callbacks", async () => {
    const callbacks: Array<(html: string) => void> = [];
    const renderMarkdown = jest.mocked(markdownToHtmlSyncWithCallback);
    renderMarkdown.mockImplementation((markdown, callback) => {
        callbacks.push(callback);
        return markdown;
    });
    const renderVersion = async (value: typeof versionData) => {
        await act(async () =>
            root.render(
                <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
                    <Version data={value} />
                </MemoryRouter>
            )
        );
    };
    await renderVersion(versionData);
    expect(renderMarkdown).toHaveBeenCalledTimes(2);
    await act(async () => callbacks[1]("Rendered build report"));
    expect(container.textContent).toContain("Rendered build report");
    expect(renderMarkdown).toHaveBeenCalledTimes(2);
    await renderVersion({ ...versionData });
    expect(renderMarkdown).toHaveBeenCalledTimes(2);
    await renderVersion({ ...versionData, buildSystemInfo: "New build report" });
    expect(renderMarkdown).toHaveBeenCalledTimes(3);
    await act(async () => callbacks[1]("Outdated report"));
    expect(container.textContent).toContain("New build report");
    expect(container.textContent).not.toContain("Outdated report");
});

test.each([false, true])(
    "global navigation selects Settings for site and system pages (expanded=%s)",
    async (expanded) => {
        for (const path of [
            "/system",
            "/website/admin",
            "/system/upgrade.html",
            "/system/proxy",
            "/dev.html",
            "/system/version",
            "/upgrade",
            "/upgrade.html",
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
                "/blog/admin/website"
            );
            expect(container.querySelector('a[href="/blog/admin/system"]')).toBeNull();
            expect(container.querySelector('a[href="/blog/admin/upgrade"]')).toBeNull();
        }
        mockConfigure = false;
        await act(async () =>
            root.render(
                <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
                    <SliderMenu expanded={expanded} />
                </MemoryRouter>
            )
        );
        expect(container.querySelector('a[href="/blog/admin/system"]')).not.toBeNull();
        expect(container.querySelector('a[href="/blog/admin/website"]')).toBeNull();
    }
);
