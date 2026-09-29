import { theme } from "antd";
import { FastColor } from "@ant-design/fast-color";

// Only mounted by DefaultGlobalStyle. Every selector targets a semantic class
// supplied by materialComponentConfig, never an independent theme or a field.
const MaterialControlsStyle = () => {
    const { token: t } = theme.useToken();
    const stateHover = new FastColor(t.colorPrimary).setA(0.08).toRgbString();
    const statePressed = new FastColor(t.colorPrimary).setA(0.12).toRgbString();
    return (
        <style data-admin-material-controls>{`
        .admin-m3-switch.admin-m3-switch {
            box-sizing: border-box;
            border: 2px solid ${t.colorBorder};
            background: ${t.colorFillSecondary};
            color: ${t.colorTextSecondary};
            overflow: visible;
        }
        .admin-m3-switch.admin-m3-switch[aria-checked="true"] {
            border-color: ${t.colorPrimary};
            background: ${t.colorPrimary};
            color: var(--admin-m3-on-primary);
        }
        .admin-m3-switch.admin-m3-switch .admin-m3-switch-content.admin-m3-switch-content { color: inherit; }
        .admin-m3-switch.admin-m3-switch:not(:disabled):hover {
            border-color: ${t.colorTextSecondary};
            background: ${t.colorFillSecondary};
        }
        .admin-m3-switch.admin-m3-switch[aria-checked="true"]:not(:disabled):hover {
            border-color: ${t.colorPrimary};
            background: ${t.colorPrimary};
        }
        .admin-m3-switch .admin-m3-switch-handle.admin-m3-switch-handle {
            top: 50%;
            transform: translateY(-50%);
            inset-inline-start: 6px;
            width: 16px;
            height: 16px;
            border-radius: 50%;
            background: ${t.colorBorder};
            transition: width ${t.motionDurationMid}, height ${t.motionDurationMid},
                inset-inline-start ${t.motionDurationMid} ${t.motionEaseInOut}, background-color ${t.motionDurationMid};
        }
        .admin-m3-switch[aria-checked="true"] .admin-m3-switch-handle.admin-m3-switch-handle {
            inset-inline-start: calc(100% - 26px);
            width: 24px;
            height: 24px;
            background: var(--admin-m3-on-primary);
        }
        .admin-m3-switch-small .admin-m3-switch-handle.admin-m3-switch-handle {
            inset-inline-start: 4px;
            width: 12px;
            height: 12px;
        }
        .admin-m3-switch-small[aria-checked="true"] .admin-m3-switch-handle.admin-m3-switch-handle {
            inset-inline-start: calc(100% - 18px);
            width: 16px;
            height: 16px;
        }
        .admin-m3-switch:not(:disabled):hover .admin-m3-switch-handle,
        .admin-m3-switch:focus-visible .admin-m3-switch-handle {
            box-shadow: 0 0 0 8px ${stateHover};
        }
        .admin-m3-switch:not(:disabled):active .admin-m3-switch-handle.admin-m3-switch-handle {
            width: 28px;
            height: 28px;
            inset-inline-start: 0;
        }
        .admin-m3-switch[aria-checked="true"]:not(:disabled):active .admin-m3-switch-handle.admin-m3-switch-handle {
            inset-inline-start: calc(100% - 28px);
        }
        .admin-m3-switch-small:not(:disabled):active .admin-m3-switch-handle.admin-m3-switch-handle {
            width: 20px;
            height: 20px;
        }
        .admin-m3-switch-small[aria-checked="true"]:not(:disabled):active .admin-m3-switch-handle.admin-m3-switch-handle {
            inset-inline-start: calc(100% - 20px);
        }
        .admin-m3-switch:disabled { opacity: 0.38; }
        .admin-m3-switch::after {
            content: "";
            position: absolute;
            inset: -6px 0;
            border-radius: inherit;
        }
        .admin-m3-switch-small::after { inset-block: -10px; }
        .admin-m3-switch:focus-visible {
            outline: ${t.lineWidthFocus}px solid ${t.colorPrimary};
            outline-offset: 3px;
        }
        .admin-m3-checkbox-icon::before, .admin-m3-radio-icon::before {
            content: "";
            position: absolute;
            width: 40px;
            height: 40px;
            inset: 50% auto auto 50%;
            transform: translate(-50%, -50%);
            border-radius: 50%;
            pointer-events: none;
            transition: background-color ${t.motionDurationFast};
        }
        .admin-m3-checkbox { min-height: 40px; align-items: center; }
        .admin-m3-checkbox:not(:has(input:disabled)):hover .admin-m3-checkbox-icon::before,
        .admin-m3-radio:not(:has(input:disabled)):hover .admin-m3-radio-icon::before {
            background: ${stateHover};
        }
        .admin-m3-checkbox:not(:has(input:disabled)):active .admin-m3-checkbox-icon::before,
        .admin-m3-radio:not(:has(input:disabled)):active .admin-m3-radio-icon::before,
        .admin-m3-checkbox:has(input:focus-visible) .admin-m3-checkbox-icon::before,
        .admin-m3-radio:has(input:focus-visible) .admin-m3-radio-icon::before {
            background: ${statePressed};
        }
        .admin-m3-checkbox:has(input:focus-visible) .admin-m3-checkbox-icon,
        .admin-m3-radio:has(input:focus-visible) .admin-m3-radio-icon {
            outline: ${t.lineWidthFocus}px solid ${t.colorPrimary};
            outline-offset: 3px;
        }
        .admin-m3-radio .admin-m3-radio-icon:has(input[type="radio"]) {
            background: transparent;
        }
        .admin-m3-checkbox-mixed .admin-m3-checkbox-icon.admin-m3-checkbox-icon {
            background: ${t.colorPrimary};
            border-color: ${t.colorPrimary};
        }
        .admin-m3-checkbox-mixed .admin-m3-checkbox-icon.admin-m3-checkbox-icon::after {
            width: 10px;
            height: 2px;
            background: var(--admin-m3-on-primary);
        }
        .admin-m3-checkbox-mixed:has(input:disabled) .admin-m3-checkbox-icon.admin-m3-checkbox-icon {
            background: ${t.colorTextDisabled};
            border-color: transparent;
        }
        .admin-m3-checkbox-mixed:has(input:disabled) .admin-m3-checkbox-icon.admin-m3-checkbox-icon::after {
            background: ${t.colorBgContainer};
        }
        .admin-m3-segmented.admin-m3-segmented {
            border: ${t.lineWidth}px solid ${t.colorBorder};
            padding: 0;
            overflow: hidden;
        }
        .admin-m3-segmented .admin-m3-segment.admin-m3-segment {
            border-radius: 0;
            box-shadow: none;
        }
        .admin-m3-segment + .admin-m3-segment {
            border-inline-start: ${t.lineWidth}px solid ${t.colorBorder};
        }
        .admin-m3-segmented-vertical .admin-m3-segment + .admin-m3-segment {
            border-inline-start: 0;
            border-block-start: ${t.lineWidth}px solid ${t.colorBorder};
        }
        .admin-m3-segment-label.admin-m3-segment-label {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            padding-inline: 16px;
            min-height: 38px;
        }
        .admin-m3-segment:has(input:checked) .admin-m3-segment-label::before {
            content: "";
            width: 12px;
            height: 12px;
            flex: none;
            background: currentColor;
            clip-path: polygon(0 48%, 14% 34%, 38% 58%, 85% 10%, 100% 25%, 38% 88%);
        }
        .admin-m3-segment:has(input:focus-visible) {
            outline: ${t.lineWidthFocus}px solid ${t.colorPrimary};
            outline-offset: -3px;
        }
        .admin-m3-segmented-small .admin-m3-segment-label.admin-m3-segment-label {
            min-height: 30px;
            padding-inline: 12px;
        }
        .admin-m3-tab.admin-m3-tab { padding-inline: 16px; }
        .admin-m3-tab:not(:has([aria-disabled="true"])):hover { background: ${stateHover}; }
        .admin-m3-tab:not(:has([aria-disabled="true"])):active { background: ${statePressed}; }
        .admin-m3-tab-indicator.admin-m3-tab-indicator { border-radius: 3px; }
        .admin-m3-chip.admin-m3-chip {
            position: relative;
            min-height: 32px;
            padding-inline: 12px;
            font-size: ${t.fontSize}px;
            line-height: 30px;
            font-weight: 500;
            border-radius: 8px;
            vertical-align: middle;
        }
        .admin-m3-chip::before {
            content: "";
            position: absolute;
            inset: 0;
            border-radius: inherit;
            background: currentColor;
            opacity: 0;
            pointer-events: none;
        }
        .admin-m3-chip:not(.admin-m3-chip-disabled):hover::before { opacity: 0.08; }
        .admin-m3-chip:not(.admin-m3-chip-disabled):active::before { opacity: 0.12; }
        .admin-m3-chip-close.admin-m3-chip-close {
            width: 24px;
            height: 24px;
            display: inline-flex;
            justify-content: center;
            align-items: center;
            border-radius: 50%;
        }
        .admin-m3-chip-close:hover { background: ${t.controlItemBgHover}; }
        .admin-m3-slider-handle:focus-visible {
            outline: ${t.lineWidthFocus}px solid ${t.colorPrimary};
            outline-offset: 8px;
        }
        .admin-m3-slider.admin-m3-slider-disabled .admin-m3-slider-handle.admin-m3-slider-handle::after {
            background: ${t.colorTextDisabled};
        }
        .admin-m3-icon-button.admin-m3-icon-button {
            border-radius: 50%;
            padding-inline: 0;
            aspect-ratio: 1;
        }
        .admin-m3-tonal-button.admin-m3-tonal-button:not(:disabled) {
            background: ${t.colorPrimaryBg};
            color: ${t.colorPrimaryText};
            border-color: transparent;
            box-shadow: none;
        }
        .admin-m3-tonal-button.admin-m3-tonal-button:not(:disabled):hover { background: ${t.controlItemBgActive}; }
        .admin-m3-tonal-button.admin-m3-tonal-button:not(:disabled):active { background: ${t.colorPrimaryBgHover}; }
        .admin-m3-dialog.admin-m3-dialog { padding: 24px; }
        .admin-m3-dialog-header.admin-m3-dialog-header { margin-bottom: 16px; }
        .admin-m3-dialog-footer.admin-m3-dialog-footer { margin-top: 24px; }
        .admin-m3-close.admin-m3-close {
            width: 40px;
            height: 40px;
            border-radius: 50%;
        }
        .admin-m3-drawer-header.admin-m3-drawer-header {
            padding: 20px 24px;
            border-bottom: 0;
            gap: 16px;
        }
        .admin-m3-drawer-title.admin-m3-drawer-title {
            font-size: ${t.fontSizeHeading4}px;
            font-weight: 500;
            line-height: 1.4;
        }
        .admin-m3-drawer-footer.admin-m3-drawer-footer { border-top: 0; }
        .admin-m3-menu-item.admin-m3-menu-item {
            min-height: 48px;
            padding-inline: 16px;
            border-radius: 0;
        }
        @media (prefers-reduced-motion: reduce) {
            .admin-m3-switch .admin-m3-switch-handle,
            .admin-m3-checkbox-icon, .admin-m3-checkbox-icon::before, .admin-m3-checkbox-icon::after,
            .admin-m3-radio-icon, .admin-m3-radio-icon::before, .admin-m3-radio-icon::after,
            .admin-m3-segment, .admin-m3-tab-indicator,
            .admin-m3-slider-handle::after, .admin-m3-progress * {
                transition: none !important;
                animation: none !important;
            }
        }
    `}</style>
    );
};

export default MaterialControlsStyle;
