import { Badge, Card, message, Space, Tag, Tooltip, Typography } from "antd";
import { getBackendServerUrl, getRealRouteUrl, getRes } from "../../utils/constants";
import Col from "antd/es/grid/col";
import { TemplateEntry } from "./index";
import { FunctionComponent, useState } from "react";
import {
    CheckOutlined,
    DeleteOutlined,
    EyeOutlined,
    LoadingOutlined,
    SettingOutlined,
    TagsOutlined,
} from "@ant-design/icons";
import { Link } from "react-router-dom";
import Popconfirm from "antd/es/popconfirm";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { Meta } from "antd/es/list/Item";
import { postRefreshCacheSse } from "../../utils/sse-utils";
import { useTheme } from "antd-style";

type TemplateCardProps = {
    template: TemplateEntry;
    onUpdate: () => void;
    selected?: boolean;
    onSelect?: (template: TemplateEntry) => void;
};

const TemplateCard: FunctionComponent<TemplateCardProps> = ({ template, onUpdate, selected = false, onSelect }) => {
    const axiosInstance = useAxiosBaseInstance();
    const theme = useTheme();

    const [applying, setApplying] = useState<boolean>(false);
    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });

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

    const getActions = (template: TemplateEntry) => {
        const links = [];
        links.push(
            <Tooltip title={getRes().websiteTemplate.actions.preview}>
                <div onClick={() => preview(template.shortTemplate)}>
                    <EyeOutlined key="preview" />
                </div>
            </Tooltip>,
            <Tooltip title={getRes().websiteTemplate.actions.config}>
                <Link to={getRealRouteUrl("/template-config?shortTemplate=" + template.shortTemplate)}>
                    <SettingOutlined key="setting" />
                </Link>
            </Tooltip>,
            <Tooltip title={getRes().websiteTemplate.actions.apply}>
                <Link
                    to={"#apply"}
                    onClick={(e) => {
                        e.preventDefault();
                        apply(template.shortTemplate);
                    }}
                >
                    {applying ? <LoadingOutlined /> : <CheckOutlined />}
                </Link>
            </Tooltip>
        );
        if (template.deleteAble) {
            links.push(
                <Popconfirm
                    title={getRes().deleteTips}
                    onConfirm={() => {
                        deleteTemplate(template.shortTemplate);
                    }}
                >
                    <Tooltip title={getRes().websiteTemplate.actions.delete}>
                        <DeleteOutlined key="delete" />
                    </Tooltip>
                </Popconfirm>
            );
        }
        return links;
    };

    return (
        <>
            {contextHolder}
            <Col lg={8} xl={6} xxl={4} md={12} xs={24}>
                <Badge.Ribbon
                    text={
                        template.use
                            ? getRes().templateConfig.inUse
                            : template.preview
                            ? getRes().templateConfig.inPreview
                            : ""
                    }
                    style={{
                        fontSize: 16,
                        display: template.use || template.preview ? "" : "none",
                    }}
                >
                    <Card
                        hoverable={true}
                        onClick={() => onSelect?.(template)}
                        style={{
                            borderColor: selected ? theme.colorPrimary : undefined,
                        }}
                        cover={
                            <div style={{ overflow: "hidden", position: "relative" }}>
                                <Tag
                                    style={{
                                        position: "absolute",
                                        top: theme.paddingXS,
                                        left: theme.paddingXS,
                                        zIndex: 1,
                                        marginInlineEnd: 0,
                                    }}
                                >
                                    v{template.version}
                                </Tag>
                                <img
                                    style={{
                                        width: "100%",
                                        aspectRatio: "16 / 10",
                                        objectFit: "cover",
                                        display: "block",
                                    }}
                                    alt={template.name}
                                    title={template.name}
                                    src={getBackendServerUrl() + template.adminPreviewImage.substring(1)}
                                />
                            </div>
                        }
                        actions={getActions(template)}
                    >
                        <Meta title={template.name} description={template.digest} />
                        {template.tags?.length > 0 && (
                            <Space size={[4, 4]} wrap style={{ marginTop: theme.marginSM }}>
                                {template.tags.slice(0, 3).map((tag) => (
                                    <Tag key={tag} icon={<TagsOutlined />}>
                                        <Typography.Text style={{ fontSize: theme.fontSizeSM }}>{tag}</Typography.Text>
                                    </Tag>
                                ))}
                            </Space>
                        )}
                    </Card>
                </Badge.Ribbon>
            </Col>
        </>
    );
};

export default TemplateCard;
