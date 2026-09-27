type NavigatorWithStandalone = Navigator & {
    standalone?: boolean;
};

export const isPWA = (): boolean => {
    const navigatorWithStandalone = window.navigator as NavigatorWithStandalone;
    if (navigatorWithStandalone.standalone) {
        return true;
    }
    return window.matchMedia("(display-mode: standalone)").matches;
};

export const isOffline = () => {
    return !navigator.onLine;
};
