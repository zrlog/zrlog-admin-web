import Row from "antd/es/grid/row";
import Col from "antd/es/grid/col";
import {
    CheckCircleOutlined,
    CloudDownloadOutlined,
    DeleteOutlined,
    EyeOutlined,
    GlobalOutlined,
    LoadingOutlined,
    SettingOutlined,
    SkinOutlined,
    TagsOutlined,
} from "@ant-design/icons";
import Button from "antd/es/button";
import { useEffect, useMemo, useState } from "react";
import {
    getBackendServerUrl,
    getLabelValueSeparator,
    getRealRouteUrl,
    getRes,
    isStaticPage,
} from "../../utils/constants";
import { Link, useLocation } from "react-router-dom";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getCsrData, getTimeInfoBySearchStr } from "../../api";
import { addToCache } from "../../utils/cache";
import TemplateCard from "./template-card";
import ThemeUpload from "./theme-upload";
import { Empty, Grid, Image, message, Popconfirm, Segmented, Select, Space, Statistic, Tag, Typography } from "antd";
import Card from "antd/es/card";
import { postRefreshCacheSse } from "../../utils/sse-utils";

export type TemplateEntry = {
    template: string;
    deleteAble: boolean;
    use: boolean;
    name: string;
    shortTemplate: string;
    previewImage: string;
    adminPreviewImage: string;
    preview: boolean;
    digest: string;
    version: string;
    author?: string;
    url?: string;
    tags: string[];
};

