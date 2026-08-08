import { DeleteOutlined, KeyOutlined, PlusOutlined } from "@ant-design/icons";
import { Button, Card, Empty, Form, Input, List, message, Modal, Space, Typography } from "antd";
import { useTheme } from "antd-style";
import type { CSSProperties } from "react";
import { useEffect, useRef, useState } from "react";
import { useAxiosBaseInstance } from "../base/AppBase";
import type {
    ApiResponse,
    PasskeyRegistrationOptionsResponse,
    PasskeyRegistrationVerifyRequest,
    PasskeySummary,
} from "../type";
import { getBackendServerUrl, getRes } from "../utils/constants";
import { canUsePasskeys, isPasskeyCancellation, registerPasskey } from "../utils/passkey";

const md5 = require("md5");

const passkeyApiBase = "/api/admin/account-security/passkey";

type PasskeyManagementProps = {
    offline: boolean;
    mfaEnabled: boolean;
    cardStyle: CSSProperties;
    modalWidth?: string;
};

type PasskeyRegistrationFormValues = {
    name: string;
    password: string;
    mfaCode?: string;
};

type PasskeyRemoveFormValues = {
    password: string;
    mfaCode?: string;
};

const ReauthenticationFields = ({ mfaEnabled }: { mfaEnabled: boolean }) => {
    return (
        <>
            <Form.Item
                label={getRes().accountSecurity.passkeyCurrentPassword}
                name="password"
                rules={[{ required: true }]}
            >
                <Input.Password autoComplete="current-password" />
            </Form.Item>
            {mfaEnabled && (
                <Form.Item label={getRes().accountSecurity.mfaCode} name="mfaCode" rules={[{ required: true }]}>
                    <Input.OTP inputMode="numeric" length={6} formatter={(value) => value.replace(/\D/g, "")} />
                </Form.Item>
            )}
        </>
    );
};

