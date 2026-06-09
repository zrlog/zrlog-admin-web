import { useTheme } from "antd-style";
import type { CSSProperties, FunctionComponent } from "react";

type HighlightTextProps = {
    text?: string;
    keyword?: string;
    caseSensitive?: boolean;
    className?: string;
    style?: CSSProperties;
};

const escapeRegExp = (value: string) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

const HighlightText: FunctionComponent<HighlightTextProps> = ({
    text,
    keyword,
    caseSensitive = false,
    className,
    style,
}) => {
    const theme = useTheme();
    const plainText = text || "";
    const normalizedKeyword = (keyword || "").trim();

    if (!normalizedKeyword) {
        return (
            <span className={className} style={style}>
                {plainText}
            </span>
        );
    }

    const regexp = new RegExp(`(${escapeRegExp(normalizedKeyword)})`, caseSensitive ? "g" : "gi");
    const parts = plainText.split(regexp);

    return (
        <span className={className} style={style}>
            {parts.map((part, index) => {
                if (!part) {
                    return null;
                }
                const matched = caseSensitive
                    ? part === normalizedKeyword
                    : part.toLocaleLowerCase() === normalizedKeyword.toLocaleLowerCase();
                if (!matched) {
                    return <span key={index}>{part}</span>;
                }
                return (
                    <mark
                        key={index}
                        style={{
                            padding: 0,
                            borderRadius: theme.borderRadiusSM,
                            backgroundColor: theme.colorWarningBg,
                            color: "inherit",
                        }}
                    >
                        {part}
                    </mark>
                );
            })}
        </span>
    );
};

export default HighlightText;
