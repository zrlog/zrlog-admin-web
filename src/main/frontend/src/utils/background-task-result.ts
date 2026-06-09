import type {BackgroundTaskFinishStatus} from "./background-task-store";

export type BackgroundTaskResult = {
    status: BackgroundTaskFinishStatus;
    description?: string;
};

type ResultLikeResponse = {
    error?: number | boolean | null;
    success?: boolean | null;
    message?: string | null;
};

type BackgroundTaskResultOptions<T> = {
    warningWhen?: (response: T) => boolean;
    successDescription?: (response: T) => string | undefined;
    warningDescription?: (response: T) => string | undefined;
    errorDescription?: (response: T) => string | undefined;
    fallbackSuccessDescription?: string;
    fallbackWarningDescription?: string;
    fallbackErrorDescription?: string;
};

const isRecord = (value: unknown): value is Record<string, unknown> => {
    return !!value && typeof value === "object";
};

const responseMessage = (response: unknown) => {
    if (!isRecord(response) || typeof response.message !== "string") {
        return undefined;
    }
    return response.message;
};

const responseHasError = (response: unknown) => {
    if (!isRecord(response)) {
        return false;
    }
    const value = response as ResultLikeResponse;
    if (value.success === false) {
        return true;
    }
    if (typeof value.error === "boolean") {
        return value.error;
    }
    if (typeof value.error === "number") {
        return value.error > 0;
    }
    return false;
};

export const buildBackgroundTaskResult = <T>(
    response: T,
    options: BackgroundTaskResultOptions<T> = {}
): BackgroundTaskResult => {
    if (responseHasError(response)) {
        return {
            status: "error",
            description: options.errorDescription?.(response) || responseMessage(response) || options.fallbackErrorDescription,
        };
    }
    if (options.warningWhen?.(response)) {
        return {
            status: "warning",
            description:
                options.warningDescription?.(response) || responseMessage(response) || options.fallbackWarningDescription,
        };
    }
    return {
        status: "success",
        description: options.successDescription?.(response) || responseMessage(response) || options.fallbackSuccessDescription,
    };
};

export const getBackgroundTaskNoticeType = (status: BackgroundTaskFinishStatus): "success" | "warning" | "error" => {
    if (status === "error") {
        return "error";
    }
    if (status === "warning" || status === "cancelled") {
        return "warning";
    }
    return "success";
};
