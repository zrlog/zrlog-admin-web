import { ThemeStyles } from "@zrlog/ui";
import { FunctionComponent } from "react";
import DefaultGlobalStyle from "./DefaultGlobalStyle";
import DeskGlobalStyle from "./DeskGlobalStyle";

type ThemeGlobalStyleProps = {
    theme: string;
};

const ThemeGlobalStyle: FunctionComponent<ThemeGlobalStyleProps> = ({ theme: themeName }) => {
    return (
        <>
            <ThemeStyles theme={themeName} />
            {themeName === "default" && <DefaultGlobalStyle />}
            {themeName === "desk" && <DeskGlobalStyle />}
        </>
    );
};

export default ThemeGlobalStyle;
