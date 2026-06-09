import { DownloadOutlined, SearchOutlined } from "@ant-design/icons";
import { Alert, Button, Descriptions, Form, Input, Space, Typography, message, theme } from "antd";
import { useEffect, useState } from "react";
import { useAxiosBaseInstance } from "../../base/AppBase";
import type { ApiResponse } from "../../type";
import { getRes } from "../../utils/constants";
import { useResponsiveFormLayout } from "../../utils/responsive-form";
import type { PersonalDataPreview } from "./index";

const layout = {
    labelCol: { span: 8 },
    wrapperCol: { span: 16 },
};

type PrivacyPreviewFormValues = {
    query: string;
};

type PersonalDataCommentExport = {
    query: string;
    exportedAt: number;
    commentCount: number;
    comments: Array<{
        id: number;
        userComment: string;
        userMail: string;
        userHome: string;
        userIp: string;
        userName: string;
        commTime: string;
        logId: number;
    }>;
};

const PrivacyForm = ({
    data,
    offline,
    offlineData,
}: {
    data: PersonalDataPreview;
    offline: boolean;
    offlineData: boolean;
}) => {
    const { token } = theme.useToken();
    const [form] = Form.useForm<PrivacyPreviewFormValues>();
    const [preview, setPreview] = useState<PersonalDataPreview>(data);
    const [previewing, setPreviewing] = useState(false);
    const [exportingComments, setExportingComments] = useState(false);
    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });
    const axiosInstance = useAxiosBaseInstance();
    const { formLayout } = useResponsiveFormLayout(layout);
    const disabled = offline || offlineData;

    useEffect(() => {
        setPreview(data);
        form.setFieldsValue({ query: data.query || "" });
    }, [data, form]);

    const previewPersonalData = async (values: PrivacyPreviewFormValues) => {
        try {
            setPreviewing(true);
            const { data: response } = await axiosInstance.post<ApiResponse<PersonalDataPreview>>(
                "/api/admin/personal-data/preview",
                values
            );
            if (response.error) {
                await messageApi.error(response.message);
                return;
            }
            setPreview(response.data);
        } catch (e) {
            await messageApi.error(e instanceof Error ? e.message : getRes().error.unknown);
        } finally {
            setPreviewing(false);
        }
    };

    const downloadJson = (data: PersonalDataCommentExport) => {
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json;charset=utf-8" });
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = "zrlog-personal-data-comments-" + data.exportedAt + ".json";
        document.body.appendChild(link);
        link.click();
        link.remove();
        URL.revokeObjectURL(url);
    };

    const exportComments = async () => {
        const query = preview.query || "";
        if (!query) {
            return;
        }
        try {
            setExportingComments(true);
            const { data: response } = await axiosInstance.post<ApiResponse<PersonalDataCommentExport>>(
                "/api/admin/personal-data/comments/export",
                { query }
            );
            if (response.error) {
                await messageApi.error(response.message);
                return;
            }
            downloadJson(response.data);
            await messageApi.success(
                getRes().websitePrivacy.exportSuccess.replace("{count}", String(response.data.commentCount))
            );
        } catch (e) {
            await messageApi.error(e instanceof Error ? e.message : getRes().error.unknown);
        } finally {
            setExportingComments(false);
        }
    };

    const adminAccountStatus = [
        preview.adminUserMatched ? getRes().websitePrivacy.adminUserMatched : undefined,
        preview.adminEmailMatched ? getRes().websitePrivacy.adminEmailMatched : undefined,
    ]
        .filter(Boolean)
        .join(getRes().websitePrivacy.matchSeparator);

    const hasPreview = Boolean(preview.query);

    return (
        <>
            {contextHolder}
            <Alert
                type="info"
                showIcon
                message={getRes().websitePrivacy.noticeTitle}
                description={getRes().websitePrivacy.noticeDesc}
                style={{ marginBottom: token.margin }}
            />
            <Form
                {...formLayout}
                form={form}
                disabled={disabled}
                onFinish={previewPersonalData}
                onValuesChange={(_changedValues, values) => {
                    if ((values.query || "") !== (preview.query || "")) {
                        setPreview({});
                    }
                }}
                initialValues={{ query: preview.query || "" }}
            >
                <Form.Item
                    name="query"
                    label={getRes().websitePrivacy.query}
                    tooltip={getRes().websitePrivacy.queryTip}
                    rules={[
                        { required: true, message: getRes().websitePrivacy.queryRequired },
                        { max: 160, message: getRes().websitePrivacy.queryTooLong },
                    ]}
                >
                    <Input allowClear maxLength={160} placeholder={getRes().websitePrivacy.queryPlaceholder} />
                </Form.Item>
                <Form.Item>
                    <Button
                        enterKeyHint="enter"
                        icon={<SearchOutlined />}
                        loading={previewing}
                        disabled={disabled}
                        type="primary"
                        htmlType="submit"
                    >
                        {getRes().websitePrivacy.preview}
                    </Button>
                </Form.Item>
            </Form>
            {hasPreview ? (
                <Space direction="vertical" size={token.marginSM} style={{ width: "100%" }}>
                    <Space wrap align="center" style={{ justifyContent: "space-between", width: "100%" }}>
                        <Typography.Text strong>{getRes().websitePrivacy.result}</Typography.Text>
                        <Button
                            icon={<DownloadOutlined />}
                            loading={exportingComments}
                            disabled={disabled}
                            onClick={exportComments}
                        >
                            {getRes().websitePrivacy.exportComments}
                        </Button>
                    </Space>
                    <Descriptions column={1} size="small" bordered>
                        <Descriptions.Item label={getRes().websitePrivacy.matchedQuery}>
                            <Typography.Text copyable={{ text: preview.query }}>{preview.query}</Typography.Text>
                        </Descriptions.Item>
                        <Descriptions.Item label={getRes().websitePrivacy.commentCount}>
                            {preview.commentCount ?? 0}
                        </Descriptions.Item>
                        <Descriptions.Item label={getRes().websitePrivacy.commentArticleCount}>
                            {preview.commentArticleCount ?? 0}
                        </Descriptions.Item>
                        <Descriptions.Item label={getRes().websitePrivacy.latestCommentTime}>
                            {preview.latestCommentTime || getRes().websitePrivacy.none}
                        </Descriptions.Item>
                        <Descriptions.Item label={getRes().websitePrivacy.adminAccount}>
                            {adminAccountStatus || getRes().websitePrivacy.adminNotMatched}
                        </Descriptions.Item>
                        <Descriptions.Item label={getRes().websitePrivacy.pluginData}>
                            {preview.pluginDataRequiresPlugin
                                ? getRes().websitePrivacy.pluginDataHint
                                : getRes().websitePrivacy.none}
                        </Descriptions.Item>
                    </Descriptions>
                </Space>
            ) : null}
        </>
    );
};

export default PrivacyForm;
