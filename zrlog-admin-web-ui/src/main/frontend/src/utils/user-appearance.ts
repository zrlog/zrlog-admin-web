import type {AdminRuntimeResourceInfo} from "./constants";
import type {UserPreferences} from "./user-preferences";

export type UserAppearance = Pick<UserPreferences, "language" | "appearance">;

export const hasUserAppearance = (user: UserAppearance | null | undefined): boolean =>
    !!user?.language && !!user.appearance;

// Shared by synchronous bootstrap and live updates, without importing the React provider.
export const userAppearanceResources = (preferences: UserAppearance): Partial<AdminRuntimeResourceInfo> => ({
    lang: preferences.language ?? undefined,
    admin_theme: preferences.appearance?.theme ?? undefined,
    admin_darkMode: preferences.appearance?.darkMode ?? undefined,
    admin_compactMode: preferences.appearance?.compactMode ?? undefined,
    admin_color_primary: preferences.appearance?.colorPrimary ?? undefined,
});
