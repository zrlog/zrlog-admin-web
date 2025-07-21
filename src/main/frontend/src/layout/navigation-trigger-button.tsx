import { MenuOutlined } from "@ant-design/icons";
import { Button } from "antd";
import { FunctionComponent } from "react";
import { useTheme } from "antd-style";
import { getAppState } from "../base/ConfigProviderApp";

type NavigationTriggerButtonProps = {
    active: boolean;
    dark: boolean;
    siderWidth: number;
    onClick: () => void;
};

const NavigationTriggerButton: FunctionComponent<NavigationTriggerButtonProps> = ({
    active,
    dark,
    siderWidth,
    onClick,
}) => {
    const theme = useTheme();

    return (
        <div
            style={{
                textAlign: "center",
                width: siderWidth,
                height: "100%",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
            }}
        >
            <Button
                type="text"
                onClick={onClick}
                style={{
                    width: 32,
                    minWidth: 32,
                    height: 32,
                    borderRadius: theme.borderRadiusLG,
                    color: active ? getAppState().colorPrimary : dark ? "rgba(255,255,255,0.88)" : "#0f0f0f",
                    background: active ? (dark ? "rgba(255,255,255,0.08)" : "rgba(15,23,42,0.06)") : "transparent",
                    boxShadow: "none",
                    paddingInline: 7,
                }}
                icon={<MenuOutlined style={{ fontSize: 18 }} />}
            />
        </div>
    );
};

export default NavigationTriggerButton;
