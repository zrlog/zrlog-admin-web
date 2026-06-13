import HtmlPreviewPanel from "@editor/dist/editor/html-preview-panel";
import { Empty, Space, Tag, Typography } from "antd";
import { useTheme } from "antd-style";
import type { CSSProperties, FunctionComponent, ReactNode } from "react";
import Tags from "../../common/Tags";

type ArticlePreviewSnapshotProps = {
    htmlContent?: string;
    dark: boolean;
    tagText?: ReactNode;
    versionText?: ReactNode;
    digest?: string;
    digestLabel?: ReactNode;
    keywords?: string;
    keywordsLabel?: ReactNode;
    emptyDescription: ReactNode;
    style?: CSSProperties;
    previewStyle?: CSSProperties;
};

const ArticlePreviewSnapshot: FunctionComponent<ArticlePreviewSnapshotProps> = ({
    htmlContent,
    dark,
    tagText,
    versionText,
    digest,
    digestLabel,
    keywords,
    keywordsLabel,
    emptyDescription,
    style,
    previewStyle,
}) => {
    const theme = useTheme();
    const content = htmlContent || "";
    const hasHeader = Boolean(tagText || versionText || digest);
    const hasKeywords = Boolean(keywords);
    const borderSecondary = `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`;
    const headerItemStyle: CSSProperties = {
        paddingTop: theme.padding,
        paddingBottom: theme.padding,
    };
    const contentFrameStyle: CSSProperties = hasHeader || hasKeywords ? headerItemStyle : { height: "100%" };
    const headerItems: ReactNode[] = [];

    if (tagText || versionText) {
        headerItems.push(
            <Space wrap>
                {tagText && <Tag color="processing">{tagText}</Tag>}
                {versionText && <Tag>{versionText}</Tag>}
            </Space>
        );
    }

    if (digest) {
        headerItems.push(
            <>
                {digestLabel && (
                    <div>
                        <Typography.Text type="secondary">{digestLabel}</Typography.Text>
                    </div>
                )}
                <HtmlPreviewPanel htmlContent={digest} dark={dark} style={{ width: "100%", overflowY: "visible" }} />
            </>
        );
    }

    return (
        <div style={{ display: "flex", flexDirection: "column", height: "100%", ...style }}>
            {hasHeader && (
                <div
                    style={{
                        borderBottom: borderSecondary,
                    }}
                >
                    <Space direction="vertical" size={0} style={{ width: "100%" }}>
                        {headerItems.map((item, index) => (
                            <div
                                key={index}
                                style={{
                                    ...headerItemStyle,
                                    borderTop: index > 0 ? borderSecondary : undefined,
                                }}
                            >
                                {item}
                            </div>
                        ))}
                    </Space>
                </div>
            )}
            <div style={{ flex: 1, overflow: "auto" }}>
                <div style={contentFrameStyle}>
                    {content.trim() ? (
                        <HtmlPreviewPanel
                            htmlContent={content}
                            dark={dark}
                            style={{
                                width: "100%",
                                height: hasHeader || hasKeywords ? undefined : "100%",
                                ...previewStyle,
                            }}
                        />
                    ) : (
                        <Empty description={emptyDescription} />
                    )}
                </div>
                {keywords && (
                    <div
                        style={{
                            ...headerItemStyle,
                            borderTop: borderSecondary,
                        }}
                    >
                        {keywordsLabel && <Typography.Text type="secondary">{keywordsLabel}</Typography.Text>}
                        <Tags closeable={false} keywords={keywords} />
                    </div>
                )}
            </div>
        </div>
    );
};

export default ArticlePreviewSnapshot;
