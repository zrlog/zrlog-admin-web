import type { UserPreferencePage } from "./account-page-routes";
import { AdminTheme, getRes, setRes } from "./constants";
import { changeAppState } from "../base/ConfigProviderApp";
import { getColorPrimaryByRes, isDarkModeByRes } from "../base/AppInit";

export type UserPreferences = {
    language?: "zh_CN" | "en_US" | null;
    appearance?: {
        theme?: AdminTheme | null;
        darkMode?: boolean | null;
        compactMode?: boolean | null;
        colorPrimary?: string | null;
    } | null;
    articlePageSize?: number | null;
    editor?: { autoSaveInterval?: number | null } | null;
    assistant?: {
        knowledgeScope?: "off" | "own_public" | "own_all" | "accessible_public" | "accessible_all" | null;
    } | null;
};

export type UserPreferencesResponse = {
    overrides: UserPreferences;
    defaults: UserPreferences;
    effective: UserPreferences;
};

let appearanceRevision = 0;
export const getUserPreferenceRevision = () => appearanceRevision;

export const applyUserPreferences = (preferences: UserPreferences) => {
    appearanceRevision++;
    const appearance = preferences.appearance;
    setRes({
        ...getRes(),
        lang: preferences.language ?? undefined,
        admin_theme: appearance?.theme ?? undefined,
        admin_darkMode: appearance?.darkMode ?? undefined,
        admin_compactMode: appearance?.compactMode ?? undefined,
        admin_color_primary: appearance?.colorPrimary ?? undefined,
    });
    changeAppState({
        lang: preferences.language ?? "zh_CN",
        theme: appearance?.theme ?? "default",
        dark: isDarkModeByRes(),
        compactMode: appearance?.compactMode ?? false,
        colorPrimary: getColorPrimaryByRes(),
    });
    document.documentElement.lang = preferences.language === "en_US" ? "en" : "zh";
};

// The form displays effective values, while only edited fields become personal overrides.
export const resolveUserPreferences = (defaults: UserPreferences, overrides: UserPreferences): UserPreferences => ({
    language: overrides.language ?? defaults.language,
    articlePageSize: overrides.articlePageSize ?? defaults.articlePageSize,
    appearance: {
        theme: overrides.appearance?.theme ?? defaults.appearance?.theme,
        darkMode: overrides.appearance?.darkMode ?? defaults.appearance?.darkMode,
        compactMode: overrides.appearance?.compactMode ?? defaults.appearance?.compactMode,
        colorPrimary: overrides.appearance?.colorPrimary ?? defaults.appearance?.colorPrimary,
    },
    editor: { autoSaveInterval: overrides.editor?.autoSaveInterval ?? defaults.editor?.autoSaveInterval },
    assistant: { knowledgeScope: overrides.assistant?.knowledgeScope ?? defaults.assistant?.knowledgeScope },
});

export const mergeUserPreferenceChanges = (current: UserPreferences, changes: UserPreferences): UserPreferences => ({
    ...current,
    ...changes,
    ...(changes.appearance ? { appearance: { ...current.appearance, ...changes.appearance } } : {}),
    ...(changes.assistant ? { assistant: { ...current.assistant, ...changes.assistant } } : {}),
    ...(changes.editor ? { editor: { ...current.editor, ...changes.editor } } : {}),
});

// A separate preferences page edits only its own fields, including when resetting defaults.
export const replaceUserPreferencePage = (
    current: UserPreferences,
    edited: UserPreferences,
    page: UserPreferencePage
): UserPreferences => {
    const fields: Record<UserPreferencePage, Array<keyof UserPreferences>> = {
        appearance: ["language", "appearance"],
        writing: ["articlePageSize", "editor"],
        assistant: ["assistant"],
    };
    const result = { ...current };
    fields[page].forEach((key) => {
        if (edited[key] === undefined) delete result[key];
        else Object.assign(result, { [key]: edited[key] });
    });
    return result;
};
