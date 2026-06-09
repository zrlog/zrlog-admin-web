import Button from "antd/es/button";
import { useTheme } from "antd-style";
import { getRes } from "../../utils/constants";
import { useResponsiveFormLayout } from "../../utils/responsive-form";

type WebsiteSubmitBarProps = {
    disabled?: boolean;
    loading?: boolean;
};

const WebsiteSubmitBar = ({ disabled, loading }: WebsiteSubmitBarProps) => {
    const theme = useTheme();
    const { narrow } = useResponsiveFormLayout();
    const spacing = narrow ? theme.paddingSM : theme.padding;
    const borderSecondary = `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`;

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
                borderTop: borderSecondary,
            }}
        >
            <Button enterKeyHint="enter" loading={loading} disabled={disabled} type="primary" htmlType="submit">
                {getRes().submit}
            </Button>
        </div>
    );
};

export default WebsiteSubmitBar;
