import { act, Suspense, useState } from "react";
import type { Dispatch, SetStateAction } from "react";
import { createRoot, Root } from "react-dom/client";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import SsData, { getSsDate, ssKeyStorageKey } from "../base/SsData";
import { getRes } from "../utils/constants";
import { applyUserPreferences } from "../utils/user-preferences";
import type { BasicUserInfo } from "../type";
import AdminDashboardPage from "./admin-dashboard-page";

const mockGet = jest.fn<Promise<any>, [string]>();
const mockAxios = { get: mockGet };
const mockGetCsrData = jest.fn<Promise<any>, unknown[]>();
const mockRedirect = jest.fn();
const mockChangeAppState = jest.fn();
let mockCachedUser: BasicUserInfo | undefined;

jest.mock("../base/AppBase", () => ({
    useAxiosBaseInstance: () => mockAxios,
    jumpToLoginPage: () => mockRedirect(),
}));
jest.mock("../api", () => ({ getCsrData: (...args: unknown[]) => mockGetCsrData(...args) }));
jest.mock("../utils/cache", () => ({ getCacheByKey: () => mockCachedUser }));
jest.mock("../base/ConfigProviderApp", () => ({ changeAppState: (state: unknown) => mockChangeAppState(state) }));
jest.mock("../base/AppInit", () => ({
    getColorPrimaryByRes: () => require("../utils/constants").getRes().admin_color_primary,
    isDarkModeByRes: () => require("../utils/constants").getRes().admin_darkMode,
}));
jest.mock("components/admin-dashboard-router", () => ({
    __esModule: true,
    default: ({ userInfo }: { userInfo: BasicUserInfo }) => <main>{userInfo.userName}</main>,
}));

const personal = {
    language: "en_US" as const,
    appearance: { theme: "antd" as const, darkMode: true, compactMode: true, colorPrimary: "#00875a" },
};
const user = (id = 1, key = "one"): BasicUserInfo => ({ userId: id, userName: `user-${id}`, header: "", key });
const preferenceResponse = () => ({ data: { error: 0, data: { effective: personal } } });
const deferred = () => {
    let resolve!: (value: unknown) => void;
    const promise = new Promise((done) => {
        resolve = done;
    });
    return { promise, resolve };
};

