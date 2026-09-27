import { beforeEach, describe, expect, it, jest } from "@jest/globals";
import type { MessageInstance } from "antd/es/message/interface";
import type { PasskeyRegistrationVerifyRequest } from "../type";
import {
    PASSKEY_API_BASE,
    PASSKEY_REGISTRATION_MESSAGE_KEY,
    PASSKEY_REGISTRATION_REFRESH_MESSAGE_KEY,
    PASSKEY_REMOVE_MESSAGE_KEY,
    PASSKEY_REMOVE_REFRESH_MESSAGE_KEY,
    postPasskeyRegistrationVerification,
    postPasskeyRemoval,
} from "./passkey-management-api";

const mockPostRefreshCacheSse = jest.fn<Promise<unknown>, unknown[]>();

jest.mock("../utils/sse-utils", () => ({
    postRefreshCacheSse: (...args: unknown[]) => mockPostRefreshCacheSse(...args),
}));

const messageApi = {} as MessageInstance;

describe("Passkey management API", () => {
    beforeEach(() => {
        mockPostRefreshCacheSse.mockReset();
    });

    it("keeps business results separate from static refresh messages", () => {
        expect(PASSKEY_REGISTRATION_MESSAGE_KEY).not.toBe(PASSKEY_REGISTRATION_REFRESH_MESSAGE_KEY);
        expect(PASSKEY_REMOVE_MESSAGE_KEY).not.toBe(PASSKEY_REMOVE_REFRESH_MESSAGE_KEY);
    });

    it("uses the refresh-cache SSE transport for registration verification", async () => {
        const request = {
            requestId: "request-id",
            name: "Work computer",
            response: {},
        } as PasskeyRegistrationVerifyRequest;
        const apiResponse = { error: 0, data: { id: 1, name: request.name, createdAt: 1 } };
        mockPostRefreshCacheSse.mockResolvedValue(apiResponse);

        await expect(postPasskeyRegistrationVerification(request, messageApi)).resolves.toBe(apiResponse);
        expect(mockPostRefreshCacheSse).toHaveBeenCalledWith(`${PASSKEY_API_BASE}/registration/verify`, {
            body: request,
            messageApi,
            messageKey: PASSKEY_REGISTRATION_REFRESH_MESSAGE_KEY,
            requiredCompletionEvent: "refresh-complete",
        });
    });

    it("uses the refresh-cache SSE transport when removing a Passkey", async () => {
        const request = { id: 1, password: "password", mfaCode: "123456" };
        const apiResponse = { error: 0, data: true };
        mockPostRefreshCacheSse.mockResolvedValue(apiResponse);

        await expect(postPasskeyRemoval(request, messageApi)).resolves.toBe(apiResponse);
        expect(mockPostRefreshCacheSse).toHaveBeenCalledWith(`${PASSKEY_API_BASE}/remove`, {
            body: request,
            messageApi,
            messageKey: PASSKEY_REMOVE_REFRESH_MESSAGE_KEY,
            requiredCompletionEvent: "refresh-complete",
        });
    });
});
