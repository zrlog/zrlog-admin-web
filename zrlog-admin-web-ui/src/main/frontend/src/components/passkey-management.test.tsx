import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import type { ApiResponse, PasskeyRegistrationVerifyRequest, PasskeySummary } from "../type";
import PasskeyManagement from "./passkey-management";

type PasskeyListAxiosResponse = {
    data: ApiResponse<PasskeySummary[]>;
};

const mockAxiosGet = jest.fn<Promise<PasskeyListAxiosResponse>, []>();
const mockAxiosPost = jest.fn<Promise<unknown>, unknown[]>();
const mockPostPasskeyRegistrationVerification = jest.fn<Promise<unknown>, unknown[]>();
const mockPostPasskeyRemoval = jest.fn<Promise<unknown>, unknown[]>();
const mockRegisterPasskey = jest.fn<Promise<unknown>, unknown[]>();
let mockCanUsePasskeys = true;
let mockPreviewMode = false;
const mockMessageError = jest.fn<PromiseLike<boolean>, [string]>();
const mockMessageSuccess = jest.fn<PromiseLike<boolean>, [string]>();

jest.mock("antd", () => {
    const React = require("react") as typeof import("react");
    type MockFormController = {
        element: HTMLFormElement | null;
        onFinish?: (values: Record<string, FormDataEntryValue>) => void;
        resetFields: () => void;
        submit: () => void;
    };
    const Form = ({
        children,
        disabled,
        form,
        onFinish,
    }: {
        children?: React.ReactNode;
        disabled?: boolean;
        form?: MockFormController;
        onFinish?: (values: Record<string, FormDataEntryValue>) => void;
    }) => {
        if (form) {
            form.onFinish = onFinish;
        }
        return React.createElement(
            "form",
            {
                "data-disabled": disabled ? "true" : "false",
                ref: (element: HTMLFormElement | null) => {
                    if (form) {
                        form.element = element;
                    }
                },
            },
            children
        );
    };
    Form.Item = ({ children, label, name }: { children?: React.ReactNode; label?: React.ReactNode; name?: string }) =>
        React.createElement(
            "label",
            null,
            label,
            React.isValidElement(children)
                ? React.cloneElement(children as React.ReactElement<Record<string, unknown>>, { name })
                : children
        );
    Form.useForm = () => {
        const formRef = React.useRef<MockFormController | null>(null);
        if (!formRef.current) {
            const controller: MockFormController = {
                element: null,
                resetFields: () => controller.element?.reset(),
                submit: () => {
                    if (!controller.element || !controller.onFinish) {
                        return;
                    }
                    const values: Record<string, FormDataEntryValue> = {};
                    new FormData(controller.element).forEach((value, key) => {
                        values[key] = value;
                    });
                    controller.onFinish(values);
                },
            };
            formRef.current = controller;
        }
        return [formRef.current];
    };

    const Input = (props: Record<string, unknown>) => React.createElement("input", props);
    Input.Password = (props: Record<string, unknown>) => React.createElement("input", { ...props, type: "password" });
    Input.OTP = (props: Record<string, unknown>) => React.createElement("input", props);

    const Empty = ({ description }: { description?: React.ReactNode }) => React.createElement("div", null, description);
    Empty.PRESENTED_IMAGE_SIMPLE = "simple";

    const ListItemMeta = ({
        avatar,
        description,
        style,
        title,
    }: {
        avatar?: React.ReactNode;
        description?: React.ReactNode;
        style?: React.CSSProperties;
        title?: React.ReactNode;
    }) =>
        React.createElement(
            "div",
            { className: "ant-list-item-meta", style },
            avatar,
            React.createElement("div", { className: "ant-list-item-meta-title" }, title),
            description
        );
    const ListItem = ({ actions, children }: { actions?: React.ReactNode[]; children?: React.ReactNode }) =>
        React.createElement("div", { className: "ant-list-item" }, children, React.createElement("div", null, actions));
    ListItem.Meta = ListItemMeta;

    const List = ({
        dataSource = [],
        loading,
        locale,
        renderItem,
    }: {
        dataSource?: unknown[];
        loading?: boolean;
        locale?: { emptyText?: React.ReactNode };
        renderItem?: (item: unknown) => React.ReactNode;
    }) =>
        React.createElement(
            "div",
            { className: loading ? "ant-list-loading" : "" },
            loading
                ? null
                : dataSource.length > 0
                ? dataSource.map((item, index) =>
                      React.createElement(React.Fragment, { key: index }, renderItem?.(item))
                  )
                : locale?.emptyText
        );
    List.Item = ListItem;

    const TypographyText = ({ children, style }: { children?: React.ReactNode; style?: React.CSSProperties }) =>
        React.createElement("span", { className: "ant-typography", style }, children);
    const TypographyParagraph = ({ children, style }: { children?: React.ReactNode; style?: React.CSSProperties }) =>
        React.createElement("p", { style }, children);

    return {
        Button: ({ children, disabled, onClick }: React.ButtonHTMLAttributes<HTMLButtonElement>) =>
            React.createElement("button", { disabled, onClick }, children),
        Card: ({
            children,
            extra,
            title,
        }: {
            children?: React.ReactNode;
            extra?: React.ReactNode;
            title?: React.ReactNode;
        }) => React.createElement("section", null, React.createElement("header", null, title, extra), children),
        Empty,
        Form,
        Input,
        List,
        message: {
            useMessage: () => [
                {
                    error: mockMessageError,
                    success: mockMessageSuccess,
                },
                null,
            ],
        },
        Modal: ({
            children,
            confirmLoading,
            okText,
            onOk,
            open,
        }: {
            children?: React.ReactNode;
            confirmLoading?: boolean;
            okText?: React.ReactNode;
            onOk?: () => void;
            open?: boolean;
        }) =>
            open
                ? React.createElement(
                      "div",
                      { role: "dialog" },
                      children,
                      React.createElement(
                          "button",
                          {
                              "data-modal-action": "ok",
                              disabled: confirmLoading,
                              onClick: onOk,
                          },
                          okText
                      )
                  )
                : null,
        Space: ({ children, style }: { children?: React.ReactNode; style?: React.CSSProperties }) =>
            React.createElement("div", { style }, children),
        Tooltip: ({ children, title }: { children?: React.ReactNode; title?: React.ReactNode }) =>
            React.createElement("span", { "data-tooltip": title }, children),
        Typography: {
            Paragraph: TypographyParagraph,
            Text: TypographyText,
        },
    };
});

