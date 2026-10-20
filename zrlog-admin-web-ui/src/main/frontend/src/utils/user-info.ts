import type {AxiosInstance} from "axios";
import type {ApiResponse, BasicUserInfo} from "../type";
import {getCsrData} from "../api";
import {getSsDate, ssKeyStorageKey} from "../base/SsData";
import {cacheUserInfo} from "./cache";
import {isStaticPage} from "./constants";
import {hasUserAppearance} from "./user-appearance";
import {applyUserPreferences, getUserPreferenceRevision} from "./user-preferences";

const listeners = new Set<(user: BasicUserInfo) => void>();
export const subscribeUserInfo = (listener: (user: BasicUserInfo) => void) => {
    listeners.add(listener);
    return () => {
        listeners.delete(listener);
    };
};

let requestRevision = 0;

// Shared by initial loading and confirmed settings changes. A failed refresh remains retryable.
export const refreshUserInfo = async (axios: AxiosInstance, isActive = () => true) => {
    const session = getSsDate().key;
    const userId = getSsDate().user?.userId;
    const appearanceRevision = getUserPreferenceRevision();
    const request = ++requestRevision;
    getSsDate().userFromCache = true;
    const response: ApiResponse<BasicUserInfo> = await getCsrData("/user/info", Date.now(), axios);
    if (
        !isActive() ||
        request !== requestRevision ||
        session !== getSsDate().key ||
        userId !== getSsDate().user?.userId
    ) {
        return null;
    }
    if (response.error !== 0) return response;
    const unchanged = appearanceRevision === getUserPreferenceRevision();
    const saved = getSsDate().user;
    const user =
        !unchanged && saved && hasUserAppearance(saved)
            ? {...response.data, language: saved.language, appearance: saved.appearance}
            : response.data;
    getSsDate().key = user.key;
    getSsDate().user = user;
    getSsDate().userFromCache = false;
    if (isStaticPage()) localStorage.setItem(ssKeyStorageKey, user.key);
    cacheUserInfo(user);
    if (unchanged && hasUserAppearance(user)) applyUserPreferences(user);
    listeners.forEach((listener) => listener(user));
    return response;
};
