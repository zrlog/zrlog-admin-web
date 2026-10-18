import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { Simulate } from "react-dom/test-utils";
import { afterEach, beforeEach, expect, jest, test } from "@jest/globals";
import ProxySettings, { ProxyData, validProxyUrl, validNoProxy } from "./Proxy";

jest.mock("@zrlog/ui/antd/Input", () => require("antd/lib/input"));
jest.mock("../common/AdminSettingsLayout", () => ({ children }: any) => <div>{children}</div>);
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
const globals = ["IS_REACT_ACT_ENVIRONMENT", "ResizeObserver", "MessageChannel", "matchMedia"];
let descriptors: Array<PropertyDescriptor | undefined>;
beforeEach(() => {
    descriptors = globals.map((key) => Object.getOwnPropertyDescriptor(globalThis, key));
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
    jest.useRealTimers();
    globals.forEach((key, index) => {
        const descriptor = descriptors[index];
        if (descriptor) Object.defineProperty(globalThis, key, descriptor);
        else Reflect.deleteProperty(globalThis, key);
    });
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
    expect(container.textContent).toContain("HTTP_PROXY overrides this setting");
    expect(container.textContent).toContain("Remove it and restart ZrLog");
    expect(button("Save configuration").disabled).toBe(false);
    await render({
        ...data,
        source: "environment",
        noProxySource: "environment",
        httpVariable: "http_proxy",
        noProxyVariable: "no_proxy",
    });
    expect(button("Save configuration").disabled).toBe(false);
    expect(container.textContent).toContain("no_proxy overrides this setting");
});

test("saving shows pending restart while preserving the active configuration", async () => {
    mockPost.mockResolvedValue({ data: { error: 0, data: { ...data, restartRequired: true } } });
    await render();
    await act(async () => Simulate.submit(container.querySelector("form")!));
    expect(mockPost).toHaveBeenCalledWith("/api/admin/system/proxy", { url: data.url, noProxy: data.noProxy });
    expect(container.textContent).toContain("Proxy settings saved; restart ZrLog to apply");
    expect(container.textContent).toContain("No proxy set here");
    expect(mockSuccess).toHaveBeenCalledWith("Proxy settings saved; restart ZrLog to apply");
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
    expect(container.querySelector('.ant-alert-error[role="alert"]')?.textContent).toContain(
        "Could not connect to the ZrLog website"
    );
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
    await act(async () => Simulate.submit(container.querySelector("form")!));
    expect(mockPost).not.toHaveBeenCalled();
});

test.each(["save", "test"])("%s locks the submitted values and prevents overlapping requests", async (action) => {
    let resolve!: (value: unknown) => void;
    mockPost.mockReturnValue(
        new Promise((done) => {
            resolve = done;
        })
    );
    await render();
    await act(async () => {
        if (action === "save") Simulate.submit(container.querySelector("form")!);
        else Simulate.click(button("Test connection"));
    });
    expect((container.querySelector("#url") as HTMLInputElement).disabled).toBe(true);
    expect((container.querySelector("#noProxy") as HTMLInputElement).disabled).toBe(true);
    await act(async () => {
        Simulate.submit(container.querySelector("form")!);
        Simulate.click(button("Test connection"));
    });
    expect(mockPost).toHaveBeenCalledTimes(1);
    await act(async () => resolve({ data: { error: 0, data: action === "save" ? data : true } }));
    expect((container.querySelector("#url") as HTMLInputElement).disabled).toBe(false);
    expect(mockSuccess).toHaveBeenCalledTimes(1);
});

test("page data refresh preserves an unsaved proxy draft", async () => {
    await render();
    const input = container.querySelector("#url") as HTMLInputElement;
    await act(async () => Simulate.change(input, { target: { value: "http://new-proxy.example:3128" } } as any));
    await render({ ...data, noProxy: ".refreshed.example" });
    expect(input.value).toBe("http://new-proxy.example:3128");
    expect((container.querySelector("#noProxy") as HTMLInputElement).value).toBe(data.noProxy);
});

test("invalid input stays a field error without sending a connection test", async () => {
    jest.useFakeTimers();
    await render({ ...data, url: "http://proxy.example/path/.." });
    await act(async () => Simulate.click(button("Test connection")));
    for (
        let elapsed = 0;
        elapsed < 1000 && !container.querySelector("#url_help .ant-form-item-explain-error");
        elapsed += 50
    ) {
        await act(async () => jest.advanceTimersByTime(50));
    }
    expect(mockPost).not.toHaveBeenCalled();
    expect(container.textContent).toContain("Enter an address in http://host:port format");
    expect(container.querySelector('.ant-alert-error[role="alert"]')).toBeNull();
    expect(button("Test connection").disabled).toBe(false);
});

test("proxy URLs reject credentials and destination URLs", () => {
    for (const value of ["", "http://proxy.example:3128", "http://[::1]:80"]) expect(validProxyUrl(value)).toBe(true);
    for (const value of [
        "https://proxy.example",
        "http://user:secret@proxy.example",
        "http://proxy/path",
        "http://proxy:0",
        "http://proxy:65536",
        "http://@proxy.example",
        "http://proxy.example?",
        "http://proxy.example#",
        "http://proxy.example/path/..",
        "http://proxy.example\\path\\..",
        "http://proxy.\nexample",
        "http://-proxy.example",
        "http://proxy_name.example",
    ]) {
        expect(validProxyUrl(value)).toBe(false);
    }
});

test("NO_PROXY accepts IPv6 literals and rejects ports and CIDR ranges", () => {
    expect(validNoProxy("localhost,::1,[::1],2001:db8::2,.example.com,*.internal")).toBe(true);
    for (const value of ["internal:8080", "10.0.0.0/8", "[not:ipv6]", "http://internal"])
        expect(validNoProxy(value)).toBe(false);
});
