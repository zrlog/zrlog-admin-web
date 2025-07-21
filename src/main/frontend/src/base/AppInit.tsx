import AppBase, { useAxiosBaseInstance } from "base/AppBase";
import { FunctionComponent, useEffect, useRef, useState } from "react";
import { AdminTheme, getRes, isStaticPage, setBackendServerUrl, setRes } from "../utils/constants";
import Init from "../components/init";
import Spin from "antd/es/spin";
import UnknownErrorPage from "../components/unknown-error-page";
import { AppState } from "../type";
import { changeAppState, getAppState } from "./ConfigProviderApp";
import { getSsDate } from "./SsData";

type AppInitProps = {
    offline: boolean;
    lang: string;
    onInit: (data: AppState) => void;
};

type AppInitState = {
    resLoaded: boolean;
    resLoadErrorMsg: string;
    requiredBackendServerUrl: boolean;
};

export const getColorPrimaryByRes = (): string => {
    return getColorByTheme(getThemeByRes());
};

export const getColorByTheme = (theme: string) => {
    if (theme === "geek") {
        return "#39ff14";
    }
    if (theme === "cartoon") {
        return "#225555";
    }
    if (theme === "shadcn") {
        return "#262626";
    }
    if (theme === "illustration") {
        return "#52C41A";
    }
    const color: string | undefined = getRes().admin_color_primary;
    if (color === undefined || (color as string).length === 0) {
        return "#1677ff";
    }
    return color;
};

const getPreferredColorScheme = (): string => {
    if (window.matchMedia) {
        if (window.matchMedia("(prefers-color-scheme: dark)").matches) {
            return "dark";
        } else {
            return "dark";
        }
    }
    return "light";
};

export const isSupportDarkMode = (theme: string): boolean => {
    return theme === "antd" || theme === "default";
};

export const isDarkByTheme = (theme: string) => {
    if (theme === "geek") {
        return true;
    }
    if (
        theme === "cartoon" ||
        theme === "shadcn" ||
        theme === "illustration" ||
        theme === "bootstrap" ||
        theme === "glass"
    ) {
        return false;
    }
    const configDarkMode = getRes().admin_darkMode;
    if (configDarkMode !== undefined) {
        return configDarkMode;
    }
    return getPreferredColorScheme() === "dark";
};

export const isDarkModeByRes = (): boolean => {
    return isDarkByTheme(getThemeByRes());
};

export const isCompactModeByRes = (): boolean => {
    return getRes().admin_compactMode === true;
};

export const getLangByRes = (): "en_US" | "zh_CN" => {
    const lang = getRes().lang;
    if (lang !== undefined) {
        return lang;
    }
    return "zh_CN";
};

export const getThemeByRes = (): AdminTheme => {
    const theme = getRes().admin_theme;
    if (theme !== undefined) {
        return theme;
    }
    return "default";
};

export const getLangByAppState = (): "en_US" | "zh_CN" => {
    return getAppState().lang;
};

const AppInit: FunctionComponent<AppInitProps> = ({ lang, offline }) => {
    const [appState, setAppState] = useState<AppInitState>({
        resLoaded: false,
        resLoadErrorMsg: "",
        requiredBackendServerUrl: false,
    });

    const axiosInstance = useAxiosBaseInstance();

    const loadResourceFromServer = (baseUrl: string) => {
        const resourceApi = "/api/public/adminResource";
        if (baseUrl.length > 0) {
            axiosInstance.defaults.baseURL = baseUrl;
        }
        axiosInstance
            .get(resourceApi)
            .then(({ data }: { data: Record<string, any> }) => {
                if (baseUrl.length > 0) {
                    setBackendServerUrl(baseUrl);
                }
                handleRes(data.data);
            })
            .catch((e) => {
                const errorMsg =
                    "Request " + axiosInstance.defaults.baseURL + resourceApi.substring(1) + " error -> " + e.message;
                //console.info(errorMsg);
                if (isStaticPage()) {
                    setAppState((prevState) => {
                        return {
                            ...prevState,
                            resLoaded: true,
                            requiredBackendServerUrl: true,
                        };
                    });
                    return;
                }
                setAppState((prevState) => {
                    return {
                        ...prevState,
                        resLoadErrorMsg: errorMsg,
                        resLoaded: false,
                    };
                });
            });
    };
    const handleRes = (data: Record<string, any>) => {
        setRes(data);
        const mergedRes = getRes();
        setRes({
            ...data,
            copyrightTips:
                mergedRes.copyright + ' <a target="_blank" href="https://blog.zrlog.com/about.html?footer">ZrLog</a>',
        });
        // @ts-ignore
        if (window.inited === undefined || window.inited === null) {
            changeAppState({
                lang: data.lang,
                dark: isDarkModeByRes(),
                colorPrimary: getColorPrimaryByRes(),
                compactMode: isCompactModeByRes(),
                theme: getThemeByRes(),
            });
            // @ts-ignore
            window.inited = true;
        } else {
            changeAppState({
                lang: data.lang,
            });
        }

        setAppState((prevState) => {
            return {
                ...prevState,
                resLoadErrorMsg: "",
                resLoaded: true,
                requiredBackendServerUrl: false,
            };
        });
    };

    const initRes = () => {
        const resourceData = getSsDate().resourceInfo;
        if (resourceData === undefined || resourceData === null || Object.keys(resourceData).length === 0) {
            loadResourceFromServer("");
        } else {
            handleRes(resourceData);
        }
    };

    const langFirst = useRef<boolean>(true);

    useEffect(() => {
        initRes();
    }, []);

    useEffect(() => {
        if (langFirst.current) {
            langFirst.current = false;
            return;
        }
        const resourceData = getSsDate().resourceInfo || {};
        handleRes({
            ...resourceData,
            lang,
        });
    }, [lang]);

    if (appState.requiredBackendServerUrl) {
        return (
            <Init
                lang={lang}
                onSubmit={(backendServerUrl) => {
                    loadResourceFromServer(backendServerUrl);
                }}
            />
        );
    } else if (appState.resLoaded) {
        return <AppBase offline={offline} />;
    } else if (appState.resLoadErrorMsg.length === 0) {
        return <Spin delay={1000} fullscreen={true} />;
    }
    return (
        <UnknownErrorPage
            code={500}
            data={{ message: appState.resLoadErrorMsg }}
            style={{ width: "100vw", height: "100vh" }}
        />
    );
};

export default AppInit;
