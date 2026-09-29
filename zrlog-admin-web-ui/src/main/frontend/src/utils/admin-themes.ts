// Compatibility names for admin consumers; catalogue and capabilities are owned by @zrlog/ui.
export {
    UI_THEMES as ADMIN_THEMES,
    getUiThemeDefinition as getAdminThemeDefinition,
    getUiThemeOptions as getAdminThemeOptions,
    supportsDarkMode,
    supportsCustomPrimary,
} from "@zrlog/ui/themes";
export type { UiTheme as AdminTheme } from "@zrlog/ui/themes";
