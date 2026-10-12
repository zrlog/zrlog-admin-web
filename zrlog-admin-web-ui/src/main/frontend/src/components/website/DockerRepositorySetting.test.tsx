import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, expect, jest, test } from "@jest/globals";
import DockerRepositorySetting from "./DockerRepositorySetting";
import type { Upgrade } from "./index";

const mockGet = jest.fn<Promise<any>, [string, unknown]>();
const mockAxios = { get: mockGet };
jest.mock("../../base/AppBase", () => ({ useAxiosBaseInstance: () => mockAxios }));
jest.mock("../../utils/constants", () => ({
    getRes: () => require("../../i18n/admin").getAdminI18n("en_US"),
}));
jest.mock("antd", () => {
    const React = require("react");
    return {
        Space: ({ children }: any) => React.createElement("div", null, children),
        Alert: ({ message, action }: any) => React.createElement("div", { role: "alert" }, message, action),
        Button: ({ children, onClick, disabled, htmlType }: any) =>
            React.createElement("button", { onClick, disabled, type: htmlType }, children),
        Select: ({ options, onChange, value, disabled }: any) =>
            React.createElement(
                "select",
                { value: value ?? "", disabled, onChange: (event: any) => onChange(event.target.value) },
                options.map((option: any) =>
                    React.createElement("option", { key: option.value, value: option.value }, option.label)
                )
            ),
    };
});
const aliyun = "registry.cn-chengdu.aliyuncs.com/hibegin/zrlog";
const hub = "docker.io/zrlog/zrlog";
const initial: Upgrade = {
    autoUpgradeVersion: 86400,
    upgradePreview: true,
    dockerMode: true,
    dockerSettings: {
        settings: { repository: hub, channel: "preview" },
        currentRepository: aliyun,
        images: [aliyun, hub].map((repository) => ({ repository, platforms: ["linux/amd64"] })),
    },
};
const response = (repository = hub) => ({
    data: {
        error: 0,
        data: {
            ...initial,
            dockerSettings: { ...initial.dockerSettings, settings: { repository, channel: "release" } },
        },
    },
});
let root: Root;
let container: HTMLDivElement;
const change = jest.fn();
const busy = jest.fn();
const render = async (data = initial, preview = true, disabled = false, value?: string) => {
    await act(async () =>
        root.render(
            <DockerRepositorySetting
                data={data}
                preview={preview}
                disabled={disabled}
                value={value}
                onChange={change}
                onLoadingChange={busy}
            />
        )
    );
};
const select = () => container.querySelector("select")!;
beforeEach(() => {
    (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
    mockGet.mockReset();
    change.mockClear();
    busy.mockClear();
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
});
afterEach(() => {
    act(() => root.unmount());
    container.remove();
    delete (globalThis as any).IS_REACT_ACT_ENVIRONMENT;
});
test("shows the initial repository without a request or independent save button", async () => {
    await render();
    expect(select().value).toBe(hub);
    expect(mockGet).not.toHaveBeenCalled();
    expect(container.querySelector("button")).toBeNull();
    await act(async () => {
        select().value = aliyun;
        select().dispatchEvent(new Event("change", { bubbles: true }));
    });
    expect(change).toHaveBeenCalledWith(aliyun);
    expect(mockGet).not.toHaveBeenCalled();
});
test("ignores stale channel replies and preserves the unsaved repository", async () => {
    let resolve!: (value: unknown) => void;
    mockGet.mockReturnValueOnce(
        new Promise((done) => {
            resolve = done;
        })
    );
    mockGet.mockResolvedValueOnce(response(hub));
    await render();
    await render(initial, false, false, aliyun);
    expect(busy).toHaveBeenLastCalledWith(true);
    await render(initial, true, false, aliyun);
    await act(async () => resolve(response("stale.example/zrlog")));
    expect(select().value).toBe(aliyun);
    expect(select().textContent).not.toContain("stale.example");
    expect(mockGet).toHaveBeenLastCalledWith("/api/admin/website/upgrade", {
        params: { channel: "preview" },
        showError: false,
    });
    expect(busy).toHaveBeenLastCalledWith(false);
    expect(change).not.toHaveBeenCalled();
});
test("keeps edited selection when channel loading fails and disables controls offline", async () => {
    mockGet.mockRejectedValue(new Error("private detail"));
    await render();
    await render(initial, false, false, aliyun);
    expect(select().value).toBe(aliyun);
    expect(select().disabled).toBe(true);
    expect(container.textContent).not.toContain("private detail");
    await render(initial, false, true, aliyun);
    await act(async () => container.querySelector("button")!.click());
    expect(mockGet).toHaveBeenCalledTimes(1);
});
test.each([
    ["metadata_unavailable", "The Docker update manifest is unavailable"],
    ["metadata_not_found", "The Docker update manifest is unavailable"],
    ["updater_unavailable", "Unable to connect to the updater"],
    ["updater_unauthorized", "Updater authentication failed"],
    ["updater_configuration_mismatch", "The updater configuration is incomplete"],
    ["unexpected private detail", "Unable to load image repositories"],
])("explains %s and retries through the existing website endpoint", async (errorCode, message) => {
    const data = { ...initial, dockerSettings: undefined, dockerErrorCode: errorCode };
    await render(data);
    expect(container.querySelector('[role="alert"]')?.textContent).toContain(message);
    expect(container.textContent).not.toContain("private");
    expect(select().disabled).toBe(true);
    mockGet.mockResolvedValue(response(aliyun));
    await act(async () => container.querySelector("button")!.click());
    expect(mockGet).toHaveBeenCalledWith("/api/admin/website/upgrade", {
        params: { channel: "preview" },
        showError: false,
    });
    expect(select().value).toBe(aliyun);
    expect(container.querySelector('[role="alert"]')).toBeNull();
});
