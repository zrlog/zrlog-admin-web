import styled from "styled-components";
import { colorToRgba } from "./slider";

type StyledIndexLayoutProps = {
    compactMode: boolean;
    colorPrimary: string;
    borderRadius: number;
    borderRadiusLG: number;
    lineWidth: number;
    lineType: string;
    colorBgContainer: string;
    colorBgElevated: string;
    colorBorderSecondary: string;
    colorError: string;
    colorFillQuaternary: string;
    colorFillSecondary: string;
    textColor: string;
    textSecondaryColor: string;
    textTertiaryColor: string;
};

const StyledIndexLayout = styled.div<StyledIndexLayoutProps>`
    position: relative;

    .website-setting-select-popup,
    .website-setting-select-popup-list {
        overscroll-behavior-x: none;
        overscroll-behavior-y: contain;
        -webkit-overflow-scrolling: touch;
        touch-action: pan-y;
    }

    .website-setting-select-popup-list {
        max-height: min(320px, calc(100dvh - 168px));
        overflow-y: auto;
    }

    .ant-layout-header {
        background: ${(props) => props.colorBgElevated} !important;
    }

    /* Menu styling */

    .ant-menu-inline,
    .ant-menu-vertical,
    .ant-menu-vertical-left {
        border: 0;
    }

    /* Icon baseline sizing */

    .ant-menu-item .anticon,
    .ant-menu-submenu-title .anticon {
        font-size: 22px;
    }

    /* Vertical menu items with stacked icon+text */

    .ant-menu-title-content > a {
        display: flex;
        flex-direction: column;
        justify-content: center;
        align-items: center;
        height: 100%;
        width: 100%;
        gap: 4px;
        background: transparent !important;
    }

    .ant-menu-item a {
        background: transparent !important;
    }

    .ant-menu-item > a > span,
    .ant-menu-submenu-title > span {
    }

    /* Custom heights for top-level menu items */

    .ant-menu-vertical > .ant-menu-item,
    .ant-menu-vertical-left > .ant-menu-item,
    .ant-menu-vertical-right > .ant-menu-item,
    .ant-menu-inline > .ant-menu-item,
    .ant-menu-vertical > .ant-menu-submenu > .ant-menu-submenu-title,
    .ant-menu-vertical-left > .ant-menu-submenu > .ant-menu-submenu-title,
    .ant-menu-vertical-right > .ant-menu-submenu > .ant-menu-submenu-title,
    .ant-menu-inline > .ant-menu-submenu > .ant-menu-submenu-title {
        height: ${(props) => (props.compactMode ? 54 : 68)}px;
        line-height: 1.5;
        padding: 0;
        margin-bottom: 6px;
        position: relative;
        overflow: hidden;
    }

    /* Submenu items - Second level */

    .ant-menu-sub {
        background: transparent !important;
    }

    .ant-menu-submenu {
        display: flex !important;
        justify-content: center !important;
    }

    .ant-menu-sub > .ant-menu-item {
        margin-bottom: 4px;
        margin-left: 0 !important;
        padding-left: 0 !important;
        display: flex !important;
        justify-content: center !important;

        a {
            display: flex !important;
            flex-direction: column !important;
            justify-content: center !important;
            align-items: center !important;
            text-align: center !important;
            width: 100%;
            padding: 0 !important;
        }

        .menu-title {
            text-align: center !important;
            margin-left: 0 !important;
            display: block !important;
            font-size: 13px;
            font-weight: 500;
        }

        &:hover {
            background: ${(props) => colorToRgba(props.colorPrimary, 0.08)} !important;
        }
    }

    .ant-menu-sub .ant-menu-item-selected {
        background: ${(props) => colorToRgba(props.colorPrimary, 0.16)} !important;
        .menu-title {
            color: ${(props) => props.colorPrimary};
            font-weight: 600;
        }
    }

    /* Menu item shape */

    .ant-menu .ant-menu-item,
    .ant-menu-submenu-title {
        margin-inline: 12px;
        width: calc(100% - 24px);
    }

    .ant-menu .ant-menu-item {
        position: relative;
    }

    /* Submenu Title */

    .ant-menu-submenu-title > i {
        display: none;
    }

    .ant-menu-submenu-title {
        margin: 0;
    }

    .ant-menu-submenu-title:hover {
        color: inherit;
    }

    .userAvatarImg {
        border: ${(props) => `${props.lineWidth}px ${props.lineType} ${props.colorBorderSecondary}`};
        background: transparent;
    }

    /* Menu hover/focus states */

    .ant-menu-item:hover,
    .ant-menu-submenu-title:hover {
        background: ${(props) => colorToRgba(props.colorPrimary, 0.08)} !important;
        color: ${(props) => props.colorPrimary} !important;
        box-shadow: none;
    }

    .ant-menu-item-selected {
        background: ${(props) => colorToRgba(props.colorPrimary, 0.12)} !important;
        color: ${(props) => props.colorPrimary} !important;
        font-weight: 600;
        box-shadow: none;

        a {
            background: transparent !important;
        }
    }

    .ant-menu-item-selected a {
        color: ${(props) => props.colorPrimary} !important;
        font-weight: 600;
        background: transparent !important;
    }

    /* Card baseline styling */

    .ant-card {
        border: ${(props) => `${props.lineWidth}px ${props.lineType} ${props.colorBorderSecondary}`};
        background: ${(props) => props.colorBgContainer};
        box-shadow: none;

        &:hover {
            box-shadow: none;
        }
    }

    .ant-card-head {
        border-bottom: ${(props) => `${props.lineWidth}px ${props.lineType} ${props.colorBorderSecondary}`};
        font-weight: 600;
    }

    /* Table baseline styling */

    .ant-table {
        background: ${(props) => props.colorBgContainer};

        /* Elegant table header */

        thead > tr > th {
            background: ${(props) => props.colorFillQuaternary};
            font-weight: 600;
            font-size: 13px;
            color: ${(props) => props.textSecondaryColor};
            border-bottom: ${(props) => `${props.lineWidth * 2}px ${props.lineType} ${props.colorBorderSecondary}`};
            padding: 16px;

            &::before {
                display: none;
            }
        }

        /* Table rows with elegant spacing */

        tbody > tr {
            border-bottom: ${(props) => `${props.lineWidth}px ${props.lineType} ${props.colorBorderSecondary}`};

            > td {
                padding: 16px;
                border-bottom: ${(props) => `${props.lineWidth}px ${props.lineType} ${props.colorBorderSecondary}`};
            }

            &:hover {
                background: ${(props) => props.colorFillQuaternary};
            }

            &:last-child > td {
                border-bottom: none;
            }
        }
    }

    /* Refined Buttons */
    /* Upload list hidden as per original */

    .ant-upload-list {
        display: none;
    }

    ul {
        margin-bottom: 0;
    }

    /* Typography Overrides */

    .ant-typography h3,
    h3.ant-typography {
        margin-bottom: 0.5em;
        font-weight: 700;
        font-size: 24px;
        line-height: 1.35;
    }

    .ant-typography h4,
    h4.ant-typography {
        margin-bottom: 0.5em;
        font-weight: 600;
        font-size: 20px;
        line-height: 1.4;
    }

    .ant-menu {
        text-align: center;
        box-shadow: none;
    }

    .header-title-block {
        min-width: 0;
        display: flex;
        flex-direction: column;
        justify-content: center;
        gap: 1px;
    }

    .header-title-eyebrow,
    .header-title-main {
        display: block;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .header-title-eyebrow {
        font-size: 11px;
        line-height: 1.1;
        font-weight: 600;
        color: ${(props) => props.textTertiaryColor};
    }

    .header-title-main {
        font-size: 16px;
        line-height: 1.2;
        font-weight: 600;
    }

    .ant-menu .menu-title {
        margin-left: 0 !important;
        font-size: 12px;
        line-height: 1.2;
        display: block;
        margin-top: 4px;
        font-weight: 500;
    }

    .ant-form-item-explain-error {
        color: ${(props) => props.colorError};
    }

    .ant-form-item-has-error .ant-radio-group {
        border: ${(props) => `${props.lineWidth}px ${props.lineType} ${props.colorError}`};
    }

    .ant-upload {
        padding: 0 !important;
        box-sizing: content-box;
    }

    /* Refined Input Fields */

    .ant-input,
    .ant-input-number,
    .ant-select-selector {
        &:hover {
            border-color: ${(props) => props.colorPrimary}80;
        }

        &:focus,
        &:focus-within {
            border-color: ${(props) => props.colorPrimary};
            box-shadow: 0 0 0 3px ${(props) => props.colorPrimary}15;
        }
    }

    .ant-typography {
        line-height: 1.65;
    }

    h1.ant-typography,
    .ant-typography h1 {
        font-weight: 700;
        letter-spacing: -0.02em;
    }

    h2.ant-typography,
    .ant-typography h2 {
        font-weight: 600;
        letter-spacing: -0.01em;
    }

    h3.ant-typography,
    .ant-typography h3 {
        font-weight: 600;
    }

    /* Form spacing improvements */

    .ant-form-item {
        margin-bottom: 24px;
    }

    .ant-form-item-label > label {
        font-weight: 500;
        color: ${(props) => props.textSecondaryColor};
    }

    /* Floating button */
    .ant-float-btn-body {
        border-radius: 50% !important;
        background: ${(props) => props.colorFillSecondary} !important;
        display: flex;
        align-items: center;
        justify-content: center;
    }

    .ant-float-btn-icon {
        color: ${(props) => props.colorPrimary} !important;
        font-size: 24px;
    }

    .ant-float-btn {
        border-radius: 50% !important;
        box-shadow: none;

        &:hover .ant-float-btn-body {
            background: ${(props) => props.colorFillQuaternary} !important;
        }
    }

    .ant-float-btn-group {
        border-radius: ${(props) => props.borderRadiusLG}px !important;
        .ant-float-btn-body {
            border-radius: ${(props) => props.borderRadiusLG}px !important;
        }
    }

    .sidebar-shell {
        display: flex;
        flex-direction: column;
        height: 100%;
        min-height: 0;
    }

    .admin-navigation-drawer .ant-drawer-header {
        padding: 8px 10px 0;
    }

    .admin-navigation-drawer .ant-drawer-header-title {
        width: 100%;
    }

    .admin-navigation-drawer .ant-drawer-title {
        display: block;
        width: 100%;
    }

    .admin-navigation-drawer .ant-drawer-content {
        border-top-right-radius: ${(props) => props.borderRadiusLG}px;
        border-bottom-right-radius: ${(props) => props.borderRadiusLG}px;
        overflow: hidden;
    }

    .sidebar-drawer-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
        width: 100%;
        box-sizing: border-box;
    }

    .sidebar-drawer-close.ant-btn {
        width: 36px;
        height: 36px;
        flex-shrink: 0;
        border-radius: ${(props) => props.borderRadius}px;
        color: ${(props) => props.textSecondaryColor};
    }

    .sidebar-drawer-close.ant-btn:hover {
        background: ${(props) => props.colorFillSecondary};
        color: ${(props) => props.textColor};
    }

    .sidebar-brand-drawer {
        margin: 0;
        width: 100%;
        min-width: 0;
        flex: 1;
    }

    .sidebar-brand {
        display: flex;
        align-items: center;
        text-decoration: none;
        color: ${(props) => props.textColor};
        margin: 10px 10px 8px;
        border-radius: ${(props) => props.borderRadiusLG}px;
        width: calc(100% - 20px);
        min-height: ${(props) => (props.compactMode ? 50 : 56)}px;
        transition: background-color 0.2s ease, border-color 0.2s ease;
    }

    .sidebar-drawer-header .sidebar-brand {
        min-height: 42px;
    }

    .sidebar-brand:hover {
        background: ${(props) => props.colorFillSecondary};
    }

    .sidebar-brand-collapsed {
        justify-content: center;
        padding: 0;
    }

    .sidebar-brand-expanded {
        justify-content: flex-start;
        gap: 9px;
    }

    .sidebar-brand-expanded .sidebar-brand-mark {
        margin-left: 4px;
    }

    .sidebar-brand-mark {
        width: 34px;
        height: 34px;
        border-radius: ${(props) => props.borderRadius}px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        background: ${(props) => colorToRgba(props.colorPrimary, 0.12)};
        color: ${(props) => props.colorPrimary};
        font-size: 18px;
        flex-shrink: 0;
    }

    .dark .sidebar-brand-mark {
        background: ${(props) => colorToRgba(props.colorPrimary, 0.2)};
    }

    .sidebar-brand-copy {
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 1px;
        padding-right: 4px;
    }

    .sidebar-brand-title {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .sidebar-brand-title {
        font-size: 13px;
        line-height: 1.15;
        font-weight: 600;
        color: ${(props) => props.textColor};
    }

    .sidebar-rail.ant-menu {
        text-align: center;
    }

    .sidebar-rail.ant-menu .ant-menu-item,
    .sidebar-rail.ant-menu .ant-menu-item-selected {
        margin: 0 10px 6px;
        width: calc(100% - 20px);
        height: ${(props) => (props.compactMode ? 60 : 66)}px;
        border-radius: ${(props) => props.borderRadiusLG}px;
    }

    .sidebar-rail.ant-menu .ant-menu-title-content > a {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 6px;
        height: 100%;
    }

    .sidebar-rail.ant-menu .menu-title {
        font-size: 11px;
        line-height: 1.1;
        font-weight: 500;
        margin-top: 0;
        color: ${(props) => props.textSecondaryColor};
        width: 100%;
        max-width: 58px;
        overflow: hidden;
        text-align: center;
        white-space: normal;
        word-break: keep-all;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
    }

    .sidebar-rail.ant-menu .ant-menu-item .anticon {
        font-size: ${(props) => (props.compactMode ? 22 : 24)}px;
    }

    .sidebar-rail.ant-menu .ant-menu-title-content > a {
        color: ${(props) => props.textColor} !important;
    }

    .sidebar-rail.ant-menu .ant-menu-item-selected .menu-title {
        color: ${(props) => props.colorPrimary};
    }

    .sidebar-rail.ant-menu .ant-menu-item-selected > .ant-menu-title-content > a {
        color: ${(props) => props.colorPrimary};
    }

    .sidebar-rail.ant-menu .ant-menu-item-selected .anticon {
        color: ${(props) => props.colorPrimary};
    }

    .sidebar-panel-layout {
        display: flex;
        flex: 1;
        flex-direction: column;
        min-height: 0;
    }

    .sidebar-panel.ant-menu {
        text-align: left;
        padding: 2px 10px 0;
    }

    .sidebar-panel-main.ant-menu {
        flex: 1;
        min-height: 0;
        overflow-y: auto;
        overscroll-behavior-y: contain;
        padding-bottom: 10px;
        -webkit-overflow-scrolling: touch;
    }

    .sidebar-panel-footer.ant-menu {
        flex-shrink: 0;
        padding-top: 8px;
        padding-bottom: calc(12px + env(safe-area-inset-bottom));
        background: ${(props) => props.colorBgContainer};
        border-top: ${(props) => `${props.lineWidth}px ${props.lineType} ${props.colorBorderSecondary}`};
    }

    .sidebar-panel.ant-menu .ant-menu-item-group {
        margin-bottom: 0;
    }

    .sidebar-panel-main.ant-menu .ant-menu-item-group + .ant-menu-item-group {
        margin-top: 10px;
    }

    .sidebar-panel-main.ant-menu .ant-menu-item-group + .ant-menu-item-group::before {
        content: "";
        display: block;
        border-top: ${(props) => `${props.lineWidth}px ${props.lineType} ${props.colorBorderSecondary}`};
        margin: 0 8px 10px;
    }

    .sidebar-panel.ant-menu .ant-menu-item-group-list {
        padding: 0;
    }

    .sidebar-panel.ant-menu .ant-menu-item-group-title {
        padding: 0 12px 6px;
        font-size: 11px;
        font-weight: 600;
        color: ${(props) => props.textTertiaryColor};
    }

    .sidebar-panel.ant-menu .ant-menu-item {
        box-sizing: border-box;
        margin: 0 8px 2px;
        width: auto;
        height: 40px !important;
        min-height: 40px;
        line-height: 40px !important;
        padding-inline: 0 !important;
        border-radius: ${(props) => props.borderRadius}px;
    }

    .sidebar-panel.ant-menu .sidebar-panel-standalone-head {
        margin-bottom: 4px;
    }

    .sidebar-panel.ant-menu .sidebar-panel-head-divider {
        margin: 8px 8px 12px;
        border-color: ${(props) => props.colorBorderSecondary};
    }

    .sidebar-panel.ant-menu .ant-menu-title-content > a {
        display: flex;
        flex-direction: row;
        align-items: center;
        justify-content: flex-start;
        gap: 11px;
        height: 100%;
        padding: 0 13px;
    }

    .sidebar-panel.ant-menu .menu-title {
        margin-top: 0;
        font-size: 14px;
        line-height: 1.2;
        font-weight: 500;
        color: ${(props) => props.textSecondaryColor} !important;
    }

    .sidebar-panel.ant-menu .ant-menu-title-content > a {
        color: ${(props) => props.textColor} !important;
    }

    .sidebar-rail.ant-menu .ant-menu-item,
    .sidebar-panel.ant-menu .ant-menu-item {
        color: ${(props) => props.textColor};
    }

    .sidebar-panel.ant-menu .ant-menu-item .anticon {
        font-size: 19px;
    }

    .sidebar-panel.ant-menu .ant-menu-item-selected .menu-title {
        color: ${(props) => props.colorPrimary};
    }

    .sidebar-panel.ant-menu .ant-menu-item-selected > .ant-menu-title-content > a {
        color: ${(props) => props.colorPrimary};
    }

    .sidebar-panel.ant-menu .ant-menu-item-selected .anticon {
        color: ${(props) => props.colorPrimary};
    }

    .sidebar-rail.ant-menu .ant-menu-item::before,
    .sidebar-panel.ant-menu .ant-menu-item::before {
        display: none;
    }

    .sidebar-rail.ant-menu .ant-menu-item:hover,
    .sidebar-panel.ant-menu .ant-menu-item:hover {
        box-shadow: none;
    }

    .sidebar-rail.ant-menu .ant-menu-item-selected,
    .sidebar-panel.ant-menu .ant-menu-item-selected {
        background: ${(props) => colorToRgba(props.colorPrimary, 0.1)} !important;
    }

    .sidebar-rail.ant-menu .ant-menu-item:hover {
        background: ${(props) => props.colorFillSecondary} !important;
    }

    .sidebar-panel.ant-menu .ant-menu-item:hover {
        background: ${(props) => props.colorFillSecondary} !important;
    }
`;

export default StyledIndexLayout;
