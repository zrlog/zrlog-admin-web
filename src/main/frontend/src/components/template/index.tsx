import {
    AppstoreOutlined,
    CloudDownloadOutlined,
    SearchOutlined,
    UnorderedListOutlined,
    UploadOutlined,
} from "@ant-design/icons";
import { Alert, Button, Empty, Grid, Input, Row, Segmented, Select, Space, Tag, Typography } from "antd";
import { useEffect, useMemo, useState } from "react";
import { useTheme } from "antd-style";
import { getBackendServerUrl, getRealRouteUrl, getRes, isStaticPage } from "../../utils/constants";
import { Link, useLocation } from "react-router-dom";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getCsrData, getTimeInfoBySearchStr } from "../../api";
import { addToCache } from "../../utils/cache";
import TemplateCard from "./template-card";
import TemplateList from "./template-list";
import ThemeUpload from "./theme-upload";
import TemplateConfigDialog from "./template-config-dialog";
import { filterTemplates, TemplateEntry, TemplateFilter } from "./template-model";

export type { TemplateEntry } from "./template-model";

const Template = ({ data, offline = false }: { data: TemplateEntry[]; offline?: boolean }) => {
    const [templateState, setTemplateState] = useState<TemplateEntry[]>(data);
    const [filter, setFilter] = useState<TemplateFilter>("all");
    const [search, setSearch] = useState("");
    const [view, setView] = useState("grid");
    const [uploadOpen, setUploadOpen] = useState(false);
    const [selectedTemplateName, setSelectedTemplateName] = useState<string>();
    const [configTemplate, setConfigTemplate] = useState<TemplateEntry>();
    const screens = Grid.useBreakpoint();
    const theme = useTheme();
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
        () => filterTemplates(templateState, filter, search),
        [filter, search, templateState]
    );
    const previewTemplate = templateState.find((template) => template.preview && !template.use);
    const host = isStaticPage() ? new URL(getBackendServerUrl()).host : window.location.host;
    return (
        <Space
            direction="vertical"
            size="large"
            style={{ width: "100%", maxWidth: 1440, margin: "0 auto", display: "flex" }}
        >
            <div
                style={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    gap: theme.margin,
                    flexWrap: "wrap",
                }}
            >
                <Space direction="vertical" size="small">
                    <Space wrap>
                        <Typography.Title level={4} style={{ margin: 0 }}>
                            {res.title}
                        </Typography.Title>
                        <Tag>
                            {res.installedThemes} · {templateState.length}
                        </Tag>
                    </Space>
                    <Typography.Text type="secondary">{res.description}</Typography.Text>
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
                        <Button type="primary" icon={<CloudDownloadOutlined />}>
                            {res.downloadMore}
                        </Button>
                    </Link>
                </Space>
            </div>
            {uploadOpen && (
                <ThemeUpload
                    templates={templateState}
                    onInstalled={(shortTemplate) => {
                        setSelectedTemplateName(shortTemplate);
                        setFilter("all");
                        setSearch("");
                        setUploadOpen(false);
                        load();
                    }}
                />
            )}
            {previewTemplate && (
                <Alert type="info" showIcon message={`${res.previewTheme} · ${previewTemplate.name}`} />
            )}
            <div style={{ display: "flex", alignItems: "center", gap: theme.marginSM, flexWrap: "wrap" }}>
                <Input
                    allowClear
                    prefix={<SearchOutlined />}
                    value={search}
                    onChange={(event) => setSearch(event.target.value)}
                    placeholder={res.searchPlaceholder}
                    aria-label={res.searchPlaceholder}
                    style={{ width: screens.md ? 280 : "100%" }}
                />
                <Select<TemplateFilter>
                    value={filter}
                    onChange={setFilter}
                    aria-label={res.filterLabel}
                    style={{ minWidth: 130 }}
                    options={[
                        { label: res.allThemes, value: "all" },
                        { label: getRes().templateConfig.inUse, value: "active" },
                        { label: getRes().templateConfig.inPreview, value: "preview" },
                        { label: res.builtInThemes, value: "builtin" },
                        { label: res.removableThemes, value: "removable" },
                    ]}
                />
                <Segmented
                    value={view}
                    onChange={(value) => setView(String(value))}
                    aria-label={res.viewLabel}
                    style={{ marginLeft: "auto" }}
                    options={[
                        { label: res.listView, value: "list", icon: <UnorderedListOutlined /> },
                        { label: res.gridView, value: "grid", icon: <AppstoreOutlined /> },
                    ]}
                />
            </div>
            {filteredTemplates.length ? (
                view === "grid" ? (
                    <Row gutter={[theme.marginLG, theme.marginLG]} align="stretch">
                        {filteredTemplates.map((template) => (
                            <TemplateCard
                                key={template.template}
                                template={template}
                                onUpdate={load}
                                onConfigure={setConfigTemplate}
                                selected={selectedTemplateName === template.shortTemplate}
                            />
                        ))}
                    </Row>
                ) : (
                    <TemplateList
                        templates={filteredTemplates}
                        onUpdate={load}
                        onConfigure={setConfigTemplate}
                        selected={selectedTemplateName}
                    />
                )
            ) : (
                <Empty description={templateState.length ? res.filterEmpty : res.empty}>
                    {templateState.length > 0 && (
                        <Button
                            onClick={() => {
                                setSearch("");
                                setFilter("all");
                            }}
                        >
                            {res.clearFilters}
                        </Button>
                    )}
                </Empty>
            )}
            <div style={{ display: "flex", justifyContent: "space-between", gap: theme.marginSM, flexWrap: "wrap" }}>
                <Typography.Text type="secondary">
                    {res.resultCount
                        .replace("{count}", String(filteredTemplates.length))
                        .replace("{total}", String(templateState.length))}
                </Typography.Text>
                <Typography.Text type="secondary">{res.manageDescription}</Typography.Text>
            </div>
            {configTemplate && (
                <TemplateConfigDialog
                    key={configTemplate.shortTemplate}
                    template={configTemplate}
                    offline={offline}
                    onClose={() => setConfigTemplate(undefined)}
                />
            )}
        </Space>
    );
};

export default Template;
