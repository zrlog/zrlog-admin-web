import { getCacheByKey } from "../utils/cache";
import { BasicUserInfo } from "../type";
import { FunctionComponent, PropsWithChildren } from "react";
import type { AdminRuntimeResourceInfo } from "../utils/constants";
import { MessageCenterStatus, syncMessageCenterStatus } from "../utils/message-center-status";

type SsDate = {
    data?: any;
    resourceInfo?: AdminRuntimeResourceInfo | null | undefined;
    user: BasicUserInfo | null;
    key: string;
    systemNotification: string;
    messageCenter?: MessageCenterStatus;
    pageBuildId: string;
};

declare global {
    interface Window {
        __SS_DATA__?: SsDate;
        __SS_PAGE_BUILD_ID__?: string;
    }
}

export const ssKeyStorageKey = "ss_key";
const SS_DATA_KEY = "__SS_DATA__";
const SS_PAGE_BUILD_ID_KEY = "__SS_PAGE_BUILD_ID__";

const createEmptySsData = (): SsDate => ({
    key: "",
    data: undefined,
    resourceInfo: {},
    user: null,
    pageBuildId: "",
    systemNotification: "",
    messageCenter: undefined,
});

export const getSsDate = (): SsDate => {
    return window[SS_DATA_KEY] ?? createEmptySsData();
};

export const getWindowPageBuildId = (): string => {
    return window[SS_PAGE_BUILD_ID_KEY] ?? "";
};

export const setWindowPageBuildId = (id: string) => {
    window[SS_PAGE_BUILD_ID_KEY] = id;
};

const SsData: FunctionComponent<PropsWithChildren> = ({ children }) => {
    const initSsData = (): SsDate => {
        const ssDataStr = document.getElementById(SS_DATA_KEY)?.innerText;
        let tSData: SsDate;
        if (ssDataStr && ssDataStr.length > 0) {
            tSData = JSON.parse(ssDataStr as string) as SsDate;
        } else {
            tSData = createEmptySsData();
        }

        if (tSData.key === "" || tSData.key === null || tSData.key === undefined) {
            const ssKey = localStorage.getItem(ssKeyStorageKey);
            if (ssKey) {
                tSData.key = ssKey;
            }
        }
        return tSData;
    };
    const ssData = initSsData();
    window[SS_DATA_KEY] = ssData;
    window[SS_PAGE_BUILD_ID_KEY] = ssData.pageBuildId;

    syncMessageCenterStatus(ssData.messageCenter);

    if (ssData.user === undefined || ssData.user === null) {
        if (ssData.key !== "") {
            ssData.user = getCacheByKey("/user");
        }
    }

    return <>{children}</>;
};

export default SsData;
