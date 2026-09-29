import { beforeEach, describe, expect, it, jest } from "@jest/globals";
import type { AxiosInstance } from "axios";
import { getCsrData } from "../api";
import { actionForPath, hasAction } from "../utils/account-access";
import { createAdminDashboardRoutes, getAdminDashboardRouteSearchItems } from "./admin-dashboard-routes";
import type { BasicUserInfo } from "../type";

jest.mock("../base/AppBase", () => ({ buildUriPaths: (uri: string) => [uri, uri + ".html"] }));
jest.mock("./my-loading-component", () => () => null);

describe("developer UI review route", () => {
    beforeEach(() => {
        window.__SS_DATA__ = { key: "review", pageBuildId: "test", systemNotification: "",
            resourceInfo: { lang: "zh_CN" }, user: { actions: ["system.manage", "site.configure"] } as BasicUserInfo };
    });

    it("keeps the data page and direct review route distinct and out of search", () => {
        const routes = createAdminDashboardRoutes();
        const review = routes.find((route) => route.paths.includes("dev/ui"));
        const dev = routes.find((route) => route.paths.includes("dev"));
        expect(review).toBeDefined();
        expect(dev).toBeDefined();
        expect(review!.lazy).not.toBe(dev!.lazy);
        expect(review!.search).toBeUndefined();
        expect(getAdminDashboardRouteSearchItems().some((item) => item.path.includes("dev/ui"))).toBe(false);
        expect(actionForPath("/dev/ui")).toBe("system.manage");
        window.__SS_DATA__!.user!.actions = ["site.configure"];
        expect(hasAction(actionForPath("/dev/ui"))).toBe(false);
    });

    it("hydrates from its read-only endpoint without fetching caches or locks", async () => {
        const get = jest.fn<Promise<any>, any[]>().mockResolvedValue({ data: { error: 0, data: true, documentTitle: "UI" } });
        const data = await getCsrData("/dev/ui", 0, { get } as unknown as AxiosInstance);
        expect(get).toHaveBeenCalledTimes(1);
        expect(get).toHaveBeenCalledWith("/api/admin/dev/ui");
        expect(data.data).toBe(true);
        expect(data.documentTitle).toBe("UI");
    });
});