const PasskeyManagement = ({ offline, mfaEnabled, cardStyle, modalWidth }: PasskeyManagementProps) => {
    const axiosInstance = useAxiosBaseInstance();
    const theme = useTheme();
    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });
    const [passkeys, setPasskeys] = useState<PasskeySummary[]>([]);
    const [loading, setLoading] = useState(!offline);
    const [hasLoaded, setHasLoaded] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [registrationOpen, setRegistrationOpen] = useState(false);
    const [removeTarget, setRemoveTarget] = useState<PasskeySummary | null>(null);
    const [registrationForm] = Form.useForm<PasskeyRegistrationFormValues>();
    const [removeForm] = Form.useForm<PasskeyRemoveFormValues>();
    const submissionInFlightRef = useRef(false);
    const registrationAvailable = canUsePasskeys(getBackendServerUrl());
    const listLoading = loading || (!offline && !hasLoaded);

    const loadPasskeys = async () => {
        setLoading(true);
        try {
            const { data } = await axiosInstance.get<ApiResponse<PasskeySummary[]>>(
                "/api/admin/account-security/passkeys"
            );
            if (data.error) {
                void messageApi.error(data.message);
                return;
            }
            setPasskeys(data.data);
        } finally {
            setHasLoaded(true);
            setLoading(false);
        }
    };

    useEffect(() => {
        if (offline) {
            setLoading(false);
            setHasLoaded(false);
            return;
        }
        void loadPasskeys().catch(() => undefined);
    }, [offline]);

    const beginSubmission = (): boolean => {
        if (submissionInFlightRef.current) {
            return false;
        }
        submissionInFlightRef.current = true;
        setSubmitting(true);
        return true;
    };

    const endSubmission = () => {
        submissionInFlightRef.current = false;
        setSubmitting(false);
    };

    const closeRegistration = () => {
        if (submitting) {
            return;
        }
        setRegistrationOpen(false);
        registrationForm.resetFields();
    };

    const closeRemove = () => {
        if (submitting) {
            return;
        }
        setRemoveTarget(null);
        removeForm.resetFields();
    };

    const submitRegistration = async (values: PasskeyRegistrationFormValues) => {
        if (!beginSubmission()) {
            return;
        }
        const name = values.name.trim();
        try {
            let optionsResponse: ApiResponse<PasskeyRegistrationOptionsResponse>;
            try {
                optionsResponse = (
                    await axiosInstance.post<ApiResponse<PasskeyRegistrationOptionsResponse>>(
                        `${passkeyApiBase}/registration/options`,
                        {
                            name,
                            password: md5(values.password),
                            mfaCode: values.mfaCode,
                        }
                    )
                ).data;
            } catch {
                return;
            }
            if (optionsResponse.error) {
                void messageApi.error(optionsResponse.message);
                return;
            }
            let response: PasskeyRegistrationVerifyRequest["response"];
            try {
                response = await registerPasskey(optionsResponse.data.options);
            } catch (error) {
                if (!isPasskeyCancellation(error)) {
                    void messageApi.error(getRes().accountSecurity.passkeyRegistrationFailed);
                }
                return;
            }
            const verifyRequest: PasskeyRegistrationVerifyRequest = {
                requestId: optionsResponse.data.requestId,
                response,
                name,
            };
            let verifyResponse: ApiResponse<unknown>;
            try {
                verifyResponse = (
                    await axiosInstance.post<ApiResponse<unknown>>(
                        `${passkeyApiBase}/registration/verify`,
                        verifyRequest
                    )
                ).data;
            } catch {
                return;
            }
            if (verifyResponse.error) {
                void messageApi.error(verifyResponse.message);
                return;
            }
            void messageApi.success(verifyResponse.message || getRes().accountSecurity.passkeyAdded);
            setRegistrationOpen(false);
            registrationForm.resetFields();
            try {
                await loadPasskeys();
            } catch {
                return;
            }
        } finally {
            endSubmission();
        }
    };

    const submitRemove = async (values: PasskeyRemoveFormValues) => {
        if (!removeTarget) {
            return;
        }
        if (!beginSubmission()) {
            return;
        }
        const targetId = removeTarget.id;
        try {
            let data: ApiResponse<unknown>;
            try {
                data = (
                    await axiosInstance.post<ApiResponse<unknown>>(`${passkeyApiBase}/remove`, {
                        id: targetId,
                        password: md5(values.password),
                        mfaCode: values.mfaCode,
                    })
                ).data;
            } catch {
                return;
            }
            if (data.error) {
                void messageApi.error(data.message);
                return;
            }
            void messageApi.success(data.message || getRes().accountSecurity.passkeyRemoved);
            setRemoveTarget(null);
            removeForm.resetFields();
            try {
                await loadPasskeys();
            } catch {
                return;
            }
        } finally {
            endSubmission();
        }
    };

    const formatTime = (timestamp?: number) => {
        if (!timestamp) {
            return getRes().accountSecurity.passkeyNeverUsed;
        }
        return new Date(timestamp).toLocaleString();
    };

    return (
        <>
            {contextHolder}
            <Card
                title={getRes().accountSecurity.passkeyTitle}
                style={cardStyle}
                extra={
                    registrationAvailable ? (
                        <Button
                            disabled={offline || listLoading || submitting}
                            icon={<PlusOutlined />}
                            type="primary"
                            onClick={() => setRegistrationOpen(true)}
                        >
                            {getRes().accountSecurity.passkeyAdd}
                        </Button>
                    ) : null
                }
            >
                <Typography.Paragraph type="secondary">
                    {getRes().accountSecurity.passkeyDescription}
                </Typography.Paragraph>
                <List
                    dataSource={passkeys}
                    loading={listLoading}
                    locale={{
                        emptyText: (
                            <Empty
                                image={Empty.PRESENTED_IMAGE_SIMPLE}
                                description={getRes().accountSecurity.passkeyEmpty}
                            />
                        ),
                    }}
                    renderItem={(passkey) => (
                        <List.Item
                            actions={[
                                <Button
                                    key="remove"
                                    danger
                                    disabled={offline || listLoading || submitting}
                                    icon={<DeleteOutlined />}
                                    type="text"
                                    onClick={() => setRemoveTarget(passkey)}
                                >
                                    {getRes().accountSecurity.passkeyRemove}
                                </Button>,
                            ]}
                        >
                            <List.Item.Meta
                                style={{ minWidth: 0 }}
                                avatar={
                                    <KeyOutlined style={{ color: theme.colorPrimary, fontSize: theme.fontSizeXL }} />
                                }
                                title={
                                    <Typography.Text
                                        ellipsis={{ tooltip: passkey.name }}
                                        style={{ display: "block", maxWidth: "100%" }}
                                    >
                                        {passkey.name}
                                    </Typography.Text>
                                }
                                description={
                                    <Space direction="vertical" size={0} style={{ maxWidth: "100%" }}>
                                        <Typography.Text type="secondary" style={{ overflowWrap: "anywhere" }}>
                                            {getRes().accountSecurity.passkeyCreatedAt}: {formatTime(passkey.createdAt)}
                                        </Typography.Text>
                                        <Typography.Text type="secondary" style={{ overflowWrap: "anywhere" }}>
                                            {getRes().accountSecurity.passkeyLastUsedAt}:{" "}
                                            {formatTime(passkey.lastUsedAt)}
                                        </Typography.Text>
                                    </Space>
                                }
                            />
                        </List.Item>
                    )}
                />
            </Card>

            <Modal
                title={getRes().accountSecurity.passkeyAddTitle}
                open={registrationOpen}
                confirmLoading={submitting}
                cancelText={getRes().cancel}
                okText={getRes().accountSecurity.passkeyAdd}
                onCancel={closeRegistration}
                onOk={() => registrationForm.submit()}
                maskClosable={!submitting}
                closable={!submitting}
                destroyOnHidden={false}
                width={modalWidth}
            >
                <Form
                    form={registrationForm}
                    layout="vertical"
                    disabled={submitting}
                    onFinish={(values) => void submitRegistration(values)}
                >
                    <Form.Item
                        label={getRes().accountSecurity.passkeyName}
                        name="name"
                        rules={[{ required: true, whitespace: true, max: 64 }]}
                    >
                        <Input
                            autoComplete="off"
                            maxLength={64}
                            placeholder={getRes().accountSecurity.passkeyNamePlaceholder}
                        />
                    </Form.Item>
                    <ReauthenticationFields mfaEnabled={mfaEnabled} />
                </Form>
            </Modal>

            <Modal
                title={getRes().accountSecurity.passkeyRemoveTitle}
                open={removeTarget !== null}
                confirmLoading={submitting}
                cancelText={getRes().cancel}
                okText={getRes().accountSecurity.passkeyRemove}
                okButtonProps={{ danger: true }}
                onCancel={closeRemove}
                onOk={() => removeForm.submit()}
                maskClosable={!submitting}
                closable={!submitting}
                destroyOnHidden={false}
                width={modalWidth}
            >
                {removeTarget && (
                    <Typography.Paragraph style={{ overflowWrap: "anywhere" }}>
                        {getRes().accountSecurity.passkeyRemoveHint.replace("{name}", removeTarget.name)}
                    </Typography.Paragraph>
                )}
                <Form
                    form={removeForm}
                    layout="vertical"
                    disabled={submitting}
                    onFinish={(values) => void submitRemove(values)}
                >
                    <ReauthenticationFields mfaEnabled={mfaEnabled} />
                </Form>
            </Modal>
        </>
    );
};

export default PasskeyManagement;
