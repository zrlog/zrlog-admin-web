import { Button, Space, theme as antdTheme } from "antd";
import { ReactNode } from "react";
import { getRes } from "../../utils/constants";
import { useResponsiveFormLayout } from "../../utils/responsive-form";

type SettingsSubmitBarProps = {
    disabled?: boolean;
    loading?: boolean;
    label?: string;
    actions?: ReactNode;
    children?: ReactNode;
};

const SettingsSubmitBar = ({ disabled, loading, label, actions, children }: SettingsSubmitBarProps) => {
    const { token: theme } = antdTheme.useToken();
    const { narrow } = useResponsiveFormLayout();
    const spacing = narrow ? theme.paddingSM : theme.padding;
    return (
        <div
            style={{
                position: "sticky",
                bottom: 0,
                paddingTop: spacing,
                paddingBottom: `calc(${spacing}px + env(safe-area-inset-bottom))`,
                background: theme.colorBgContainer,
                zIndex: 10,
                marginTop: narrow ? theme.marginSM : theme.marginLG,
                borderTop: `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`,
            }}
        >
            <Space wrap>
                <Button enterKeyHint="enter" loading={loading} disabled={disabled} type="primary" htmlType="submit">
                    {label ?? getRes().submit}
                </Button>
                {actions}
            </Space>
            {children}
        </div>
    );
};

export default SettingsSubmitBar;
