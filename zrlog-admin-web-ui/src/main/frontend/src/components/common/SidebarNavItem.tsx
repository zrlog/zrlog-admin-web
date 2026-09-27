import { CSSProperties, FunctionComponent, ReactNode } from "react";
import { theme } from "antd";

type SidebarNavItemProps = {
    icon?: ReactNode;
    label: ReactNode;
    active?: boolean;
    style?: CSSProperties;
};

const SidebarNavItem: FunctionComponent<SidebarNavItemProps> = ({ icon, label }) => {
    const { token } = theme.useToken();

    return (
        <span
            style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "flex-start",
                gap: 8,
                minWidth: 0,
                width: "100%",
                height: 32,
                borderRadius: token.borderRadiusSM,
                fontWeight: 400,
            }}
        >
            {icon ? (
                <span
                    style={{
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        width: 16,
                        fontSize: 16,
                    }}
                >
                    {icon}
                </span>
            ) : null}
            <span
                style={{
                    minWidth: 0,
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                    display: "flex",
                    alignItems: "center",
                }}
            >
                {label}
            </span>
        </span>
    );
};

export default SidebarNavItem;
