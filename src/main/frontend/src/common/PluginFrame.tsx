import { CSSProperties, FunctionComponent, useEffect, useMemo, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { navigateToPluginAdminRoute, resolvePluginAdminRoute } from "./plugin-admin-route";

type PluginFrameBridgeMessage = {
    source?: string;
    type?: string;
    route?: string;
    path?: string;
    url?: string;
    replace?: boolean;
    openAdminRoute?: unknown;
};

const BRIDGE_SOURCE = "zrlog-plugin";
const NAVIGATE_MESSAGE_TYPES = new Set(["zrlog-admin:navigate", "admin:navigate", "navigate"]);

const parseMessage = (data: unknown): PluginFrameBridgeMessage | undefined => {
    if (typeof data === "string") {
        try {
            return parseMessage(JSON.parse(data));
        } catch {
            return undefined;
        }
    }
    if (!data || typeof data !== "object") {
        return undefined;
    }
    return data as PluginFrameBridgeMessage;
};

const originOf = (src: string) => {
    try {
        return new URL(src, window.location.href).origin;
    } catch {
        return "";
    }
};

const resolveBridgeRoute = (message: PluginFrameBridgeMessage) => {
    if (message.source !== BRIDGE_SOURCE || !message.type || !NAVIGATE_MESSAGE_TYPES.has(message.type)) {
        return undefined;
    }
    return resolvePluginAdminRoute(message.openAdminRoute || message);
};

const PluginFrame: FunctionComponent<{
    title: string;
    src: string;
    height: number | string;
    minHeight?: number | string;
    style?: CSSProperties;
    enableAdminRouteBridge?: boolean;
}> = ({ title, src, height, minHeight, style, enableAdminRouteBridge = true }) => {
    const navigate = useNavigate();
    const frameRef = useRef<HTMLIFrameElement>(null);
    const allowedOrigin = useMemo(() => originOf(src), [src]);

    useEffect(() => {
        if (!enableAdminRouteBridge) {
            return;
        }
        const onMessage = (event: MessageEvent) => {
            const frameWindow = frameRef.current?.contentWindow;
            if (!frameWindow || event.source !== frameWindow) {
                return;
            }
            if (allowedOrigin && event.origin !== allowedOrigin) {
                return;
            }
            const route = resolveBridgeRoute(parseMessage(event.data) || {});
            if (route) {
                navigateToPluginAdminRoute(navigate, route);
            }
        };
        window.addEventListener("message", onMessage);
        return () => window.removeEventListener("message", onMessage);
    }, [allowedOrigin, enableAdminRouteBridge, navigate]);

    return (
        <iframe
            ref={frameRef}
            title={title}
            style={{ border: 0, width: "100%", height, minHeight, display: "block", ...style }}
            src={src}
        />
    );
};

export default PluginFrame;
