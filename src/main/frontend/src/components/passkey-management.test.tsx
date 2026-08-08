import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, jest } from "@jest/globals";
import type { ApiResponse, PasskeySummary } from "../type";
import PasskeyManagement from "./passkey-management";

type PasskeyListAxiosResponse = {
    data: ApiResponse<PasskeySummary[]>;
};

const mockAxiosGet = jest.fn<Promise<PasskeyListAxiosResponse>, []>();
const mockAxiosPost = jest.fn();
let mockCanUsePasskeys = true;
let mockPreviewMode = false;
const mockMessageError = jest.fn<PromiseLike<boolean>, [string]>();
const mockMessageSuccess = jest.fn<PromiseLike<boolean>, [string]>();

jest.mock("antd", () => {
    const React = require("react") as typeof import("react");
    const Form = ({ children, disabled }: { children?: React.ReactNode; disabled?: boolean }) =>
        React.createElement("form", { "data-disabled": disabled ? "true" : "false" }, children);
    Form.Item = ({ children, label }: { children?: React.ReactNode; label?: React.ReactNode }) =>
        React.createElement("label", null, label, children);
    Form.useForm = () => [{ resetFields: () => undefined, submit: () => undefined }];

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
        Modal: ({ children, open }: { children?: React.ReactNode; open?: boolean }) =>
            open ? React.createElement("div", null, children) : null,
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
    registerPasskey: require("@jest/globals").jest.fn(),
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
});
