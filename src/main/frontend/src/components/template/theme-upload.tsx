import { InboxOutlined, SafetyCertificateOutlined } from "@ant-design/icons";
import { Alert, Card, message, Modal, Space, Typography, Upload } from "antd";
import type { RcFile } from "antd/es/upload";
import { useState } from "react";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getRes } from "../../utils/constants";
import type { TemplateEntry } from "./index";

type UploadThemeResponse = {
    error: number;
    message?: string;
    data?: {
        shortTemplate: string;
        name: string;
        version?: string;
        overwritten: boolean;
    };
};

type ThemeUploadProps = {
    templates: TemplateEntry[];
    onInstalled: (shortTemplate: string) => void;
};

const SHORT_TEMPLATE_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$/;

const ThemeUpload = ({ templates, onInstalled }: ThemeUploadProps) => {
    const axiosInstance = useAxiosBaseInstance();
    const [uploading, setUploading] = useState(false);
    const [messageApi, messageContextHolder] = message.useMessage({ maxCount: 3 });
    const [modal, modalContextHolder] = Modal.useModal();
    const res = getRes().websiteTemplate.upload;

    const upload = async (file: RcFile, shortTemplate: string, overwrite: boolean) => {
        setUploading(true);
        try {
            const formData = new FormData();
            formData.append("file", file);
            const query = new URLSearchParams({
                shortTemplate,
                overwrite: String(overwrite),
            });
            const { data } = await axiosInstance.post<UploadThemeResponse>(
                `/api/admin/template/upload?${query.toString()}`,
                formData
            );
            if (data.error || !data.data) {
                await messageApi.error(data.message || res.failed);
                return;
            }
            await messageApi.success(data.message || res.success);
            onInstalled(data.data.shortTemplate);
        } catch {
            await messageApi.error(res.failed);
        } finally {
            setUploading(false);
        }
    };

    const beforeUpload = (file: RcFile) => {
        const zipSuffixIndex = file.name.toLowerCase().lastIndexOf(".zip");
        if (zipSuffixIndex <= 0 || zipSuffixIndex !== file.name.length - 4) {
            void messageApi.error(res.zipOnly);
            return false;
        }
        const shortTemplate = file.name.substring(0, zipSuffixIndex);
        if (!SHORT_TEMPLATE_PATTERN.test(shortTemplate)) {
            void messageApi.error(res.invalidName);
            return false;
        }

        const existing = templates.find((template) => template.shortTemplate === shortTemplate);
        if (!existing) {
            void upload(file, shortTemplate, false);
            return false;
        }
        if (!existing.deleteAble) {
            void messageApi.error(res.builtInCannotOverwrite);
            return false;
        }

        modal.confirm({
            title: res.overwriteTitle,
            content: (existing.use ? res.overwriteActiveWarning : res.overwriteWarning).replace(
                "{theme}",
                existing.name || shortTemplate
            ),
            okText: res.overwriteConfirm,
            okType: "danger",
            cancelText: getRes().cancel,
            onOk: () => upload(file, shortTemplate, true),
        });
        return false;
    };

    return (
        <Card size="small" title={res.title}>
            {messageContextHolder}
            {modalContextHolder}
            <Space direction="vertical" size={12} style={{ width: "100%" }}>
                <Alert
                    icon={<SafetyCertificateOutlined />}
                    message={res.riskTitle}
                    description={res.riskDescription}
                    showIcon
                    type="warning"
                />
                <Upload.Dragger
                    accept=".zip,application/zip"
                    beforeUpload={beforeUpload}
                    disabled={uploading}
                    multiple={false}
                    showUploadList={false}
                >
                    <Space direction="vertical" size={4}>
                        <InboxOutlined style={{ fontSize: 28 }} />
                        <Typography.Text>{uploading ? res.uploading : res.dropHere}</Typography.Text>
                        <Typography.Text type="secondary">{res.fileNameHint}</Typography.Text>
                    </Space>
                </Upload.Dragger>
            </Space>
        </Card>
    );
};

export default ThemeUpload;
