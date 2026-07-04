import { theme as antdTheme } from "antd";
import { FunctionComponent } from "react";

const DeskGlobalStyle: FunctionComponent = () => {
    const { token } = antdTheme.useToken();

    return (
        <style>
            {`
            body.light {
              background-color: ${token.colorBgLayout};
              background-image:
                linear-gradient(rgba(23, 32, 51, 0.035) 1px, transparent 1px),
                linear-gradient(90deg, rgba(23, 32, 51, 0.035) 1px, transparent 1px);
              background-size: 72px 72px;
            }
            body.light .ant-app,
            body.light .ant-layout,
            body.light .ant-layout-content,
            body.light .ant-layout-footer {
              background: transparent;
            }
            body.light a,
            body.light .ant-btn-link {
              color: ${token.colorText};
            }
            body.light a:hover,
            body.light .ant-btn-link:not(:disabled):not(.ant-btn-disabled):hover {
              color: ${token.colorTextSecondary};
            }
            body.light .ant-layout-sider,
            body.light .ant-layout-header {
              background: ${token.colorBgContainer};
            }
            body.light .sidebar-shell {
              background:
                linear-gradient(180deg, rgba(255, 253, 248, 0.98) 0%, rgba(246, 240, 230, 0.98) 100%);
              border-right: ${token.lineWidth}px ${token.lineType} ${token.colorBorder};
              box-shadow: inset -1px 0 0 rgba(23, 32, 51, 0.04);
            }
            body.light .sidebar-brand {
              background: rgba(255, 253, 248, 0.78);
              border: ${token.lineWidth}px ${token.lineType} ${token.colorBorder};
              border-radius: ${token.borderRadiusSM}px;
              box-shadow: 3px 3px 0 rgba(23, 32, 51, 0.05);
              margin: 10px 8px 12px;
              width: calc(100% - 16px);
            }
            body.light .sidebar-brand:hover {
              background: ${token.colorBgContainer};
              border-color: ${token.colorPrimaryBorder};
            }
            body.light .sidebar-brand-mark {
              background: ${token.colorText};
              border-radius: ${token.borderRadiusSM}px;
              color: ${token.colorWhite};
              box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.18);
            }
            body.light .sidebar-rail.ant-menu {
              padding-top: 4px;
            }
            body.light .sidebar-rail.ant-menu .ant-menu-item,
            body.light .sidebar-panel.ant-menu .ant-menu-item {
              border: ${token.lineWidth}px ${token.lineType} transparent;
              border-radius: ${token.borderRadiusSM}px !important;
            }
            body.light .sidebar-rail.ant-menu .ant-menu-item:hover,
            body.light .sidebar-panel.ant-menu .ant-menu-item:hover {
              background: rgba(255, 253, 248, 0.88) !important;
              border-color: ${token.colorBorder};
            }
            body.light .sidebar-rail.ant-menu .ant-menu-item-selected {
              background: ${token.colorText} !important;
              border-color: ${token.colorText};
              box-shadow: 4px 4px 0 rgba(23, 32, 51, 0.09);
            }
            body.light .sidebar-rail.ant-menu .ant-menu-item-selected .anticon,
            body.light .sidebar-rail.ant-menu .ant-menu-item-selected .menu-title,
            body.light .sidebar-rail.ant-menu .ant-menu-item-selected > .ant-menu-title-content > a {
              color: ${token.colorWhite} !important;
            }
            body.light .sidebar-panel.ant-menu {
              background: rgba(255, 253, 248, 0.42);
            }
            body.light .sidebar-panel-main.ant-menu .ant-menu-item-group + .ant-menu-item-group::before {
              border-top-style: dashed;
            }
            body.light .sidebar-panel.ant-menu .ant-menu-item-selected {
              background: rgba(23, 32, 51, 0.08) !important;
              border-color: ${token.colorText};
              box-shadow: inset 3px 0 0 ${token.colorText};
            }
            body.light .sidebar-panel-footer.ant-menu {
              background: rgba(255, 253, 248, 0.92);
              border-top-color: ${token.colorBorder};
            }
            body.light .ant-card,
            body.light .ant-table-wrapper .ant-table,
            body.light .ant-list-bordered,
            body.light .ant-collapse,
            body.light .ant-descriptions-bordered .ant-descriptions-view {
              border-color: ${token.colorBorder};
              box-shadow: ${token.boxShadowSecondary};
            }
            body.light .ant-card {
              border-radius: ${token.borderRadiusLG}px;
              background: linear-gradient(180deg, ${token.colorBgContainer} 0%, rgba(255, 253, 248, 0.96) 100%);
            }
            body.light .ant-card-head {
              min-height: 44px;
              border-bottom-color: ${token.colorBorderSecondary};
            }
            body.light .ant-card-head-title,
            body.light .ant-modal-title,
            body.light .ant-drawer-title {
              color: ${token.colorText};
              font-weight: 650;
            }
            body.light .ant-btn {
              border-radius: ${token.borderRadiusSM}px;
              box-shadow: none;
              font-weight: 560;
            }
            body.light .ant-btn:not(.ant-btn-text):not(.ant-btn-link) {
              border-color: ${token.colorBorder};
            }
            body.light .ant-btn-primary {
              background: ${token.colorText};
              border-color: ${token.colorText};
              color: ${token.colorWhite};
            }
            body.light .ant-btn-primary:not(:disabled):not(.ant-btn-disabled):hover,
            body.light .ant-btn-primary:not(:disabled):not(.ant-btn-disabled):focus-visible {
              background: ${token.colorPrimary};
              border-color: ${token.colorPrimary};
              color: ${token.colorWhite};
            }
            body.light .ant-btn-variant-text,
            body.light .ant-btn-variant-link {
              border-radius: ${token.borderRadiusSM}px !important;
            }
            body.light .ant-input,
            body.light .ant-input-affix-wrapper,
            body.light .ant-input-number,
            body.light .ant-picker,
            body.light .ant-select,
            body.light .ant-select-content,
            body.light .ant-select-selector,
            body.light .ant-mentions,
            body.light .ant-radio-button-wrapper,
            body.light .ant-upload.ant-upload-drag {
              border-radius: ${token.borderRadiusSM}px !important;
            }
            body.light .ant-input,
            body.light .ant-input-affix-wrapper,
            body.light .ant-input-number,
            body.light .ant-picker,
            body.light .ant-select,
            body.light .ant-select-selector {
              background: ${token.colorBgContainer};
              border-color: ${token.colorBorder};
            }
            body.light .ant-form-item-label > label {
              color: ${token.colorTextSecondary};
              font-weight: 560;
            }
            body.light .ant-table-wrapper .ant-table {
              border-radius: ${token.borderRadiusLG}px;
              overflow: hidden;
            }
            body.light .ant-table-thead > tr > th {
              border-bottom-color: ${token.colorBorder};
              color: ${token.colorTextSecondary};
              font-weight: 700;
              letter-spacing: 0;
              text-transform: uppercase;
            }
            body.light .ant-table-tbody > tr > td {
              border-bottom-color: ${token.colorBorderSecondary};
            }
            body.light .ant-table-tbody > tr:last-child > td {
              border-bottom-color: transparent;
            }
            body.light .article-desk-list-toolbar {
              background: rgba(255, 253, 248, 0.76);
              border: ${token.lineWidth}px ${token.lineType} ${token.colorBorder};
              border-radius: ${token.borderRadiusLG}px;
              box-shadow: ${token.boxShadowTertiary};
              padding: 12px;
            }
            body.light .article-desk-list > .ant-divider {
              margin: 12px 0 4px;
            }
            body.light .article-desk-list .ant-table-wrapper .ant-table {
              background: ${token.colorBgContainer};
              border: ${token.lineWidth}px ${token.lineType} ${token.colorBorder};
              border-radius: ${token.borderRadiusLG}px;
              box-shadow: ${token.boxShadowTertiary};
              overflow: hidden;
            }
            body.light .article-desk-list .ant-table-container {
              border-radius: ${token.borderRadiusLG}px !important;
            }
            body.light .article-desk-list .ant-table table {
              border-collapse: separate;
              border-spacing: 0;
            }
            body.light .article-desk-list .ant-table-thead > tr > th {
              background: ${token.colorFillSecondary} !important;
              border-bottom: ${token.lineWidth}px ${token.lineType} ${token.colorBorder};
              color: ${token.colorTextSecondary};
              font-weight: 700;
              letter-spacing: 0;
              padding: 12px 14px;
              text-transform: none;
            }
            body.light .article-desk-list .ant-table-measure-row > td {
              background: transparent !important;
              border: 0 !important;
              height: 0 !important;
              padding: 0 !important;
            }
            body.light .article-desk-list .ant-table-tbody > tr.ant-table-row > td {
              background: ${token.colorBgContainer};
              border-bottom: ${token.lineWidth}px ${token.lineType} ${token.colorBorderSecondary};
              border-top: 0;
              box-shadow: none;
              padding: 12px 14px;
            }
            body.light .article-desk-list .ant-table-tbody > tr.ant-table-row > td:first-child {
              border-left: 0;
              border-radius: 0;
            }
            body.light .article-desk-list .ant-table-tbody > tr.ant-table-row > td:last-child {
              border-radius: 0;
              border-right: 0;
            }
            body.light .article-desk-list .article-desk-title-cell {
              gap: 7px;
            }
            body.light .article-desk-list .article-desk-title-cell a {
              font-weight: 650;
            }
            body.light .article-desk-list .ant-table-tbody > tr.ant-table-row:hover > td {
              background: ${token.colorFillSecondary};
            }
            body.light .article-desk-list .ant-table-tbody > tr.ant-table-row-selected > td {
              background: rgba(23, 32, 51, 0.07) !important;
            }
            body.light .article-desk-list .ant-table-cell-fix-right,
            body.light .article-desk-list .ant-table-cell-fix-right-first,
            body.light .article-desk-list .ant-table-cell-fix-right-last {
              background: ${token.colorBgContainer};
            }
            body.light .article-desk-list .ant-table-tbody > tr.ant-table-row:hover > td.ant-table-cell-fix-right,
            body.light .article-desk-list .ant-table-tbody > tr.ant-table-row:hover > td.ant-table-cell-fix-right-first,
            body.light .article-desk-list .ant-table-tbody > tr.ant-table-row:hover > td.ant-table-cell-fix-right-last {
              background: ${token.colorFillSecondary};
            }
            body.light .article-desk-list .ant-table-cell-fix-left-first::after,
            body.light .article-desk-list .ant-table-cell-fix-right-first::after {
              box-shadow: none !important;
            }
            body.light .activity-graph {
              scrollbar-color: ${token.colorBorder} transparent;
              scrollbar-width: thin;
            }
            body.light .activity-graph::-webkit-scrollbar {
              height: 6px;
            }
            body.light .activity-graph::-webkit-scrollbar-thumb {
              background: ${token.colorBorder};
              border-radius: ${token.borderRadiusSM}px;
            }
            body.light .ant-menu {
              background: transparent;
            }
            body.light .ant-menu-item,
            body.light .ant-menu-submenu-title {
              border-radius: ${token.borderRadiusSM}px !important;
              font-weight: 560;
            }
            body.light .ant-menu-item-selected {
              background: rgba(23, 32, 51, 0.08) !important;
              box-shadow: inset 3px 0 0 ${token.colorText};
            }
            body.light .cm-editor,
            body.light .cm-scroller,
            body.light .cm-gutters {
              background: ${token.colorBgContainer};
              color: ${token.colorText};
            }
            body.light .cm-gutters,
            body.light .cm-activeLineGutter {
              color: ${token.colorTextTertiary};
              border-color: ${token.colorBorderSecondary};
            }
            body.light .cm-activeLine,
            body.light .cm-activeLineGutter {
              background: ${token.colorFillSecondary} !important;
            }
            body.light .cm-focused .cm-selectionBackground,
            body.light .cm-selectionBackground,
            body.light .cm-content ::selection {
              background: rgba(23, 32, 51, 0.16) !important;
            }
            body.light .ant-tabs-tab {
              font-weight: 560;
            }
            body.light .ant-tag,
            body.light .ant-badge .ant-badge-count,
            body.light .ant-segmented,
            body.light .ant-pagination-item,
            body.light .ant-pagination-prev .ant-pagination-item-link,
            body.light .ant-pagination-next .ant-pagination-item-link {
              border-radius: ${token.borderRadiusSM}px;
            }
            body.light .ant-tag {
              border-color: ${token.colorBorder};
              font-weight: 560;
            }
            body.light .ant-tag-blue,
            body.light .ant-tag-cyan,
            body.light .ant-tag-geekblue,
            body.light .ant-tag-processing {
              background: ${token.colorFillSecondary};
              border-color: ${token.colorBorder};
              color: ${token.colorTextSecondary};
            }
            body.light .ant-tag-blue .anticon,
            body.light .ant-tag-cyan .anticon,
            body.light .ant-tag-geekblue .anticon,
            body.light .ant-tag-processing .anticon {
              color: inherit;
            }
            body.light .ant-alert,
            body.light .ant-message-notice-content,
            body.light .ant-notification-notice,
            body.light .ant-popover-inner,
            body.light .ant-dropdown-menu,
            body.light .ant-picker-dropdown .ant-picker-panel-container,
            body.light .ant-select-dropdown,
            body.light .ant-modal-container,
            body.light .ant-modal-content,
            body.light .ant-drawer-container,
            body.light .ant-drawer-content {
              border: ${token.lineWidth}px ${token.lineType} ${token.colorBorder};
              border-radius: ${token.borderRadiusLG}px !important;
              box-shadow: ${token.boxShadow};
            }
            body.light .ant-modal-header,
            body.light .ant-drawer-header,
            body.light .ant-modal-footer,
            body.light .ant-drawer-footer {
              background: ${token.colorBgContainer};
              border-color: ${token.colorBorderSecondary};
            }
            body.light .ant-modal-close,
            body.light .ant-drawer-close,
            body.light .ant-color-picker-trigger,
            body.light .ant-float-btn-body,
            body.light .ant-back-top-content {
              border-radius: ${token.borderRadiusSM}px !important;
            }
            body.light .ant-statistic-title,
            body.light .ant-typography-secondary {
              color: ${token.colorTextSecondary};
            }
            body.light .ant-divider {
              border-color: ${token.colorBorderSecondary};
            }
          `}
        </style>
    );
};

export default DeskGlobalStyle;
