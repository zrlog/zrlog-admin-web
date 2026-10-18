import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { Simulate } from "react-dom/test-utils";
import { afterEach, beforeEach, expect, jest, test } from "@jest/globals";
import UpgradeSettingForm from "./UpgradeSettingForm";
import type { Upgrade } from "./index";

jest.mock("antd/es/form", () => require("antd/lib/form"));
jest.mock("antd/es/select", () => require("antd/lib/select"));
jest.mock("antd/es/button", () => require("antd/lib/button"));
jest.mock("antd/es/grid/row", () => require("antd/lib/grid/row"));
jest.mock("antd/es/grid/col", () => require("antd/lib/grid/col"));
jest.mock("@zrlog/ui/antd/Switch", () => require("antd/lib/switch"));
jest.mock("@zrlog/ui/feedback", () => ({ useUiApp: () => ({ modal: {} }), useUiMessage: () => [{}, null] }));
jest.mock("react-router-dom", () => ({ useNavigate: () => () => undefined }));
jest.mock("../upgrade-content", () => () => null);
jest.mock("../../utils/responsive-form", () => ({
    useResponsiveFormLayout: () => ({ formLayout: {}, narrow: false, screens: { md: true } }),
}));
const mockGet = jest.fn<Promise<any>, any[]>();
const mockAxios = { get: mockGet };
jest.mock("../../base/AppBase", () => ({ useAxiosBaseInstance: () => mockAxios }));
jest.mock("../../utils/constants", () => ({ getRes: () => require("../../i18n/admin").getAdminI18n("en_US") }));

let root: Root;
let container: HTMLDivElement;
const submit = jest.fn();
const data: Upgrade = {
    autoUpgradeVersion: 86400,
    upgradePreview: true,
    upgradePackageSource: "github",
    dockerMode: true,
    dockerSettings: {
        settings: { repository: "docker.io/zrlog/zrlog", channel: "preview" },
        currentRepository: "docker.io/zrlog/zrlog",
        images: [{ repository: "registry.example/zrlog", platforms: ["linux/amd64"] }],
    },
};
beforeEach(() => {
    (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
    (globalThis as any).ResizeObserver = class {
        observe() {
            return undefined;
        }
        unobserve() {
            return undefined;
        }
        disconnect() {
            return undefined;
        }
    };
    (globalThis as any).MessageChannel = class {
        port1 = { onmessage: () => undefined };
        port2 = { postMessage: () => Promise.resolve().then(() => this.port1.onmessage()) };
    };
    window.matchMedia = (() => ({
        matches: false,
        addListener() {
            return undefined;
        },
        removeListener() {
            return undefined;
        },
        addEventListener() {
            return undefined;
        },
        removeEventListener() {
            return undefined;
        },
    })) as any;
    const computedStyle = window.getComputedStyle;
    jest.spyOn(window, "getComputedStyle").mockImplementation((element) => computedStyle(element));
    submit.mockReset();
    mockGet.mockReset();
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
});
afterEach(() => {
    act(() => root.unmount());
    container.remove();
    jest.restoreAllMocks();
    delete (globalThis as any).IS_REACT_ACT_ENVIRONMENT;
    delete (globalThis as any).MessageChannel;
});
const render = async (value = data, offline = false) => {
    await act(async () =>
        root.render(<UpgradeSettingForm data={value} offline={offline} offlineData={false} onSubmit={submit} />)
    );
};
const save = async () => {
    await act(async () => Simulate.submit(container.querySelector("form")!));
};
test("Docker hydration shows only the repository with one final submit and no mode probe", async () => {
    await render();
    expect(container.textContent).toContain("Docker image repository");
    expect(container.textContent).not.toContain("Package download source");
    expect(mockGet).not.toHaveBeenCalled();
    expect(container.querySelectorAll('button[type="submit"]')).toHaveLength(1);
    expect(container.querySelectorAll("button")[container.querySelectorAll("button").length - 1].type).toBe("submit");
    await save();
    expect(submit).toHaveBeenLastCalledWith({ autoUpgradeVersion: 86400, upgradePreview: true });
});
test("package deployments submit only the package source", async () => {
    await render({ ...data, dockerMode: false, dockerSettings: undefined });
    expect(container.textContent).not.toContain("Docker image repository");
    expect(container.textContent).toContain("Package download source");
    await save();
    expect(submit).toHaveBeenLastCalledWith({
        autoUpgradeVersion: 86400,
        upgradePreview: true,
        upgradePackageSource: "github",
    });
    expect(mockGet).not.toHaveBeenCalled();
});
test("the common submit includes edited repository and retains it after a failed save", async () => {
    await render();
    const input = container.querySelector('input[aria-label="Docker image repository"]')!;
    await act(async () => Simulate.mouseDown(input));
    const option = Array.from(document.querySelectorAll(".ant-select-item-option")).find(
        (item) => item.textContent === "registry.example/zrlog"
    )!;
    await act(async () => Simulate.click(option));
    await save();
    expect(submit).toHaveBeenLastCalledWith({
        autoUpgradeVersion: 86400,
        upgradePreview: true,
        dockerRepository: "registry.example/zrlog",
    });
    await save();
    expect(submit).toHaveBeenCalledTimes(2);
    expect(submit.mock.calls[1]).toEqual(submit.mock.calls[0]);
});
test("channel refresh does not overwrite unsaved form fields", async () => {
    mockGet.mockResolvedValue({ data: { error: 0, data: { ...data, autoUpgradeVersion: -1, upgradePreview: true } } });
    await render();
    await act(async () => (container.querySelector("#upgradePreview") as HTMLElement).click());
    expect(mockGet).toHaveBeenCalledWith("/api/admin/system/upgrade", {
        params: { channel: "release" },
        showError: false,
    });
    await save();
    expect(submit).toHaveBeenLastCalledWith({ autoUpgradeVersion: 86400, upgradePreview: false });
});
test("offline data keeps the mode visible and cannot submit", async () => {
    await render(data, true);
    expect(container.textContent).toContain("Docker image repository");
    await save();
    expect(submit).not.toHaveBeenCalled();
    expect(mockGet).not.toHaveBeenCalled();
});
