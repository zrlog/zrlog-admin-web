import {useCallback, useEffect, useRef, useState} from "react";
import {useAxiosBaseInstance} from "../base/AppBase";
import {getSsDate} from "../base/SsData";
import type {ApiResponse, MessageCenterNotice} from "../type";
import {syncMessageCenterNotices} from "../utils/message-center-notice-sync";

export function useAssistantNotices(session: string, enabled: boolean) {
    const api = useAxiosBaseInstance();
    const generation = useRef(0);
    const pending = useRef(false);
    const refreshPending = useRef(false);
    const [loading, setLoading] = useState(false);
    const [hasUnread, setHasUnread] = useState(false);
    const refresh = useCallback((force = false) => {
        if (!enabled || session !== getSsDate().key) return;
        if (pending.current) {if (force) refreshPending.current = true; return;}
        const requestGeneration = generation.current;
        pending.current = true;
        setLoading(true);
        void api.get<ApiResponse<MessageCenterNotice[]>>("/api/admin/assistant/notices", {showError: false} as any)
            .then(({data}) => {
                if (requestGeneration !== generation.current || session !== getSsDate().key || data.error) return;
                syncMessageCenterNotices(data.data || [], true);
                setHasUnread((data.data || []).length > 0);
            }).catch(() => undefined).finally(() => {
                if (requestGeneration !== generation.current) return;
                pending.current = false;
                setLoading(false);
                if (refreshPending.current) {refreshPending.current = false; refresh();}
            });
    }, [api, enabled, session]);
    useEffect(() => {
        generation.current++;
        pending.current = false;
        refreshPending.current = false;
        setHasUnread(false);
        setLoading(false);
        syncMessageCenterNotices([], true);
        if (enabled) refresh();
        const timer = enabled ? window.setInterval(() => {
            if (document.visibilityState === "visible") refresh();
        }, 30000) : undefined;
        return () => {
            generation.current++;
            window.clearInterval(timer);
            syncMessageCenterNotices([], true);
        };
    }, [enabled, refresh]);
    return {refresh, loading, hasUnread};
}
