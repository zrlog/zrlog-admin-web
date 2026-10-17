import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { Simulate } from "react-dom/test-utils";
import { afterEach, beforeEach, expect, jest, test } from "@jest/globals";
import ProxySettings, { ProxyData, validProxyUrl, validNoProxy } from "./Proxy";

jest.mock("@zrlog/ui/antd/Input", () => require("antd/lib/input"));
jest.mock("@zrlog/ui/antd/Tag", () => require("antd/lib/tag"));
jest.mock("../common/SettingsLayout", () => ({ children }: any) => <div>{children}</div>);
const mockPost = jest.fn<Promise<any>, any[]>();
const mockAxios = { post: mockPost };
const mockSuccess = jest.fn();
jest.mock("../../base/AppBase", () => ({ useAxiosBaseInstance: () => mockAxios }));
jest.mock("@zrlog/ui/feedback", () => ({ useUiApp: () => ({ message: { success: mockSuccess } }) }));
jest.mock("../../utils/constants", () => ({ getRes: () => require("../../i18n/admin").getAdminI18n("en_US") }));

const data: ProxyData = {
    url: "http://proxy.example:3128",
    noProxy: ".internal.example",
    source: "file",
    noProxySource: "file",
    httpsSource: "file",
    httpVariable: "",
    httpsVariable: "",
    noProxyVariable: "",
    activeUrl: "",
    activeHttpsUrl: "",
    activeNoProxy: "",
    restartRequired: false,
    writable: true,
};
let root: Root;
let container: HTMLDivElement;
beforeEach(() => {
    (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
    (globalThis as any).ResizeObserver = class {
        observe = jest.fn();
        unobserve = jest.fn();
        disconnect = jest.fn();
    };
    (globalThis as any).MessageChannel = class {
        port1 = { onmessage: () => undefined };
        port2 = { postMessage: () => Promise.resolve().then(() => this.port1.onmessage()) };
    };
    window.matchMedia = (() => ({
        matches: false,
        addListener: jest.fn(),
        removeListener: jest.fn(),
        addEventListener: jest.fn(),
        removeEventListener: jest.fn(),
    })) as any;
    const computedStyle = window.getComputedStyle;
    jest.spyOn(window, "getComputedStyle").mockImplementation((element) => computedStyle(element));
    mockPost.mockReset();
    mockSuccess.mockReset();
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
const render = async (value = data) => {
    await act(async () => root.render(<ProxySettings data={value} offline={false} />));
};
const button = (label: string) =>
    Array.from(container.querySelectorAll("button")).find((item) => item.textContent === label)!;

test("file settings remain editable while environment priority is explained", async () => {
    await render({ ...data, source: "environment", httpVariable: "HTTP_PROXY" });
    expect((container.querySelector("#url") as HTMLInputElement).disabled).toBe(false);
    expect((container.querySelector("#noProxy") as HTMLInputElement).disabled).toBe(false);
    expect(container.textContent).toContain("HTTP_PROXY takes priority");
    expect(container.textContent).toContain("You can still save the file");
    expect(button("Save configuration").disabled).toBe(false);
    await render({
        ...data,
        source: "environment",
        noProxySource: "environment",
        httpVariable: "http_proxy",
        noProxyVariable: "no_proxy",
    });
    expect(button("Save configuration").disabled).toBe(false);
    expect(container.textContent).toContain("no_proxy takes priority");
});

test("saving shows pending restart while preserving the active configuration", async () => {
    mockPost.mockResolvedValue({ data: { error: 0, data: { ...data, restartRequired: true } } });
    await render();
    await act(async () => Simulate.submit(container.querySelector("form")!));
    expect(mockPost).toHaveBeenCalledWith("/api/admin/system/proxy", { url: data.url, noProxy: data.noProxy });
    expect(container.textContent).toContain("Settings saved; restart required");
    expect(container.textContent).toContain("No application proxy");
    expect(mockSuccess).toHaveBeenCalledTimes(1);
});

test("connection and save failures remain visible and preserve form values", async () => {
    mockPost.mockResolvedValue({ data: { error: 0, data: false } });
    await render();
    await act(async () => Simulate.click(button("Test connection")));
    expect(mockPost).toHaveBeenCalledWith(
        "/api/admin/system/proxy/test",
        { url: data.url, noProxy: data.noProxy },
        { timeout: 15000 }
    );
    expect(container.querySelector('.ant-alert-error[role="alert"]')?.textContent).toContain("Connection test failed");
    mockPost.mockRejectedValue(new Error("disk unavailable"));
    await act(async () => Simulate.submit(container.querySelector("form")!));
    expect(container.querySelector('.ant-alert-error[role="alert"]')?.textContent).toContain("Could not save");
    expect((container.querySelector("#url") as HTMLInputElement).value).toBe(data.url);
});

test("read-only storage prevents saving but permits a connection test", async () => {
    await render({ ...data, writable: false });
    expect(button("Save configuration").disabled).toBe(true);
    expect(button("Test connection").disabled).toBe(false);
    expect(container.textContent).toContain("read-only");
});

test("proxy URLs reject credentials and destination URLs", () => {
    for (const value of ["", "http://proxy.example:3128", "http://[::1]:80"]) expect(validProxyUrl(value)).toBe(true);
    for (const value of [
        "https://proxy.example",
        "http://user:secret@proxy.example",
        "http://proxy/path",
        "http://proxy:0",
        "http://proxy:65536",
    ]) {
        expect(validProxyUrl(value)).toBe(false);
    }
});

test("NO_PROXY accepts IPv6 literals and rejects ports and CIDR ranges", () => {
    expect(validNoProxy("localhost,::1,[::1],2001:db8::2,.example.com,*.internal")).toBe(true);
    for (const value of ["internal:8080", "10.0.0.0/8", "[not:ipv6]", "http://internal"])
        expect(validNoProxy(value)).toBe(false);
});
