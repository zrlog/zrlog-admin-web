import { useUiTheme } from "@zrlog/ui";
import type { AppState } from "../type";

// Translate the admin state at the host boundary; theme definitions live in @zrlog/ui.
export const useThemeConfig = (appState: AppState) =>
    useUiTheme({
        theme: appState.theme,
        colorPrimary: appState.colorPrimary,
        dark: appState.dark,
        compactMode: appState.compactMode,
    });
