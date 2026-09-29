import type { CSSProperties } from "react";
import type { ConfigProviderProps } from "antd";
import type { materialColors } from "./material-colors";

// Public semantic slots keep Material presentation inside the selected theme,
// including portals. Component-level props/styles retain their normal precedence.
export const materialComponentConfig = (c: ReturnType<typeof materialColors>, brand: string): ConfigProviderProps => ({
    switch: {
        classNames: ({ props }) => ({
            root: `admin-m3-switch${props.size === "small" ? " admin-m3-switch-small" : ""}`,
            indicator: "admin-m3-switch-handle",
            content: "admin-m3-switch-content",
        }),
        styles: { root: { "--admin-m3-on-primary": c.onPrimary } as CSSProperties },
    },
    checkbox: {
        classNames: ({ props }) => ({
            root: `admin-m3-checkbox${props.indeterminate ? " admin-m3-checkbox-mixed" : ""}`,
            icon: "admin-m3-checkbox-icon",
        }),
        styles: { root: { "--admin-m3-on-primary": c.onPrimary } as CSSProperties },
    },
    radio: { classNames: { root: "admin-m3-radio", icon: "admin-m3-radio-icon" } },
    segmented: {
        classNames: ({ props }) => ({
            root: `admin-m3-segmented${props.vertical || props.orientation === "vertical" ? " admin-m3-segmented-vertical" : ""}${props.size === "small" ? " admin-m3-segmented-small" : ""}`,
            item: "admin-m3-segment",
            label: "admin-m3-segment-label",
        }),
    },
    tabs: {
        classNames: { item: "admin-m3-tab", indicator: "admin-m3-tab-indicator" },
        indicator: { size: (width) => Math.max(24, width - 32), align: "center" },
        styles: ({ props }) => ({
            indicator: ["start", "end", "left", "right"].includes(props.tabPlacement || props.tabPosition || "top")
                ? { width: 3 } : { height: 3 },
        }),
    },
    tag: {
        classNames: ({ props }) => ({
            root: props.onClick || props.href || props.closable
                ? `admin-m3-chip${props.disabled ? " admin-m3-chip-disabled" : ""}` : "",
            close: "admin-m3-chip-close",
        }),
        styles: ({ props }) => ({
            root: !props.disabled && (props.onClick || props.href || props.closable) &&
                (!props.color || props.color === brand)
                ? { background: c.primaryContainer, color: c.onPrimaryContainer, borderColor: "transparent" } : {},
        }),
    },
    slider: {
        classNames: ({ props }) => ({
            root: `admin-m3-slider${props.disabled ? " admin-m3-slider-disabled" : ""}`,
            handle: "admin-m3-slider-handle",
        }),
    },
    progress: {
        classNames: { root: "admin-m3-progress" },
        styles: ({ props }) => {
            // Keep explicit dimensions, stepped bars and in-track percentages.
            if ((props.type && props.type !== "line") || props.steps || props.strokeWidth ||
                Array.isArray(props.size) || typeof props.size === "number" || props.percentPosition?.type === "inner") {
                return {};
            }
            return { rail: { height: 4 }, track: { height: 4 } };
        },
    },
    button: {
        classNames: ({ props }) => ({
            root: [
                "admin-m3-button",
                props.icon && !props.children && props.shape !== "square" ? "admin-m3-icon-button" : "",
                props.variant === "filled" && !props.danger && (props.color === "primary" || props.color === "default")
                    ? "admin-m3-tonal-button" : "",
            ].filter(Boolean).join(" "),
        }),
    },
    modal: {
        classNames: { container: "admin-m3-dialog", header: "admin-m3-dialog-header", footer: "admin-m3-dialog-footer", close: "admin-m3-close" },
        styles: { title: { paddingInlineEnd: 32 } },
        cancelButtonProps: { type: "text" },
    },
    drawer: {
        // The outer provider's drawer defaults are replaced by this object.
        closable: { placement: "end" },
        classNames: { header: "admin-m3-drawer-header", title: "admin-m3-drawer-title", footer: "admin-m3-drawer-footer", close: "admin-m3-close" },
    },
    dropdown: { classNames: { item: "admin-m3-menu-item" } },
});
