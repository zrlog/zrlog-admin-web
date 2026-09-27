import { theme as antdTheme } from "antd";
import { FunctionComponent } from "react";

const DefaultGlobalStyle: FunctionComponent = () => {
    const { token } = antdTheme.useToken();

    return (
        <style>
            {`
            .ant-modal-close {
              border-radius: 50% !important;
            }
            .ant-btn-variant-text {
                border-radius: ${token.borderRadius}px !important;
            }
            .ant-drawer-close {
                border-radius: 50% !important;
            }
            .ant-dropdown-menu-item {
                border-radius: ${token.borderRadius}px !important;
            }
            .ant-select-item-option {
                border-radius: ${token.borderRadius}px !important;
            }
            .ant-menu-sub {
                border-radius: ${token.borderRadiusLG}px !important;
            }
          `}
        </style>
    );
};

export default DefaultGlobalStyle;
