import type { AxiosInstance, AxiosRequestConfig } from "axios";
import { FileEntry, formatSize } from "../../file-manager/shared";
import { getRes } from "../../../utils/constants";

export const aiAssetSizeLimit = (maxBytes: number) =>
    getRes().articleEdit.files.assetSizeLimit.replace("{size}", formatSize(maxBytes));

const readImageHeader = (blob: Blob): Promise<Uint8Array> =>
    new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(new Uint8Array(reader.result as ArrayBuffer));
        reader.onerror = () => reject(new Error(getRes().articleEdit.files.assetReadFailed));
        reader.onabort = reader.onerror;
        reader.readAsArrayBuffer(blob.slice(0, 12));
    });

// Download bytes from the selected resource, never pass its URL as model input.
export async function loadAiAsset(
    entry: FileEntry,
    client: AxiosInstance,
    maxBytes: number,
    signal: AbortSignal
): Promise<File> {
    const res = getRes().articleEdit.files;
    if (
        entry.type !== "file" ||
        !entry.image ||
        !entry.actions?.includes("SELECT") ||
        !/\.(png|jpe?g|webp)$/i.test(entry.name)
    )
        throw new Error(res.unsupported);
    if (entry.size > maxBytes) throw new Error(aiAssetSizeLimit(maxBytes));
    const controller = new AbortController();
    const abort = () => controller.abort();
    signal.addEventListener("abort", abort, { once: true });
    if (signal.aborted) controller.abort();
    let tooLarge = false;
    const checkSize = (size: number) => {
        if (size > maxBytes) {
            tooLarge = true;
            controller.abort();
            throw new Error(aiAssetSizeLimit(maxBytes));
        }
    };
    try {
        let blob: Blob;
        if (/^(https?:)?\/\//i.test(entry.path)) {
            const url = new URL(entry.path, window.location.href);
            if (!/^https?:$/.test(url.protocol) || url.username || url.password) throw new Error(res.assetReadFailed);
            // External library entries do not receive the admin session or Axios headers.
            const response = await fetch(url.href, { credentials: "omit", signal: controller.signal });
            if (!response.ok || !response.body) throw new Error(res.assetReadFailed);
            checkSize(Number(response.headers.get("content-length")) || 0);
            const reader = response.body.getReader();
            const chunks: Uint8Array[] = [];
            let size = 0;
            try {
                for (;;) {
                    const next = await reader.read();
                    if (next.done) break;
                    checkSize((size += next.value.byteLength));
                    chunks.push(next.value);
                }
            } finally {
                reader.releaseLock();
            }
            blob = new Blob(chunks, { type: response.headers.get("content-type") || "" });
        } else {
            if (
                !entry.path.startsWith("/attached/") ||
                entry.path.includes("\\") ||
                entry.path.split("/").includes("..")
            )
                throw new Error(res.assetReadFailed);
            const requestConfig: AxiosRequestConfig & { showError: boolean } = {
                params: { path: entry.path },
                responseType: "blob",
                signal: controller.signal,
                // The picker owns errors and cancellation, so do not open a global error dialog.
                showError: false,
                onDownloadProgress: ({ loaded }) => {
                    if (loaded > maxBytes) {
                        tooLarge = true;
                        controller.abort();
                    }
                },
            };
            const response = await client.get<Blob>("/api/admin/file-manager/download", requestConfig);
            blob = response.data;
        }
        checkSize(blob.size);
        const extension = entry.name.split(".").pop()!.toLowerCase();
        const mime = extension === "jpg" || extension === "jpeg" ? "image/jpeg" : `image/${extension}`;
        // The existing admin download endpoint can label binary responses as JSON.
        // Validate the bytes so those images work while login/error documents are rejected.
        const header = await readImageHeader(blob);
        const startsWith = (signature: number[], offset = 0) =>
            signature.every((byte, index) => header[offset + index] === byte);
        const valid =
            mime === "image/png"
                ? startsWith([137, 80, 78, 71, 13, 10, 26, 10])
                : mime === "image/jpeg"
                ? startsWith([255, 216, 255])
                : startsWith([82, 73, 70, 70]) && startsWith([87, 69, 66, 80], 8);
        if (!valid) throw new Error(res.assetReadFailed);
        return new File([blob], entry.name, { type: mime });
    } catch (error) {
        if (tooLarge) throw new Error(aiAssetSizeLimit(maxBytes));
        throw error;
    } finally {
        signal.removeEventListener("abort", abort);
    }
}
