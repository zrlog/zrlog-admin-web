import { CloudDownloadOutlined, UploadOutlined } from "@ant-design/icons";
import { Alert, Button, Empty, Grid, Row, Segmented, Select, Space, Tag, Typography } from "antd";
import { useEffect, useMemo, useState } from "react";
import { getBackendServerUrl, getRealRouteUrl, getRes, isStaticPage } from "../../utils/constants";
import { Link, useLocation } from "react-router-dom";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getCsrData, getTimeInfoBySearchStr } from "../../api";
import { addToCache } from "../../utils/cache";
import TemplateCard from "./template-card";
import ThemeUpload from "./theme-upload";

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
    const [filter, setFilter] = useState("all");
    const [uploadOpen, setUploadOpen] = useState(false);
    const [selectedTemplateName, setSelectedTemplateName] = useState<string>();
    const screens = Grid.useBreakpoint();
    const axiosInstance = useAxiosBaseInstance();
    const location = useLocation();
    const res = getRes().websiteTemplate;

    const load = () => {
        getCsrData("/template", getTimeInfoBySearchStr(location.search), axiosInstance).then(({ data }) => {
            setTemplateState(data);
            addToCache(data, location.pathname);
        });
    };

    useEffect(() => {
        setSelectedTemplateName(new URLSearchParams(location.search).get("shortTemplate") || undefined);
    }, [location.search]);

    useEffect(() => setTemplateState(data), [data]);

    const filteredTemplates = useMemo(
        () =>
            templateState
                .filter((template) => {
                    if (filter === "active") return template.use;
                    if (filter === "preview") return template.preview;
                    if (filter === "removable") return template.deleteAble;
                    return true;
                })
                .sort((a, b) => Number(b.use) - Number(a.use)),
        [filter, templateState]
    );
    const previewTemplate = templateState.find((template) => template.preview && !template.use);
    const filterOptions = [
        { label: res.allThemes, value: "all" },
        { label: getRes().templateConfig.inUse, value: "active" },
        { label: getRes().templateConfig.inPreview, value: "preview" },
        { label: res.removableThemes, value: "removable" },
    ];
    const host = isStaticPage() ? new URL(getBackendServerUrl()).host : window.location.host;

    return (
        <Space
            direction="vertical"
            size={24}
            style={{ width: "100%", maxWidth: 1440, margin: "0 auto", display: "flex" }}
        >
            <div
                style={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    gap: 16,
                    flexWrap: "wrap",
                }}
            >
                <Space size={12}>
                    <Typography.Title level={4} style={{ margin: 0 }}>
                        {res.themeLibrary}
                    </Typography.Title>
                    <Tag>
                        {res.installedThemes} · {templateState.length}
                    </Tag>
                </Space>
                <Space wrap>
                    <Button
                        icon={<UploadOutlined />}
                        aria-expanded={uploadOpen}
                        onClick={() => setUploadOpen(!uploadOpen)}
                    >
                        {res.upload.title}
                    </Button>
                    <Link to={getRealRouteUrl(`/template-center?host=${host}`)}>
                        <Button icon={<CloudDownloadOutlined />}>{res.downloadMore}</Button>
                    </Link>
                </Space>
            </div>
            {uploadOpen && (
                <ThemeUpload
                    templates={templateState}
                    onInstalled={(shortTemplate) => {
                        setSelectedTemplateName(shortTemplate);
                        setFilter("all");
                        setUploadOpen(false);
                        load();
                    }}
                />
            )}
            {previewTemplate && (
                <Alert type="info" showIcon message={`${res.previewTheme} · ${previewTemplate.name}`} />
            )}
            <div
                style={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    gap: 16,
                    flexWrap: "wrap",
                }}
            >
                {screens.md ? (
                    <Segmented value={filter} onChange={(value) => setFilter(String(value))} options={filterOptions} />
                ) : (
                    <Select value={filter} onChange={setFilter} options={filterOptions} style={{ width: "100%" }} />
                )}
                <Typography.Text type="secondary">{res.manageDescription}</Typography.Text>
            </div>
            {filteredTemplates.length ? (
                <Row gutter={[24, 24]} align="stretch">
                    {filteredTemplates.map((template) => (
                        <TemplateCard
                            key={template.template}
                            template={template}
                            onUpdate={load}
                            selected={selectedTemplateName === template.shortTemplate}
                        />
                    ))}
                </Row>
            ) : (
                <Empty description={templateState.length ? res.filterEmpty : res.empty} />
            )}
        </Space>
    );
};

export default Template;
