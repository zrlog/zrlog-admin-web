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
    closeLabel,
    brand,
    onClose,
    children,
}) => {
    const theme = useTheme();
    const borderSecondary = `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`;

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
                        className="sidebar-drawer-close"
                        aria-label={closeLabel}
                        onClick={onClose}
                        icon={<CloseOutlined style={{ fontSize: 18 }} />}
                    />
                </div>
            }
            className="admin-navigation-drawer"
            rootStyle={{
                position: "fixed",
                top: 0,
                left: 0,
                height: "100dvh",
                maxHeight: "100dvh",
            }}
            styles={{
                header: {
                    padding: 0,
                    borderBottom: borderSecondary,
                    background: theme.colorBgElevated,
                    flexShrink: 0,
                },
                body: {
                    display: "flex",
                    flexDirection: "column",
                    flex: 1,
                    minHeight: 0,
                    padding: "10px 0 0",
                    background: theme.colorBgElevated,
                    overflow: "hidden",
                },
                content: {
                    display: "flex",
                    flexDirection: "column",
                    height: "100dvh",
                    maxHeight: "100dvh",
                },
                wrapper: {
                    height: "100dvh",
                    maxHeight: "100dvh",
                    boxShadow: theme.boxShadowSecondary,
                },
            }}
        >
            <div className="sidebar-shell">{children}</div>
        </Drawer>
    );
};

export default AdminNavigationDrawer;
