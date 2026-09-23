import { Avatar, Grid, Space, Table, Typography } from "antd";
import { SkinOutlined } from "@ant-design/icons";
import { useTheme } from "antd-style";
import { getBackendServerUrl, getRes } from "../../utils/constants";
import { getTemplatePreviewUrl } from "./template-model";
import TemplateActions from "./template-actions";
import { TemplateAuthor, TemplateBadges } from "./template-details";
import type { TemplateEntry } from "./template-model";

const TemplateList = ({
    templates,
    onUpdate,
    onConfigure,
    selected,
}: {
    templates: TemplateEntry[];
    onUpdate: () => void;
    onConfigure: (template: TemplateEntry) => void;
    selected?: string;
}) => {
    const theme = useTheme();
    const screens = Grid.useBreakpoint();
    const res = getRes().websiteTemplate;
    return (
        <Table<TemplateEntry>
            dataSource={templates}
            rowKey="template"
            pagination={false}
            style={{ background: theme.colorBgContainer, borderRadius: theme.borderRadiusLG }}
            onRow={(template) => ({
                style: { background: selected === template.shortTemplate ? theme.colorPrimaryBg : undefined },
            })}
            columns={[
                {
                    title: res.title,
                    key: "info",
                    render: (_, template) => (
                        <div style={{ display: "flex", gap: theme.margin, whiteSpace: "normal" }}>
                            <Avatar
                                shape="square"
                                size="large"
                                style={{ flexShrink: 0 }}
                                src={getTemplatePreviewUrl(template.adminPreviewImage, getBackendServerUrl())}
                                icon={<SkinOutlined />}
                            />
                            <Space direction="vertical" size="small" style={{ minWidth: 0, overflowWrap: "anywhere" }}>
                                <Space wrap>
                                    <Typography.Text strong>{template.name}</Typography.Text>
                                    <TemplateBadges template={template} />
                                </Space>
                                <Typography.Text type="secondary">{template.digest}</Typography.Text>
                                <Space wrap>
                                    <TemplateAuthor template={template} />
                                    <Typography.Text type="secondary">v{template.version}</Typography.Text>
                                </Space>
                                {!screens.lg && (
                                    <TemplateActions
                                        template={template}
                                        onUpdate={onUpdate}
                                        onConfigure={onConfigure}
                                    />
                                )}
                            </Space>
                        </div>
                    ),
                },
                {
                    title: res.operations,
                    key: "actions",
                    responsive: ["lg"],
                    width: 300,
                    render: (_, template) => (
                        <TemplateActions template={template} onUpdate={onUpdate} onConfigure={onConfigure} />
                    ),
                },
            ]}
        />
    );
};

export default TemplateList;
