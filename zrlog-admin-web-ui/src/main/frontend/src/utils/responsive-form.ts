import { Grid } from "antd";
import type { FormProps } from "antd";

export const desktopFormLayout: FormProps = {
    labelCol: { span: 8 },
    wrapperCol: { span: 16 },
};

const verticalFormLayout: FormProps = {
    layout: "vertical",
};

export const useResponsiveFormLayout = (layout: FormProps = desktopFormLayout) => {
    const screens = Grid.useBreakpoint();
    const narrow = screens.md !== true;

    return {
        formLayout: narrow ? verticalFormLayout : layout,
        narrow,
        screens,
    };
};
