import { beforeEach, describe, expect, it, jest } from "@jest/globals";
import { browserSupportsWebAuthn, startAuthentication, startRegistration } from "@simplewebauthn/browser";
import type {
    PublicKeyCredentialCreationOptionsJSON,
    PublicKeyCredentialRequestOptionsJSON,
} from "@simplewebauthn/browser";
import { authenticateWithPasskey, canUsePasskeys, isPasskeyCancellation, registerPasskey } from "./passkey";

jest.mock("@simplewebauthn/browser", () => {
    const mockJest = require("@jest/globals").jest;
    return {
        browserSupportsWebAuthn: mockJest.fn(),
        startAuthentication: mockJest.fn(),
        startRegistration: mockJest.fn(),
    };
});

describe("passkey helpers", () => {
    const supportsWebAuthn = jest.mocked(browserSupportsWebAuthn);
    const startAuthenticationMock = jest.mocked(startAuthentication);
    const startRegistrationMock = jest.mocked(startRegistration);

    beforeEach(() => {
        jest.clearAllMocks();
        supportsWebAuthn.mockReturnValue(true);
        Object.defineProperty(window, "isSecureContext", { configurable: true, value: true });
    });

    it("only enables passkeys for a supported secure same-origin backend", () => {
        expect(canUsePasskeys(window.location.origin + "/blog/")).toBe(true);
        expect(canUsePasskeys("/blog/")).toBe(true);
        expect(canUsePasskeys("blog/")).toBe(false);
        expect(canUsePasskeys("https://admin.example.com/")).toBe(false);
        expect(canUsePasskeys("http://127.0.0.1/")).toBe(false);
        expect(canUsePasskeys("https://[::1]/")).toBe(false);
        expect(
            canUsePasskeys(window.location.origin, {
                backendServerUrlConfigured: false,
            })
        ).toBe(false);

        Object.defineProperty(window, "isSecureContext", { configurable: true, value: false });
        expect(canUsePasskeys(window.location.origin)).toBe(false);

        Object.defineProperty(window, "isSecureContext", { configurable: true, value: true });
        supportsWebAuthn.mockReturnValue(false);
        expect(canUsePasskeys(window.location.origin)).toBe(false);
    });

    it("passes authentication options to SimpleWebAuthn", async () => {
        const options = {
            challenge: "authentication-challenge",
            rpId: window.location.hostname,
        } as PublicKeyCredentialRequestOptionsJSON;
        const response = { id: "credential-id" } as Awaited<ReturnType<typeof startAuthentication>>;
        startAuthenticationMock.mockResolvedValue(response);

        await expect(authenticateWithPasskey(options)).resolves.toBe(response);
        expect(startAuthenticationMock).toHaveBeenCalledWith({ optionsJSON: options });
    });

    it("passes registration options to SimpleWebAuthn", async () => {
        const options = {
            rp: { name: "ZrLog", id: window.location.hostname },
            user: { id: "user-id", name: "admin", displayName: "admin" },
            challenge: "registration-challenge",
            pubKeyCredParams: [{ type: "public-key", alg: -7 }],
        } as PublicKeyCredentialCreationOptionsJSON;
        const response = { id: "credential-id" } as Awaited<ReturnType<typeof startRegistration>>;
        startRegistrationMock.mockResolvedValue(response);

        await expect(registerPasskey(options)).resolves.toBe(response);
        expect(startRegistrationMock).toHaveBeenCalledWith({ optionsJSON: options });
    });

    it("recognizes browser cancellations without hiding other failures", () => {
        expect(isPasskeyCancellation({ name: "NotAllowedError" })).toBe(false);
        expect(
            isPasskeyCancellation({
                name: "NotAllowedError",
                code: "ERROR_PASSTHROUGH_SEE_CAUSE_PROPERTY",
                cause: { name: "NotAllowedError" },
            })
        ).toBe(false);
        expect(isPasskeyCancellation({ cause: { name: "AbortError" } })).toBe(true);
        expect(isPasskeyCancellation({ code: "ERROR_CEREMONY_ABORTED" })).toBe(true);
        expect(isPasskeyCancellation({ name: "SecurityError" })).toBe(false);
    });
});
