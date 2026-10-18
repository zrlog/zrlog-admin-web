import { cacheUserInfo, getCacheByKey } from "../utils/cache";
import { BasicUserInfo } from "../type";
import { FunctionComponent, PropsWithChildren } from "react";
import type { AdminRuntimeResourceInfo } from "../utils/constants";
import { MessageCenterStatus, syncMessageCenterStatus } from "../utils/message-center-status";
import { hasUserAppearance, userAppearanceResources } from "../utils/user-appearance";

type SsDate = {
    data?: any;
    resourceInfo?: AdminRuntimeResourceInfo | null | undefined;
    user: BasicUserInfo | null;
    key: string;
    systemNotification: string;
    messageCenter?: MessageCenterStatus;
    pageBuildId: string;
    // Cached identity is usable offline and revalidated once when this document goes online.
    userFromCache?: boolean;
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
            const cached = getCacheByKey<BasicUserInfo | undefined>("/user");
            if (cached && cached.key === ssData.key) {
                ssData.user = cached;
                ssData.userFromCache = true;
            }
        }
    }

    if (ssData.user?.key === ssData.key && hasUserAppearance(ssData.user)) {
        // Apply before ConfigProviderApp's first render, including offline static pages.
        if (ssData.resourceInfo && Object.keys(ssData.resourceInfo).length > 0) {
            ssData.resourceInfo = { ...ssData.resourceInfo, ...userAppearanceResources(ssData.user) };
        }
        document.documentElement.lang = ssData.user.language === "en_US" ? "en" : "zh";
        if (!ssData.userFromCache) cacheUserInfo(ssData.user);
    }

    return <>{children}</>;
};

export default SsData;
