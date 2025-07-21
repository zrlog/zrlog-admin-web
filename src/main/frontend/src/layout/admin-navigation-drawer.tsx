import { CloseOutlined } from "@ant-design/icons";
import { Button, Drawer } from "antd";
import { FunctionComponent, ReactNode } from "react";
import { useTheme } from "antd-style";

type AdminNavigationDrawerProps = {
    open: boolean;
    width: number;
    dark: boolean;
    closeLabel: string;
    brand: ReactNode;
    onClose: () => void;
    children: ReactNode;
};

const AdminNavigationDrawer: FunctionComponent<AdminNavigationDrawerProps> = ({
    open,
    width,
    dark,
    closeLabel,
    brand,
    onClose,
    children,
}) => {
    const theme = useTheme();

    return (
        <Drawer
            placement="left"
            open={open}
            onClose={onClose}
            closable={false}
            getContainer={false}
            width={width}
            title={
                <div className="sidebar-drawer-header">
                    {brand}
                    <Button
                        type="text"
                        aria-label={closeLabel}
                        onClick={onClose}
                        style={{
                            width: 32,
                            minWidth: 32,
                            height: 32,
                            borderRadius: theme.borderRadiusLG,
                            color: dark ? "rgba(255,255,255,0.88)" : "#0f0f0f",
                            flexShrink: 0,
                            paddingInline: 7,
                        }}
                        icon={<CloseOutlined style={{ fontSize: 18 }} />}
                    />
                </div>
            }
            className="admin-navigation-drawer"
            rootStyle={{
                position: "absolute",
                top: 0,
                left: 0,
                height: "100%",
            }}
            styles={{
                header: {
                    padding: "10px 10px 0",
                    borderBottom: "none",
                    background: dark ? "rgba(26, 26, 26, 0.95)" : "rgba(255, 255, 255, 0.95)",
                },
                body: {
                    padding: "0 0 20px",
                    background: dark ? "rgba(26, 26, 26, 0.95)" : "rgba(255, 255, 255, 0.95)",
                },
                wrapper: {
                    boxShadow: dark ? "18px 0 36px rgba(0,0,0,0.34)" : "18px 0 36px rgba(15,23,42,0.08)",
                },
            }}
        >
            <div className="sidebar-shell">{children}</div>
        </Drawer>
    );
};

export default AdminNavigationDrawer;
