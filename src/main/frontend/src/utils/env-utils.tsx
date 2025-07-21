// Function to check if the page is running as a PWA
export const isPWA = (): boolean => {
    //@ts-ignore
    if (window.navigator.standalone) {
        return true;
    }
    return window.matchMedia("(display-mode: standalone)").matches;
};

export const isOffline = () => {
    return !navigator.onLine;
};
