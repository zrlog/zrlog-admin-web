import { beforeEach, describe, expect, it, jest } from "@jest/globals";
import type { AxiosInstance } from "axios";
import { getCsrData } from "../api";
import { createAdminDashboardRoutes } from "../components/admin-dashboard-routes";
import { getRes } from "./constants";
import {
    getAccountPage,
    USER_APPLICATION_PAGES,
    USER_PREFERENCE_PAGES,
    USER_ROUTES,
    WEBSITE_ROUTES,
} from "./account-page-routes";

jest.mock("../base/AppBase", () => ({ buildUriPaths: (uri: string) => [uri, uri + ".html"] }));
jest.mock("../components/my-loading-component", () => () => null);

describe("account page routing", () => {
    beforeEach(() => {
        window.__SS_DATA__ = {
            key: "routes",
            pageBuildId: "test",
            systemNotification: "",
            user: null,
            resourceInfo: { lang: "zh_CN" },
        };
    });

    it("loads nested pages from existing APIs and preserves authorization query parameters", async () => {
        const get = jest.fn<Promise<any>, any[]>().mockImplementation(async () => ({ data: { error: 0, data: {} } }));
        const api = { get } as unknown as AxiosInstance;
        await getCsrData("/user/applications/authorize.html?request_id=a%2Bb&v=1", 0, api);
        expect(get).toHaveBeenLastCalledWith("/api/admin/oauth/authorize?request_id=a%2Bb&v=1");
        await getCsrData("/website/members", 0, api);
        expect(get).toHaveBeenLastCalledWith("/api/admin/members");
        const members = await getCsrData("/website/members.html?v=1", 0, api);
        expect(get).toHaveBeenLastCalledWith("/api/admin/members?v=1");
        expect(members.documentTitle).toContain(getRes().members.title);
        await getCsrData("/user/security.html", 0, api);
        expect(get).toHaveBeenLastCalledWith("/api/admin/account-security");
        for (const page of [...USER_PREFERENCE_PAGES, ...USER_APPLICATION_PAGES]) {
            const route = USER_ROUTES[page];
            const result = await getCsrData(route + ".html?v=1", 0, api);
            expect(get).toHaveBeenLastCalledWith(getAccountPage(route)!.api + "?v=1");
            expect(result.documentTitle).toContain(getAccountPage(route)!.title(getRes()));
        }
    });

    it("registers account pages and static variants without old webpage aliases", () => {
        const paths = createAdminDashboardRoutes().flatMap((route) => route.paths);
        for (const path of [...Object.values(USER_ROUTES), ...Object.values(WEBSITE_ROUTES)]) {
            expect(paths).toContain(path.slice(1));
            expect(paths).toContain(path.slice(1) + ".html");
        }
        for (const old of [
            "members",
            "user/members",
            "user/permissions",
            "user/preferences",
            "user/applications",
            "access",
            "oauth",
            "oauth/authorize",
            "account-security",
            "user-update-password",
        ]) {
            expect(paths).not.toContain(old);
            expect(paths).not.toContain(old + ".html");
        }
    });

    it("keeps system settings together without the development-only subdivisions", () => {
        const routes = createAdminDashboardRoutes();
        for (const path of ["system", "system/proxy", "system/version", "system/upgrade", "dev"]) {
            const route = routes.find((route) => route.paths.includes(path));
            expect(route?.paths).toContain(path + ".html");
            expect(route?.search).toHaveLength(1);
        }
        for (const path of ["system/runtime", "system/resources"]) {
            expect(routes.some((route) => route.paths.includes(path) || route.paths.includes(path + ".html"))).toBe(
                false
            );
        }
        for (const page of ["version", "upgrade"]) {
            const redirect = routes.find((route) => route.paths.includes(`website/${page}`));
            expect(redirect?.paths).toContain(`website/${page}.html`);
            expect(redirect?.search).toBeUndefined();
            expect(redirect?.props).toEqual({ page });
            expect(redirect?.lazy).not.toBe(routes.find((route) => route.paths.includes(`system/${page}`))?.lazy);
        }
    });
    it("loads assistant and AI settings as independent dynamic and static pages", async () => {
        const get = jest.fn<Promise<any>, any[]>().mockImplementation(async () => ({ data: { error: 0, data: {} } }));
        for (const key of ["assistant", "ai"]) {
            await getCsrData(`/website/${key}.html`, 0, { get } as unknown as AxiosInstance);
            expect(get).toHaveBeenLastCalledWith(`/api/admin/website/${key}`);
            const route = createAdminDashboardRoutes().find((route) => route.paths.includes(`website/${key}`));
            expect(route?.paths).toContain(`website/${key}.html`);
            expect(route?.props).toEqual({ activeKey: key });
        }
    });
});