jest.mock("antd-style", () => ({
    useTheme: () => ({
        colorPrimary: "#1677ff",
        fontSizeXL: 20,
    }),
}));

jest.mock("../base/AppBase", () => ({
    useAxiosBaseInstance: () => ({
        get: mockAxiosGet,
        post: mockAxiosPost,
    }),
}));

jest.mock("../utils/constants", () => ({
    getBackendServerUrl: () => globalThis.location.origin,
    getRes: () => ({
        cancel: "Cancel",
        defaultLoginInfo: mockPreviewMode ? {} : undefined,
        accountSecurity: {
            mfaCode: "MFA code",
            passkeyAdd: "Add Passkey",
            passkeyAddTitle: "Add Passkey",
            passkeyAdded: "Passkey added",
            passkeyCreatedAt: "Added",
            passkeyCurrentPassword: "Current password",
            passkeyDescription: "Manage passkeys",
            passkeyEmpty: "No passkeys added",
            passkeyLastUsedAt: "Last used",
            passkeyName: "Name",
            passkeyNamePlaceholder: "Work computer",
            passkeyNeverUsed: "Not used yet",
            passkeyPreviewModeDisabled: "Passkeys cannot be changed in preview mode",
            passkeyRegistrationFailed: "Could not add passkey",
            passkeyRemove: "Remove",
            passkeyRemoved: "Passkey removed",
            passkeyRemoveHint: "Remove {name}",
            passkeyRemoveTitle: "Remove Passkey",
            passkeyTitle: "Passkeys",
        },
    }),
}));

jest.mock("../utils/passkey", () => ({
    canUsePasskeys: () => mockCanUsePasskeys,
    isPasskeyCancellation: () => false,
    registerPasskey: (...args: unknown[]) => mockRegisterPasskey(...args),
}));

jest.mock("./passkey-management-api", () => ({
    PASSKEY_API_BASE: "/api/admin/account-security/passkey",
    PASSKEY_REGISTRATION_MESSAGE_KEY: "passkeyRegistration",
    PASSKEY_REMOVE_MESSAGE_KEY: "passkeyRemove",
    postPasskeyRegistrationVerification: (...args: unknown[]) => mockPostPasskeyRegistrationVerification(...args),
    postPasskeyRemoval: (...args: unknown[]) => mockPostPasskeyRemoval(...args),
}));

const reactActEnvironment = globalThis as typeof globalThis & {
    IS_REACT_ACT_ENVIRONMENT?: boolean;
};

const apiResponse = <T,>(data: T, error = 0, message = ""): ApiResponse<T> => ({
    data,
    error,
    message,
    pageBuildId: "test",
});

const deferred = <T,>() => {
    let resolve!: (value: T) => void;
    const promise = new Promise<T>((nextResolve) => {
        resolve = nextResolve;
    });
    return { promise, resolve };
};

