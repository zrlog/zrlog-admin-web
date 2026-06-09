import { FunctionComponent, useMemo } from "react";
import { Typography } from "antd";
import { useTheme } from "antd-style";

type DiffLineType = "context" | "add" | "remove";

type DiffLine = {
    type: DiffLineType;
    content: string;
};

type MarkdownDiffViewProps = {
    beforeText: string;
    afterText: string;
    beforeLabel: string;
    afterLabel: string;
    maxHeight?: number | string;
};

const MONOSPACE_FONT = "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace";

export const buildLineDiff = (beforeText: string, afterText: string): DiffLine[] => {
    const before = beforeText.split("\n");
    const after = afterText.split("\n");
    const dp: number[][] = Array.from({ length: before.length + 1 }, () => Array(after.length + 1).fill(0));

    for (let i = before.length - 1; i >= 0; i--) {
        for (let j = after.length - 1; j >= 0; j--) {
            if (before[i] === after[j]) {
                dp[i][j] = dp[i + 1][j + 1] + 1;
            } else {
                dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }
    }

    const lines: DiffLine[] = [];
    let i = 0;
    let j = 0;
    while (i < before.length && j < after.length) {
        if (before[i] === after[j]) {
            lines.push({ type: "context", content: before[i] });
            i++;
            j++;
        } else if (dp[i + 1][j] >= dp[i][j + 1]) {
            lines.push({ type: "remove", content: before[i] });
            i++;
        } else {
            lines.push({ type: "add", content: after[j] });
            j++;
        }
    }

    while (i < before.length) {
        lines.push({ type: "remove", content: before[i] });
        i++;
    }
    while (j < after.length) {
        lines.push({ type: "add", content: after[j] });
        j++;
    }

    return lines;
};

const getDiffLineStyle = (type: DiffLineType, theme: ReturnType<typeof useTheme>) => {
    switch (type) {
        case "add":
            return {
                background: theme.colorSuccessBg,
                color: theme.colorSuccessText,
            };
        case "remove":
            return {
                background: theme.colorErrorBg,
                color: theme.colorErrorText,
            };
        default:
            return {
                background: "transparent",
                color: theme.colorText,
            };
    }
};

const getDiffPrefix = (type: DiffLineType) => {
    switch (type) {
        case "add":
            return "+";
        case "remove":
            return "-";
        default:
            return " ";
    }
};

const MarkdownDiffView: FunctionComponent<MarkdownDiffViewProps> = ({
    beforeText,
    afterText,
    beforeLabel,
    afterLabel,
    maxHeight,
}) => {
    const theme = useTheme();
    const diffBorder = `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`;
    const diffLines = useMemo(() => buildLineDiff(beforeText, afterText), [beforeText, afterText]);
    return (
        <div
            style={{
                border: diffBorder,
                borderRadius: theme.borderRadius,
                overflow: "hidden",
            }}
        >
            <div
                style={{
                    background: theme.colorFillQuaternary,
                    borderBottom: diffBorder,
                    fontFamily: MONOSPACE_FONT,
                    fontSize: 13,
                    padding: "8px 10px",
                }}
            >
                <Typography.Text style={{ color: theme.colorErrorText, display: "block" }}>
                    {`--- ${beforeLabel}`}
                </Typography.Text>
                <Typography.Text style={{ color: theme.colorSuccessText, display: "block" }}>
                    {`+++ ${afterLabel}`}
                </Typography.Text>
            </div>
            <div style={{ maxHeight, overflow: maxHeight ? "auto" : undefined }}>
                {diffLines.map((line, index) => (
                    <div
                        key={`${line.type}-${index}`}
                        style={{
                            ...getDiffLineStyle(line.type, theme),
                            fontFamily: MONOSPACE_FONT,
                            fontSize: 13,
                            lineHeight: 1.6,
                            padding: "4px 10px",
                            whiteSpace: "pre-wrap",
                            wordBreak: "break-word",
                        }}
                    >
                        {`${getDiffPrefix(line.type)} ${line.content}`}
                    </div>
                ))}
            </div>
        </div>
    );
};

export default MarkdownDiffView;
