import MenuIcon from "@zrlog/ui/icons/menu";
import { Button } from "antd";
import { FunctionComponent } from "react";
import { useTheme } from "antd-style";
import { getRes } from "../utils/constants";

type NavigationTriggerButtonProps = {
    active: boolean;
    dark: boolean;
    siderWidth: number;
    onClick: () => void;
};

const NavigationTriggerButton: FunctionComponent<NavigationTriggerButtonProps> = ({ active, siderWidth, onClick }) => {
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
                className="admin-navigation-trigger"
                aria-label={getRes().common.management}
                aria-expanded={active}
                type="text"
                onClick={onClick}
                style={{
                    width: 32,
                    minWidth: 32,
                    height: 32,
                    borderRadius: theme.borderRadiusLG,
                    color: active ? theme.colorPrimary : theme.colorText,
                    background: active ? theme.colorFillSecondary : "transparent",
                    boxShadow: "none",
                    paddingInline: 7,
                }}
                icon={<MenuIcon style={{ fontSize: 18 }} />}
            />
        </div>
    );
};

export default NavigationTriggerButton;
