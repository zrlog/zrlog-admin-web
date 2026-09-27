import { Grid } from "antd";
import { useTheme } from "antd-style";

// Ant Design subscribes after the initial render. Use the real viewport until
// that subscription is ready so the header and its buttons agree from mount.
const useArticleEditorScreens = () => {
    const screens = Grid.useBreakpoint();
    const theme = useTheme();
    if (Object.keys(screens).length > 0) {
        return screens;
    }
    const matches = (query: string) => typeof window !== "undefined" && window.matchMedia(query).matches;
    return {
        xs: matches(`(max-width: ${theme.screenXSMax}px)`),
        sm: matches(`(min-width: ${theme.screenSM}px)`),
        md: matches(`(min-width: ${theme.screenMD}px)`),
        lg: matches(`(min-width: ${theme.screenLG}px)`),
        xl: matches(`(min-width: ${theme.screenXL}px)`),
        xxl: matches(`(min-width: ${theme.screenXXL}px)`),
    };
};

export default useArticleEditorScreens;
