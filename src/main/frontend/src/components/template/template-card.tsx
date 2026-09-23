import { Card, Col, Space, Tag, Typography } from "antd";
import { CheckCircleOutlined, EyeOutlined, SkinOutlined } from "@ant-design/icons";
import { useState } from "react";
import { useTheme } from "antd-style";
import { getBackendServerUrl, getRes } from "../../utils/constants";
import { getTemplatePreviewUrl } from "./template-model";
import TemplateActions from "./template-actions";
import { TemplateAuthor, TemplateBadges } from "./template-details";
import type { TemplateEntry } from "./template-model";

const TemplateCard = ({
    template,
    onUpdate,
    onConfigure,
    selected = false,
}: {
    template: TemplateEntry;
    onUpdate: () => void;
    onConfigure: (template: TemplateEntry) => void;
    selected?: boolean;
}) => {
    const theme = useTheme();
    const [imageFailed, setImageFailed] = useState(false);
    return (
        <Col xs={24} md={12} xl={8} style={{ display: "flex" }}>
            <Card
                style={{
                    width: "100%",
                    overflow: "hidden",
                    display: "flex",
                    flexDirection: "column",
                    borderColor: template.use || selected ? theme.colorPrimary : undefined,
                }}
                styles={{ body: { display: "flex", flexDirection: "column", flex: 1 } }}
                cover={
                    <div
                        style={{
                            position: "relative",
                            background: theme.colorFillAlter,
                            aspectRatio: "16 / 9",
                            borderBottom: `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`,
                        }}
                    >
                        {imageFailed || !template.adminPreviewImage ? (
                            <div
                                role="img"
                                aria-label={template.name}
                                style={{
                                    height: "100%",
                                    display: "flex",
                                    flexDirection: "column",
                                    gap: theme.marginSM,
                                    alignItems: "center",
                                    justifyContent: "center",
                                    color: theme.colorTextSecondary,
                                }}
                            >
                                <SkinOutlined style={{ fontSize: theme.fontSizeHeading1 }} />
                                <Typography.Text type="secondary">{template.name}</Typography.Text>
                            </div>
                        ) : (
                            <img
                                src={getTemplatePreviewUrl(template.adminPreviewImage, getBackendServerUrl())}
                                alt={template.name}
                                onError={() => setImageFailed(true)}
                                style={{ width: "100%", height: "100%", objectFit: "contain", display: "block" }}
                            />
                        )}
                        {(template.use || template.preview) && (
                            <Tag
                                color={template.use ? "success" : "processing"}
                                icon={template.use ? <CheckCircleOutlined /> : <EyeOutlined />}
                                style={{ position: "absolute", top: theme.paddingSM, left: theme.paddingSM }}
                            >
                                {template.use ? getRes().templateConfig.inUse : getRes().templateConfig.inPreview}
                            </Tag>
                        )}
                    </div>
                }
            >
                <Space direction="vertical" size="middle" style={{ width: "100%", flex: 1 }}>
                    <div
                        style={{
                            display: "flex",
                            justifyContent: "space-between",
                            alignItems: "baseline",
                            gap: theme.marginXS,
                        }}
                    >
                        <Space wrap size="small" style={{ minWidth: 0 }}>
                            <Typography.Title level={5} style={{ margin: 0, overflowWrap: "anywhere" }}>
                                {template.name}
                            </Typography.Title>
                            <TemplateBadges template={template} status={false} />
                        </Space>
                        <Typography.Text type="secondary" style={{ whiteSpace: "nowrap" }}>
                            v{template.version}
                        </Typography.Text>
                    </div>
                    <Typography.Paragraph
                        type="secondary"
                        ellipsis={{ rows: 2, tooltip: template.digest }}
                        style={{ margin: 0, minHeight: theme.fontSize * theme.lineHeight * 2 }}
                    >
                        {template.digest}
                    </Typography.Paragraph>
                    <Space wrap size="small">
                        <TemplateAuthor template={template} />
                        {template.tags?.map((tag) => (
                            <Tag key={tag}>{tag}</Tag>
                        ))}
                    </Space>
                </Space>
                <div
                    style={{
                        marginTop: theme.margin,
                        paddingTop: theme.paddingSM,
                        borderTop: `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`,
                    }}
                >
                    <TemplateActions template={template} onUpdate={onUpdate} onConfigure={onConfigure} />
                </div>
            </Card>
        </Col>
    );
};

export default TemplateCard;
