import { browserSupportsWebAuthn, startAuthentication, startRegistration } from "@simplewebauthn/browser";
import type {
    AuthenticationResponseJSON,
    PublicKeyCredentialCreationOptionsJSON,
    PublicKeyCredentialRequestOptionsJSON,
    RegistrationResponseJSON,
} from "@simplewebauthn/browser";

type PasskeyAvailabilityOptions = {
    backendServerUrlConfigured?: boolean;
};

const isAbsoluteOrRootRelativeUrl = (url: string): boolean => {
    if (url.startsWith("/")) {
        return true;
    }
    try {
        new URL(url);
        return true;
    } catch {
        return false;
    }
};

const isIpLiteral = (hostname: string): boolean => {
    const normalizedHostname = hostname.startsWith("[") && hostname.endsWith("]") ? hostname.slice(1, -1) : hostname;
    return normalizedHostname.includes(":") || /^(?:\d{1,3}\.){3}\d{1,3}$/.test(normalizedHostname);
};

export const canUsePasskeys = (
    backendServerUrl: string,
    { backendServerUrlConfigured = true }: PasskeyAvailabilityOptions = {}
): boolean => {
    const normalizedBackendServerUrl = backendServerUrl.trim();
    if (!backendServerUrlConfigured || !isAbsoluteOrRootRelativeUrl(normalizedBackendServerUrl)) {
        return false;
    }
    if (!window.isSecureContext || !browserSupportsWebAuthn()) {
        return false;
    }
    try {
        const backendUrl = new URL(normalizedBackendServerUrl, window.location.href);
        return (
            !isIpLiteral(backendUrl.hostname) &&
            (backendUrl.protocol === "https:" || backendUrl.protocol === "http:") &&
            backendUrl.origin === window.location.origin
        );
    } catch {
        return false;
    }
};

export const authenticateWithPasskey = (
    options: PublicKeyCredentialRequestOptionsJSON
): Promise<AuthenticationResponseJSON> => {
    return startAuthentication({ optionsJSON: options });
};

export const registerPasskey = (options: PublicKeyCredentialCreationOptionsJSON): Promise<RegistrationResponseJSON> => {
    return startRegistration({ optionsJSON: options });
};

export const isPasskeyCancellation = (error: unknown): boolean => {
    if (!error || typeof error !== "object") {
        return false;
    }
    const webAuthnError = error as {
        code?: string;
        name?: string;
        cause?: { name?: string };
    };
    return (
        webAuthnError.code === "ERROR_CEREMONY_ABORTED" ||
        webAuthnError.name === "AbortError" ||
        webAuthnError.cause?.name === "AbortError"
    );
};
