import { FunctionComponent, PropsWithChildren } from "react";

type StyledAppProps = {
    theme: string;
};

const StyledApp: FunctionComponent<PropsWithChildren<StyledAppProps>> = ({ theme }) => {
    if (theme === "default") {
        return (
            <style>
                {`
            .ant-modal-close {
              border-radius: 50% !important;
            }
            .ant-btn-variant-text {
                border-radius: 12px !important;
            }
            .ant-drawer-close {
                border-radius: 50% !important;
            }
            .ant-dropdown-menu-item {
                border-radius: 12px !important;
            }
            .ant-select-item-option {
                border-radius: 12px !important;
            }
            .ant-menu-sub {
                border-radius: 28px !important;
            }
          `}
            </style>
        );
    }
    return <></>;
};

export default StyledApp;