describe("dashboard appearance initialization", () => {
    let root: Root;
    let container: HTMLDivElement;
    let payload: HTMLPreElement;
    let setMounted: Dispatch<SetStateAction<boolean>>;
    let setOffline: Dispatch<SetStateAction<boolean>>;

    const Harness = ({ initiallyOffline }: { initiallyOffline: boolean }) => {
        const [mounted, updateMounted] = useState(true);
        const [offline, updateOffline] = useState(initiallyOffline);
        setMounted = updateMounted;
        setOffline = updateOffline;
        return mounted ? <AdminDashboardPage offline={offline} /> : null;
    };

    const render = async (data: Record<string, unknown>, offline = false) => {
        payload.innerText = JSON.stringify({ pageBuildId: "test", systemNotification: "", ...data });
        await act(async () =>
            root.render(
                <SsData>
                    <MemoryRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
                        <Suspense fallback={null}>
                            <Harness initiallyOffline={offline} />
                        </Suspense>
                    </MemoryRouter>
                </SsData>
            )
        );
    };
    const remount = async () => {
        await act(async () => setMounted(false));
        await act(async () => setMounted(true));
    };

    beforeEach(() => {
        (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
        localStorage.clear();
        delete window.__SS_DATA__;
        mockCachedUser = undefined;
        mockGet.mockReset().mockResolvedValue(preferenceResponse());
        mockGetCsrData.mockReset();
        mockRedirect.mockReset();
        mockChangeAppState.mockReset();
        container = document.createElement("div");
        payload = document.createElement("pre");
        payload.id = "__SS_DATA__";
        document.body.append(container, payload);
        root = createRoot(container);
    });

    afterEach(() => {
        act(() => root.unmount());
        container.remove();
        payload.remove();
        localStorage.clear();
        delete window.__SS_DATA__;
        (globalThis as any).IS_REACT_ACT_ENVIRONMENT = false;
    });

    it("uses authenticated server appearance without a second preferences request, including re-entry", async () => {
        await render({
            user: user(),
            key: "one",
            resourceInfo: { lang: "en_US", admin_theme: "antd", admin_darkMode: true, staticPage: false },
        });
        await remount();
        expect(container.textContent).toBe("user-1");
        expect(getRes()).toMatchObject({ lang: "en_US", admin_theme: "antd", admin_darkMode: true });
        expect(mockGet).not.toHaveBeenCalled();
        expect(mockGetCsrData).not.toHaveBeenCalled();
        expect(mockChangeAppState).not.toHaveBeenCalled();
    });

    it.each([true, false])(
        "loads personal appearance for a cached user on a public shell (static=%s)",
        async (staticPage) => {
            mockCachedUser = user();
            localStorage.setItem(ssKeyStorageKey, "one");
            await render({ user: null, key: null, resourceInfo: { lang: "zh_CN", staticPage } });
            expect(container.textContent).toBe("user-1");
            expect(mockGet).toHaveBeenCalledWith("/api/admin/user/preferences");
            expect(getRes()).toMatchObject({ lang: "en_US", admin_theme: "antd", admin_darkMode: true });
            expect(document.documentElement.lang).toBe("en");
            await remount();
            expect(mockGet).toHaveBeenCalledTimes(1);
            expect(getSsDate().appearanceSession).toEqual({ key: "one", userId: 1 });
        }
    );

    it("loads preferences after resolving a signed-in user without server or browser user data", async () => {
        mockGetCsrData.mockResolvedValue({ error: 0, data: user(2, "new-session") });
        await render({ user: null, resourceInfo: { lang: "zh_CN", staticPage: true } });
        expect(container.textContent).toBe("user-2");
        expect(getSsDate().appearanceSession).toEqual({ key: "new-session", userId: 2 });
        expect(getRes().lang).toBe("en_US");
        expect(mockGet).toHaveBeenCalledTimes(1);
    });

    it("loads the next login's preferences instead of reusing the previous session marker", async () => {
        await render({ user: user(), key: "one", resourceInfo: { lang: "zh_CN", staticPage: false } });
        await act(async () => setMounted(false));
        getSsDate().user = user(2, "two");
        getSsDate().key = "two";
        await act(async () => setMounted(true));
        expect(container.textContent).toBe("user-2");
        expect(getRes().lang).toBe("en_US");
        expect(getSsDate().appearanceSession).toEqual({ key: "two", userId: 2 });
        expect(mockGet).toHaveBeenCalledTimes(1);
    });

    it("defers missing preferences while offline and loads them once after reconnection", async () => {
        mockCachedUser = user();
        localStorage.setItem(ssKeyStorageKey, "one");
        await render({ user: null, resourceInfo: { lang: "zh_CN", staticPage: true } }, true);
        expect(container.textContent).toBe("user-1");
        expect(mockGet).not.toHaveBeenCalled();
        await act(async () => setOffline(false));
        expect(getRes().lang).toBe("en_US");
        await act(async () => setOffline(true));
        await act(async () => setOffline(false));
        expect(mockGet).toHaveBeenCalledTimes(1);
    });

    it("does not overwrite an appearance preview with a late bootstrap response", async () => {
        const pending = deferred();
        mockGet.mockReturnValue(pending.promise);
        mockCachedUser = user();
        localStorage.setItem(ssKeyStorageKey, "one");
        await render({ user: null, resourceInfo: { lang: "zh_CN", staticPage: true } });
        applyUserPreferences({ language: "zh_CN", appearance: { theme: "desk", darkMode: false } });
        await act(async () => pending.resolve(preferenceResponse()));
        expect(getRes()).toMatchObject({ lang: "zh_CN", admin_theme: "desk", admin_darkMode: false });
        expect(getSsDate().appearanceSession).toBeUndefined();
    });

    it("ignores a previous session's response and retries failed initialization on re-entry", async () => {
        const pending = deferred();
        mockGet.mockReturnValueOnce(pending.promise);
        mockCachedUser = user();
        localStorage.setItem(ssKeyStorageKey, "one");
        await render({ user: null, resourceInfo: { lang: "zh_CN", staticPage: true } });
        getSsDate().key = "two";
        getSsDate().user = user(2, "two");
        await act(async () => pending.resolve(preferenceResponse()));
        expect(getRes().lang).toBe("zh_CN");
        expect(getSsDate().appearanceSession).toBeUndefined();
        mockGet.mockRejectedValueOnce(new Error("unavailable"));
        await remount();
        expect(getSsDate().appearanceSession).toBeUndefined();
        await remount();
        expect(container.textContent).toBe("user-2");
        expect(getRes().lang).toBe("en_US");
        expect(getSsDate().appearanceSession).toEqual({ key: "two", userId: 2 });
    });
});
