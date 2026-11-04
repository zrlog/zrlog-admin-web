const isRecord = (value: unknown): value is Record<string, unknown> =>
    value !== null && typeof value === "object";

export class ApiResponseError extends Error {}

export const readBlobMessage = (blob: Blob): Promise<string | undefined> =>
    new Promise((resolve) => {
        const reader = new FileReader();
        reader.onload = () => resolve(getResponseMessage(reader.result));
        reader.onerror = () => resolve(undefined);
        reader.onabort = reader.onerror;
        reader.readAsText(blob);
    });

/** API messages are localized by the server; keep their wording intact. */
export const getResponseMessage = (data: unknown): string | undefined => {
    if (typeof data === "string") {
        try {
            data = JSON.parse(data);
        } catch {
            return undefined;
        }
    }
    return isRecord(data) && typeof data.message === "string" && data.message.trim()
        ? data.message
        : undefined;
};

export const getErrorMessage = (error: unknown, fallback: string): string => {
    if (isRecord(error) && isRecord(error.response)) {
        return getResponseMessage(error.response.data) || fallback;
    }
    if (isRecord(error) && error.isAxiosError) return fallback;
    return getResponseMessage(error) || fallback;
};

export const readResponseError = async (response: Response, fallback: string): Promise<Error> => {
    try {
        return new Error(getResponseMessage(await response.json()) || fallback);
    } catch {
        return new Error(fallback);
    }
};
