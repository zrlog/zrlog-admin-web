import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { afterEach, beforeEach, expect, jest, test } from "@jest/globals";
import DockerRepositorySetting from "./DockerRepositorySetting";

const mockGet = jest.fn<Promise<any>, [string, unknown]>();
const mockPost = jest.fn<Promise<any>, [string, unknown, unknown]>();
const mockAxios = { get: mockGet, post: mockPost };
jest.mock("../../base/AppBase", () => ({ useAxiosBaseInstance: () => mockAxios }));
jest.mock("../../utils/constants", () => ({
    getRes: () => require("../../i18n/admin").getAdminI18n("en_US"),
}));
jest.mock("antd", () => {
    const React = require("react");
    const Container = ({ children }: any) => React.createElement("div", null, children);
    return {
        Form: { Item: Container },
        Space: Container,
        Alert: ({ type, message }: any) => React.createElement("div", { role: "status", "data-result": type }, message),
        Button: ({ children, onClick, disabled, htmlType }: any) =>
            React.createElement("button", { onClick, disabled, type: htmlType }, children),
        Select: ({ options, onChange, value, disabled }: any) =>
            React.createElement(
                "select",
                { value, disabled, onChange: (event: any) => onChange(event.target.value) },
                options.map((option: { value: string; label: string }) =>
                    React.createElement("option", { key: option.value, value: option.value }, option.label)
                )
            ),
    };
});

const aliyun = "registry.cn-chengdu.aliyuncs.com/hibegin/zrlog";
const hub = "docker.io/zrlog/zrlog";
const settings = (repository = "", channel = "preview") => ({
    data: {
        error: 0,
        data: {
            settings: { repository, channel },
            currentRepository: aliyun,
            images: [
                { repository: aliyun, platforms: ["linux/amd64", "linux/arm64"] },
                { repository: hub, platforms: ["linux/amd64", "linux/arm64"] },
            ],
        },
    },
});
const deferred = () => {
    let resolve!: (value: unknown) => void;
    const promise = new Promise((done) => {
        resolve = done;
    });
    return { promise, resolve };
};
let root: Root;
let container: HTMLDivElement;
const reactActEnvironment = globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean };
const render = async (preview = true, disabled = false) => {
    await act(async () => root.render(<DockerRepositorySetting preview={preview} disabled={disabled} />));
};
const click = async (label: string) => {
    const element = Array.from(container.querySelectorAll("button")).find((item) => item.textContent === label);
    expect(element).toBeDefined();
    await act(async () => element!.click());
};
const select = () => container.querySelector("select")!;

beforeEach(() => {
    reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = true;
    mockGet.mockReset();
    mockPost.mockReset();
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
});
afterEach(() => {
    act(() => root.unmount());
    container.remove();
    reactActEnvironment.IS_REACT_ACT_ENVIRONMENT = false;
});

test("loads the persisted repository and explicitly saves Aliyun for the selected channel", async () => {
    mockGet.mockResolvedValue(settings(hub));
    mockPost.mockResolvedValue({ data: { error: 0, data: { repository: aliyun } } });
    await render();
    expect(mockGet).not.toHaveBeenCalled();
    await click("Load image repositories");
    expect(mockGet).toHaveBeenCalledWith("/api/admin/upgrade/docker-settings", {
        params: { channel: "preview" },
        showError: false,
    });
    expect(select().value).toBe(hub);
    expect(select().textContent).toContain(`Use current deployment repository (${aliyun})`);
    await act(async () => {
        select().value = aliyun;
        select().dispatchEvent(new Event("change", { bubbles: true }));
    });
    expect(mockPost).not.toHaveBeenCalled();
    await click("Save image repository");
    expect(mockPost).toHaveBeenCalledWith(
        "/api/admin/upgrade/save-docker-settings",
        {
            repository: aliyun,
            channel: "preview",
        },
        { showError: false, timeout: 30000 }
    );
    expect(container.querySelector('[data-result="success"]')?.textContent).toBe("Image repository saved");
    expect(select().value).toBe(aliyun);
});

test("ignores a previous channel response after switching from preview to release", async () => {
    const preview = deferred();
    mockGet.mockReturnValueOnce(preview.promise);
    mockGet.mockResolvedValue(settings(aliyun, "release"));
    await render();
    await click("Load image repositories");
    await render(false);
    await click("Load image repositories");
    await act(async () => preview.resolve(settings(hub)));
    expect(select().value).toBe(aliyun);
    mockPost.mockResolvedValue({ data: { error: 0, data: { repository: aliyun } } });
    await click("Save image repository");
    expect(mockPost).toHaveBeenCalledWith(
        "/api/admin/upgrade/save-docker-settings",
        {
            repository: aliyun,
            channel: "release",
        },
        expect.anything()
    );
});

test("keeps the selected repository on save failure and leaves saving disabled while offline", async () => {
    mockGet.mockResolvedValue(settings(aliyun));
    mockPost.mockResolvedValue({ data: { error: 1 } });
    await render();
    await click("Load image repositories");
    await click("Save image repository");
    expect(select().value).toBe(aliyun);
    expect(container.querySelector('[data-result="error"]')).not.toBeNull();
    expect(container.querySelector('[data-result="success"]')).toBeNull();
    await render(true, true);
    await click("Save image repository");
    await click("Load image repositories");
    expect(mockPost).toHaveBeenCalledTimes(1);
    expect(mockGet).toHaveBeenCalledTimes(1);
    expect(select().disabled).toBe(true);
});
