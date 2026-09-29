import { useCallback, useEffect, useRef } from "react";
import { getAppState } from "../base/ConfigProviderApp";
import { getSsDate } from "../base/SsData";
import { applyUserPreferences } from "./user-preferences";
import type { UserPreferences } from "./user-preferences";
import { getRes } from "./constants";
import type { AdminTheme } from "./constants";

// Shared by non-persistent appearance previews. Personal preferences keep their
// own save/request lifecycle but all three surfaces use applyUserPreferences.
export const useAppearancePreview = () => {
    const baseline = useRef<{ session: string; preferences: UserPreferences }>();
    const restore = useCallback(() => {
        const saved = baseline.current;
        baseline.current = undefined;
        if (saved && saved.session === getSsDate().key) applyUserPreferences(saved.preferences);
    }, []);
    useEffect(() => restore, [restore]);
    const preview = useCallback((preferences: UserPreferences) => {
        if (!baseline.current) {
            const state = getAppState();
            const res = getRes();
            baseline.current = { session: getSsDate().key, preferences: {
                language: state.lang,
                appearance: { theme: state.theme as AdminTheme, darkMode: state.dark, compactMode: state.compactMode,
                    colorPrimary: res.admin_color_primary ?? state.colorPrimary },
            } };
        }
        applyUserPreferences(preferences);
    }, []);
    return { preview, restore };
};
