import { CheckCircleOutlined, ExportOutlined, EyeOutlined, SafetyCertificateOutlined } from "@ant-design/icons";
import { Space, Tag, Typography } from "antd";
import { getRes } from "../../utils/constants";
import { getTemplateAuthorUrl, TemplateEntry } from "./template-model";

export const TemplateAuthor = ({ template }: { template: TemplateEntry }) => {
    if (!template.author) return null;
    const href = getTemplateAuthorUrl(template.url);
    return href ? (
        <Typography.Link
            href={href}
            target="_blank"
            rel="noopener noreferrer"
            aria-label={getRes().websiteTemplate.authorHomepage.replace("{author}", template.author)}
        >
            {template.author} <ExportOutlined />
        </Typography.Link>
    ) : (
        <Typography.Text type="secondary">{template.author}</Typography.Text>
    );
};

export const TemplateBadges = ({ template, status = true }: { template: TemplateEntry; status?: boolean }) => (
    <Space size="small" wrap>
        {template.builtIn && <Tag icon={<SafetyCertificateOutlined />}>{getRes().websiteTemplate.builtIn}</Tag>}
        {status && template.use && (
            <Tag color="success" icon={<CheckCircleOutlined />}>
                {getRes().templateConfig.inUse}
            </Tag>
        )}
        {status && template.preview && !template.use && (
            <Tag color="processing" icon={<EyeOutlined />}>
                {getRes().templateConfig.inPreview}
            </Tag>
        )}
    </Space>
);
