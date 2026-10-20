import {beforeEach, describe, expect, it, jest} from "@jest/globals";
import type {AxiosInstance} from "axios";
import {getCsrData} from "../api";
import {actionForPath, hasAction} from "../utils/account-access";
import {createAdminDashboardRoutes, getAdminDashboardRouteSearchItems} from "./admin-dashboard-routes";
import type {BasicUserInfo} from "../type";
import {setRes} from "../utils/constants";
import {createSpotlightSearchSources} from "../layout/spotlight-search-sources";
import type {SpotlightSearchContext} from "../layout/spotlight-search-types";

jest.mock("../base/AppBase", () => ({buildUriPaths: (uri: string) => [uri, uri + ".html"]}));
jest.mock("./my-loading-component", () => () => null);

describe("developer UI review route", () => {
    beforeEach(() => {
        window.__SS_DATA__ = {
            key: "review",
            pageBuildId: "test",
            systemNotification: "",
            resourceInfo: {lang: "zh_CN"},
            user: {actions: ["system.manage", "site.configure"]} as BasicUserInfo,
        };
    });

    it("keeps the data page and direct review route distinct and out of search", () => {
        const routes = createAdminDashboardRoutes();
        const review = routes.find((route) => route.paths.includes("dev/ui"));
        const dev = routes.find((route) => route.paths.includes("dev"));
        expect(review).toBeDefined();
        expect(dev).toBeDefined();
        expect(review!.paths).toContain("dev/ui.html");
        expect(review!.lazy).not.toBe(dev!.lazy);
        expect(review!.search).toBeUndefined();
        expect(getAdminDashboardRouteSearchItems().some((item) => item.path.includes("dev/ui"))).toBe(false);
        expect(actionForPath("/dev/ui")).toBe("system.manage");
        expect(actionForPath("/dev/ui.html")).toBe("system.manage");
        window.__SS_DATA__!.user!.actions = ["site.configure"];
        expect(hasAction(actionForPath("/dev/ui"))).toBe(false);
    });

    it.each(["/dev/ui", "/dev/ui.html"])(
        "hydrates %s from its read-only endpoint without fetching caches or locks",
        async (uri) => {
            const get = jest
                .fn<Promise<any>, any[]>()
                .mockResolvedValue({data: {error: 0, data: true, documentTitle: "UI"}});
            const data = await getCsrData(uri, 0, {get} as unknown as AxiosInstance);
            expect(get).toHaveBeenCalledTimes(1);
            expect(get).toHaveBeenCalledWith("/api/admin/dev/ui");
            expect(data.data).toBe(true);
            expect(data.documentTitle).toBe("UI");
        }
    );

    it("shows developer tools in search only when the server enables development mode and the user has permission", () => {
        const devVisible = () => getAdminDashboardRouteSearchItems().some((item) => item.path === "/dev");
        expect(devVisible()).toBe(false);
        setRes({devMode: true});
        expect(devVisible()).toBe(true);
        window.__SS_DATA__!.user!.actions = ["site.configure"];
        expect(devVisible()).toBe(false);
        window.__SS_DATA__!.user!.actions = ["system.manage"];
        setRes({devMode: false});
        expect(devVisible()).toBe(false);
    });

    it("updates an existing spotlight source after the server development mode changes", async () => {
        const source = createSpotlightSearchSources().find((item) => item.id === "route")!;
        const context: SpotlightSearchContext = {
            keyword: "dev",
            normalizedKeyword: "dev",
            axiosInstance: {} as AxiosInstance,
            renderImageIcon: (_src, _title, fallback) => fallback,
        };
        const devVisible = async () => (await source.search(context)).some((item) => item.path === "/dev");

        expect(await devVisible()).toBe(false);
        setRes({devMode: true});
        expect(await devVisible()).toBe(true);
        window.__SS_DATA__!.user!.actions = ["site.configure"];
        expect(await devVisible()).toBe(false);
        window.__SS_DATA__!.user!.actions = ["system.manage"];
        setRes({devMode: false});
        expect(await devVisible()).toBe(false);
    });
});
