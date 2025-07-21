import { AxiosInstance } from "axios";
import { getSsDate, getWindowPageBuildId, setWindowPageBuildId } from "./base/SsData";
import { cacheIgnoreReloadTime } from "./utils/constants";

export const getCsrData = async (uri: string, t: number, axiosInstance: AxiosInstance) => {
    let requestUri = "/api/admin" + uri.replace(".html", "");
    if (t > 0) {
        requestUri = requestUri + `${uri.includes("?") ? "&" : "?"}${cacheIgnoreReloadTime}=` + t;
    }
    const { data } = await axiosInstance.get(requestUri);
    if (data.pageBuildId !== undefined) {
        getSsDate().pageBuildId = data.pageBuildId as string as never;
        getSsDate().systemNotification = data.systemNotification as string as never;
        if (getWindowPageBuildId() === "" || getWindowPageBuildId() === null || getWindowPageBuildId() === undefined) {
            setWindowPageBuildId(data.pageBuildId);
        }
    }
    return data;
};

export const getVersion = async (buildId: string, axiosInstance: AxiosInstance) => {
    const { data } = await axiosInstance.get("/api/public/version?buildId=" + buildId);
    return data;
};

export const getTimeInfoBySearchStr = (search: string): number => {
    const t = new URLSearchParams(search).get(cacheIgnoreReloadTime);
    return t ? parseInt(t as string) : 0;
};
