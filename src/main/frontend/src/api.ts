import { AxiosInstance } from "axios";
import { getSsDate, getWindowPageBuildId, setWindowPageBuildId } from "./base/SsData";
import { cacheIgnoreReloadTime } from "./utils/constants";
import { syncMessageCenterStatus } from "./utils/message-center-status";
import type { ApiResponse, PublicVersionResponse } from "./type";

export const API_VERSION_PATH = "/api/public/version";
export const API_DO_UPGRADE_PATH = "/api/admin/upgrade/doUpgrade";
export const API_ADMIN_STATIC_SITE_SYNC_PATH = "/api/admin/static-site/startSync";

export const getCsrData = async (uri: string, t: number, axiosInstance: AxiosInstance) => {
    let requestUri = "/api/admin" + uri.replace(".html", "");
    if (t > 0) {
        requestUri = requestUri + `${uri.includes("?") ? "&" : "?"}${cacheIgnoreReloadTime}=` + t;
    }
    const { data } = await axiosInstance.get(requestUri);
    if (data.pageBuildId !== undefined) {
        getSsDate().pageBuildId = data.pageBuildId as string as never;
        getSsDate().systemNotification = data.systemNotification as string as never;
        getSsDate().messageCenter = data.messageCenter;
        syncMessageCenterStatus(data.messageCenter);
        if (getWindowPageBuildId() === "" || getWindowPageBuildId() === null || getWindowPageBuildId() === undefined) {
            setWindowPageBuildId(data.pageBuildId);
        }
    }
    return data;
};

export const getVersion = async (buildId: string, axiosInstance: AxiosInstance): Promise<ApiResponse<PublicVersionResponse>> => {
    const { data } = await axiosInstance.get<ApiResponse<PublicVersionResponse>>(
        API_VERSION_PATH + "?buildId=" + encodeURIComponent(buildId)
    );
    return data;
};

export const getTimeInfoBySearchStr = (search: string): number => {
    const t = new URLSearchParams(search).get(cacheIgnoreReloadTime);
    return t ? parseInt(t as string) : 0;
};
