import type {MockedFunction} from "jest-mock";
import {act, useState} from "react";
import {createRoot, Root} from "react-dom/client";
import {Simulate} from "react-dom/test-utils";
import {afterEach, beforeEach, expect, it, jest} from "@jest/globals";
import {ConfigProvider, Drawer} from "antd";
import {AiFileCapabilities, AiFileList, AiFilePicker, encodeAiFiles, rememberAiFilePreviews,} from "./article-ai-files";
import type {AxiosInstance} from "axios";
import {getRes} from "../../../utils/constants";
import {hasAction} from "../../../utils/account-access";
import {isModuleEnabled} from "../../../utils/module-capabilities";

jest.mock("../../../utils/account-access", () => ({hasAction: require("@jest/globals").jest.fn()}));
jest.mock("../../../utils/module-capabilities", () => ({isModuleEnabled: require("@jest/globals").jest.fn()}));
jest.mock("./article-ai-asset-picker", () => ({
    __esModule: true,
    default: ({onSelect, onClose, maxBytes, extensions}: any) => (
        <>
            <button
                data-testid="asset-image"
                data-max-bytes={maxBytes}
                data-extensions={extensions.join(",")}
                onClick={() => onSelect(new File(["pixels"], "library.png", {type: "image/png"}))}
            >
                Select image
            </button>
            <button data-testid="cancel-assets" onClick={onClose}>
                Cancel assets
            </button>
        </>
    ),
}));

const capabilities: AiFileCapabilities = {
    extensions: [".png", ".jpg", ".jpeg", ".webp", ".pdf", ".txt"],
    maxFiles: 4,
    maxFileBytes: 4 * 1024 * 1024,
    maxTotalBytes: 8 * 1024 * 1024,
};
const originalCreate = URL.createObjectURL;
const originalRevoke = URL.revokeObjectURL;
let root: Root;
let container: HTMLDivElement;
let selected: File[];
const previewUrls: string[] = [];
const res = () => getRes().articleEdit.files;

function Composer({disabled = false, limits = capabilities}: { disabled?: boolean; limits?: AiFileCapabilities }) {
    const [files, setFiles] = useState<File[]>([]);
    selected = files;
    return (
        <ConfigProvider theme={{token: {motion: false}}}>
            <AiFilePicker capabilities={limits} files={files} disabled={disabled} onChange={setFiles}>
                <textarea aria-label="Message"/>
            </AiFilePicker>
        </ConfigProvider>
    );
}

const render = (disabled = false, limits = capabilities) =>
    act(() => root.render(<Composer disabled={disabled} limits={limits}/>));
const textarea = () => container.querySelector("textarea")!;
const choose = (...files: File[]) =>
    act(() => Simulate.change(container.querySelector('input[type="file"]')!, {target: {files, value: ""}} as any));
const paste = (files: File[], text = "") => {
    const preventDefault = jest.fn();
    const stopPropagation = jest.fn();
    act(() =>
        Simulate.paste(textarea(), {
            clipboardData: {files, getData: () => text},
            preventDefault,
            stopPropagation,
        } as any)
    );
    return {preventDefault, stopPropagation};
};
const drop = (files: File[]) => {
    const preventDefault = jest.fn();
    const dataTransfer = {files, types: ["Files"], dropEffect: "none"};
    act(() => Simulate.dragEnter(textarea(), {dataTransfer} as any));
    act(() => Simulate.dragOver(textarea(), {dataTransfer, preventDefault} as any));
    act(() => Simulate.drop(textarea(), {dataTransfer, preventDefault} as any));
    return {preventDefault, dataTransfer};
};

