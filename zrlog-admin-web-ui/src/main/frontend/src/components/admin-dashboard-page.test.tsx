import { act, Suspense, useState } from "react";
import type { Dispatch, SetStateAction } from "react";
import { createRoot, Root } from "react-dom/client";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import SsData, { getSsDate, ssKeyStorageKey } from "../base/SsData";
import { getRes } from "../utils/constants";
import { refreshUserInfo } from "../utils/user-info";
import { applyUserPreferences, cacheUserAppearance } from "../utils/user-preferences";
import type { BasicUserInfo } from "../type";
import AdminDashboardPage from "./admin-dashboard-page";

const mockAxios = {};
const mockGetCsrData = jest.fn<Promise<any>, unknown[]>();
const mockRedirect = jest.fn();
const mockChangeAppState = jest.fn();
let mockCachedUser: BasicUserInfo | undefined;

jest.mock("../base/AppBase", () => ({
    useAxiosBaseInstance: () => mockAxios,
    jumpToLoginPage: () => mockRedirect(),
}));
jest.mock("../api", () => ({ getCsrData: (...args: unknown[]) => mockGetCsrData(...args) }));
jest.mock("../utils/cache", () => ({
    getCacheByKey: () => mockCachedUser,
    cacheUserInfo: (user: BasicUserInfo) => {
        mockCachedUser = user;
    },
}));
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
const user = (id = 1, key = "one"): BasicUserInfo => ({
    userId: id,
    userName: `user-${id}`,
    header: "",
    key,
    ...personal,
});
const userResponse = (value = user()) => ({ error: 0, data: value });
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
        mockGetCsrData.mockReset().mockResolvedValue(userResponse());
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

    it("reuses authenticated server identity and appearance on entry and re-entry", async () => {
        await render({ user: user(), key: "one", resourceInfo: { lang: "zh_CN", staticPage: false } });
        await remount();
        expect(container.textContent).toBe("user-1");
        expect(getRes()).toMatchObject({ lang: "en_US", admin_theme: "antd", admin_darkMode: true });
        expect(mockGetCsrData).not.toHaveBeenCalled();
        expect(mockCachedUser).toEqual(user());
        expect(mockChangeAppState).not.toHaveBeenCalled();
    });

    it.each([true, false])(
        "restores cached appearance before revalidating a public shell (static=%s)",
        async (staticPage) => {
            const pending = deferred();
            mockGetCsrData.mockReturnValueOnce(pending.promise);
            mockCachedUser = user();
            localStorage.setItem(ssKeyStorageKey, "one");
            await render({ user: null, key: null, resourceInfo: { lang: "zh_CN", staticPage } });
            expect(container.textContent).toBe("user-1");
            expect(getRes()).toMatchObject({ lang: "en_US", admin_theme: "antd", admin_darkMode: true });
            expect(document.documentElement.lang).toBe("en");
            expect(mockGetCsrData).toHaveBeenCalledWith("/user/info", expect.any(Number), mockAxios);
            const fresh = { ...user(), appearance: { ...personal.appearance, colorPrimary: "#123456" } };
            await act(async () => pending.resolve(userResponse(fresh)));
            await remount();
            expect(mockGetCsrData).toHaveBeenCalledTimes(1);
            expect(getRes().admin_color_primary).toBe("#123456");
            expect(mockCachedUser).toEqual(fresh);
        }
    );

    it("initializes identity, session and appearance together without cached account data", async () => {
        mockGetCsrData.mockResolvedValue(userResponse(user(2, "new-session")));
        await render({ user: null, key: "", resourceInfo: { lang: "zh_CN", staticPage: true } });
        expect(container.textContent).toBe("user-2");
        expect(getSsDate().key).toBe("new-session");
        expect(localStorage.getItem(ssKeyStorageKey)).toBe("new-session");
        expect(getRes().lang).toBe("en_US");
        expect(mockCachedUser).toEqual(user(2, "new-session"));
        expect(mockGetCsrData).toHaveBeenCalledTimes(1);
    });

    it("refreshes legacy account data without appearance using user/info", async () => {
        const legacy = { ...user(), language: undefined, appearance: undefined };
        await render({ user: legacy, key: "one", resourceInfo: { lang: "zh_CN" } });
        expect(mockGetCsrData).toHaveBeenCalledTimes(1);
        expect(getSsDate().user).toEqual(user());
        expect(getRes().admin_theme).toBe("antd");
    });

    it("renders cached appearance offline and revalidates once after reconnecting", async () => {
        mockCachedUser = user();
        localStorage.setItem(ssKeyStorageKey, "one");
        await render({ user: null, resourceInfo: { lang: "zh_CN", staticPage: true } }, true);
        expect(container.textContent).toBe("user-1");
        expect(getRes().lang).toBe("en_US");
        expect(mockGetCsrData).not.toHaveBeenCalled();
        await act(async () => setOffline(false));
        await act(async () => setOffline(true));
        await act(async () => setOffline(false));
        expect(mockGetCsrData).toHaveBeenCalledTimes(1);
        expect(mockCachedUser).toEqual(user());
    });

    it("reconnects a cold offline shell and redirects invalid authentication", async () => {
        mockGetCsrData.mockResolvedValue({ error: 401 });
        await render({ user: null, key: "", resourceInfo: { staticPage: true } }, true);
        expect(mockGetCsrData).not.toHaveBeenCalled();
        await act(async () => setOffline(false));
        expect(mockRedirect).toHaveBeenCalledTimes(1);
        expect(getSsDate().user).toBeNull();
    });

    it("does not overwrite or cache a temporary preview when revalidation completes", async () => {
        const pending = deferred();
        mockGetCsrData.mockReturnValueOnce(pending.promise);
        mockCachedUser = user();
        localStorage.setItem(ssKeyStorageKey, "one");
        await render({ user: null, resourceInfo: { staticPage: true } });
        applyUserPreferences({ language: "zh_CN", appearance: { theme: "desk", darkMode: false } });
        await act(async () => pending.resolve(userResponse()));
        expect(getRes()).toMatchObject({ lang: "zh_CN", admin_theme: "desk", admin_darkMode: false });
        expect(mockCachedUser).toEqual(user());
    });

    it("ignores responses for a previous account and retries transient failures on re-entry", async () => {
        const pending = deferred();
        mockGetCsrData.mockReturnValueOnce(pending.promise);
        mockCachedUser = user();
        localStorage.setItem(ssKeyStorageKey, "one");
        await render({ user: null, resourceInfo: { staticPage: true } });
        getSsDate().key = "two";
        getSsDate().user = user(2, "two");
        await act(async () => pending.resolve(userResponse()));
        expect(getSsDate().user).toEqual(user(2, "two"));
        mockGetCsrData.mockRejectedValueOnce(new Error("unavailable"));
        await remount();
        expect(mockRedirect).not.toHaveBeenCalled();
        expect(container.textContent).toBe("user-2");
        mockGetCsrData.mockResolvedValue(userResponse(user(2, "two")));
        await remount();
        expect(mockCachedUser).toEqual(user(2, "two"));
        expect(getSsDate().userFromCache).toBe(false);
    });

    it("refreshes the saved account and ignores an earlier request that finishes afterward", async () => {
        const old = deferred();
        mockGetCsrData.mockReturnValueOnce(old.promise);
        mockCachedUser = user();
        localStorage.setItem(ssKeyStorageKey, "one");
        await render({ user: null, resourceInfo: { staticPage: true } });
        const saved = { language: "zh_CN" as const, appearance: { ...personal.appearance, compactMode: false } };
        cacheUserAppearance(saved);
        applyUserPreferences(saved);
        mockGetCsrData.mockResolvedValueOnce(userResponse({ ...user(), ...saved, userName: "updated" }));
        await act(async () => {
            await refreshUserInfo(mockAxios as any);
        });
        await act(async () => old.resolve(userResponse()));
        expect(container.textContent).toBe("updated");
        expect(mockCachedUser).toMatchObject({ userName: "updated", ...saved });
        expect(getRes()).toMatchObject({ lang: "zh_CN", admin_compactMode: false });
    });

    it("retains a confirmed save when refreshing fails and retries on re-entry", async () => {
        await render({ user: user(), key: "one", resourceInfo: { staticPage: false } });
        const saved = { language: "zh_CN" as const, appearance: { ...personal.appearance, compactMode: false } };
        cacheUserAppearance(saved);
        applyUserPreferences(saved);
        mockGetCsrData.mockRejectedValueOnce(new Error("unavailable"));
        await act(async () => {
            await refreshUserInfo(mockAxios as any).catch(() => undefined);
        });
        expect(mockCachedUser).toMatchObject(saved);
        expect(getSsDate().userFromCache).toBe(true);
        mockGetCsrData.mockResolvedValueOnce(userResponse({ ...user(), ...saved }));
        await remount();
        expect(getSsDate().userFromCache).toBe(false);
        expect(mockCachedUser).toMatchObject(saved);
    });

    it("rejects a cached account whose session does not match", async () => {
        mockCachedUser = user(2, "two");
        localStorage.setItem(ssKeyStorageKey, "one");
        await render({ user: null, resourceInfo: { lang: "zh_CN", staticPage: true } }, true);
        expect(getSsDate().user).toBeNull();
        expect(getRes().lang).toBe("zh_CN");
        expect(mockGetCsrData).not.toHaveBeenCalled();
    });
});
