import { ThemeStyles } from "@zrlog/ui";
import { FunctionComponent } from "react";
import DefaultGlobalStyle from "./DefaultGlobalStyle";
import DeskGlobalStyle from "./DeskGlobalStyle";
import { getAdminThemeDefinition } from "../../utils/admin-themes";

type ThemeGlobalStyleProps = {
    theme?: string | null;
};

const ThemeGlobalStyle: FunctionComponent<ThemeGlobalStyleProps> = ({ theme: themeName }) => {
    const { id } = getAdminThemeDefinition(themeName ?? "");
    return (
        <>
            <ThemeStyles theme={id} />
            {id === "default" && <DefaultGlobalStyle />}
            {id === "desk" && <DeskGlobalStyle />}
        </>
    );
};

export default ThemeGlobalStyle;
