import { Button, List, Progress, Space, Typography } from "antd";
import { CSSProperties, FunctionComponent } from "react";
import { useTheme } from "antd-style";
import { AssistantToolPayload } from "../../article-ai-assistant.types";
import { getScoreStrokeColor } from "../article-ai-assistant-score-color";
import { PublishCheckTarget } from "../../../index.types";
import { getRes } from "../../../../../utils/constants";

type PublishCheckResultPayload = Extract<AssistantToolPayload, { tool: "score" | "publishCheck" }>;

type PublishCheckResultProps = {
    toolPayload: PublishCheckResultPayload;
    style?: CSSProperties;
    onLocateTarget?: (target: PublishCheckTarget) => void;
};

const publishCheckTargetPatterns: { target: PublishCheckTarget; pattern: RegExp }[] = [
    { target: "title", pattern: /标题|title/i },
    { target: "alias", pattern: /别名|链接别名|alias|slug/i },
    { target: "digest", pattern: /摘要|简介|description|digest|summary|excerpt/i },
    { target: "tags", pattern: /标签|关键词|关键字|tag|keyword/i },
    { target: "cover", pattern: /封面|缩略图|cover|thumbnail/i },
    {
        target: "settings",
        pattern:
            /结构化数据|静态|同步|AI 使用|AI 辅助|schema|json-ld|BlogPosting|static|sync|structured|AI usage|AI-assisted|fact check/i,
    },
    { target: "markdown", pattern: /正文|内容|资源|图片|链接|markdown|content|asset|image|link|url/i },
];

const resolvePublishCheckTarget = (name?: string, suggestion?: string) => {
    const text = [name, suggestion].filter(Boolean).join(" ");
    const matched = publishCheckTargetPatterns.find(({ pattern }) => pattern.test(text));
    return matched?.target;
};

const PublishCheckResult: FunctionComponent<PublishCheckResultProps> = ({ toolPayload, style, onLocateTarget }) => {
    const theme = useTheme();
    const scoreResult = toolPayload.payload;
    const score = scoreResult.score || 0;
    const items = scoreResult.items || [];

    return (
        <Space
            direction="vertical"
            size={8}
            style={{
                width: "100%",
                paddingInlineEnd: 4,
                ...style,
            }}
        >
            <Progress percent={score} strokeColor={getScoreStrokeColor(score, theme)} />
            {scoreResult.summary && (
                <Typography.Paragraph style={{ marginBottom: 0 }}>{scoreResult.summary}</Typography.Paragraph>
            )}
            <List
                size="small"
                dataSource={items}
                renderItem={(item) => {
                    const target = onLocateTarget ? resolvePublishCheckTarget(item.name, item.suggestion) : undefined;
                    return (
                        <List.Item>
                            <Space direction="vertical" size={2} style={{ width: "100%" }}>
                                <Space
                                    style={{
                                        width: "100%",
                                        justifyContent: "space-between",
                                        alignItems: "center",
                                    }}
                                >
                                    <Space>
                                        <Typography.Text>{item.name}</Typography.Text>
                                        <Typography.Text type="secondary">{item.score}</Typography.Text>
                                    </Space>
                                    {target && (
                                        <Button
                                            size="small"
                                            type="link"
                                            style={{ paddingInline: 0 }}
                                            onClick={() => onLocateTarget?.(target)}
                                        >
                                            {getRes().articleEdit.publishCheck.locate}
                                        </Button>
                                    )}
                                </Space>
                                <Progress
                                    percent={item.score}
                                    size="small"
                                    showInfo={false}
                                    strokeColor={getScoreStrokeColor(item.score, theme)}
                                />
                                <Typography.Text type="secondary">{item.suggestion}</Typography.Text>
                            </Space>
                        </List.Item>
                    );
                }}
            />
        </Space>
    );
};

export default PublishCheckResult;
