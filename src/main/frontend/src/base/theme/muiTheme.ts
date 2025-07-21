import { useMemo } from "react";
import raf from "@rc-component/util/lib/raf";
import type { ConfigProviderProps, GetProp } from "antd";
import { colorToRgba } from "../../layout/slider";
import { getAppState } from "../ConfigProviderApp";
import { AppState } from "../../type";
import { themeAlgorithms } from "../../utils/theme-utils";

type WaveConfig = GetProp<ConfigProviderProps, "wave">;

// Prepare effect holder
const createHolder = (node: HTMLElement) => {
    const { borderWidth } = getComputedStyle(node);
    const borderWidthNum = Number.parseInt(borderWidth, 10);

    const div = document.createElement("div");
    div.style.position = "absolute";
    div.style.inset = `-${borderWidthNum}px`;
    div.style.borderRadius = "inherit";
    div.style.background = "transparent";
    div.style.zIndex = "999";
    div.style.pointerEvents = "none";
    div.style.overflow = "hidden";
    node.appendChild(div);

    return div;
};

const createDot = (holder: HTMLElement, color: string, left: number, top: number, size = 0) => {
    const dot = document.createElement("div");
    dot.style.position = "absolute";
    dot.style.left = `${left}px`;
    dot.style.top = `${top}px`;
    dot.style.width = `${size}px`;
    dot.style.height = `${size}px`;
    dot.style.borderRadius = "50%";
    dot.style.background = color;
    dot.style.transform = "translate3d(-50%, -50%, 0)";
    dot.style.transition = "all 1s ease-out";
    holder.appendChild(dot);
    return dot;
};

// Inset Effect
const showInsetEffect: WaveConfig["showEffect"] = (node, { event, component }) => {
    if (component !== "Button") {
        return;
    }

    const holder = createHolder(node);

    const rect = holder.getBoundingClientRect();

    const left = event.clientX - rect.left;
    const top = event.clientY - rect.top;

    const dot = createDot(holder, "rgba(255, 255, 255, 0.65)", left, top);

    // Motion
    raf(() => {
        dot.ontransitionend = () => {
            holder.remove();
        };

        dot.style.width = "200px";
        dot.style.height = "200px";
        dot.style.opacity = "0";
    });
};

