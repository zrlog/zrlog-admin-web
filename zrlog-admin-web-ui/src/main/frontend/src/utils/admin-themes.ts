// Single catalogue for theme identity, display order and appearance capabilities.
// Labels remain in i18n; component token implementations remain in base/theme.
export const ADMIN_THEMES = [
    { id: "default", colorMode: "selectable" },
    { id: "desk", colorMode: "light", primaryColor: "#172033" },
    { id: "antd", colorMode: "selectable" },
    { id: "bootstrap", colorMode: "light" },
    { id: "geek", colorMode: "dark", primaryColor: "#39ff14" },
    { id: "cartoon", colorMode: "light", primaryColor: "#225555" },
    { id: "glass", colorMode: "light" },
    { id: "shadcn", colorMode: "light", primaryColor: "#262626" },
    { id: "illustration", colorMode: "light", primaryColor: "#52C41A" },
] as const;

export type AdminTheme = (typeof ADMIN_THEMES)[number]["id"];
type AdminThemeDefinition = { id: AdminTheme; colorMode: "selectable" | "light" | "dark"; primaryColor?: string };

export const getAdminThemeDefinition = (id: string): AdminThemeDefinition =>
    ADMIN_THEMES.find((item) => item.id === id) ?? ADMIN_THEMES[0];

export const supportsDarkMode = (id: string) => getAdminThemeDefinition(id).colorMode === "selectable";
export const supportsCustomPrimary = (id: string) => getAdminThemeDefinition(id).primaryColor === undefined;
export const getAdminThemeOptions = (labels: Record<AdminTheme, string>) =>
    ADMIN_THEMES.map(({ id }) => ({ value: id, label: labels[id] }));
