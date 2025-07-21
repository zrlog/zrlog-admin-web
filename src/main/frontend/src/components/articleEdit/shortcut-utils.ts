type ShortcutOptions = {
    ctrlOrCmd?: boolean;
    alt?: boolean;
    shift?: boolean;
    key: string;
};

const isClient = typeof window !== "undefined";

export const isTouchLikeDevice = () => {
    if (!isClient) {
        return false;
    }
    if ("maxTouchPoints" in navigator && navigator.maxTouchPoints > 0) {
        return true;
    }
    return window.matchMedia("(pointer: coarse)").matches;
};

export const isMacLikeDevice = () => {
    if (typeof navigator === "undefined") {
        return false;
    }
    return /Mac|iPhone|iPad|iPod/i.test(navigator.userAgent);
};

export const formatShortcutLabel = ({ ctrlOrCmd, alt, shift, key }: ShortcutOptions) => {
    const parts: string[] = [];
    if (ctrlOrCmd) {
        parts.push(isMacLikeDevice() ? "Cmd" : "Ctrl");
    }
    if (alt) {
        parts.push("Alt");
    }
    if (shift) {
        parts.push("Shift");
    }
    parts.push(key);
    return parts.join(" + ");
};

export const getShortcutTitle = (title: string, shortcut?: ShortcutOptions) => {
    if (!shortcut || isTouchLikeDevice()) {
        return title;
    }
    return `${title} (${formatShortcutLabel(shortcut)})`;
};
