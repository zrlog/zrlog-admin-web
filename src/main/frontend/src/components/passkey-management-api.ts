import type { MessageInstance } from "antd/es/message/interface";
import type { ApiResponse, PasskeyRegistrationVerifyRequest, PasskeySummary } from "../type";
import { postRefreshCacheSse } from "../utils/sse-utils";

export const PASSKEY_API_BASE = "/api/admin/account-security/passkey";
export const PASSKEY_REGISTRATION_MESSAGE_KEY = "passkeyRegistration";
export const PASSKEY_REGISTRATION_REFRESH_MESSAGE_KEY = "passkeyRegistrationRefreshCache";
export const PASSKEY_REMOVE_MESSAGE_KEY = "passkeyRemove";
export const PASSKEY_REMOVE_REFRESH_MESSAGE_KEY = "passkeyRemoveRefreshCache";

export type PasskeyRemoveRequest = {
    id: number;
    password: string;
    mfaCode?: string;
};

export const postPasskeyRegistrationVerification = (
    request: PasskeyRegistrationVerifyRequest,
    messageApi: MessageInstance
) =>
    postRefreshCacheSse<ApiResponse<PasskeySummary>>(`${PASSKEY_API_BASE}/registration/verify`, {
        body: request,
        messageApi,
        messageKey: PASSKEY_REGISTRATION_REFRESH_MESSAGE_KEY,
        requiredCompletionEvent: "refresh-complete",
    });

export const postPasskeyRemoval = (request: PasskeyRemoveRequest, messageApi: MessageInstance) =>
    postRefreshCacheSse<ApiResponse<boolean>>(`${PASSKEY_API_BASE}/remove`, {
        body: request,
        messageApi,
        messageKey: PASSKEY_REMOVE_REFRESH_MESSAGE_KEY,
        requiredCompletionEvent: "refresh-complete",
    });
