import { getRes } from "./constants";

// Resolve the active language on each render so preference previews take effect immediately.
export const formatDateTime = (value: number | Date): string =>
    new Date(value).toLocaleString(getRes().lang === "en_US" ? "en-US" : "zh-CN");
