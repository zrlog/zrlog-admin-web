import { App, ConfigProvider, theme as antdTheme } from "antd";
import { useEffect, useState } from "react";
import { isOffline } from "../utils/env-utils";
import { getContextPath } from "../utils/helpers";
import zh_CN from "antd/es/locale/zh_CN";
import en_US from "antd/es/locale/en_US";
import { legacyLogicalPropertiesTransformer, StyleProvider } from "@ant-design/cssinjs";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import AppInit, {
    getColorPrimaryByRes,
    getLangByRes,
    getThemeByRes,
    isCompactModeByRes,
    isDarkModeByRes,
} from "./AppInit";
import {
    AppColorPrimaryState,
    AppCompactModeState,
    AppDarkState,
    AppLangState,
    AppState,
    AppThemeState,
} from "../type";
import { useThemeConfig } from "../utils/theme-utils";
import ThemeGlobalStyle from "./theme/ThemeGlobalStyle";

type ChangeAbleState = AppCompactModeState | AppColorPrimaryState | AppDarkState | AppLangState | AppThemeState;

declare global {
    interface Window {
        changeAppState?: (appState: ChangeAbleState | AppState) => void;
    }
}

export const changeAppState = (appState: ChangeAbleState | AppState) => {
    window.changeAppState?.(appState);
};

const getDefaultAppState = (): AppState => {
    return {
        lang: getLangByRes(),
        dark: isDarkModeByRes(),
        colorPrimary: getColorPrimaryByRes(),
        offline: isOffline(),
        theme: getThemeByRes(),
        compactMode: isCompactModeByRes(),
    };
};

let gAppState = getDefaultAppState();

export const getAppState = (): AppState => {
    return gAppState;
};

type ConfiguredAppContentProps = {
    appState: AppState;
    basePath: string;
    onInit: (newState: ChangeAbleState) => void;
};

const ConfiguredAppContent = ({ appState, basePath, onInit }: ConfiguredAppContentProps) => {
    const { token } = antdTheme.useToken();

    return (
        <ConfigProvider
            avatar={{
                style: {
                    borderRadius: token.borderRadiusLG,
                },
            }}
        >
            <App>
                <StyleProvider transformers={[legacyLogicalPropertiesTransformer]}>
                    <ThemeGlobalStyle theme={appState.theme} />
                    <BrowserRouter
                        basename={basePath}
                        future={{
                            v7_relativeSplatPath: true,
                            v7_startTransition: true,
                        }}
                    >
                        <Routes>
                            <Route
                                path={"/*"}
                                element={<AppInit lang={appState.lang} offline={appState.offline} onInit={onInit} />}
                            />
                        </Routes>
                    </BrowserRouter>
                </StyleProvider>
            </App>
        </ConfigProvider>
    );
};

const ConfigProviderApp = () => {
    const [appState, setState] = useState<AppState>(gAppState);

    const updateOnlineStatus = () => {
        setState((prevState) => {
            return {
                ...prevState,
                offline: isOffline(),
            };
        });
    };

    window.changeAppState = (newAppState: ChangeAbleState) => {
        setState((prevState) => {
            gAppState = {
                ...prevState,
                ...newAppState,
            };
            return gAppState;
        });
    };

    useEffect(() => {
        window.addEventListener("online", updateOnlineStatus);
        window.addEventListener("offline", updateOnlineStatus);
        // Cleanup event listeners on component unmount
        return () => {
            window.removeEventListener("online", updateOnlineStatus);
            window.removeEventListener("offline", updateOnlineStatus);
        };
    }, []);

    const basePath = getContextPath() + "admin";

    useEffect(() => {
        window.document.body.setAttribute("class", appState.dark ? "dark" : "light");
    }, [appState.dark]);

    const configProviderProps = useThemeConfig(appState);

    return (
        <ConfigProvider
            table={{
                style: {
                    whiteSpace: "nowrap",
                },
            }}
            drawer={{
                closable: {
                    placement: "end",
                },
            }}
            divider={{
                style: {
                    margin: "16px 0",
                },
            }}
            {...configProviderProps}
            locale={appState.lang.startsWith("en") ? en_US : zh_CN}
            componentSize={appState.compactMode ? "small" : undefined}
        >
            <ConfiguredAppContent
                appState={appState}
                basePath={basePath}
                onInit={(newState) => {
                    setState((prevState) => {
                        return {
                            ...prevState,
                            ...newState,
                        };
                    });
                }}
            />
        </ConfigProvider>
    );
};

export default ConfigProviderApp;
