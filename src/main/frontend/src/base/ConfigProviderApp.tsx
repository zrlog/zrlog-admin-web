import { App, ConfigProvider } from "antd";
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
import StyledApp from "./StyledApp";

type ChangeAbleState = AppCompactModeState | AppColorPrimaryState | AppDarkState | AppLangState | AppThemeState;

export const changeAppState = (appState: ChangeAbleState | AppState) => {
    //@ts-ignore
    window.changeAppState(appState);
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
    //console.info(gAppState);
    return gAppState;
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

    //@ts-ignore
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
            <App>
                <StyleProvider transformers={[legacyLogicalPropertiesTransformer]}>
                    <StyledApp theme={appState.theme} />
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
                                element={
                                    <AppInit
                                        lang={appState.lang}
                                        offline={appState.offline}
                                        onInit={(newState) => {
                                            setState((prevState) => {
                                                return {
                                                    ...prevState,
                                                    ...newState,
                                                };
                                            });
                                        }}
                                    />
                                }
                            />
                        </Routes>
                    </BrowserRouter>
                </StyleProvider>
            </App>
        </ConfigProvider>
    );
};

export default ConfigProviderApp;
