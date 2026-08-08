import { useEffect, useRef, useState } from "react";
import Card from "antd/es/card";

import Form from "antd/es/form";
import { Input, message, Modal, QRCode, Row, Space, Typography } from "antd";
import Button from "antd/es/button";
import Col from "antd/es/grid/col";
import { useTheme } from "antd-style";
import { getRes } from "../utils/constants";
import { useAxiosBaseInstance } from "../base/AppBase";
import { AdminCommonProps, MfaStatusResponse } from "../type";
import { getPageDataCacheKeyByPath } from "../utils/cache";
import { useLocation } from "react-router-dom";
import { useResponsiveFormLayout } from "../utils/responsive-form";
import PasskeyManagement from "./passkey-management";

const layout = {
    labelCol: { span: 8 },
    wrapperCol: { span: 16 },
};

const accountSecurityApiBase = "/api/admin/account-security";
const panelMaxWidth = 600;
const passwordInputStyle = {
    maxWidth: 320,
};

type AccountSecurityData = {
    mfaEnabled?: boolean;
};

const AccountSecurity = ({ offline, data, updateCache }: AdminCommonProps<AccountSecurityData>) => {
    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });
    const [mfaStatus, setMfaStatus] = useState<MfaStatusResponse | null>(
        data?.mfaEnabled ? { enabled: true, secret: "", issuer: "", accountName: "", otpauthUrl: "" } : null
    );
    const [mfaSubmitting, setMfaSubmitting] = useState(false);
    const [mfaDialogAction, setMfaDialogAction] = useState<"enable" | "disable" | null>(null);
    const theme = useTheme();
    const location = useLocation();

    const axiosInstance = useAxiosBaseInstance();
    const [mfaForm] = Form.useForm();
    const mfaCode = Form.useWatch("code", mfaForm) as string | undefined;
    const lastAutoSubmittedMfaCodeRef = useRef("");
    const { formLayout, narrow } = useResponsiveFormLayout(layout);
    const passwordStyle = narrow ? { width: "100%" } : passwordInputStyle;
    const securitySurface = {
        card: {
            borderRadius: theme.borderRadiusLG,
            height: "100%",
            maxWidth: panelMaxWidth,
            width: "100%",
        },
        contentSpace: {
            width: "100%",
            marginBottom: theme.marginSM,
            alignItems: "center",
        },
        actions: {
            width: "100%",
        },
        modalWidth: narrow ? `calc(100vw - ${theme.margin * 2}px)` : undefined,
        verticalGap: theme.marginSM,
    };

    const loadMfaStatus = async () => {
        const { data } = await axiosInstance.get(accountSecurityApiBase + "/mfa");
        if (data.error === 0) {
            setMfaStatus(data.data);
        }
    };

    const syncCachedMfaEnabled = (enabled: boolean) => {
        if (!updateCache) {
            return;
        }
        const url = new URL(window.location.href);
        const cacheKey = getPageDataCacheKeyByPath(location.pathname, "?" + url.searchParams.toString());
        updateCache({ ...(data || {}), mfaEnabled: enabled }, cacheKey);
    };

    useEffect(() => {
        if (data?.mfaEnabled) {
            setMfaStatus({ enabled: true, secret: "", issuer: "", accountName: "", otpauthUrl: "" });
            return;
        }
        loadMfaStatus();
    }, [data?.mfaEnabled]);

    const onFinish = (allValues: Record<string, any>) => {
        axiosInstance.post(accountSecurityApiBase + "/updatePassword", allValues).then(async ({ data }) => {
            if (data.error) {
                await messageApi.error(data.message);
            } else if (data.error === 0) {
                await messageApi.success(data.message);
            }
        });
    };

    const onUpdateMfa = async (uri: string) => {
        setMfaSubmitting(true);
        try {
            const { data } = await axiosInstance.post(uri, mfaForm.getFieldsValue());
            if (data.error) {
                await messageApi.error(data.message);
                return;
            }
            await messageApi.success(data.message);
            mfaForm.resetFields();
            lastAutoSubmittedMfaCodeRef.current = "";
            setMfaDialogAction(null);
            if (uri.endsWith("/enableMfa")) {
                setMfaStatus({ enabled: true, secret: "", issuer: "", accountName: "", otpauthUrl: "" });
                syncCachedMfaEnabled(true);
            } else {
                syncCachedMfaEnabled(false);
                await loadMfaStatus();
            }
        } finally {
            setMfaSubmitting(false);
        }
    };

    useEffect(() => {
        if (offline || mfaSubmitting || !mfaCode || mfaCode.length !== 6 || !mfaDialogAction) {
            return;
        }
        if (lastAutoSubmittedMfaCodeRef.current === mfaCode) {
            return;
        }
        lastAutoSubmittedMfaCodeRef.current = mfaCode;
        void onUpdateMfa(`${accountSecurityApiBase}/${mfaDialogAction === "disable" ? "disableMfa" : "enableMfa"}`);
    }, [mfaCode, mfaDialogAction, mfaSubmitting, offline]);

    const openMfaDialog = async (action: "enable" | "disable") => {
        mfaForm.resetFields();
        lastAutoSubmittedMfaCodeRef.current = "";
        if (action === "enable" && (!mfaStatus || mfaStatus.enabled || !mfaStatus.otpauthUrl)) {
            await loadMfaStatus();
        }
        setMfaDialogAction(action);
    };

    const closeMfaDialog = () => {
        setMfaDialogAction(null);
        mfaForm.resetFields();
        lastAutoSubmittedMfaCodeRef.current = "";
    };

    return (
        <>
            {contextHolder}
            <Row gutter={[24, 24]}>
                <Col xs={24}>
                    <Card title={getRes().accountSecurity.passwordTitle} style={securitySurface.card}>
                        <Form {...formLayout} onFinish={(value) => onFinish(value)}>
                            <Form.Item
                                name="oldPassword"
                                label={getRes().accountSecurity.oldPassword}
                                rules={[{ required: true }]}
                            >
                                <Input.Password style={passwordStyle} />
                            </Form.Item>
                            <Form.Item
                                name="newPassword"
                                label={getRes().accountSecurity.newPassword}
                                rules={[{ required: true }]}
                            >
                                <Input.Password style={passwordStyle} />
                            </Form.Item>

                            <div
                                style={{
                                    position: "sticky",
                                    bottom: 0,
                                    paddingTop: 16,
                                    paddingBottom: "calc(16px + env(safe-area-inset-bottom))",
                                    background: theme.colorBgContainer,
                                    zIndex: 10,
                                    marginTop: 24,
                                    borderTop: `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`,
                                }}
                            >
                                <Button disabled={offline} type="primary" htmlType="submit">
                                    {getRes().submit}
                                </Button>
                            </div>
                        </Form>
                    </Card>
                </Col>
                <Col xs={24}>
                    <Card title={getRes().accountSecurity.mfaTitle} style={securitySurface.card}>
                        <Typography.Paragraph>
                            {mfaStatus?.enabled
                                ? getRes().accountSecurity.mfaEnabled
                                : getRes().accountSecurity.mfaDisabled}
                        </Typography.Paragraph>
                        {!mfaStatus?.enabled ? (
                            <Button disabled={offline} type="primary" onClick={() => void openMfaDialog("enable")}>
                                {getRes().accountSecurity.enableMfa}
                            </Button>
                        ) : (
                            <Button disabled={offline} danger onClick={() => void openMfaDialog("disable")}>
                                {getRes().accountSecurity.disableMfa}
                            </Button>
                        )}
                    </Card>
                </Col>
                <Col xs={24}>
                    <PasskeyManagement
                        offline={offline}
                        mfaEnabled={mfaStatus?.enabled ?? data?.mfaEnabled === true}
                        cardStyle={securitySurface.card}
                        modalWidth={securitySurface.modalWidth}
                    />
                </Col>
            </Row>
            <Modal
                title={
                    mfaDialogAction === "disable"
                        ? getRes().accountSecurity.disableMfa
                        : getRes().accountSecurity.enableMfa
                }
                open={mfaDialogAction !== null}
                onCancel={closeMfaDialog}
                footer={null}
                destroyOnHidden={false}
                width={securitySurface.modalWidth}
            >
                <Form form={mfaForm} layout="vertical">
                    {mfaDialogAction === "enable" && (
                        <>
                            <Typography.Paragraph>{getRes().accountSecurity.mfaSetupHint}</Typography.Paragraph>
                            {mfaStatus?.otpauthUrl && (
                                <Space
                                    direction="vertical"
                                    size={securitySurface.verticalGap}
                                    style={securitySurface.contentSpace}
                                >
                                    <QRCode value={mfaStatus.otpauthUrl} size={narrow ? 160 : 180} />
                                </Space>
                            )}
                            <Form.Item label={getRes().accountSecurity.mfaSecret}>
                                <Input readOnly value={mfaStatus?.secret ?? ""} />
                            </Form.Item>
                            <Form.Item label={getRes().accountSecurity.mfaSetupUrl}>
                                <Input readOnly value={mfaStatus?.otpauthUrl ?? ""} />
                            </Form.Item>
                        </>
                    )}
                    <Form.Item label={getRes().accountSecurity.mfaCode} name="code" rules={[{ required: true }]}>
                        <Input.OTP
                            autoFocus
                            inputMode={"numeric"}
                            length={6}
                            formatter={(value) => value.replace(/\D/g, "")}
                            onChange={(value) => {
                                if (value !== lastAutoSubmittedMfaCodeRef.current) {
                                    lastAutoSubmittedMfaCodeRef.current = "";
                                }
                                mfaForm.setFieldValue("code", value);
                            }}
                        />
                    </Form.Item>

                    <Space direction="vertical" style={securitySurface.actions} size={securitySurface.verticalGap}>
                        <Button
                            disabled={offline || (mfaCode?.length ?? 0) !== 6}
                            loading={mfaSubmitting}
                            onClick={() =>
                                void onUpdateMfa(
                                    `${accountSecurityApiBase}/${
                                        mfaDialogAction === "disable" ? "disableMfa" : "enableMfa"
                                    }`
                                )
                            }
                            type={mfaDialogAction === "disable" ? "default" : "primary"}
                            danger={mfaDialogAction === "disable"}
                            style={securitySurface.actions}
                        >
                            {mfaDialogAction === "disable"
                                ? getRes().accountSecurity.disableMfa
                                : getRes().accountSecurity.enableMfa}
                        </Button>
                        <Button disabled={mfaSubmitting} onClick={closeMfaDialog} style={securitySurface.actions}>
                            {getRes().close}
                        </Button>
                    </Space>
                </Form>
            </Modal>
        </>
    );
};

export default AccountSecurity;
