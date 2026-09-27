import { NavigateFunction } from "react-router-dom";
import { getRealRouteUrl } from "../utils/constants";

export type PluginAdminRoute =
    | string
    | {
          route?: string;
          path?: string;
          url?: string;
          replace?: boolean;
      };

export type ResolvedPluginAdminRoute = {
    route: string;
    replace?: boolean;
};

const internalRoute = (route: string) => {
    const trimmed = route.trim();
    if (!trimmed.startsWith("/") || trimmed.startsWith("//")) {
        return "";
    }
    if (/^[a-z][a-z0-9+.-]*:/i.test(trimmed)) {
        return "";
    }
    return trimmed;
};

export const resolvePluginAdminRoute = (value: unknown): ResolvedPluginAdminRoute | undefined => {
    if (typeof value === "string") {
        const route = internalRoute(value);
        return route ? { route } : undefined;
    }
    if (!value || typeof value !== "object") {
        return undefined;
    }
    const routeValue = value as Exclude<PluginAdminRoute, string>;
    const candidate =
        typeof routeValue.route === "string"
            ? routeValue.route
            : typeof routeValue.path === "string"
              ? routeValue.path
              : typeof routeValue.url === "string"
                ? routeValue.url
                : "";
    const route = internalRoute(candidate);
    if (!route) {
        return undefined;
    }
    return {
        route,
        replace: routeValue.replace === true,
    };
};

export const navigateToPluginAdminRoute = (navigate: NavigateFunction, value: unknown) => {
    const target = resolvePluginAdminRoute(value);
    if (!target) {
        return false;
    }
    navigate(getRealRouteUrl(target.route), { replace: target.replace === true });
    return true;
};
