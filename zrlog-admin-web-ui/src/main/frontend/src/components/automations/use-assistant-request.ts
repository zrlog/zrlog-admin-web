import { useCallback } from "react";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getSsDate } from "../../base/SsData";
import { getRes } from "../../utils/constants";
import type { Request } from "./types";
import { useUiApp } from "@zrlog/ui/feedback";

export function useAssistantRequest(): Request {
    const api = useAxiosBaseInstance();
    const { message } = useUiApp();
    const failed = getRes().automations.failedRequest;
    return useCallback(
        async <T>(path: string, body?: unknown): Promise<T> => {
            const session = getSsDate().key;
            try {
                const url = `/api/admin/assistant${path ? (path.startsWith("?") ? path : "/" + path) : ""}`;
                const response = body === undefined ? await api.get(url) : await api.post(url, body);
                if (session !== getSsDate().key) throw new Error(failed);
                if (response.data.error) throw new Error(response.data.message || failed);
                return response.data.data as T;
            } catch (error) {
                if (session === getSsDate().key) message.error(error instanceof Error ? error.message : failed);
                throw error;
            }
        },
        [api, message, failed]
    );
}