const Template = ({ data }: { data: TemplateEntry[] }) => {
    const [templateState, setTemplateState] = useState<TemplateEntry[]>(data);
    const [selectedTemplateName, setSelectedTemplateName] = useState<string>();
    const [filter, setFilter] = useState<string>("all");
    const [applyingTemplate, setApplyingTemplate] = useState<string>();
    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });
    const screens = Grid.useBreakpoint();
    const compactFilter = screens.lg !== true;

    const axiosInstance = useAxiosBaseInstance();

    const location = useLocation();

    const load = () => {
        getCsrData("/template", getTimeInfoBySearchStr(location.search), axiosInstance).then(({ data }) => {
            setTemplateState(data);
            addToCache(data, location.pathname);
        });
    };

    useEffect(() => {
        setSelectedTemplateName(new URLSearchParams(location.search).get("shortTemplate") || undefined);
    }, [location.search]);

    useEffect(() => {
        setTemplateState(data);
    }, [data]);

    const getHost = () => {
        if (isStaticPage()) {
            return new URL(getBackendServerUrl()).host;
        }
        return window.location.host;
    };

    const activeTemplate = templateState.find((template) => template.use);
    const previewTemplate = templateState.find((template) => template.preview && !template.use);
    const selectedTemplate =
        templateState.find((template) => template.shortTemplate === selectedTemplateName) ||
        previewTemplate ||
        activeTemplate ||
        templateState[0];
    const filterOptions = [
        { label: getRes().websiteTemplate.allThemes, value: "all" },
        { label: getRes().templateConfig.inUse, value: "active" },
        { label: getRes().templateConfig.inPreview, value: "preview" },
        { label: getRes().websiteTemplate.removableThemes, value: "removable" },
    ];
    const filteredTemplates = useMemo(() => {
        const unselectedTemplates = templateState.filter(
            (template) => template.shortTemplate !== selectedTemplate?.shortTemplate
        );
        if (filter === "active") {
            return unselectedTemplates.filter((template) => template.use);
        }
        if (filter === "preview") {
            return unselectedTemplates.filter((template) => template.preview);
        }
        if (filter === "removable") {
            return unselectedTemplates.filter((template) => template.deleteAble);
        }
        return unselectedTemplates;
    }, [filter, selectedTemplate?.shortTemplate, templateState]);

    const preview = (template: TemplateEntry) => {
        axiosInstance.post("/api/admin/template/preview?shortTemplate=" + template.shortTemplate).then(() => {
            window.open(document.baseURI, "_blank");
            load();
        });
    };

    const apply = (template: TemplateEntry) => {
        setApplyingTemplate(template.shortTemplate);
        postRefreshCacheSse<any>("/api/admin/template/apply?shortTemplate=" + template.shortTemplate, {
            messageApi,
            messageKey: "templateApplyRefreshCache",
            backgroundTaskTitle: getRes().backgroundTask.title + " · " + template.name,
        })
            .then(async (data) => {
                if (data.error) {
                    await messageApi.error(data.message);
                    return;
                }
                load();
            })
            .finally(() => {
                setApplyingTemplate(undefined);
            });
    };

    const deleteTemplate = (template: TemplateEntry) => {
        axiosInstance
            .post("/api/admin/template/delete?shortTemplate=" + template.shortTemplate)
            .then(async ({ data }) => {
                if (data.error) {
                    await messageApi.error(data.message);
                    return;
                }
                setSelectedTemplateName(undefined);
                load();
            });
    };

    return (
        <Space direction="vertical" size={24} style={{ width: "100%" }}>
            {contextHolder}
            <Row gutter={[20, 20]} align="stretch">
                <Col xs={24} lg={16}>
                    <Card
                        title={getRes().websiteTemplate.selectedTheme}
                        extra={
                            selectedTemplate?.use ? (
                                <Tag icon={<CheckCircleOutlined />} color="success">
                                    {getRes().templateConfig.inUse}
                                </Tag>
                            ) : selectedTemplate?.preview ? (
                                <Tag icon={<EyeOutlined />} color="processing">
                                    {getRes().templateConfig.inPreview}
                                </Tag>
                            ) : null
                        }
                    >
                        {selectedTemplate ? (
                            <Row gutter={[20, 20]} align="middle">
                                <Col xs={24} md={15}>
                                    <Image
                                        alt={selectedTemplate.name}
                                        src={getBackendServerUrl() + selectedTemplate.adminPreviewImage.substring(1)}
                                        style={{
                                            width: "100%",
                                            aspectRatio: "16 / 10",
                                            objectFit: "cover",
                                        }}
                                    />
                                </Col>
                                <Col xs={24} md={9}>
                                    <Space direction="vertical" size={12} style={{ width: "100%" }}>
                                        <Typography.Title level={4} style={{ margin: 0 }}>
                                            {selectedTemplate.name}
                                        </Typography.Title>
                                        <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
                                            {selectedTemplate.digest}
                                        </Typography.Paragraph>
                                        <Space wrap>
                                            <Tag icon={<SkinOutlined />}>
                                                {getRes().websiteTemplate.version} {selectedTemplate.version}
                                            </Tag>
                                            {selectedTemplate.author && (
                                                <Tag icon={<GlobalOutlined />}>
                                                    {selectedTemplate.url ? (
                                                        <Typography.Link
                                                            href={selectedTemplate.url}
                                                            target="_blank"
                                                            rel="noreferrer"
                                                        >
                                                            {getRes().author}
                                                            {getLabelValueSeparator()}
                                                            {selectedTemplate.author}
                                                        </Typography.Link>
                                                    ) : (
                                                        <>
                                                            {getRes().author}
                                                            {getLabelValueSeparator()}
                                                            {selectedTemplate.author}
                                                        </>
                                                    )}
                                                </Tag>
                                            )}
                                            {selectedTemplate.tags?.map((tag) => (
                                                <Tag icon={<TagsOutlined />} key={tag}>
                                                    {tag}
                                                </Tag>
                                            ))}
                                        </Space>
                                        <Typography.Text type="secondary">
                                            {getRes().websiteTemplate.manageDescription}
                                        </Typography.Text>
                                        <Space wrap>
                                            <Button icon={<EyeOutlined />} onClick={() => preview(selectedTemplate)}>
                                                {getRes().websiteTemplate.actions.preview}
                                            </Button>
                                            <Link
                                                to={getRealRouteUrl(
                                                    "/template-config?shortTemplate=" + selectedTemplate.shortTemplate
                                                )}
                                            >
                                                <Button icon={<SettingOutlined />}>
                                                    {getRes().websiteTemplate.actions.config}
                                                </Button>
                                            </Link>
                                            <Button
                                                type="primary"
                                                icon={
                                                    applyingTemplate === selectedTemplate.shortTemplate ? (
                                                        <LoadingOutlined />
                                                    ) : (
                                                        <CheckCircleOutlined />
                                                    )
                                                }
                                                loading={applyingTemplate === selectedTemplate.shortTemplate}
                                                onClick={() => apply(selectedTemplate)}
                                            >
                                                {getRes().websiteTemplate.actions.apply}
                                            </Button>
                                            {selectedTemplate.deleteAble && (
                                                <Popconfirm
                                                    title={getRes().deleteTips}
                                                    onConfirm={() => deleteTemplate(selectedTemplate)}
                                                >
                                                    <Button danger icon={<DeleteOutlined />}>
                                                        {getRes().websiteTemplate.actions.delete}
                                                    </Button>
                                                </Popconfirm>
                                            )}
                                        </Space>
                                    </Space>
                                </Col>
                            </Row>
                        ) : (
                            <Empty description={getRes().websiteTemplate.noSelectedTheme} />
                        )}
                    </Card>
                </Col>
                <Col xs={24} lg={8}>
                    <Space direction="vertical" size={16} style={{ width: "100%" }}>
                        <Card>
                            <Statistic
                                title={getRes().websiteTemplate.activeTheme}
                                value={activeTemplate?.name || "-"}
                                prefix={<SkinOutlined />}
                            />
                        </Card>
                        <Card>
                            <Statistic
                                title={getRes().websiteTemplate.previewTheme}
                                value={previewTemplate?.name || getRes().websiteTemplate.noPreviewTheme}
                                prefix={<EyeOutlined />}
                            />
                        </Card>
                        <Card>
                            <Statistic title={getRes().websiteTemplate.installedThemes} value={templateState.length} />
                        </Card>
                        <ThemeUpload
                            templates={templateState}
                            onInstalled={(shortTemplate) => {
                                setSelectedTemplateName(shortTemplate);
                                load();
                            }}
                        />
                        <Link to={getRealRouteUrl(`/template-center?host=${getHost()}`)}>
                            <Button block icon={<CloudDownloadOutlined />} type="primary">
                                {getRes().websiteTemplate.downloadMore}
                            </Button>
                        </Link>
                    </Space>
                </Col>
            </Row>
            <Space direction="vertical" size={16} style={{ width: "100%" }}>
                <Space
                    align={compactFilter ? undefined : "center"}
                    direction={compactFilter ? "vertical" : "horizontal"}
                    style={{ width: "100%", justifyContent: "space-between" }}
                    wrap={!compactFilter}
                >
                    <Typography.Title level={4} style={{ margin: 0 }}>
                        {getRes().websiteTemplate.themeLibrary}
                    </Typography.Title>
                    {compactFilter ? (
                        <Select
                            value={filter}
                            onChange={(value) => setFilter(value)}
                            options={filterOptions}
                            style={{ width: "100%" }}
                        />
                    ) : (
                        <Segmented
                            value={filter}
                            onChange={(value) => setFilter(String(value))}
                            options={filterOptions}
                        />
                    )}
                </Space>
                {filteredTemplates.length > 0 ? (
                    <Row gutter={[16, 16]}>
                        {filteredTemplates.map((template) => {
                            return (
                                <TemplateCard
                                    key={template.template}
                                    template={template}
                                    onUpdate={load}
                                    selected={selectedTemplate?.shortTemplate === template.shortTemplate}
                                    onSelect={(template) => setSelectedTemplateName(template.shortTemplate)}
                                />
                            );
                        })}
                    </Row>
                ) : (
                    <Empty
                        description={
                            templateState.length > 0
                                ? getRes().websiteTemplate.filterEmpty
                                : getRes().websiteTemplate.empty
                        }
                    />
                )}
            </Space>
        </Space>
    );
};

export default Template;