describe("PasskeyManagement", () => {
    let container: HTMLDivElement;
    let root: Root;

    const render = async (offline: boolean) => {
        await act(async () => {
            root.render(<PasskeyManagement offline={offline} mfaEnabled={false} cardStyle={{}} />);
            await Promise.resolve();
        });
    };

    beforeEach(() => {
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = true;
        container = document.createElement("div");
        document.body.appendChild(container);
        root = createRoot(container);
        jest.clearAllMocks();
        mockCanUsePasskeys = true;
        mockPreviewMode = false;
    });

    afterEach(() => {
        act(() => {
            root.unmount();
        });
        container.remove();
        reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = false;
    });

    it("skips offline loading and reloads with a stable loading state after reconnecting", async () => {
        const response = deferred<PasskeyListAxiosResponse>();
        const longName = "work-computer-with-a-name-that-must-not-overflow-the-passkey-list";
        mockAxiosGet.mockReturnValueOnce(response.promise);

        await render(true);
        expect(mockAxiosGet).not.toHaveBeenCalled();
        expect(container.querySelector(".ant-list-loading")).toBeNull();

        await render(false);
        expect(mockAxiosGet).toHaveBeenCalledTimes(1);
        expect(container.querySelector(".ant-list-loading")).not.toBeNull();
        expect(container.textContent).not.toContain("No passkeys added");

        await act(async () => {
            response.resolve({
                data: apiResponse([
                    {
                        id: 1,
                        name: longName,
                        createdAt: 1,
                    },
                ]),
            });
            await response.promise;
        });

        expect(container.querySelector(".ant-list-loading")).toBeNull();
        const title = container.querySelector<HTMLElement>(".ant-list-item-meta-title .ant-typography");
        expect(title?.textContent).toBe(longName);
        expect(title?.style.display).toBe("block");
        expect(title?.style.maxWidth).toBe("100%");
    });

    it("does not keep loading while an error message remains open", async () => {
        mockMessageError.mockReturnValue(new Promise<boolean>(() => undefined));
        mockAxiosGet.mockResolvedValueOnce({
            data: apiResponse<PasskeySummary[]>([], 1, "Could not load passkeys"),
        });

        await render(false);

        expect(mockMessageError).toHaveBeenCalledWith("Could not load passkeys");
        expect(container.querySelector(".ant-list-loading")).toBeNull();
        expect(container.textContent).toContain("No passkeys added");
    });

    it("shows loading immediately when reconnecting after a completed load", async () => {
        const reconnectResponse = deferred<PasskeyListAxiosResponse>();
        mockAxiosGet
            .mockResolvedValueOnce({
                data: apiResponse<PasskeySummary[]>([
                    {
                        id: 1,
                        name: "Work computer",
                        createdAt: 1,
                    },
                ]),
            })
            .mockReturnValueOnce(reconnectResponse.promise);

        await render(false);
        expect(container.textContent).toContain("Work computer");

        await render(true);
        expect(mockAxiosGet).toHaveBeenCalledTimes(1);

        await render(false);
        expect(mockAxiosGet).toHaveBeenCalledTimes(2);
        expect(container.querySelector(".ant-list-loading")).not.toBeNull();

        await act(async () => {
            reconnectResponse.resolve({ data: apiResponse<PasskeySummary[]>([]) });
            await reconnectResponse.promise;
        });
    });

    it("keeps the registration action visible when WebAuthn is unavailable", async () => {
        mockAxiosGet.mockResolvedValueOnce({ data: apiResponse<PasskeySummary[]>([]) });

        await render(false);
        let addButton = Array.from(container.querySelectorAll("button")).find(
            (button) => button.textContent === "Add Passkey"
        );
        expect(addButton).toBeDefined();
        expect(addButton?.disabled).toBe(false);

        mockCanUsePasskeys = false;
        await render(false);
        addButton = Array.from(container.querySelectorAll("button")).find(
            (button) => button.textContent === "Add Passkey"
        );
        expect(addButton).toBeDefined();
        expect(addButton?.disabled).toBe(true);
    });

    it("keeps Passkey actions visible but disables them in preview mode", async () => {
        mockPreviewMode = true;
        mockAxiosGet.mockResolvedValueOnce({
            data: apiResponse<PasskeySummary[]>([
                {
                    id: 1,
                    name: "Work computer",
                    createdAt: 1,
                },
            ]),
        });

        await render(false);

        const addButton = Array.from(container.querySelectorAll("button")).find(
            (button) => button.textContent === "Add Passkey"
        );
        const removeButton = Array.from(container.querySelectorAll("button")).find(
            (button) => button.textContent === "Remove"
        );
        expect(addButton).toBeDefined();
        expect(addButton?.disabled).toBe(true);
        expect(removeButton).toBeDefined();
        expect(removeButton?.disabled).toBe(true);
        expect(addButton?.parentElement?.dataset.tooltip).toBe("Passkeys cannot be changed in preview mode");
    });

    it("submits registration through the SSE API wrapper, closes the modal, and reloads the list", async () => {
        const addedPasskey: PasskeySummary = {
            id: 2,
            name: "Work computer",
            createdAt: 2,
        };
        const credentialResponse = {
            id: "credential-id",
            rawId: "credential-id",
            response: {
                clientDataJSON: "client-data",
                attestationObject: "attestation-object",
            },
            clientExtensionResults: {},
            type: "public-key" as const,
        };
        mockAxiosGet
            .mockResolvedValueOnce({ data: apiResponse<PasskeySummary[]>([]) })
            .mockResolvedValueOnce({ data: apiResponse([addedPasskey]) });
        mockAxiosPost.mockResolvedValueOnce({
            data: apiResponse({
                requestId: "registration-request",
                options: { challenge: "challenge" },
            }),
        });
        mockRegisterPasskey.mockResolvedValueOnce(credentialResponse);
        mockPostPasskeyRegistrationVerification.mockResolvedValueOnce(apiResponse(addedPasskey));

        await render(false);
        const addButton = Array.from(container.querySelectorAll("button")).find(
            (button) => button.textContent === "Add Passkey"
        );
        await act(async () => {
            addButton?.click();
        });

        const dialog = container.querySelector<HTMLElement>("[role='dialog']");
        const nameInput = dialog?.querySelector<HTMLInputElement>("input[name='name']");
        const passwordInput = dialog?.querySelector<HTMLInputElement>("input[name='password']");
        expect(nameInput).not.toBeNull();
        expect(passwordInput).not.toBeNull();
        nameInput!.value = "  Work computer  ";
        passwordInput!.value = "current-password";

        await act(async () => {
            dialog?.querySelector<HTMLButtonElement>("[data-modal-action='ok']")?.click();
            for (let index = 0; index < 8; index += 1) {
                await Promise.resolve();
            }
        });

        const expectedVerifyRequest: PasskeyRegistrationVerifyRequest = {
            requestId: "registration-request",
            response: credentialResponse,
            name: "Work computer",
        };
        expect(mockPostPasskeyRegistrationVerification).toHaveBeenCalledWith(expectedVerifyRequest, expect.anything());
        expect(mockMessageSuccess).toHaveBeenCalledWith({
            key: "passkeyRegistration",
            content: "Passkey added",
        });
        expect(container.querySelector("[role='dialog']")).toBeNull();
        expect(mockAxiosGet).toHaveBeenCalledTimes(2);
        expect(container.textContent).toContain("Work computer");
    });

    it("submits removal through the SSE API wrapper, closes the modal, and reloads the list", async () => {
        const passkey: PasskeySummary = {
            id: 3,
            name: "Old computer",
            createdAt: 3,
        };
        mockAxiosGet
            .mockResolvedValueOnce({ data: apiResponse([passkey]) })
            .mockResolvedValueOnce({ data: apiResponse<PasskeySummary[]>([]) });
        mockPostPasskeyRemoval.mockResolvedValueOnce(apiResponse(true));

        await render(false);
        const removeButton = Array.from(container.querySelectorAll("button")).find(
            (button) => button.textContent === "Remove"
        );
        await act(async () => {
            removeButton?.click();
        });

        const dialog = container.querySelector<HTMLElement>("[role='dialog']");
        const passwordInput = dialog?.querySelector<HTMLInputElement>("input[name='password']");
        expect(passwordInput).not.toBeNull();
        passwordInput!.value = "current-password";

        await act(async () => {
            dialog?.querySelector<HTMLButtonElement>("[data-modal-action='ok']")?.click();
            for (let index = 0; index < 5; index += 1) {
                await Promise.resolve();
            }
        });

        expect(mockPostPasskeyRemoval).toHaveBeenCalledWith(
            {
                id: 3,
                password: expect.any(String),
                mfaCode: undefined,
            },
            expect.anything()
        );
        expect(mockMessageSuccess).toHaveBeenCalledWith({
            key: "passkeyRemove",
            content: "Passkey removed",
        });
        expect(container.querySelector("[role='dialog']")).toBeNull();
        expect(mockAxiosGet).toHaveBeenCalledTimes(2);
        expect(container.textContent).toContain("No passkeys added");
    });
});
