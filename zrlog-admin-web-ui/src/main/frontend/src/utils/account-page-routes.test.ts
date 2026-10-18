import { beforeEach, describe, expect, it, jest } from "@jest/globals";
import type { AxiosInstance } from "axios";
import { getCsrData } from "../api";
import { createAdminDashboardRoutes } from "../components/admin-dashboard-routes";
import { getRes } from "./constants";
import {
    USER_ROUTES,
    WEBSITE_ROUTES,
    USER_PREFERENCE_PAGES,
    USER_APPLICATION_PAGES,
    getAccountPage,
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

    it("registers canonical system pages and keeps the old version URL only as a redirect", () => {
        const routes = createAdminDashboardRoutes();
        for (const path of ["system", "system/runtime", "system/resources", "system/proxy", "system/version", "dev"]) {
            const route = routes.find((route) => route.paths.includes(path));
            expect(route?.paths).toContain(path + ".html");
            expect(route?.search).toHaveLength(1);
        }
        const legacy = routes.find((route) => route.paths.includes("website/version"));
        expect(legacy?.paths).toContain("website/version.html");
        expect(legacy?.search).toBeUndefined();
        expect(legacy?.lazy).not.toBe(routes.find((route) => route.paths.includes("system/version"))?.lazy);
        for (const page of ["runtime", "resources"]) {
            expect(routes.find((route) => route.paths.includes(`system/${page}`))?.props).toEqual({ activePage: page });
        }
    });
});
