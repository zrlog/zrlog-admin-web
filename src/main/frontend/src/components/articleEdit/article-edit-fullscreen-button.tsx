import { FullscreenExitOutlined, FullscreenOutlined } from "@ant-design/icons";
import { isPWA } from "../../utils/env-utils";
import { Button } from "antd";
import screenfull from "screenfull";
import { FunctionComponent, useEffect } from "react";
import { FullScreenProps } from "./index.types";
import { getEnterFullscreen, getExitFullscreen } from "../../utils/constants";
import { getAppState } from "../../base/ConfigProviderApp";
import { useTheme } from "antd-style";

type ArticleEditFullscreenButton = FullScreenProps & {
    fullScreenElement: HTMLDivElement;
};

const ArticleEditFullscreenButton: FunctionComponent<ArticleEditFullscreenButton> = ({
    fullScreenElement,
    onExitFullScreen,
    onFullScreen,
    fullScreen,
}) => {
    const theme = useTheme();
    const toggleFullScreen = () => {
        if (fullScreen) {
            onfullscreenExit();
        } else {
            onfullscreen();
        }
    };

    const onfullscreen = () => {
        try {
            if (screenfull.isEnabled) {
                void screenfull.request(fullScreenElement).catch((e) => {
                    console.error(e);
                });
            }
        } catch (e) {
            console.error(e);
        } finally {
            onFullScreen();
        }
    };

    const onfullscreenExit = () => {
        if (screenfull.isEnabled) {
            screenfull
                .exit()
                .then(() => {
                    onExitFullScreen();
                })
                .catch((e) => {
                    console.error(e);
                });
        }
    };

    /**
     * 监听：用原生事件判断状态
     */
    const nativeFullscreenChange = () => {
        const isFullscreen = !!document.fullscreenElement;
        if (isFullscreen) {
            // Entering fullscreen is already handled by the click action.
        } else {
            onfullscreenExit();
        }
    };

    useEffect(() => {
        if (fullScreen && isPWA()) {
            onfullscreen();
        }
        document.addEventListener("fullscreenchange", nativeFullscreenChange);
        return () => {
            document.removeEventListener("fullscreenchange", nativeFullscreenChange);
        };
    });

    return (
        <Button
            type={"text"}
            href={fullScreen ? "#exitFullScreen" : "#enterFullScreen"}
            icon={
                fullScreen ? (
                    <FullscreenExitOutlined
                        title={getExitFullscreen()}
                        style={{ fontSize: getAppState().compactMode ? 18 : 24, display: "flex" }}
                    />
                ) : (
                    <FullscreenOutlined
                        title={getEnterFullscreen()}
                        style={{ fontSize: getAppState().compactMode ? 18 : 24, display: "flex" }}
                    />
                )
            }
            style={{
                border: 0,
                display: "flex",
                justifyContent: "center",
                alignItems: "center",
                color: theme.colorTextTertiary,
            }}
            onClick={(e) => {
                toggleFullScreen();
                e.stopPropagation();
                e.preventDefault();
            }}
        />
    );
};

export default ArticleEditFullscreenButton;
