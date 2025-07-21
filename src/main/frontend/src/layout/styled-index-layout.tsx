import styled from "styled-components";
import { colorToRgba } from "./slider";

type StyledIndexLayoutProps = {
    compactMode: boolean;
    colorPrimary: string;
    borderRadius: number;
    borderRadiusLG: number;
    textColor: string;
    textSecondaryColor: string;
    textTertiaryColor: string;
};

const StyledIndexLayout = styled.div<StyledIndexLayoutProps>`
    position: relative;

    .ant-layout-header {
        backdrop-filter: blur(10px);

        .dark & {
            background: linear-gradient(135deg, rgba(26, 26, 26, 0.95) 0%, rgba(31, 31, 31, 0.98) 100%) !important;
        }
    }

    /* Menu Styling with Modern Effects */

    .ant-menu-inline,
    .ant-menu-vertical,
    .ant-menu-vertical-left {
        border: 0;
    }

    /* Icon adjustments with glow effect */

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

        &::before {
            content: "";
            position: absolute;
            top: 0;
            left: -100%;
            width: 100%;
            height: 100%;
            background: linear-gradient(90deg, transparent, ${(props) => props.colorPrimary}10, transparent);
        }

        &:hover::before {
            left: 100%;
        }
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

    /* Menu Item shape with glassmorphism */

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
        border-radius: 50%;
        border: 2px solid transparent;
        background: linear-gradient(white, white) padding-box,
            linear-gradient(135deg, ${(props) => props.colorPrimary}80, ${(props) => props.colorPrimary}40) border-box;

        &:hover {
            box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1); /* Soft subtle */
        }
    }

    /* Enhanced hover effects for menu items - MD3 State Layers */

    .ant-menu-item:hover,
    .ant-menu-submenu-title:hover {
        background: ${(props) => colorToRgba(props.colorPrimary, 0.08)} !important; /* MD3 Hover state layer */
        color: ${(props) => props.colorPrimary} !important;
        box-shadow: none;

        .anticon {
            filter: drop-shadow(0 1px 2px ${(props) => props.colorPrimary}20);
        }
    }

    .ant-menu-item-selected {
        background: ${(props) => colorToRgba(props.colorPrimary, 0.12)} !important; /* MD3 Focus/Active state layer */
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

    /* Premium Cards with elevated design */

    .ant-card {
        border: 1px solid transparent;
        background: linear-gradient(135deg, rgba(255, 255, 255, 1) 0%, rgba(255, 255, 255, 0.98) 100%);

        &:hover {
            box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08), 0 2px 8px rgba(0, 0, 0, 0.04);
            border-color: rgba(0, 0, 0, 0.06);
        }

        .dark & {
            background: linear-gradient(135deg, rgba(26, 26, 26, 1) 0%, rgba(31, 31, 31, 0.98) 100%);
            border-color: rgba(255, 255, 255, 0.06);

            &:hover {
                box-shadow: 0 8px 32px rgba(0, 0, 0, 0.6), 0 2px 8px rgba(0, 0, 0, 0.4);
                border-color: rgba(255, 255, 255, 0.1);
            }
        }
    }

    .ant-card-head {
        border-bottom: 1px solid rgba(0, 0, 0, 0.06);
        font-weight: 600;

        .dark & {
            border-bottom-color: rgba(255, 255, 255, 0.08);
        }
    }

    /* Premium Table Design */

    .ant-table {
        overflow: hidden;
        background: white;

        .dark & {
            background: #1a1a1a;
        }

        /* Elegant table header */

        thead > tr > th {
            background: linear-gradient(180deg, #fafbfc 0%, #f7f8fa 100%);
            font-weight: 600;
            font-size: 13px;
            letter-spacing: 0.3px;
            text-transform: uppercase;
            color: #3c4043;
            border-bottom: 2px solid #e8eaed;
            padding: 16px;

            &::before {
                display: none;
            }

            .dark & {
                background: linear-gradient(180deg, #1f1f1f 0%, #1a1a1a 100%);
                color: #c4c7cb;
                border-bottom-color: #2a2a2a;
            }
        }

        /* Table rows with elegant spacing */

        tbody > tr {
            border-bottom: 1px solid #f0f0f0;

            > td {
                padding: 16px;
                border-bottom: 1px solid #f0f0f0;
            }

            &:hover {
                background: linear-gradient(
                    90deg,
                    ${(props) => props.colorPrimary}05 0%,
                    ${(props) => props.colorPrimary}03 100%
                );
                box-shadow: inset 3px 0 0 ${(props) => props.colorPrimary};
            }

            &:last-child > td {
                border-bottom: none;
            }

            .dark & {
                border-bottom-color: #2a2a2a;

                > td {
                    border-bottom-color: #2a2a2a;
                }

                &:hover {
                    background: linear-gradient(
                        90deg,
                        ${(props) => props.colorPrimary}15 0%,
                        ${(props) => props.colorPrimary}08 100%
                    );
                }
            }
        }

        /* Striped rows (subtle) */

        tbody > tr:nth-child(even) {
            background: rgba(0, 0, 0, 0.01);

            .dark & {
                background: rgba(255, 255, 255, 0.02);
            }
        }
    }

    /* Refined Buttons */

    .ant-btn-primary {
        background: linear-gradient(
            135deg,
            ${(props) => props.colorPrimary} 0%,
            ${(props) => props.colorPrimary}dd 100%
        );
        border: none;
        box-shadow: 0 2px 8px ${(props) => props.colorPrimary}30, 0 1px 2px ${(props) => props.colorPrimary}20;
        font-weight: 500;
    }

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
        letter-spacing: 0.04em;
        text-transform: uppercase;
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
        color: #ff4d4f;
    }

    .ant-form-item-has-error .ant-radio-group {
        border: 1px solid #ff4d4f;
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
        color: #5f6368;

        .dark & {
            color: #9aa0a6;
        }
    }

    /* MD3 Floating Action Button (FAB) */
    .ant-float-btn-body {
        border-radius: 50% !important; /* MD3 circular FAB */
        background: ${(props) => props.colorPrimary}20 !important; /* Secondary container default */
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
        box-shadow: 0 4px 8px rgba(0, 0, 0, 0.15), 0 1px 3px rgba(0, 0, 0, 0.1);

        .dark & {
            box-shadow: 0 4px 8px rgba(0, 0, 0, 0.4), 0 1px 3px rgba(0, 0, 0, 0.3);
        }

        &:hover .ant-float-btn-body {
            background: ${(props) => colorToRgba(props.colorPrimary, 0.3)} !important;
            box-shadow: 0 6px 12px rgba(0, 0, 0, 0.2), 0 2px 4px rgba(0, 0, 0, 0.15);
        }

        .dark &:hover .ant-float-btn-body {
            background: ${(props) => colorToRgba(props.colorPrimary, 0.4)} !important;
            box-shadow: 0 6px 12px rgba(0, 0, 0, 0.5), 0 2px 4px rgba(0, 0, 0, 0.4);
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
    }

    .admin-navigation-drawer .ant-drawer-header {
        padding: 10px 10px 0;
        border-top-right-radius: ${(props) => props.borderRadiusLG}px;
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

    .sidebar-brand:hover {
        background: rgba(0, 0, 0, 0.03);
    }

    .dark .sidebar-brand:hover {
        background: rgba(255, 255, 255, 0.05);
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
        margin-left: 12px;
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
        padding-right: 14px;
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

    .sidebar-panel.ant-menu {
        text-align: left;
        padding: 4px 10px 24px;
    }

    .sidebar-panel.ant-menu .ant-menu-item-group {
        margin-bottom: 16px;
    }

    .sidebar-panel.ant-menu .ant-menu-item-group-title {
        padding: 0 12px 8px;
        font-size: 11px;
        font-weight: 700;
        letter-spacing: 0.4px;
        text-transform: uppercase;
        color: ${(props) => props.textTertiaryColor};
    }

    .sidebar-panel.ant-menu .ant-menu-item {
        box-sizing: border-box;
        margin: 0 6px 4px;
        width: auto;
        height: 46px;
        border-radius: ${(props) => props.borderRadius}px;
    }

    .sidebar-panel.ant-menu .ant-menu-title-content > a {
        display: flex;
        flex-direction: row;
        align-items: center;
        justify-content: flex-start;
        gap: 12px;
        height: 100%;
        padding: 0 14px;
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
        background: ${(props) => colorToRgba(props.colorPrimary, 0.12)} !important;
    }

    .sidebar-rail.ant-menu .ant-menu-item:hover {
        background: rgba(15, 23, 42, 0.05) !important;
    }

    .sidebar-panel.ant-menu .ant-menu-item:hover {
        background: rgba(15, 23, 42, 0.045) !important;
    }

    .dark .sidebar-rail.ant-menu .ant-menu-item:hover,
    .dark .sidebar-panel.ant-menu .ant-menu-item:hover {
        background: rgba(255, 255, 255, 0.06) !important;
    }
`;

export default StyledIndexLayout;
