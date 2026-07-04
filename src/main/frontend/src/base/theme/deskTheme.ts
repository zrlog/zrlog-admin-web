import type { ConfigProviderProps } from "antd";
import { useMemo } from "react";

import type { AppState } from "../../type";
import { themeAlgorithms } from "../../utils/theme-utils";

const deskInk = "#172033";
const deskMuted = "#667085";
const deskPaper = "#fbfaf7";
const deskSurface = "#fffdf8";
const deskLine = "#d9d2c5";
const deskLineStrong = "#b9ae9f";
const deskAccent = deskInk;
const deskAccentSoft = "#ece8df";

const useDeskTheme = (appState: AppState): ConfigProviderProps => {
  return useMemo<ConfigProviderProps>(() => ({
    theme: {
      algorithm: themeAlgorithms(appState),
      token: {
        colorPrimary: deskAccent,
        colorInfo: deskAccent,
        colorLink: deskInk,
        colorLinkHover: deskMuted,
        colorLinkActive: deskInk,
        colorSuccess: "#0f7b45",
        colorWarning: "#a15c00",
        colorError: "#b42318",
        colorText: deskInk,
        colorTextSecondary: deskMuted,
        colorTextTertiary: "#8a8175",
        colorBorder: deskLine,
        colorBorderSecondary: "#e6dfd3",
        colorBgBase: deskPaper,
        colorBgLayout: deskPaper,
        colorBgContainer: deskSurface,
        colorBgElevated: deskSurface,
        colorFillAlter: "#f5f0e8",
        colorFillSecondary: "#f1ebe2",
        borderRadius: 3,
        borderRadiusLG: 4,
        borderRadiusSM: 2,
        borderRadiusXS: 1,
        boxShadow: "0 1px 0 rgba(23, 32, 51, 0.08), 8px 8px 0 rgba(23, 32, 51, 0.05)",
        boxShadowSecondary: "0 1px 0 rgba(23, 32, 51, 0.08), 4px 4px 0 rgba(23, 32, 51, 0.04)",
        boxShadowTertiary: "0 1px 0 rgba(23, 32, 51, 0.08)",
        controlHeight: 34,
        controlHeightLG: 40,
        controlHeightSM: 28,
        controlOutline: "rgba(23, 32, 51, 0.16)",
        fontFamily: [
          "Inter",
          "-apple-system",
          "BlinkMacSystemFont",
          "\"Segoe UI\"",
          "sans-serif",
        ].join(", "),
        fontSize: 14,
        lineWidth: 1,
        lineType: "solid",
        motionDurationFast: "0.08s",
        motionDurationMid: "0.12s",
      },
      components: {
        Alert: {
          borderRadiusLG: 3,
          withDescriptionIconSize: 18,
        },
        App: {
          colorBgLayout: deskPaper,
        },
        Badge: {
          indicatorHeight: 17,
          textFontSize: 11,
        },
        Button: {
          borderRadius: 2,
          controlHeight: 34,
          controlHeightLG: 40,
          defaultBg: deskSurface,
          defaultBorderColor: deskLineStrong,
          defaultColor: deskInk,
          defaultHoverBg: "#f4f0e7",
          defaultHoverBorderColor: deskInk,
          defaultHoverColor: deskInk,
          defaultActiveBg: "#eee7dc",
          defaultActiveBorderColor: deskInk,
          primaryColor: "#ffffff",
          primaryShadow: "none",
          defaultShadow: "none",
          dangerShadow: "none",
          textTextColor: deskInk,
          textTextHoverColor: deskInk,
          textTextActiveColor: deskInk,
          textHoverBg: "#f4f0e7",
          linkHoverBg: "#f4f0e7",
          paddingInline: 13,
          paddingInlineLG: 16,
        },
        Card: {
          borderRadiusLG: 4,
          bodyPadding: 16,
          bodyPaddingSM: 12,
          headerBg: deskSurface,
          headerHeight: 44,
          boxShadow: "0 1px 0 rgba(23, 32, 51, 0.08), 8px 8px 0 rgba(23, 32, 51, 0.05)",
        },
        Checkbox: {
          borderRadiusSM: 2,
        },
        DatePicker: {
          borderRadius: 2,
          activeShadow: "0 0 0 2px rgba(23, 32, 51, 0.12)",
        },
        Drawer: {
          footerPaddingBlock: 12,
          footerPaddingInline: 16,
        },
        Dropdown: {
          borderRadiusLG: 3,
          controlItemBgHover: "#f4f0e7",
        },
        Input: {
          activeBorderColor: deskAccent,
          activeShadow: "0 0 0 2px rgba(23, 32, 51, 0.12)",
          borderRadius: 2,
          hoverBorderColor: deskLineStrong,
        },
        InputNumber: {
          activeBorderColor: deskAccent,
          activeShadow: "0 0 0 2px rgba(23, 32, 51, 0.12)",
          borderRadius: 2,
          hoverBorderColor: deskLineStrong,
        },
        Layout: {
          bodyBg: deskPaper,
          footerBg: deskPaper,
          headerBg: deskSurface,
          siderBg: deskSurface,
        },
        Menu: {
          activeBarBorderWidth: 0,
          itemBg: "transparent",
          itemBorderRadius: 2,
          itemColor: "#414b5d",
          itemHeight: 36,
          itemHoverBg: "#f4f0e7",
          itemHoverColor: deskInk,
          itemSelectedBg: deskAccentSoft,
          itemSelectedColor: deskAccent,
          subMenuItemBg: "transparent",
          subMenuItemBorderRadius: 2,
        },
        Modal: {
          borderRadiusLG: 4,
          contentBg: deskSurface,
          footerBg: deskSurface,
          headerBg: deskSurface,
        },
        Notification: {
          borderRadiusLG: 3,
        },
        Pagination: {
          borderRadius: 2,
          itemActiveBg: deskSurface,
          itemBg: deskSurface,
          itemInputBg: deskSurface,
          itemSize: 32,
        },
        Popover: {
          borderRadiusLG: 3,
        },
        Radio: {
          borderRadiusSM: 2,
        },
        Segmented: {
          borderRadius: 2,
          itemActiveBg: "#ece5da",
          itemHoverBg: "#f4f0e7",
          itemSelectedBg: deskSurface,
        },
        Select: {
          activeBorderColor: deskAccent,
          activeOutlineColor: "rgba(23, 32, 51, 0.12)",
          borderRadius: 2,
          hoverBorderColor: deskLineStrong,
          optionActiveBg: "#f4f0e7",
          optionSelectedBg: deskAccentSoft,
        },
        Slider: {
          handleColor: deskAccent,
          handleActiveColor: deskAccent,
          railBg: "#e5ded3",
          railHoverBg: "#d9d2c5",
          trackBg: deskAccent,
          trackHoverBg: deskAccent,
        },
        Switch: {
          handleBg: deskSurface,
          trackHeight: 20,
          trackMinWidth: 38,
        },
        Table: {
          borderColor: deskLine,
          borderRadius: 3,
          cellPaddingBlock: 12,
          cellPaddingInline: 12,
          headerBg: "#f1ebe2",
          headerColor: "#414b5d",
          rowHoverBg: "#f7f2ea",
        },
        Tabs: {
          cardBg: "#f5f0e8",
          horizontalItemPadding: "10px 0",
          itemSelectedColor: deskAccent,
          titleFontSize: 14,
        },
        Tag: {
          borderRadiusSM: 1,
          defaultBg: "#f5f0e8",
        },
        Tooltip: {
          borderRadius: 3,
        },
        Tree: {
          nodeHoverBg: "#f4f0e7",
          nodeSelectedBg: deskAccentSoft,
        },
        Upload: {
          colorFillAlter: "#f5f0e8",
        },
      },
    },
  }), [appState]);
};

export default useDeskTheme;
