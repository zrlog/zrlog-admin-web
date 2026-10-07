import Tag from "@zrlog/ui/antd/Tag";
import SuccessIcon from "@zrlog/ui/icons/success";
import ExternalLinkIcon from "@zrlog/ui/icons/external-link";
import EyeIcon from "@zrlog/ui/icons/eye";
import SecurityIcon from "@zrlog/ui/icons/security";
import { Space, Typography } from "antd";
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
            {template.author} <ExternalLinkIcon />
        </Typography.Link>
    ) : (
        <Typography.Text type="secondary">{template.author}</Typography.Text>
    );
};

export const TemplateBadges = ({ template, status = true }: { template: TemplateEntry; status?: boolean }) => (
    <Space size="small" wrap>
        {template.builtIn && <Tag icon={<SecurityIcon />}>{getRes().websiteTemplate.builtIn}</Tag>}
        {status && template.use && (
            <Tag color="success" icon={<SuccessIcon />}>
                {getRes().templateConfig.inUse}
            </Tag>
        )}
        {status && template.preview && !template.use && (
            <Tag color="processing" icon={<EyeIcon />}>
                {getRes().templateConfig.inPreview}
            </Tag>
        )}
    </Space>
);
