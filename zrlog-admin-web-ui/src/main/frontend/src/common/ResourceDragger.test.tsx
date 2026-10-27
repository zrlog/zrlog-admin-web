import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { Simulate } from "react-dom/test-utils";
import { beforeEach, afterEach, expect, it, jest } from "@jest/globals";
import ResourceDragger from "./ResourceDragger";

jest.mock("../components/file-manager/picker", () => ({ __esModule: true, default: () => null }));
jest.mock("../utils/constants", () => ({
    getRes: () => ({ fileManager: { uploadFailed: "Upload failed" } }),
    tryAppendBackendServerUrl: (url: string) => `https://backend.example${url}`,
}));

let root: Root;
let container: HTMLDivElement;
beforeEach(() => {
    (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
    window.matchMedia = jest.fn(() => ({ matches: false, addListener: jest.fn(), removeListener: jest.fn() })) as any;
    global.ResizeObserver = class {
        observe = jest.fn();
        unobserve = jest.fn();
        disconnect = jest.fn();
    };
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
});
afterEach(() => {
    act(() => root.unmount());
    container.remove();
});
const choose = async () =>
    act(async () => {
        Simulate.change(container.querySelector('input[type="file"]')!, {
            target: { files: [new File(["data"], "original.zip")] } as any,
        });
    });

it("uses the host transport, multipart filename and URL mapping and reports actual progress", async () => {
    const success = jest.fn();
    const progress = jest.fn();
    const post = jest.fn(async (_url: string, form: FormData, options: any) => {
        expect((form.get("imgFile") as File).name).toBe("original.zip");
        expect(options.signal).toBeInstanceOf(AbortSignal);
        options.onUploadProgress({ loaded: 2, total: 4 });
        return { data: { error: 0, data: { url: "/attached/hash.zip" } } };
    });
    act(() =>
        root.render(
            <ResourceDragger axiosInstance={{ post } as any} type="archive" onSuccess={success} onProgress={progress} />
        )
    );
    await choose();
    expect(post.mock.calls[0][0]).toBe("/api/admin/upload?dir=archive");
    expect(progress).toHaveBeenCalledWith(50);
    expect(success).toHaveBeenCalledWith({ data: { url: "https://backend.example/attached/hash.zip" } });
});

it("reports business failures without selecting a file", async () => {
    const success = jest.fn();
    const error = jest.fn();
    const post = jest.fn(async () => ({ data: { error: 9001, message: "Session expired" } }));
    act(() => root.render(<ResourceDragger axiosInstance={{ post } as any} onSuccess={success} onError={error} />));
    await choose();
    expect(success).not.toHaveBeenCalled();
    expect(error).toHaveBeenCalledWith(new Error("Session expired"));
});

it("aborts on unmount and suppresses a late successful response", async () => {
    const success = jest.fn();
    let finish: (value: any) => void = jest.fn();
    let signal: AbortSignal;
    const post = jest.fn((_url: string, _form: FormData, options: any) => {
        signal = options.signal;
        return new Promise((resolve) => {
            finish = resolve;
        });
    });
    act(() => root.render(<ResourceDragger axiosInstance={{ post } as any} onSuccess={success} />));
    await choose();
    act(() => root.render(<></>));
    expect(signal!.aborted).toBe(true);
    await act(async () => finish({ data: { data: { url: "/attached/late.zip" } } }));
    expect(success).not.toHaveBeenCalled();
});
