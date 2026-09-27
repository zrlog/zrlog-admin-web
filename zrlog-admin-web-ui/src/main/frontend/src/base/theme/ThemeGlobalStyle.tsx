import { FunctionComponent } from "react";
import DefaultGlobalStyle from "./DefaultGlobalStyle";
import DeskGlobalStyle from "./DeskGlobalStyle";

type ThemeGlobalStyleProps = {
    theme: string;
};

const ThemeGlobalStyle: FunctionComponent<ThemeGlobalStyleProps> = ({ theme: themeName }) => {
    if (themeName === "default") {
        return <DefaultGlobalStyle />;
    }
    if (themeName === "desk") {
        return <DeskGlobalStyle />;
    }
    return <></>;
};

export default ThemeGlobalStyle;
