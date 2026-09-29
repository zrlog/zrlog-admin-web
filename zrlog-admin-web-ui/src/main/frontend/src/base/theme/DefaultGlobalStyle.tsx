import { theme as antdTheme } from "antd";
import { FunctionComponent } from "react";

// Mounted only by the default theme. Shell overrides use a dedicated scope so
// switching to Desk / Ant Design also removes all Material presentation.
const DefaultGlobalStyle: FunctionComponent = () => {
    const { token: t } = antdTheme.useToken();
    return (
        <>
            <style data-admin-material-style>
                {`
            .admin-material-shell.admin-material-shell {
                background: ${t.colorBgLayout};
            }
            .admin-material-shell.admin-material-shell .ant-layout-header,
            .admin-material-shell.admin-material-shell .ant-layout-sider {
                background: ${t.colorBgLayout} !important;
                border: none !important;
                backdrop-filter: none !important;
            }
            .admin-material-shell.admin-material-shell .header-title-main {
                font-size: ${t.fontSizeHeading4}px;
                font-weight: ${t.fontWeightStrong};
                line-height: ${t.lineHeightHeading4};
            }
            .admin-material-shell.admin-material-shell .header-title-eyebrow {
                font-size: ${t.fontSizeSM}px;
                color: ${t.colorTextSecondary};
                font-weight: 400;
            }
            .admin-material-shell.admin-material-shell .admin-navigation-trigger {
                width: 44px !important;
                height: 44px !important;
                border-radius: 50% !important;
            }
            .admin-material-shell.admin-material-shell .sidebar-brand-mark {
                width: 48px;
                height: 48px;
                border-radius: ${t.borderRadiusLG}px;
                background: ${t.colorFillSecondary};
                color: ${t.colorText};
            }
            .admin-material-shell.admin-material-shell .sidebar-rail .ant-menu-item,
            .admin-material-shell.admin-material-shell .sidebar-rail .ant-menu-item:hover,
            .admin-material-shell.admin-material-shell .sidebar-rail .ant-menu-item-selected {
                background: transparent !important;
                margin-inline: 4px;
                width: calc(100% - 8px);
                height: 72px;
                border-radius: ${t.borderRadiusLG}px;
            }
            .admin-material-shell.admin-material-shell .sidebar-rail .anticon {
                display: inline-flex;
                align-items: center;
                justify-content: center;
                width: 56px;
                height: 32px;
                border-radius: ${t.borderRadiusLG}px;
                color: ${t.colorTextSecondary};
                transition: background-color ${t.motionDurationMid} ${t.motionEaseInOut};
            }
            .admin-material-shell.admin-material-shell .sidebar-rail .ant-menu-item:hover .anticon {
                background: ${t.controlItemBgHover};
                color: ${t.colorText};
            }
            .admin-material-shell.admin-material-shell .sidebar-rail .ant-menu-item-selected .anticon {
                background: ${t.colorPrimaryBg};
                color: ${t.colorPrimaryText};
            }
            .admin-material-shell.admin-material-shell .sidebar-rail .menu-title {
                font-size: ${t.fontSizeSM}px;
                line-height: ${t.lineHeight};
                flex-shrink: 0;
                max-width: 68px;
                color: ${t.colorTextSecondary};
            }
            .admin-material-shell.admin-material-shell .sidebar-rail .ant-menu-item-selected .menu-title {
                color: ${t.colorText};
                font-weight: 600;
            }
            .admin-material-shell.admin-material-shell .sidebar-panel .ant-menu-item {
                height: 48px !important;
                border-radius: ${t.borderRadiusLG}px;
            }
            .admin-material-shell.admin-material-shell .sidebar-panel .ant-menu-item-selected {
                background: ${t.colorPrimaryBg} !important;
            }
            .admin-material-shell.admin-material-shell .sidebar-panel .ant-menu-item-selected .menu-title,
            .admin-material-shell.admin-material-shell .sidebar-panel .ant-menu-item-selected .anticon {
                color: ${t.colorPrimaryText} !important;
            }
            .admin-material-shell.admin-material-shell .sidebar-panel .ant-menu-item-group-title {
                font-size: ${t.fontSizeSM}px;
                color: ${t.colorTextSecondary};
            }
            .admin-material-shell.admin-material-shell .sidebar-drawer-close {
                width: 44px;
                height: 44px;
            }
            .admin-material-shell.admin-material-shell .ant-card {
                border-color: transparent;
                box-shadow: none;
            }
            .admin-material-shell.admin-material-shell .ant-card-head {
                border-bottom-color: transparent;
                font-weight: ${t.fontWeightStrong};
            }
            .admin-material-shell.admin-material-shell .dashboard-welcome {
                background: ${t.colorPrimaryBg};
            }
            .admin-material-shell.admin-material-shell .dashboard-welcome > .ant-card-head {
                background: transparent !important;
                color: ${t.colorPrimaryText} !important;
            }
            .admin-material-shell.admin-material-shell .dashboard-action-tile-icon {
                background: ${t.colorPrimaryBg} !important;
                color: ${t.colorPrimaryText} !important;
            }
            .admin-material-shell.admin-material-shell .dashboard-action-tile:hover {
                background: ${t.controlItemBgHover} !important;
            }
            .admin-material-shell.admin-material-shell .ant-table thead > tr > th {
                background: ${t.colorFillAlter};
                color: ${t.colorTextSecondary};
                font-weight: ${t.fontWeightStrong};
                border-bottom-width: ${t.lineWidth}px;
            }
            .admin-material-shell.admin-material-shell a:focus-visible {
                outline: ${t.lineWidthFocus}px ${t.lineType} ${t.colorPrimary};
                outline-offset: 2px;
            }
            .admin-material-shell.admin-material-shell[data-compact-mode="true"] .sidebar-rail .ant-menu-item {
                height: 60px;
            }
            .admin-material-shell.admin-material-shell[data-compact-mode="true"] .sidebar-rail .anticon {
                width: 48px;
            }
            .admin-material-shell.admin-material-shell[data-compact-mode="true"] .sidebar-panel .ant-menu-item {
                height: 40px !important;
            }
            @media (max-width: ${t.screenSM - 1}px) {
                .admin-material-shell.admin-material-shell .header-title-main {
                    font-size: ${t.fontSizeLG}px;
                }
            }
            @media (prefers-reduced-motion: reduce) {
                .admin-material-shell.admin-material-shell .sidebar-rail .anticon,
                .admin-material-shell.admin-material-shell .dashboard-action-tile,
                .admin-material-shell.admin-material-shell .admin-dashboard-item {
                    animation: none !important;
                    transition: none !important;
                }
            }
            `}
            </style>
        </>
    );
};
export default DefaultGlobalStyle;
