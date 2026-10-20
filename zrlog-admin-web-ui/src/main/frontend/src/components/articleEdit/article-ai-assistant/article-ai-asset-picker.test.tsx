import type {MockedFunction} from "jest-mock";
import {act} from "react";
import {createRoot, Root} from "react-dom/client";
import {afterEach, beforeEach, expect, it, jest} from "@jest/globals";
import AiAssetPicker from "./article-ai-asset-picker";
import {getRes} from "../../../utils/constants";
import {loadAiAsset} from "./article-ai-assets";

jest.mock("../../../base/AppBase", () => ({useAxiosBaseInstance: () => ({})}));
jest.mock("./article-ai-assets", () => ({
    ...require("@jest/globals").jest.requireActual("./article-ai-assets"),
    loadAiAsset: require("@jest/globals").jest.fn(),
}));
jest.mock("../../file-manager/picker", () => ({
    __esModule: true,
    default: ({onSelectFile, onlyImage, disabled}: any) => (
        <button
            disabled={disabled}
            onClick={() => onSelectFile("/sub/attached/photo.png", {name: "photo.png", path: "/attached/photo.png"})}
        >
            {onlyImage ? "Select image" : "Select file"}
        </button>
    ),
}));
let root: Root;
let container: HTMLDivElement;
const selected = jest.fn();
const closed = jest.fn();
const load = loadAiAsset as MockedFunction<typeof loadAiAsset>;
const click = async (label: string) => {
    await act(async () => {
        const target = Array.from(document.body.querySelectorAll("button")).find(
            (button) => button.textContent === label || button.getAttribute("aria-label") === label
        )!;
        target.click();
    });
};
beforeEach(() => {
    (globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
    window.matchMedia = jest.fn(() => ({matches: false, addListener: jest.fn(), removeListener: jest.fn()})) as any;
    const computedStyle = window.getComputedStyle;
    jest.spyOn(window, "getComputedStyle").mockImplementation((element) => computedStyle(element));
    load.mockReset();
    selected.mockClear();
    closed.mockClear();
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
});
afterEach(() => {
    act(() => root.unmount());
    container.remove();
    jest.restoreAllMocks();
    delete (globalThis as any).IS_REACT_ACT_ENVIRONMENT;
});
const render = () =>
    act(() =>
        root.render(
            <AiAssetPicker
                extensions={[".png", ".jpg", ".webp"]}
                maxBytes={1024}
                onSelect={selected}
                onClose={closed}
            />
        )
    );

it("shows reading status then returns the selected image bytes", async () => {
    let finish!: (file: File) => void;
    load.mockReturnValue(
        new Promise((resolve) => {
            finish = resolve;
        })
    );
    render();
    await click("Select image");
    expect(document.body.textContent).toContain(getRes().articleEdit.files.assetLoading);
    expect(load.mock.calls[0][0].path).toBe("/attached/photo.png");
    expect(load.mock.calls[0][2]).toBe(1024);
    const file = new File(["image"], "photo.png");
    await act(async () => finish(file));
    expect(selected).toHaveBeenCalledWith(file);
});

it("keeps the picker open on failure and allows retry", async () => {
    load.mockRejectedValueOnce(new Error("Network failure"));
    render();
    await click("Select image");
    expect(document.body.textContent).toContain(getRes().articleEdit.files.assetReadFailed);
    expect(selected).not.toHaveBeenCalled();
    load.mockResolvedValueOnce(new File(["image"], "photo.png"));
    await click("Select image");
    expect(selected).toHaveBeenCalledTimes(1);
});

it("ignores a late download after the user cancels", async () => {
    let finish!: (file: File) => void;
    load.mockReturnValue(
        new Promise((resolve) => {
            finish = resolve;
        })
    );
    render();
    await click("Select image");
    await click("Close");
    expect(closed).toHaveBeenCalledTimes(1);
    expect(load.mock.calls[0][3].aborted).toBe(true);
    await act(async () => finish(new File(["image"], "photo.png")));
    expect(selected).not.toHaveBeenCalled();
});
