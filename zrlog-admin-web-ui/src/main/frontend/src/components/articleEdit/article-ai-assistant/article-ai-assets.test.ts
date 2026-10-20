import {afterEach, beforeEach, expect, it, jest} from "@jest/globals";
import type {AxiosInstance} from "axios";
import type {FileEntry} from "../../file-manager/shared";
import {aiAssetSizeLimit, loadAiAsset} from "./article-ai-assets";
import {getRes} from "../../../utils/constants";

const entry: FileEntry = {
    name: "照片.png",
    path: "/attached/2026/照片.png",
    type: "file",
    size: 6,
    mimeType: "image/png",
    lastModified: 0,
    image: true,
    actions: ["SELECT", "DOWNLOAD"],
};
const get = jest.fn<Promise<any>, any[]>();
const client = {get} as unknown as AxiosInstance;
const fetchImage = jest.fn<Promise<any>, any[]>();
const originalFetch = global.fetch;
const bytes = new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10]);
const signal = () => new AbortController().signal;
const read = (asset = entry, limit = 1024) => loadAiAsset(asset, client, limit, signal());
const response = (chunks = [bytes], headers: Record<string, string> = {}) => {
    const read = jest.fn<Promise<any>, []>();
    chunks.forEach((value) => read.mockResolvedValueOnce({value, done: false}));
    read.mockResolvedValue({done: true});
    const releaseLock = jest.fn();
    return {
        ok: true,
        headers: {get: (name: string) => headers[name] || null},
        body: {getReader: () => ({read, releaseLock})},
        read,
        releaseLock,
    };
};
beforeEach(() => {
    get.mockReset();
    fetchImage.mockReset();
    global.fetch = fetchImage as any;
});
afterEach(() => {
    global.fetch = originalFetch;
});

it("loads local resource bytes through the authenticated download endpoint using the raw path", async () => {
    get.mockResolvedValue({data: new Blob([bytes], {type: "application/json"})});
    const file = await read();
    expect(file.name).toBe(entry.name);
    expect(file.type).toBe("image/png");
    const content = await new Promise<string>((resolve) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.readAsDataURL(file);
    });
    expect(content).toBe(`data:image/png;base64,${Buffer.from(bytes).toString("base64")}`);
    expect(get).toHaveBeenCalledWith(
        "/api/admin/file-manager/download",
        expect.objectContaining({params: {path: entry.path}, responseType: "blob", showError: false})
    );
    expect(fetchImage).not.toHaveBeenCalled();
});

it("checks image bytes rather than trusting response MIME types", async () => {
    get.mockResolvedValueOnce({data: new Blob(["<html>Sign in</html>"], {type: "image/png"})});
    await expect(read()).rejects.toThrow(getRes().articleEdit.files.assetReadFailed);
    get.mockResolvedValueOnce({data: new Blob([new Uint8Array([255, 216, 255, 224, 255, 217])])});
    expect((await read({...entry, name: "photo.JPEG"})).type).toBe("image/jpeg");
    get.mockResolvedValueOnce({data: new Blob(["RIFF", new Uint8Array(4), "WEBP"])});
    expect((await read({...entry, name: "photo.webp"})).type).toBe("image/webp");
    get.mockResolvedValueOnce({data: new Blob([bytes])});
    await expect(read({...entry, name: "photo.jpg"})).rejects.toThrow(getRes().articleEdit.files.assetReadFailed);
});

it("reads external resources without passing the admin client or credentials", async () => {
    const body = response();
    fetchImage.mockResolvedValue(body);
    const file = await read({...entry, path: "https://cdn.example/photo.png"});
    expect(file.size).toBe(bytes.length);
    expect(fetchImage).toHaveBeenCalledWith(
        "https://cdn.example/photo.png",
        expect.objectContaining({credentials: "omit", signal: expect.any(AbortSignal)})
    );
    expect(get).not.toHaveBeenCalled();
    expect(body.releaseLock).toHaveBeenCalled();
});

it("rejects invalid entries, unsupported formats, unauthorized paths and known oversized images before fetching", async () => {
    for (const asset of [
        {...entry, actions: []},
        {...entry, image: false},
        {...entry, name: "vector.svg"},
        {...entry, path: "/attached/../conf/private.png"},
        {...entry, path: "file:///secret.png"},
        {...entry, path: "https://user:password@cdn.example/pic.png"},
        {...entry, size: 2048},
    ])
        await expect(read(asset)).rejects.toThrow();
    expect(get).not.toHaveBeenCalled();
    expect(fetchImage).not.toHaveBeenCalled();
});

it("enforces actual downloaded size and rejects login or error documents", async () => {
    get.mockResolvedValueOnce({data: new Blob([bytes])});
    await expect(read({...entry, size: 0}, 4)).rejects.toThrow(aiAssetSizeLimit(4));
    get.mockResolvedValueOnce({data: new Blob(['{"error":401}'], {type: "application/json"})});
    await expect(read()).rejects.toThrow(getRes().articleEdit.files.assetReadFailed);
    get.mockResolvedValueOnce({data: new Blob([])});
    await expect(read()).rejects.toThrow(getRes().articleEdit.files.assetReadFailed);
});

it("bounds external streams even when content length is missing or misleading", async () => {
    const body = response([bytes, bytes]);
    fetchImage.mockResolvedValue(body);
    await expect(read({...entry, path: "//cdn.example/photo.png", size: 0}, 8)).rejects.toThrow(aiAssetSizeLimit(8));
    expect(fetchImage.mock.calls[0][1].signal.aborted).toBe(true);
    expect(body.releaseLock).toHaveBeenCalled();
    fetchImage.mockResolvedValueOnce(response([bytes], {"content-length": "4096"}));
    await expect(read({...entry, path: "https://cdn.example/photo.png"})).rejects.toThrow(aiAssetSizeLimit(1024));
});

it("cancels local downloads when the picker closes and stops oversized transfers", async () => {
    const controller = new AbortController();
    get.mockImplementationOnce(async (_url, config) => {
        controller.abort();
        expect(config.signal.aborted).toBe(true);
        throw new Error("aborted");
    });
    await expect(loadAiAsset(entry, client, 1024, controller.signal)).rejects.toThrow("aborted");
    get.mockImplementationOnce(async (_url, config) => {
        config.onDownloadProgress({loaded: 2048});
        expect(config.signal.aborted).toBe(true);
        throw new Error("aborted");
    });
    await expect(read()).rejects.toThrow(aiAssetSizeLimit(1024));
});