const useMuiTheme = (appState: AppState) => {
    return useMemo<ConfigProviderProps>(
        () => ({
            theme: {
                algorithm: themeAlgorithms(appState),
                token: {
                    // ── MD3 Color ──────────────────────────────────────────────────
                    colorPrimary: appState.colorPrimary,
                    wireframe: false,
                    lineHeight: 1.5, // MD3 standard

                    // ── MD3 Shape ──────────────────────────────────────────────────
                    // MD3 Shape Scale:
                    //   None=0, ExtraSmall=4, Small=8, Medium=12,
                    //   Large=16, ExtraLarge=28, Full=∞
                    borderRadius: 12, // Medium — cards, chips, menus
                    borderRadiusSM: 8, // Extra Small — text fields, list items
                    borderRadiusLG: 16, // Large — sheets, nav drawers
                    borderRadiusXS: 8,
                    controlHeight: 40,

                    // ── MD3 Elevation (Shadow) ─────────────────────────────────────
                    // Level 1 — Cards, menus (surface tint + shadow)
                    boxShadow: appState.dark
                        ? "0 1px 2px rgba(0,0,0,0.45), 0 1px 3px 1px rgba(0,0,0,0.35)"
                        : "0 1px 2px rgba(0,0,0,0.10), 0 1px 3px 1px rgba(0,0,0,0.07)",
                    // Level 2 — FABs, dialogs
                    boxShadowSecondary: appState.dark
                        ? "0 1px 2px rgba(0,0,0,0.45), 0 2px 6px 2px rgba(0,0,0,0.35)"
                        : "0 1px 2px rgba(0,0,0,0.10), 0 2px 6px 2px rgba(0,0,0,0.07)",

                    // ── MD3 Surfaces ────────────────────────────────────────────────
                    // Surface / Surface Container / Surface Container Highest
                    colorBgLayout: appState.dark ? "rgba(26, 26, 26, 0.85)" : colorToRgba(appState.colorPrimary, 0.04),
                },
                components: {
                    // ── Button ─────────────────────────────────────────────────────
                    // MD3 Filled Button: full-rounded pill, height 40dp, no border
                    Button: {
                        borderRadius: 20, // Pill shape (MD3 Full)
                        controlHeight: 40, // MD3 standard 40dp
                        fontWeight: 500, // Medium weight
                        primaryShadow: appState.dark
                            ? "0 1px 2px rgba(0,0,0,0.45)"
                            : `0 1px 2px ${appState.colorPrimary}40`,
                        defaultShadow:
                            "0px 3px 1px -2px rgba(0,0,0,0.2), 0px 2px 2px 0px rgba(0,0,0,0.14), 0px 1px 5px 0px rgba(0,0,0,0.12)",
                        dangerShadow:
                            "0px 3px 1px -2px rgba(0,0,0,0.2), 0px 2px 2px 0px rgba(0,0,0,0.14), 0px 1px 5px 0px rgba(0,0,0,0.12)",
                        defaultBorderColor: "rgba(0, 0, 0, 0.23)",
                        defaultColor: "rgba(0, 0, 0, 0.87)",
                        defaultBg: "#ffffff",
                        paddingInline: 16,
                        paddingBlock: 6,
                    },
                    Header: {
                        backgroundColor: appState.dark ? "rgba(26, 26, 26, 0.85)" : appState.colorPrimary + "10",
                    },

                    // ── Input / Text Field ─────────────────────────────────────────
                    // MD3 Outlined Text Field: radius ExtraSmall (4), height 56dp
                    Input: {
                        borderRadius: 12,
                        controlHeight: 40,
                        activeShadow: `0 0 0 2px ${appState.colorPrimary}30`,
                    },
                    InputNumber: {
                        borderRadius: 12,
                        controlHeight: 40,
                        activeShadow: `0 0 0 2px ${appState.colorPrimary}30`,
                    },

                    // ── Card ───────────────────────────────────────────────────────
                    // MD3 Card: radius Medium-Large (12), Elevation Level 1
                    Card: {
                        bodyPadding: 12,
                        borderRadiusLG: 16,
                        paddingLG: 20,
                        boxShadow: appState.dark
                            ? "0 1px 2px rgba(0,0,0,0.45), 0 1px 3px 1px rgba(0,0,0,0.35)"
                            : "0 1px 2px rgba(0,0,0,0.10), 0 1px 3px 1px rgba(0,0,0,0.07)",
                        boxShadowTertiary: appState.dark
                            ? "0 1px 2px rgba(0,0,0,0.45), 0 2px 6px 2px rgba(0,0,0,0.35)"
                            : "0 1px 2px rgba(0,0,0,0.10), 0 2px 6px 2px rgba(0,0,0,0.07)",
                    },

                    // ── Menu / Navigation Rail ─────────────────────────────────────
                    Menu: {
                        itemBorderRadius: 28, // Navigation Rail active indicator (pill)
                        subMenuItemBorderRadius: 28,
                        itemMarginInline: 8,
                        itemSelectedBg: appState.dark ? `${appState.colorPrimary}30` : `${appState.colorPrimary}18`,
                        background: appState.dark ? "transparent" : getAppState().colorPrimary + "10",
                    },

                    // ── Modal / Dialog ─────────────────────────────────────────────
                    // MD3 Dialog: radius Extra Large (28)
                    Modal: {
                        borderRadiusLG: 28,
                    },

                    Alert: {
                        borderRadius: 8,
                    },
                    Table: {
                        borderRadius: 12,
                        cellPaddingBlock: 16,
                        cellPaddingInline: 16,
                    },

                    // ── Tabs ───────────────────────────────────────────────────────
                    Tabs: {
                        borderRadius: 0, // MD3 Tabs use no radius on indicator
                    },

                    // ── Divider ────────────────────────────────────────────────────
                    Divider: {
                        colorSplit: appState.dark ? "#fdfdfd1f" : "#dddddd",
                    },

                    // ── Select ─────────────────────────────────────────────────────
                    Select: {
                        borderRadius: 12,
                        controlHeight: 40,
                    },
                },
            },
            wave: {
                showEffect: showInsetEffect,
            },
        }),
        [appState]
    );
};
export default useMuiTheme;
