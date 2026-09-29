import { useMemo } from "react";
import type { ConfigProviderProps, GetProp } from "antd";
import { materialColors } from "./material-colors";
import { materialSpinIndicator } from "./MaterialSpinIndicator";
import { materialComponentConfig } from "./material-component-config";
import { AppState } from "../../type";
import { themeAlgorithms } from "../../utils/theme-utils";

// One contained ripple; honors reduced motion and uses the actual button foreground.
const showInsetEffect: GetProp<ConfigProviderProps, "wave">["showEffect"] = (node, { event, component }) => {
    if (component !== "Button" || window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const rect = node.getBoundingClientRect();
    const holder = document.createElement("span");
    holder.setAttribute("aria-hidden", "true");
    Object.assign(holder.style, {
        position: "absolute",
        inset: "0",
        borderRadius: "inherit",
        pointerEvents: "none",
        overflow: "hidden",
    });
    const dot = document.createElement("span");
    const size = Math.hypot(rect.width, rect.height) * 2;
    Object.assign(dot.style, {
        position: "absolute",
        borderRadius: "50%",
        background: "currentColor",
        opacity: "0.12",
        width: `${size}px`,
        height: `${size}px`,
        left: `${event.detail === 0 ? rect.width / 2 : event.clientX - rect.left}px`,
        top: `${event.detail === 0 ? rect.height / 2 : event.clientY - rect.top}px`,
        transform: "translate(-50%, -50%) scale(0)",
    });
    holder.appendChild(dot);
    node.appendChild(holder);
    const animation = dot.animate(
        [
            { transform: "translate(-50%, -50%) scale(0)", opacity: 0.12 },
            { transform: "translate(-50%, -50%) scale(1)", opacity: 0 },
        ],
        { duration: 400, easing: "cubic-bezier(0.2, 0, 0, 1)" }
    );
    animation.onfinish = animation.oncancel = () => holder.remove();
};

export const createMuiTheme = (appState: AppState): ConfigProviderProps => {
    const c = materialColors(appState.colorPrimary, appState.dark);
    const field = {
        borderRadius: 4,
        activeBorderColor: c.primary,
        hoverBorderColor: c.onSurface,
        activeShadow: `0 0 0 1px ${c.primary}`,
    };
    // Preserve separate Form labels and all field geometry; only states change.
    const fieldInteraction = {
        ...field,
        hoverBg: c.containerHigh,
        activeBg: c.container,
        errorActiveShadow: `0 0 0 1px ${appState.dark ? "#ffb4ab" : "#b3261e"}`,
        colorBgContainerDisabled: c.containerHigh,
    };
    return {
        ...materialComponentConfig(c, appState.colorPrimary),
        theme: {
            algorithm: themeAlgorithms(appState),
            token: {
                colorPrimary: c.primary,
                colorPrimaryHover: c.primaryHover,
                colorPrimaryActive: c.primaryActive,
                colorPrimaryBg: c.primaryContainer,
                colorPrimaryBgHover: c.pressed,
                colorPrimaryText: c.onPrimaryContainer,
                colorPrimaryTextHover: c.primary,
                colorPrimaryTextActive: c.primary,
                colorLink: c.primary,
                colorLinkHover: c.primary,
                colorLinkActive: c.onPrimaryContainer,
                colorText: c.onSurface,
                colorTextSecondary: c.onSurfaceVariant,
                colorTextTertiary: c.onSurfaceVariant,
                colorError: appState.dark ? "#ffb4ab" : "#b3261e",
                colorErrorText: appState.dark ? "#ffb4ab" : "#b3261e",
                colorErrorBg: appState.dark ? "#570e0a" : "#ffdad6",
                colorErrorBorder: appState.dark ? "#ffb4ab" : "#b3261e",
                colorErrorHover: appState.dark ? "#ffc7c0" : "#8c1d18",
                colorErrorActive: appState.dark ? "#f29c93" : "#7f1913",
                colorBgBase: c.surface,
                colorBgLayout: c.surface,
                colorBgContainer: c.container,
                colorBgElevated: c.containerHigh,
                colorBorder: c.outline,
                colorBorderSecondary: c.outlineVariant,
                colorSplit: c.outlineVariant,
                colorFillAlter: c.containerHigh,
                colorFillSecondary: c.containerHighest,
                colorFillTertiary: c.containerHigh,
                colorFillQuaternary: c.containerHigh,
                controlItemBgHover: c.hover,
                controlItemBgActive: c.primaryContainer,
                controlOutline: c.primary,
                wireframe: false,
                lineHeight: 1.5,
                fontSizeHeading1: 32,
                fontSizeHeading2: 28,
                fontSizeHeading3: 24,
                fontSizeHeading4: 20,
                fontSizeHeading5: 16,
                fontWeightStrong: 500,
                borderRadius: 12,
                borderRadiusSM: 8,
                borderRadiusLG: 24,
                borderRadiusXS: 4,
                controlHeight: 40,
                controlHeightLG: 48,
                controlHeightSM: 32,
                boxShadow: "0 1px 3px rgba(0,0,0,0.16), 0 1px 2px rgba(0,0,0,0.12)",
                boxShadowSecondary: "0 3px 8px rgba(0,0,0,0.16), 0 1px 3px rgba(0,0,0,0.12)",
                boxShadowTertiary: "none",
                motionEaseInOut: "cubic-bezier(0.2, 0, 0, 1)",
            },
            components: {
                Button: {
                    borderRadius: 20,
                    borderRadiusLG: 24,
                    borderRadiusSM: 16,
                    controlHeight: 40,
                    controlHeightLG: 48,
                    controlHeightSM: 32,
                    fontWeight: 500,
                    paddingInline: 24,
                    paddingInlineSM: 16,
                    primaryColor: c.onPrimary,
                    dangerColor: appState.dark ? "#690005" : "#ffffff",
                    primaryShadow: "none",
                    defaultShadow: "none",
                    dangerShadow: "none",
                    defaultBg: "transparent",
                    defaultColor: c.primary,
                    defaultBorderColor: c.outline,
                    defaultHoverBg: c.hover,
                    defaultHoverColor: c.primary,
                    defaultHoverBorderColor: c.primary,
                    defaultActiveBg: c.pressed,
                    defaultActiveColor: c.primary,
                    defaultActiveBorderColor: c.primary,
                    textTextColor: c.primary,
                    textTextHoverColor: c.primary,
                    textTextActiveColor: c.primary,
                    textHoverBg: c.hover,
                },
                Layout: { bodyBg: c.surface, headerBg: c.surface, siderBg: c.surface },
                Input: fieldInteraction,
                InputNumber: {
                    ...fieldInteraction,
                    handleBg: c.containerHigh, handleActiveBg: c.primaryContainer, handleHoverColor: c.primary,
                },
                DatePicker: field,
                Select: {
                    ...field,
                    activeOutlineColor: c.primary,
                    optionSelectedBg: c.primaryContainer,
                    optionSelectedColor: c.onPrimaryContainer,
                    optionActiveBg: c.hover,
                    optionSelectedFontWeight: 500,
                    colorBgContainerDisabled: c.containerHigh,
                },
                Card: {
                    bodyPadding: 24,
                    bodyPaddingSM: 16,
                    headerHeight: 64,
                    headerHeightSM: 48,
                    borderRadiusLG: 24,
                    headerFontSize: 18,
                    headerFontSizeSM: 16,
                    headerBg: "transparent",
                    boxShadowTertiary: "none",
                },
                Menu: {
                    itemBorderRadius: 28,
                    subMenuItemBorderRadius: 16,
                    itemHeight: 48,
                    itemBg: "transparent",
                    itemColor: c.onSurfaceVariant,
                    itemHoverBg: c.hover,
                    itemHoverColor: c.onSurface,
                    itemSelectedBg: c.primaryContainer,
                    itemSelectedColor: c.onPrimaryContainer,
                    darkItemBg: "transparent",
                    darkItemColor: c.onSurfaceVariant,
                    darkItemHoverBg: c.hover,
                    darkItemHoverColor: c.onSurface,
                    darkItemSelectedBg: c.primaryContainer,
                    darkItemSelectedColor: c.onPrimaryContainer,
                    darkSubMenuItemBg: c.containerHigh,
                },
                Modal: {
                    borderRadiusLG: 28, contentBg: c.containerHigh, headerBg: "transparent",
                    titleFontSize: 24, titleLineHeight: 1.33,
                },
                Drawer: { colorBgElevated: c.containerHigh, footerPaddingBlock: 16, footerPaddingInline: 24 },
                Alert: { borderRadiusLG: 12 },
                Table: {
                    headerBg: c.containerHigh,
                    headerColor: c.onSurfaceVariant,
                    headerSplitColor: "transparent",
                    borderColor: c.outlineVariant,
                    rowHoverBg: c.hover,
                    rowSelectedBg: c.primaryContainer,
                    rowSelectedHoverBg: c.primaryContainer,
                    cellPaddingBlock: 16,
                    cellPaddingInline: 16,
                    headerBorderRadius: 12,
                },
                Tabs: {
                    inkBarColor: c.primary, itemSelectedColor: c.primary, itemColor: c.onSurfaceVariant,
                    itemHoverColor: c.primary, itemActiveColor: c.primary,
                    horizontalItemPadding: "16px 0", horizontalItemGutter: 0,
                    horizontalItemPaddingSM: "12px 0", horizontalItemPaddingLG: "20px 0",
                },
                Segmented: {
                    borderRadius: 20,
                    borderRadiusSM: 0,
                    trackPadding: 0,
                    trackBg: "transparent",
                    itemColor: c.onSurface,
                    itemHoverColor: c.onSurface,
                    itemHoverBg: c.hover,
                    itemActiveBg: c.pressed,
                    itemSelectedBg: c.primaryContainer,
                    itemSelectedColor: c.onPrimaryContainer,
                    boxShadowTertiary: "none",
                },
                Tag: { borderRadiusSM: 8, defaultBg: c.containerHigh, defaultColor: c.onSurfaceVariant },
                Switch: {
                    handleBg: "transparent", handleShadow: "none",
                    trackHeight: 32, trackMinWidth: 52, handleSize: 24,
                    trackHeightSM: 24, trackMinWidthSM: 40, handleSizeSM: 16,
                    trackPadding: 2, innerMinMargin: 8, innerMaxMargin: 28,
                    innerMinMarginSM: 6, innerMaxMarginSM: 20,
                },
                Checkbox: {
                    controlInteractiveSize: 18, borderRadiusSM: 2, lineWidth: 2,
                    colorBorder: c.onSurfaceVariant, colorBgContainer: "transparent", colorWhite: c.onPrimary,
                },
                Radio: {
                    wireframe: true, radioSize: 20, dotSize: 10, lineWidth: 2,
                    colorBorder: c.onSurfaceVariant, colorBgContainer: "transparent",
                    buttonBg: "transparent", buttonCheckedBg: c.primaryContainer,
                    buttonSolidCheckedBg: c.primaryContainer, buttonSolidCheckedColor: c.onPrimaryContainer,
                    buttonSolidCheckedHoverBg: c.primaryContainer, buttonSolidCheckedActiveBg: c.pressed,
                    borderRadius: 20, borderRadiusLG: 24, borderRadiusSM: 16,
                },
                Slider: {
                    railSize: 4, handleSize: 20, handleSizeHover: 20,
                    handleLineWidth: 0, handleLineWidthHover: 0,
                    railBg: c.containerHighest, railHoverBg: c.containerHighest,
                    trackBg: c.primary, trackHoverBg: c.primary,
                    handleColor: c.primary, handleActiveColor: c.primary,
                    colorBgElevated: c.primary, dotActiveBorderColor: c.primary,
                },
                Progress: { defaultColor: c.primary, remainingColor: c.containerHighest, lineBorderRadius: 2 },
                Dropdown: { borderRadiusLG: 4, paddingBlock: 8, colorBgElevated: c.containerHigh, controlPaddingHorizontal: 16 },
                Tooltip: { borderRadius: 4 },
                Spin: { dotSize: 40, dotSizeSM: 20, dotSizeLG: 48 },
            },
        },
        spin: { indicator: materialSpinIndicator },
        wave: { showEffect: showInsetEffect },
    };
};

const useMuiTheme = (appState: AppState) => useMemo(() => createMuiTheme(appState), [appState]);
export default useMuiTheme;