beforeEach(() => {
    (hasAction as MockedFunction<typeof hasAction>).mockReturnValue(true);
    (isModuleEnabled as MockedFunction<typeof isModuleEnabled>).mockReturnValue(true);
    (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
    window.matchMedia = jest.fn(() => ({matches: false, addListener: jest.fn(), removeListener: jest.fn()})) as any;
    global.ResizeObserver = class {
        observe = jest.fn();
        unobserve = jest.fn();
        disconnect = jest.fn();
    };
    const computedStyle = window.getComputedStyle;
    jest.spyOn(window, "getComputedStyle").mockImplementation((element) => computedStyle(element));
    previewUrls.length = 0;
    URL.createObjectURL = jest.fn(() => {
        const url = `blob:preview-${previewUrls.length}`;
        previewUrls.push(url);
        return url;
    });
    URL.revokeObjectURL = jest.fn();
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
});
afterEach(() => {
    act(() => root.unmount());
    container.remove();
    URL.createObjectURL = originalCreate;
    URL.revokeObjectURL = originalRevoke;
    jest.restoreAllMocks();
    delete (globalThis as any).IS_REACT_ACT_ENVIRONMENT;
});

it("accepts pasted screenshots and dropped documents in the same composer without navigating away", () => {
    render();
    const image = new File(["pixels"], "screenshot.png", {type: "image/png"});
    const pdf = new File(["%PDF-document"], "report.pdf");
    expect(paste([image]).preventDefault).toHaveBeenCalled();
    const dropped = drop([pdf]);
    expect(dropped.preventDefault).toHaveBeenCalled();
    expect(dropped.dataTransfer.dropEffect).toBe("copy");
    expect(selected).toEqual([image, pdf]);
    expect(container.querySelector(`button[aria-label="${res().preview} screenshot.png"]`)).not.toBeNull();
    expect(container.textContent).toContain("report.pdf");
    expect(container.textContent).not.toContain(res().dropHint);
});

it("keeps ordinary and mixed clipboard text available to the textarea", () => {
    render();
    expect(paste([], "Keep this text").preventDefault).not.toHaveBeenCalled();
    const image = new File(["pixels"], "photo.webp");
    expect(paste([image], "Describe this photo").preventDefault).not.toHaveBeenCalled();
    expect(selected).toEqual([image]);
});

it("previews selected images and releases preview URLs after removal and unmount", async () => {
    render();
    choose(new File(["image"], "photo.JPEG"));
    expect(previewUrls).toHaveLength(1);
    const preview = container.querySelector(`button[aria-label="${res().preview} photo.JPEG"]`) as HTMLButtonElement;
    await act(async () => preview.click());
    expect(document.body.querySelector(".ant-image-preview")).not.toBeNull();
    const close = document.body.querySelector(".ant-image-preview-close") as HTMLElement;
    await act(async () => close.click());
    act(() =>
        (container.querySelector(`button[aria-label="${res().remove} photo.JPEG"]`) as HTMLButtonElement).click()
    );
    expect(URL.revokeObjectURL).toHaveBeenCalledWith(previewUrls[0]);
    choose(new File(["image"], "next.png"));
    act(() => root.render(<div/>));
    expect(URL.revokeObjectURL).toHaveBeenCalledWith(previewUrls[1]);
});

it("preserves selected attachments when a paste or drop fails validation", () => {
    render();
    const file = new File(["notes"], "notes.txt");
    choose(file);
    paste([new File(["<svg/>"], "unsafe.svg")]);
    expect(container.textContent).toContain(res().unsupported);
    expect(selected).toEqual([file]);
    drop([new File(["x".repeat(capabilities.maxFileBytes + 1)], "large.png")]);
    expect(container.textContent).toContain(res().sizeLimit);
    expect(selected).toEqual([file]);
    paste(Array.from({length: 4}, (_, i) => new File(["x"], `${i}.jpg`)));
    expect(container.textContent).toContain(res().countLimit);
    expect(selected).toEqual([file]);
});

it("rejects attachments when disabled or unsupported while allowing text input", () => {
    render(true);
    const image = new File(["image"], "photo.png");
    paste([image]);
    drop([image]);
    choose(image);
    expect(selected).toEqual([]);
    render(false, {...capabilities, extensions: []});
    paste([image]);
    expect(container.textContent).toContain(res().modelUnsupported);
    expect(selected).toEqual([]);
    expect(paste([], "Text is still usable").preventDefault).not.toHaveBeenCalled();
});

it("keeps server-provided limits and formats inside the attachment menu", async () => {
    render(false, {...capabilities, maxFiles: 2, maxFileBytes: 1024 * 1024, maxTotalBytes: 2 * 1024 * 1024});
    expect(container.querySelector("details")).toBeNull();
    await openAttachmentMenu();
    const details = document.body.querySelector("details")!;
    expect(details.open).toBe(false);
    expect(details.querySelector("summary")!.textContent).toBe(res().requirements);
    expect(details.textContent).toContain("1 MiB");
    expect(details.textContent).toContain(".webp");
    expect(container.querySelector('input[type="file"]')!.getAttribute("accept")).toContain(".png");
});

it("encodes image bytes unchanged and reports file read failures", async () => {
    const bytes = new Uint8Array([137, 80, 78, 71, 0, 255]);
    const file = new File([bytes], "photo.png", {type: "image/png"});
    expect(await encodeAiFiles([file])).toEqual([{name: "photo.png", data: Buffer.from(bytes).toString("base64")}]);
    jest.spyOn(FileReader.prototype, "readAsDataURL").mockImplementation(function (this: FileReader) {
        this.dispatchEvent(new ProgressEvent("error"));
    });
    await expect(encodeAiFiles([file])).rejects.toThrow(res().readFailed);
});

const attachmentButton = () => document.body.querySelector(`button[aria-label="${res().add}"]`) as HTMLButtonElement;
const openAttachmentMenu = () => act(async () => attachmentButton().click());
const assetsMenuItem = () =>
    Array.from(document.body.querySelectorAll<HTMLElement>('[role="menuitem"]')).find(
        (item) => item.textContent === getRes().articleEdit.actions.chooseFromAssets
    );
const openAssets = async () => {
    await openAttachmentMenu();
    await act(async () => assetsMenuItem()!.click());
};

it("adds a library image to existing attachments and passes the remaining allowance", async () => {
    render(false, {...capabilities, maxFileBytes: 10, maxTotalBytes: 10});
    const text = new File(["note"], "notes.txt");
    choose(text);
    await openAssets();
    const image = container.querySelector('[data-testid="asset-image"]') as HTMLButtonElement;
    expect(image.dataset.maxBytes).toBe("6");
    expect(image.dataset.extensions).toBe(".png,.jpg,.jpeg,.webp");
    await act(async () => image.click());
    expect(selected.map((file) => file.name)).toEqual(["notes.txt", "library.png"]);
    expect(container.querySelector('[data-testid="asset-image"]')).toBeNull();
    expect(container.querySelector(`button[aria-label="${res().preview} library.png"]`)).not.toBeNull();
    expect(attachmentButton().disabled).toBe(true);
});

it("hides library access without permission or the assets module and disables it for unsupported models", async () => {
    (hasAction as MockedFunction<typeof hasAction>).mockReturnValue(false);
    await act(async () => render());
    await openAttachmentMenu();
    expect(assetsMenuItem()).toBeUndefined();
    (hasAction as MockedFunction<typeof hasAction>).mockReturnValue(true);
    (isModuleEnabled as MockedFunction<typeof isModuleEnabled>).mockReturnValue(false);
    await act(async () => render());
    expect(assetsMenuItem()).toBeUndefined();
    (isModuleEnabled as MockedFunction<typeof isModuleEnabled>).mockReturnValue(true);
    await act(async () => render(false, {...capabilities, extensions: [".txt"]}));
    expect(assetsMenuItem()!.getAttribute("aria-disabled")).toBe("true");
    await act(async () => render(true));
    expect(attachmentButton().disabled).toBe(true);
});

it("returns keyboard focus to the attachment button when the picker closes", async () => {
    render();
    await openAssets();
    const cancel = container.querySelector('[data-testid="cancel-assets"]') as HTMLButtonElement;
    act(() => cancel.focus());
    await act(async () => cancel.click());
    expect(document.activeElement).toBe(attachmentButton());
    expect(container.querySelector('[data-testid="asset-image"]')).toBeNull();
});

it("closes only the attachment menu on Escape and keeps the assistant drawer open", async () => {
    const closeDrawer = jest.fn();
    await act(async () =>
        root.render(
            <ConfigProvider theme={{token: {motion: false}}}>
                <Drawer open onClose={closeDrawer}>
                    <Composer/>
                </Drawer>
            </ConfigProvider>
        )
    );
    await openAttachmentMenu();
    const summary = document.body.querySelector("summary")!;
    await act(async () => {
        summary.focus();
        summary.dispatchEvent(new KeyboardEvent("keydown", {key: "Escape", keyCode: 27, bubbles: true}));
    });
    expect(closeDrawer).not.toHaveBeenCalled();
    expect(attachmentButton().getAttribute("aria-expanded")).toBe("false");
    expect(document.activeElement).toBe(attachmentButton());
});

it("restores a sent image through the authenticated article endpoint and releases its URL", async () => {
    const get = jest
        .fn<Promise<{ data: Blob }>, any[]>()
        .mockResolvedValue({data: new Blob(["image"], {type: "image/png"})});
    const api = {get} as unknown as AxiosInstance;
    const files = [{id: "saved-image", name: "sent.png", size: 5}];
    await act(async () => root.render(<AiFileList files={files} previewContext={{api, articleId: 7}}/>));
    expect(get).toHaveBeenCalledWith(
        "/api/admin/article/ai/file",
        expect.objectContaining({
            params: {id: 7, fileId: "saved-image"},
            responseType: "blob",
            showError: false,
        })
    );
    expect(container.querySelector(`button[aria-label="${res().preview} sent.png"]`)).not.toBeNull();
    act(() => root.render(<div/>));
    expect(get.mock.calls[0][1].signal.aborted).toBe(true);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith(previewUrls[0]);
});

it("retains local image previews after sending without putting bytes in message metadata", async () => {
    const file = new File(["image"], "sent.png", {type: "image/png"});
    const files = rememberAiFilePreviews([{id: "saved-image", name: file.name, size: file.size}], [file]);
    const get = jest.fn();
    await act(async () =>
        root.render(
            <AiFileList files={files} previewContext={{api: {get} as unknown as AxiosInstance, articleId: 7}}/>
        )
    );
    expect(container.querySelector(`button[aria-label="${res().preview} sent.png"]`)).not.toBeNull();
    expect(get).not.toHaveBeenCalled();
    expect(JSON.stringify(files)).toBe('[{"id":"saved-image","name":"sent.png","size":5}]');
});

it("keeps attachment details and allows retry after an expired session or failed image response", async () => {
    const get = jest
        .fn<Promise<{ data: Blob }>, any[]>()
        .mockResolvedValueOnce({data: new Blob(['{"error":401}'], {type: "application/json"})})
        .mockResolvedValueOnce({data: new Blob(["image"], {type: "image/png"})});
    await act(async () =>
        root.render(
            <AiFileList
                files={[{id: "saved-image", name: "sent.png", size: 5}]}
                previewContext={{api: {get} as unknown as AxiosInstance, articleId: 0}}
            />
        )
    );
    expect(container.textContent).toContain("sent.png");
    expect(URL.createObjectURL).not.toHaveBeenCalled();
    const retry = Array.from(container.querySelectorAll("button")).find(
        (button) => button.textContent === res().retryPreview
    )!;
    await act(async () => retry.click());
    expect(container.querySelector(`button[aria-label="${res().preview} sent.png"]`)).not.toBeNull();
});

it("ignores a late image response after leaving the conversation", async () => {
    let finish!: (value: { data: Blob }) => void;
    const get = jest.fn(
        () =>
            new Promise<{ data: Blob }>((resolve) => {
                finish = resolve;
            })
    );
    await act(async () =>
        root.render(
            <AiFileList
                files={[{id: "saved-image", name: "sent.png", size: 5}]}
                previewContext={{api: {get} as unknown as AxiosInstance, articleId: 7}}
            />
        )
    );
    act(() => root.render(<div/>));
    await act(async () => finish({data: new Blob(["image"], {type: "image/png"})}));
    expect(URL.createObjectURL).not.toHaveBeenCalled();
});
