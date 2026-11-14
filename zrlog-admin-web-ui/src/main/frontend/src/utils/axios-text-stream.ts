import axios, { AxiosAdapter, AxiosError, AxiosInstance, CanceledError, GenericAbortSignal } from "axios";

declare module "axios" {
    interface AxiosRequestConfig {
        /** Accumulated response text, delivered at most once per animation frame. */
        onTextProgress?: (text: string) => void;
    }
}

const readText = async (
    stream: ReadableStream<Uint8Array> | null,
    signal?: GenericAbortSignal,
    onText?: (text: string) => void
): Promise<string> => {
    if (!stream) return "";
    const reader = stream.getReader();
    const decoder = new TextDecoder();
    let text = "";
    let deliveredLength = 0;
    let frame: number | undefined;
    let callbackError: unknown;
    let callbackFailed = false;
    let completed = false;
    const cancelFrame = () => {
        if (frame !== undefined) cancelAnimationFrame(frame);
        frame = undefined;
    };
    const publish = () => {
        cancelFrame();
        if (!signal?.aborted && !callbackFailed && text.length !== deliveredLength) {
            deliveredLength = text.length;
            try {
                onText?.(text);
            } catch (error) {
                callbackFailed = true;
                callbackError = error;
                void reader.cancel().catch(() => undefined);
            }
        }
    };
    const abort = () => {
        cancelFrame();
        void reader.cancel().catch(() => undefined);
    };
    signal?.addEventListener?.("abort", abort);
    try {
        for (;;) {
            if (signal?.aborted) throw new CanceledError();
            const { done, value } = await reader.read();
            if (signal?.aborted) throw new CanceledError();
            if (callbackFailed) throw callbackError;
            text += done ? decoder.decode() : decoder.decode(value, { stream: true });
            if (done) {
                completed = true;
                publish();
                if (callbackFailed) throw callbackError;
                return text;
            }
            if (onText && frame === undefined && text.length !== deliveredLength) {
                frame = requestAnimationFrame(publish);
            }
        }
    } finally {
        // Preserve complete events already received before a connection failure, including approvals.
        publish();
        signal?.removeEventListener?.("abort", abort);
        if (!completed) {
            try {
                await reader.cancel();
            } catch {
                // Keep the original transport or callback error.
            }
        }
        reader.releaseLock();
    }
};

const textStreamAdapter: AxiosAdapter = async (config) => {
    const fetchAdapter = axios.getAdapter("fetch");
    let response;
    try {
        response = await fetchAdapter({ ...config, responseType: "stream", onDownloadProgress: undefined });
    } catch (error) {
        if (axios.isAxiosError(error) && error.response?.data?.getReader) {
            // HTTP errors must reach the existing interceptors with their backend message intact.
            try {
                error.response.data = await readText(error.response.data, config.signal);
            } catch {
                error.response.data = "";
            }
            error.config = config;
            error.response.config = config;
        }
        throw error;
    }
    // Resolve only when the stream ends, preserving the normal Axios text-response contract.
    try {
        const data = await readText(response.data, config.signal, config.onTextProgress);
        return { ...response, config, data };
    } catch (error) {
        throw AxiosError.from(error, undefined, config, response.request);
    }
};

/** Opt-in streaming for Axios callers; regular requests keep their existing adapter. */
export const enableAxiosTextStreaming = (client: AxiosInstance) =>
    client.interceptors.request.use((config) => {
        if (config.onTextProgress) {
            config.adapter = textStreamAdapter;
            config.responseType = "text";
        }
        return config;
    });
