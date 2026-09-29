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
          `}
        </style>
    );
};

export default DeskGlobalStyle;
