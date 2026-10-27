import { act } from "react";
import { createRoot, Root } from "react-dom/client";
import { getRes } from "../../utils/constants";
import { afterEach, beforeEach, expect, it, jest } from "@jest/globals";
import FileManagerPicker from "./picker";
import type { FileEntry } from "./shared";

jest.mock("antd-style", () => ({ useTheme: () => ({}) }));
jest.mock("../../base/AppBase", () => ({ useAxiosBaseInstance: () => mockClient }));
jest.mock("../../common/BackendImage", () => ({ __esModule: true, default: () => null }));
jest.mock("../../utils/constants", () => ({
    ...require("@jest/globals").jest.requireActual("../../utils/constants"),
    tryAppendBackendServerUrl: (path: string) => `https://backend.example/blog${path}`,
}));
const mockClient = { get: jest.fn<Promise<any>, []>().mockResolvedValue({ data: { data: { entries: [] } } }) };
const photo: FileEntry = {
    name: "photo.png",
    path: "/attached/photo.png",
    type: "file",
    size: 6,
    mimeType: "image/png",
    lastModified: 0,
    image: true,
    actions: ["SELECT"],
};
const folder: FileEntry = {
    ...photo,
    name: "folder",
    path: "/attached/folder",
    type: "directory",
    image: false,
    actions: ["OPEN"],
};
const entries = [photo, { ...photo, name: "animation.gif", path: "/attached/animation.gif" }, folder];
const selected = jest.fn();
let root: Root;
let container: HTMLDivElement;
beforeEach(() => {
    (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
    (globalThis as any).MessageChannel = class {
        port1 = { onmessage: jest.fn(), close: jest.fn() };
        port2 = { postMessage: () => setTimeout(() => this.port1.onmessage(), 0), close: jest.fn() };
    };
    global.ResizeObserver = class {
        observe = jest.fn();
        unobserve = jest.fn();
        disconnect = jest.fn();
    };
    window.matchMedia = jest.fn(() => ({ matches: false, addListener: jest.fn(), removeListener: jest.fn() })) as any;
    selected.mockClear();
    mockClient.get.mockClear();
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
});
afterEach(() => {
    act(() => root.unmount());
    container.remove();
    delete (globalThis as any).IS_REACT_ACT_ENVIRONMENT;
});
const render = (disabled = false, extensions?: string[]) =>
    act(() =>
        root.render(
            <FileManagerPicker
                data={{ entries, shortcuts: [] } as any}
                onlyImage
                acceptExtensions={extensions}
                disabled={disabled}
                onSelectFile={selected}
            />
        )
    );

it("keeps resolved URL callbacks compatible and includes raw metadata in grid and list modes", () => {
    render();
    act(() => (container.querySelector('button[aria-label="photo.png"]') as HTMLElement).click());
    const toggle = Array.from(container.querySelectorAll("button")).find(
        (button) => button.textContent === getRes().fileManager.view.list
    )!;
    act(() => toggle.click());
    act(() => (container.querySelector('button[aria-label="photo.png"]') as HTMLElement).click());
    expect(selected).toHaveBeenCalledTimes(2);
    expect(selected).toHaveBeenLastCalledWith("https://backend.example/blog/attached/photo.png", photo);
    expect(container.textContent).toContain("animation.gif");
});

it("filters accepted image extensions while retaining folders and disables selection during download", () => {
    render(true, [".png"]);
    expect(container.textContent).not.toContain("animation.gif");
    expect(container.textContent).toContain("folder");
    for (const item of Array.from(
        container.querySelectorAll<HTMLButtonElement>('button[aria-label="photo.png"], button[aria-label="folder"]')
    )) {
        expect(item.disabled).toBe(true);
        act(() => item.click());
    }
    expect(selected).not.toHaveBeenCalled();
    expect(mockClient.get).not.toHaveBeenCalled();
});
