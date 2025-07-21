import { AppState } from "../type";
import { ConfigProviderProps, MappingAlgorithm, theme } from "antd";
import useGlassTheme from "../base/theme/glassTheme";
import useShadcnTheme from "../base/theme/shadcnTheme";
import useGeekTheme from "../base/theme/geekTheme";
import useCartoonTheme from "../base/theme/cartoonTheme";
import useIllustrationTheme from "../base/theme/illustrationTheme";
import useBootstrapTheme from "../base/theme/bootstrapTheme";
import useMuiTheme from "../base/theme/muiTheme";

const { darkAlgorithm, defaultAlgorithm, compactAlgorithm } = theme;

export const themeAlgorithms = (appState: AppState) => {
    const themeAlgorithms: MappingAlgorithm[] = [];

    if (appState.dark) {
        themeAlgorithms.push(darkAlgorithm);
    } else {
        themeAlgorithms.push(defaultAlgorithm);
    }

    if (appState.compactMode) {
        themeAlgorithms.push(compactAlgorithm);
    }
    return themeAlgorithms;
};

export const useThemeConfig = (appState: AppState): ConfigProviderProps => {
    const glassTheme = useGlassTheme(appState.colorPrimary);
    const shadcn = useShadcnTheme();
    const geek = useGeekTheme();
    const cartoon = useCartoonTheme();
    const illustration = useIllustrationTheme();
    const bootstrap = useBootstrapTheme(appState);
    const defaultTheme = useMuiTheme(appState);

    let theme: ConfigProviderProps;
    if (appState.theme === "antd") {
        theme = {
            theme: {
                algorithm: themeAlgorithms(appState),
                token: {
                    colorPrimary: appState.colorPrimary,
                },
            },
        };
    } else if (appState.theme === "glass") {
        theme = glassTheme;
    } else if (appState.theme === "shadcn") {
        theme = shadcn;
    } else if (appState.theme === "geek") {
        theme = geek;
    } else if (appState.theme === "cartoon") {
        theme = cartoon;
    } else if (appState.theme === "illustration") {
        theme = illustration;
    } else if (appState.theme === "bootstrap") {
        theme = bootstrap;
    } else {
        theme = defaultTheme;
    }
    return theme;
};
