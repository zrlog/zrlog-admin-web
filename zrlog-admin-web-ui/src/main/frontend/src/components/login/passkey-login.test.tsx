import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import type { ApiResponse, PasskeyAuthenticationOptionsResponse } from "../../type";
import Index from "./index";

const mockAxiosPost = jest.fn();
const mockAuthenticateWithPasskey = jest.fn();
let mockPasskeyLoginEnabled = true;

jest.mock("antd", () => {
    const React = require("react") as typeof import("react");
    const mockJest = require("@jest/globals").jest;
    const Form = ({ children }: { children?: React.ReactNode }) => React.createElement("form", null, children);
    Form.Item = ({ children, label }: { children?: React.ReactNode; label?: React.ReactNode }) =>
        React.createElement("label", null, label, children);

    const Input = (props: Record<string, unknown>) => React.createElement("input", props);
    Input.Password = (props: Record<string, unknown>) => React.createElement("input", { ...props, type: "password" });
    Input.OTP = (props: Record<string, unknown>) => React.createElement("input", props);

    return {
        Button: ({ children, disabled, onClick }: React.ButtonHTMLAttributes<HTMLButtonElement>) =>
            React.createElement("button", { disabled, onClick }, children),
        Divider: ({ children }: { children?: React.ReactNode }) => React.createElement("div", null, children),
        Form,
        Input,
        Layout: ({ children }: { children?: React.ReactNode }) => React.createElement("main", null, children),
        message: {
            useMessage: () => [{ error: mockJest.fn() }, null],
        },
        Space: ({ children }: { children?: React.ReactNode }) => React.createElement("div", null, children),
    };
});

jest.mock("antd/es/typography/Title", () => ({
    __esModule: true,
    default: ({ children }: { children?: import("react").ReactNode }) =>
        require("react").createElement("h1", null, children),
}));

jest.mock("styled-components", () => ({
    __esModule: true,
    default: (Component: import("react").ComponentType<unknown>) => () => Component,
}));

jest.mock("react-router-dom", () => ({
    useNavigate: () => require("@jest/globals").jest.fn(),
}));

jest.mock("../../base/PWAHandler", () => ({
    __esModule: true,
    default: ({ children }: { children?: import("react").ReactNode }) => children,
}));

jest.mock("../../utils/cache", () => ({
    addToCache: require("@jest/globals").jest.fn(),
    removeAllCaches: require("@jest/globals").jest.fn(),
}));

jest.mock("../../utils/constants", () => ({
    getBackendServerUrl: () => "/",
    getDefaultLoginInfo: () => ({ userName: "", password: "", backendServerUrl: "/" }),
    getRealRouteUrl: (url: string) => url,
    getRes: () => ({
        error: { unknown: "Unknown error" },
        login: {
            backendServerUrl: "Gateway URL",
            copyrightCurrentYear: "2026",
            mfaBack: "Back",
            mfaCode: "MFA code",
            mfaStepHint: "Enter MFA code",
            mfaSubmit: "Verify",
            passkeyDivider: "or",
            passkeyFailed: "Passkey failed",
            passkeySubmit: "Sign in with Passkey",
            password: "Password",
            submit: "Sign in",
            title: "Sign in",
            userName: "Username",
            userNameAndPassword: "Use your account",
        },
        passkeyLoginEnabled: mockPasskeyLoginEnabled,
        websiteTitle: "ZrLog",
    }),
    hasConfiguredBackendServerUrl: () => true,
    isStaticPage: () => false,
    setBackendServerUrl: require("@jest/globals").jest.fn(),
}));

jest.mock("../../utils/helpers", () => ({
    getContextPath: () => "/",
}));

jest.mock("../../base/AppBase", () => ({
    useAxiosBaseInstance: () => ({
        defaults: {},
        post: (url: string, data?: unknown) => mockAxiosPost(url, data),
    }),
}));

jest.mock("../../api", () => ({
    getCsrData: require("@jest/globals").jest.fn(),
}));

jest.mock("../../base/SsData", () => ({
    getSsDate: () => ({}),
    ssKeyStorageKey: "ss-key",
}));

jest.mock("../../base/ConfigProviderApp", () => ({
    getAppState: () => ({ colorPrimary: "#1677ff", dark: false, theme: "default" }),
}));

jest.mock("antd-style", () => ({
    useTheme: () => ({
        borderRadiusLG: 8,
        boxShadowSecondary: "none",
        colorBgContainer: "#fff",
        colorBgLayout: "#f5f5f5",
        colorText: "#111",
        colorTextSecondary: "#666",
        colorWhite: "#fff",
    }),
}));

jest.mock("../../common/admin-error-code", () => ({
    ADMIN_ERROR_CODE: { mfaCodeInvalid: 2, mfaCodeRequired: 1 },
}));

jest.mock("../../assets/login-publishing-workspace.webp", () => "login.webp");

jest.mock("@ant-design/icons", () => ({
    KeyOutlined: () => null,
}));

jest.mock("../../utils/passkey", () => ({
    authenticateWithPasskey: (options: unknown) => mockAuthenticateWithPasskey(options),
    canUsePasskeys: () => true,
    isPasskeyCancellation: () => true,
}));

const reactActEnvironment = globalThis as typeof globalThis & {
    IS_REACT_ACT_ENVIRONMENT?: boolean;
};

const deferred = <T,>() => {
    let resolve!: (value: T) => void;
    const promise = new Promise<T>((nextResolve) => {
        resolve = nextResolve;
    });
    return { promise, resolve };
};

const apiResponse = <T,>(data: T): ApiResponse<T> => ({
    data,
    error: 0,
    message: "",
    pageBuildId: "test",
});

describe("Passkey login", () => {
    let container: HTMLDivElement;
    let root: Root;

    beforeEach(() => {
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = true;
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
        jest.clearAllMocks();
        mockPasskeyLoginEnabled = true;
    });

    afterEach(() => {
        act(() => {
            root.unmount();
        });
        container.remove();
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = false;
    });

    it("creates only one challenge when the Passkey button is clicked twice before rerender", async () => {
        const optionsResponse = deferred<{ data: ApiResponse<PasskeyAuthenticationOptionsResponse> }>();
        mockAxiosPost.mockReturnValueOnce(optionsResponse.promise);
        mockAuthenticateWithPasskey.mockRejectedValueOnce({ code: "ERROR_CEREMONY_ABORTED" });

        await act(async () => {
            root.render(<Index offline={false} />);
            await Promise.resolve();
        });

        const passkeyButton = Array.from(container.querySelectorAll("button")).find(
            (button) => button.textContent === "Sign in with Passkey"
        );
        expect(passkeyButton).toBeDefined();

        act(() => {
            passkeyButton?.click();
            passkeyButton?.click();
        });

        expect(mockAxiosPost).toHaveBeenCalledTimes(1);
        expect(mockAxiosPost).toHaveBeenCalledWith("/api/admin/passkey/authentication/options", {});

        await act(async () => {
            optionsResponse.resolve({
                data: apiResponse({
                    requestId: "request-id",
                    options: {} as PasskeyAuthenticationOptionsResponse["options"],
                }),
            });
            await optionsResponse.promise;
        });
    });

    it("hides Passkey login until the site has a registered credential", async () => {
        mockPasskeyLoginEnabled = false;

        await act(async () => {
            root.render(<Index offline={false} />);
            await Promise.resolve();
        });

        const passkeyButton = Array.from(container.querySelectorAll("button")).find(
            (button) => button.textContent === "Sign in with Passkey"
        );
        expect(passkeyButton).toBeUndefined();
        expect(mockAxiosPost).not.toHaveBeenCalled();
    });
});
