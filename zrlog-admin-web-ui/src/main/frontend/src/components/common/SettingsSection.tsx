import { useId } from "react";
import type { ReactNode } from "react";
import { theme as antdTheme } from "antd";

type SettingsSectionProps = {
    title?: ReactNode;
    extra?: ReactNode;
    divided?: boolean;
    children: ReactNode;
};

const SettingsSection = ({ title, extra, divided = false, children }: SettingsSectionProps) => {
    const { token: theme } = antdTheme.useToken();
    const titleId = useId();
    return (
        <section
            aria-labelledby={title ? titleId : undefined}
            style={{
                minWidth: 0,
                ...(divided && {
                    borderTop: `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`,
                    paddingTop: theme.paddingLG,
                }),
            }}
        >
            {(title || extra) && (
                <div
                    style={{
                        display: "flex",
                        alignItems: "center",
                        justifyContent: title ? "space-between" : "flex-end",
                        flexWrap: "wrap",
                        gap: theme.marginSM,
                        marginBottom: theme.margin,
                    }}
                >
                    {title && (
                        <h3
                            id={titleId}
                            style={{
                                margin: 0,
                                fontSize: theme.fontSizeHeading5,
                                lineHeight: theme.lineHeightHeading5,
                                fontWeight: theme.fontWeightStrong,
                            }}
                        >
                            {title}
                        </h3>
                    )}
                    {extra}
                </div>
            )}
            {children}
        </section>
    );
};

export default SettingsSection;
