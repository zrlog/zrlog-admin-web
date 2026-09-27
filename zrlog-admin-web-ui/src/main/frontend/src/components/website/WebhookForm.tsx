import { Alert, Button, Form, Popconfirm, Space, Switch, Typography, message } from "antd";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getRealRouteUrl, getRes } from "../../utils/constants";
import { USER_ROUTES } from "../../utils/account-page-routes";
import { useAxiosBaseInstance } from "../../base/AppBase";
import type { ApiResponse } from "../../type";
import type { WebhookConfig } from "./index";
import { useResponsiveFormLayout } from "../../utils/responsive-form";
import WebsiteSubmitBar from "./WebsiteSubmitBar";

/** Compatibility page for bookmarks and revoking previously issued site-wide credentials. */
export default function WebhookForm({
    data,
    offlineData,
    offline,
    onSubmit,
    onConfigChange,
    loading,
}: {
    data: WebhookConfig;
    offlineData: boolean;
    offline: boolean;
    onSubmit: (data: WebhookConfig) => void;
    onConfigChange?: (data: WebhookConfig) => void;
    loading?: boolean;
}) {
    const [form] = Form.useForm<WebhookConfig>();
    const [config, setConfig] = useState(data);
    const [revoking, setRevoking] = useState(false);
    const [notice, contextHolder] = message.useMessage();
    const api = useAxiosBaseInstance();
    const disabled = offline || offlineData;
    const { formLayout } = useResponsiveFormLayout({ labelCol: { span: 8 }, wrapperCol: { span: 16 } });
    useEffect(() => {
        setConfig(data);
        form.setFieldsValue(data);
    }, [data, form]);
    const revoke = async () => {
        setRevoking(true);
        try {
            const { data: response } = await api.post<ApiResponse<WebhookConfig>>("/api/admin/webhook/token/revoke");
            if (response.error) {
                notice.error(response.message);
                return;
            }
            setConfig(response.data);
            form.setFieldsValue(response.data);
            onConfigChange?.(response.data);
            notice.success(getRes().websiteWebhook.tokenRevoked);
        } catch {
            notice.error(getRes().error.requestError);
        } finally {
            setRevoking(false);
        }
    };
    return (
        <Space orientation="vertical" size="large" style={{ width: "100%" }}>
            {contextHolder}
            <Alert
                type="info"
                showIcon
                title={getRes().websiteWebhook.personalTokenHelp}
                description={
                    <Link to={getRealRouteUrl(USER_ROUTES.tokens)}>{getRes().oauth.personalTokens.title}</Link>
                }
            />
            <Form
                {...formLayout}
                form={form}
                disabled={disabled}
                initialValues={data}
                onFinish={(values) => onSubmit({ ...config, ...values })}
            >
                <Form.Item
                    name="enabled"
                    valuePropName="checked"
                    label={getRes().websiteLab.webhook}
                    tooltip={getRes().websiteWebhook.webhookStatusTip}
                >
                    <Switch />
                </Form.Item>
                <WebsiteSubmitBar loading={loading} disabled={disabled} />
            </Form>
            {config.hasToken && (
                <Space wrap>
                    <Typography.Text>
                        {getRes().websiteWebhook.tokenConfigured.replace("{token}", config.tokenPreview || "")}
                    </Typography.Text>
                    <Popconfirm title={getRes().websiteWebhook.revokeConfirm} onConfirm={revoke} disabled={disabled}>
                        <Button danger loading={revoking} disabled={disabled}>
                            {getRes().websiteWebhook.revokeToken}
                        </Button>
                    </Popconfirm>
                </Space>
            )}
        </Space>
    );
}
