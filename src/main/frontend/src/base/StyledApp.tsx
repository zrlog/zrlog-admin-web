import { FunctionComponent, PropsWithChildren } from "react";
import { theme as antdTheme } from "antd";

type StyledAppProps = {
    theme: string;
};

const StyledApp: FunctionComponent<PropsWithChildren<StyledAppProps>> = ({ theme: themeName }) => {
    const { token } = antdTheme.useToken();

    if (themeName === "default") {
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
    }
    return <></>;
};

export default StyledApp;
