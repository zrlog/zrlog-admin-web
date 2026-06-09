import { CSSProperties, FunctionComponent, useEffect, useState } from "react";
import { getAppState } from "../base/ConfigProviderApp";

type MyLoadingComponentProps = {
    delayMs?: number;
    mode?: "immediate" | "delayed";
};

const MyLoadingComponent: FunctionComponent<MyLoadingComponentProps> = ({ delayMs = 200, mode = "immediate" }) => {
    const delayed = mode === "delayed";
    const [visible, setVisible] = useState(!delayed || delayMs <= 0);
    const colorPrimary = getAppState().colorPrimary;
    const topBarShellStyle: CSSProperties = {
        position: "absolute",
        top: 0,
        left: 0,
        right: 0,
        zIndex: 10,
        width: "100%",
        height: 2,
        backgroundColor: `${colorPrimary}26`,
        overflow: "hidden",
        pointerEvents: "none",
    };

    const topBarIndicatorStyle: CSSProperties = {
        width: "30%",
        height: "100%",
        backgroundColor: colorPrimary,
        animation: "adminTopLoadingSlide 1.2s ease-in-out infinite",
    };

    useEffect(() => {
        if (!delayed || delayMs <= 0) {
            setVisible(true);
            return;
        }
        const timer = window.setTimeout(() => setVisible(true), delayMs);
        return () => window.clearTimeout(timer);
    }, [delayMs, delayed]);

    if (!visible) {
        return null;
    }

    return (
        <>
            <style>
                {`
                    @keyframes adminTopLoadingSlide {
                        0% { transform: translateX(-120%); }
                        100% { transform: translateX(420%); }
                    }
                `}
            </style>
            <div style={topBarShellStyle}>
                <div style={topBarIndicatorStyle} />
            </div>
        </>
    );
};

export default MyLoadingComponent;
