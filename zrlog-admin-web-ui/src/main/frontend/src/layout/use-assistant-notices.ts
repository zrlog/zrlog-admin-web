import { useCallback, useEffect, useRef, useState } from "react";
import { useAxiosBaseInstance } from "../base/AppBase";
import { getSsDate } from "../base/SsData";
import type { ApiResponse, MessageCenterNotice } from "../type";
import { syncMessageCenterNotices } from "../utils/message-center-notice-sync";
import { syncAssistantUnreadStatus } from "../utils/message-center-status";

export function useAssistantNotices(session: string, enabled: boolean) {
    const api = useAxiosBaseInstance();
    const pending = useRef<AbortController>();
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        setLoading(false);
        return () => {
            pending.current?.abort();
            pending.current = undefined;
            syncMessageCenterNotices([], "account");
        };
    }, [session, enabled]);

    const refresh = useCallback(async () => {
        if (!enabled || session !== getSsDate().key || pending.current) return;
        const request = new AbortController();
        pending.current = request;
        setLoading(true);
        try {
            const { data } = await api.get<ApiResponse<MessageCenterNotice[]>>("/api/admin/assistant/notices", {
                signal: request.signal,
            });
            if (request.signal.aborted || session !== getSsDate().key || data.error) return;
            const notices = data.data || [];
            syncMessageCenterNotices(notices, "account");
            syncAssistantUnreadStatus(notices.length > 0 ? getSsDate().user?.userId : undefined);
        } catch {
            // The shared API client displays the error for this user-initiated request.
        } finally {
            if (pending.current === request) {
                pending.current = undefined;
                setLoading(false);
            }
        }
    }, [api, enabled, session]);
    return { refresh, loading };
}
