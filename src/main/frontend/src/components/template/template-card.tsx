import { Button, Card, message, Space, Tag, Tooltip, Typography, Popconfirm } from "antd";
import { getBackendServerUrl, getRealRouteUrl, getRes } from "../../utils/constants";
import Col from "antd/es/grid/col";
import type { TemplateEntry } from "./index";
import { useState } from "react";
import { CheckCircleOutlined, DeleteOutlined, EyeOutlined, SettingOutlined, SkinOutlined } from "@ant-design/icons";
import { postRefreshCacheSse } from "../../utils/sse-utils";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { useTheme } from "antd-style";
import { Link } from "react-router-dom";

const TemplateCard = ({
    template,
    onUpdate,
    selected = false,
}: {
    template: TemplateEntry;
    onUpdate: () => void;
    selected?: boolean;
}) => {
    const axiosInstance = useAxiosBaseInstance();
    const theme = useTheme();
    const res = getRes().websiteTemplate;
    const [applying, setApplying] = useState(false);
    const [imageFailed, setImageFailed] = useState(false);
    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });
    const templateConfigPath = getRealRouteUrl("/template-config?shortTemplate=" + template.shortTemplate);
    const preview = (shortTemplate: string) => {
        axiosInstance.post("/api/admin/template/preview?shortTemplate=" + shortTemplate).then(() => {
            window.open(document.baseURI, "_blank");
            onUpdate();
        });
    };

    const apply = (shortTemplate: string) => {
        setApplying(true);
        postRefreshCacheSse<any>("/api/admin/template/apply?shortTemplate=" + shortTemplate, {
            messageApi,
            messageKey: "templateApplyRefreshCache",
            backgroundTaskTitle: getRes().backgroundTask.title + " · " + template.name,
        })
            .then(async (data) => {
                if (data.error) {
                    await messageApi.error(data.message);
                    return;
                }
                onUpdate();
            })
            .finally(() => {
                setApplying(false);
            });
    };

    const deleteTemplate = (shortTemplate: string) => {
        axiosInstance.post("/api/admin/template/delete?shortTemplate=" + shortTemplate).then(async ({ data }) => {
            if (data.error) {
                await messageApi.error(data.message);
                return;
            }
            onUpdate();
        });
    };

    return (
        <Col xs={24} md={12} xl={8} style={{ display: "flex" }}>
            {contextHolder}
            <Card
                style={{
                    width: "100%",
                    overflow: "hidden",
                    borderColor: template.use || selected ? theme.colorPrimary : undefined,
                }}
                styles={{ body: { padding: theme.paddingLG } }}
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
                                    gap: 12,
                                    alignItems: "center",
                                    justifyContent: "center",
                                    color: theme.colorTextSecondary,
                                }}
                            >
                                <SkinOutlined style={{ fontSize: 40 }} />
                                <Typography.Text type="secondary">{template.name}</Typography.Text>
                            </div>
                        ) : (
                            <img
                                src={getBackendServerUrl() + template.adminPreviewImage.substring(1)}
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
                <Space direction="vertical" size={16} style={{ width: "100%" }}>
                    <div>
                        <div
                            style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", gap: 8 }}
                        >
                            <Typography.Title level={5} ellipsis={{ tooltip: template.name }} style={{ margin: 0 }}>
                                {template.name}
                            </Typography.Title>
                            <Typography.Text
                                type="secondary"
                                style={{ whiteSpace: "nowrap", fontSize: theme.fontSizeSM }}
                            >
                                v{template.version}
                            </Typography.Text>
                        </div>
                        <Typography.Paragraph
                            type="secondary"
                            ellipsis={{ rows: 2, tooltip: template.digest }}
                            style={{
                                marginTop: theme.marginXS,
                                marginBottom: 0,
                                minHeight: theme.fontSize * theme.lineHeight * 2,
                            }}
                        >
                            {template.digest}
                        </Typography.Paragraph>
                    </div>
                    <div style={{ minHeight: 22, display: "flex", justifyContent: "space-between", gap: 8 }}>
                        <Typography.Text type="secondary" ellipsis style={{ fontSize: theme.fontSizeSM }}>
                            {template.author}
                        </Typography.Text>
                        <Space size={0}>
                            {template.tags?.slice(0, 2).map((tag) => (
                                <Tag key={tag}>{tag}</Tag>
                            ))}
                        </Space>
                    </div>
                    <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
                        <Space size={8}>
                            {template.use ? (
                                <Link to={templateConfigPath}>
                                    <Button type="primary" icon={<SettingOutlined />}>
                                        {res.actions.config}
                                    </Button>
                                </Link>
                            ) : (
                                <>
                                    <Button icon={<EyeOutlined />} onClick={() => preview(template.shortTemplate)}>
                                        {res.actions.preview}
                                    </Button>
                                    <Button
                                        type="primary"
                                        loading={applying}
                                        onClick={() => apply(template.shortTemplate)}
                                    >
                                        {res.actions.apply}
                                    </Button>
                                </>
                            )}
                        </Space>
                        <Space size={0}>
                            {!template.use && (
                                <Tooltip title={res.actions.config}>
                                    <Link to={templateConfigPath}>
                                        <Button
                                            type="text"
                                            aria-label={res.actions.config}
                                            icon={<SettingOutlined />}
                                        />
                                    </Link>
                                </Tooltip>
                            )}
                            {template.deleteAble && !template.use && (
                                <Popconfirm
                                    title={getRes().deleteTips}
                                    onConfirm={() => deleteTemplate(template.shortTemplate)}
                                >
                                    <Button
                                        type="text"
                                        danger
                                        aria-label={res.actions.delete}
                                        icon={<DeleteOutlined />}
                                    />
                                </Popconfirm>
                            )}
                        </Space>
                    </div>
                </Space>
            </Card>
        </Col>
    );
};

export default TemplateCard;
