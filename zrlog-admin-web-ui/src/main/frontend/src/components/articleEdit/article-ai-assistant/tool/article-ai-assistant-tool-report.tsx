import Typography from "@zrlog/ui/antd/Typography";
import Progress from "@zrlog/ui/antd/Progress";
import { Empty } from "antd";
import { useTheme } from "antd-style";
import { Children, ReactNode } from "react";
import { getRes } from "../../../../utils/constants";
import { getScoreStrokeColor } from "./article-ai-assistant-score-color";

export const ToolReportSummary = ({ score, summary }: { score?: number; summary?: string }) => {
    const theme = useTheme();
    return (
        <div style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: theme.margin }}>
            {score !== undefined && (
                <div style={{ flex: "0 0 auto", minWidth: 104 }}>
                    <Typography.Text type="secondary" style={{ display: "block", fontSize: theme.fontSizeSM }}>
                        {getRes().articleEdit.assistant.overallScore}
                    </Typography.Text>
                    <div style={{ display: "flex", alignItems: "baseline", gap: theme.marginXXS }}>
                        <Typography.Text
                            strong
                            style={{ fontSize: theme.fontSizeHeading2, lineHeight: theme.lineHeightHeading2 }}
                        >
                            {score}
                        </Typography.Text>
                        <Typography.Text type="secondary">/ 100</Typography.Text>
                    </div>
                    <Progress
                        percent={score}
                        size="small"
                        showInfo={false}
                        strokeColor={getScoreStrokeColor(score, theme)}
                        aria-label={getRes().articleEdit.assistant.overallScore}
                    />
                </div>
            )}
            {summary && (
                <Typography.Paragraph style={{ flex: "1 1 200px", minWidth: 0, margin: 0, whiteSpace: "pre-wrap" }}>
                    {summary}
                </Typography.Paragraph>
            )}
        </div>
    );
};

export const ToolReportList = ({ children }: { children: ReactNode }) => {
    const theme = useTheme();
    if (Children.count(children) === 0) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} />;
    return (
        <ul role="list" style={{ listStyle: "none", padding: 0, margin: 0, marginTop: theme.marginSM }}>
            {children}
        </ul>
    );
};

export const ToolReportItem = ({
    title,
    extra,
    children,
}: {
    title: string;
    extra?: ReactNode;
    children: ReactNode;
}) => {
    const theme = useTheme();
    return (
        <li
            style={{
                paddingBlock: theme.paddingSM,
                borderTop: `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`,
            }}
        >
            <div
                style={{
                    display: "flex",
                    flexWrap: "wrap",
                    alignItems: "baseline",
                    gap: theme.marginXS,
                    marginBottom: theme.marginXS,
                }}
            >
                <Typography.Title
                    level={4}
                    style={{ flex: "1 1 160px", minWidth: 0, margin: 0, fontSize: theme.fontSizeLG }}
                >
                    {title}
                </Typography.Title>
                {extra}
            </div>
            {children}
        </li>
    );
};

export const ToolReportDetail = ({
    label,
    children,
    secondary = false,
    copyable = false,
}: {
    label?: string;
    children: string;
    secondary?: boolean;
    copyable?: boolean;
}) => {
    const theme = useTheme();
    return (
        <div style={{ marginTop: theme.marginXXS, whiteSpace: "pre-wrap" }}>
            {label && (
                <Typography.Text type="secondary" style={{ marginInlineEnd: theme.marginXS }}>
                    {label}
                </Typography.Text>
            )}
            <Typography.Text type={secondary ? "secondary" : undefined} copyable={copyable}>
                {children}
            </Typography.Text>
        </div>
    );
};
