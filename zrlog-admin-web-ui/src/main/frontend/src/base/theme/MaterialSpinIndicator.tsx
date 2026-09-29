import type { CSSProperties } from "react";

type MaterialSpinIndicatorProps = {
    className?: string;
    style?: CSSProperties;
    percent?: number;
};

// ConfigProvider supplies this only for the default theme. Spin owns sizing,
// delay, the loading region and aria-busy; the indicator only draws the arc.
const MaterialSpinIndicator = ({ className, style, percent }: MaterialSpinIndicatorProps) => {
    const value =
        typeof percent === "number" && Number.isFinite(percent) ? Math.max(0, Math.min(100, percent)) : undefined;

    return (
        <span
            className={["admin-material-spin", className].filter(Boolean).join(" ")}
            style={style}
            data-indeterminate={value === undefined}
        >
            <svg
                viewBox="0 0 48 48"
                focusable="false"
                aria-hidden={value === undefined ? true : undefined}
                role={value === undefined ? undefined : "progressbar"}
                aria-valuemin={value === undefined ? undefined : 0}
                aria-valuemax={value === undefined ? undefined : 100}
                aria-valuenow={value}
            >
                <circle
                    cx="24"
                    cy="24"
                    r="20"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="4"
                    strokeLinecap="round"
                    pathLength="100"
                    strokeDasharray={`${value ?? 70} 100`}
                    transform="rotate(-90 24 24)"
                    opacity={value === 0 ? 0 : 1}
                />
            </svg>
        </span>
    );
};

export default MaterialSpinIndicator;

export const materialSpinIndicator = <MaterialSpinIndicator />;
