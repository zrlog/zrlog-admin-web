import {
    Alert,
    Button,
    Descriptions,
    Divider,
    Form,
    Popconfirm,
    Space,
    Switch,
    Typography,
    message,
    theme,
} from "antd";
import { useEffect, useMemo, useState } from "react";
import { ReloadOutlined, StopOutlined } from "@ant-design/icons";
import TimeAgo from "@editor/dist/src/editor/TimeAgo";
import { getLabelValueSeparator, getRes, tryAppendBackendServerUrl } from "../../utils/constants";
import { useAxiosBaseInstance } from "../../base/AppBase";
import type { ApiResponse } from "../../type";
import type { WebhookConfig } from "./index";
import { useResponsiveFormLayout } from "../../utils/responsive-form";

const layout = {
    labelCol: { span: 8 },
    wrapperCol: { span: 16 },
};

type WebhookTokenResponse = {
    token: string;
    config: WebhookConfig;
};

const WebhookForm = ({
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
}) => {
    const [form] = Form.useForm<WebhookConfig>();
    const [config, setConfig] = useState<WebhookConfig>(data);
    const [generatedToken, setGeneratedToken] = useState("");
    const [rotating, setRotating] = useState(false);
    const [revoking, setRevoking] = useState(false);
    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });
    const axiosInstance = useAxiosBaseInstance();
    const { token } = theme.useToken();
    const disabled = offline || offlineData;
    const { formLayout } = useResponsiveFormLayout(layout);
    const requestBodyExample = getRes().websiteWebhook.requestBodyExample;

    useEffect(() => {
        setConfig(data);
        setGeneratedToken("");
        form.setFieldsValue(data);
    }, [data, form]);

    const endpoint = useMemo(() => {
        if (!config.endpoint) {
            return "";
        }
        const realEndpoint = tryAppendBackendServerUrl(config.endpoint);
        if (realEndpoint.startsWith("http://") || realEndpoint.startsWith("https://")) {
            return realEndpoint;
        }
        try {
            return new URL(realEndpoint, window.location.origin).toString();
        } catch {
            return realEndpoint;
        }
    }, [config.endpoint]);

    const syncConfig = (nextConfig: WebhookConfig) => {
        setConfig(nextConfig);
        form.setFieldsValue(nextConfig);
        if (onConfigChange) {
            onConfigChange(nextConfig);
        }
    };

    const rotateToken = async () => {
        if (disabled || rotating) {
            return;
        }
        try {
            setRotating(true);
            const { data: response } = await axiosInstance.post<ApiResponse<WebhookTokenResponse>>(
                "/api/admin/webhook/token"
            );
            if (response.error) {
                await messageApi.error(response.message);
                return;
            }
            setGeneratedToken(response.data.token);
            syncConfig(response.data.config);
            await messageApi.success(getRes().websiteWebhook.tokenGenerated);
        } catch (e) {
            await messageApi.error(e instanceof Error ? e.message : getRes().error.unknown);
        } finally {
            setRotating(false);
        }
    };

    const revokeToken = async () => {
        if (disabled || revoking) {
            return;
        }
        try {
            setRevoking(true);
            const { data: response } = await axiosInstance.post<ApiResponse<WebhookConfig>>(
                "/api/admin/webhook/token/revoke"
            );
            if (response.error) {
                await messageApi.error(response.message);
                return;
            }
            setGeneratedToken("");
            syncConfig(response.data);
            await messageApi.success(getRes().websiteWebhook.tokenRevoked);
        } catch (e) {
            await messageApi.error(e instanceof Error ? e.message : getRes().error.unknown);
        } finally {
            setRevoking(false);
        }
    };

    const tokenStatus = config.hasToken
        ? getRes().websiteWebhook.tokenConfigured.replace("{token}", config.tokenPreview || "")
        : getRes().websiteWebhook.tokenNotConfigured;

    return (
        <>
            {contextHolder}
            {config.enabled && !config.hasToken ? (
                <Alert
                    type="warning"
                    showIcon
                    message={getRes().websiteWebhook.tokenRequiredNotice}
                    style={{ marginBottom: token.margin }}
                />
            ) : null}
            {generatedToken ? (
                <Alert
                    type="success"
                    showIcon
                    message={getRes().websiteWebhook.tokenGeneratedNotice}
                    description={
                        <Typography.Paragraph
                            copyable={{ text: generatedToken }}
                            style={{ marginBottom: 0, wordBreak: "break-all" }}
                        >
                            {generatedToken}
                        </Typography.Paragraph>
                    }
                    style={{ marginBottom: token.margin }}
                />
            ) : null}
            <Descriptions column={1} size="small" bordered>
                <Descriptions.Item label={getRes().websiteWebhook.supportedTypes}>
                    {getRes().websiteWebhook.messageCenterNotice}
                </Descriptions.Item>
                <Descriptions.Item label={getRes().websiteWebhook.endpoint}>
                    <Typography.Text copyable={{ text: endpoint }} style={{ wordBreak: "break-all" }}>
                        {endpoint}
                    </Typography.Text>
                </Descriptions.Item>
                <Descriptions.Item label={getRes().websiteWebhook.tokenHeader}>
                    <Space direction="vertical" size={token.marginXXS}>
                        <Typography.Text copyable={{ text: config.tokenHeader }}>{config.tokenHeader}</Typography.Text>
                        <Typography.Text type="secondary">{getRes().websiteWebhook.headerTip}</Typography.Text>
                    </Space>
                </Descriptions.Item>
                <Descriptions.Item label={getRes().websiteWebhook.requestBody}>
                    <Space direction="vertical" size={token.marginXXS} style={{ width: "100%" }}>
                        <Typography.Text type="secondary">{getRes().websiteWebhook.requestBodyTip}</Typography.Text>
                        <Typography.Paragraph
                            copyable={{ text: requestBodyExample }}
                            style={{
                                background: token.colorFillQuaternary,
                                border: `${token.lineWidth}px ${token.lineType} ${token.colorBorder}`,
                                borderRadius: token.borderRadius,
                                marginBottom: 0,
                                padding: token.paddingSM,
                                whiteSpace: "pre-wrap",
                                wordBreak: "break-word",
                            }}
                        >
                            {requestBodyExample}
                        </Typography.Paragraph>
                    </Space>
                </Descriptions.Item>
                <Descriptions.Item label={getRes().websiteWebhook.status}>
                    <Space direction="vertical" size={token.marginXXS}>
                        <span>{tokenStatus}</span>
                        {config.tokenUpdatedAt ? (
                            <Typography.Text type="secondary">
                                {getRes().websiteWebhook.tokenUpdatedAt}
                                {getLabelValueSeparator()}
                                <TimeAgo timestamp={config.tokenUpdatedAt} />
                            </Typography.Text>
                        ) : null}
                    </Space>
                </Descriptions.Item>
            </Descriptions>
            <Divider />
            <Form
                {...formLayout}
                form={form}
                disabled={disabled}
                initialValues={data}
                onValuesChange={(_changedValues, values) => setConfig({ ...config, ...values })}
                onFinish={(values) => {
                    const nextConfig = { ...config, ...values };
                    setGeneratedToken("");
                    setConfig(nextConfig);
                    onSubmit(nextConfig);
                }}
            >
                <Form.Item
                    valuePropName="checked"
                    name="enabled"
                    label={getRes().websiteWebhook.enabled}
                    tooltip={getRes().websiteWebhook.enabledTip}
                >
                    <Switch />
                </Form.Item>
                <Divider />
                <Space wrap>
                    <Button enterKeyHint="enter" loading={loading} disabled={disabled} type="primary" htmlType="submit">
                        {getRes().submit}
                    </Button>
                    <Popconfirm
                        title={
                            config.hasToken
                                ? getRes().websiteWebhook.rotateConfirm
                                : getRes().websiteWebhook.generateConfirm
                        }
                        okText={getRes().confirm}
                        cancelText={getRes().cancel}
                        disabled={disabled}
                        onConfirm={rotateToken}
                    >
                        <Button icon={<ReloadOutlined />} loading={rotating} disabled={disabled}>
                            {config.hasToken
                                ? getRes().websiteWebhook.rotateToken
                                : getRes().websiteWebhook.generateToken}
                        </Button>
                    </Popconfirm>
                    <Popconfirm
                        title={getRes().websiteWebhook.revokeConfirm}
                        okText={getRes().confirm}
                        cancelText={getRes().cancel}
                        disabled={disabled || !config.hasToken}
                        onConfirm={revokeToken}
                    >
                        <Button
                            danger
                            icon={<StopOutlined />}
                            loading={revoking}
                            disabled={disabled || !config.hasToken}
                        >
                            {getRes().websiteWebhook.revokeToken}
                        </Button>
                    </Popconfirm>
                </Space>
            </Form>
        </>
    );
};

export default WebhookForm;
