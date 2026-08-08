import { useEffect, useRef, useState } from "react";
import { Button, Divider, Form, Input, Layout, message, Space } from "antd";
import {
    getBackendServerUrl,
    getDefaultLoginInfo,
    getRealRouteUrl,
    getRes,
    hasConfiguredBackendServerUrl,
    isStaticPage,
    LoginUserInfo,
    setBackendServerUrl,
} from "../../utils/constants";
import { useNavigate } from "react-router-dom";
import Title from "antd/es/typography/Title";
import PWAHandler from "../../base/PWAHandler";
import { addToCache, removeAllCaches } from "../../utils/cache";
import styled from "styled-components";
import { getContextPath } from "../../utils/helpers";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getCsrData } from "../../api";
import {
    ApiResponse,
    LoginResponse,
    PasskeyAuthenticationOptionsResponse,
    PasskeyAuthenticationVerifyRequest,
} from "../../type";
import { AxiosInstance } from "axios";
import { getSsDate, ssKeyStorageKey } from "../../base/SsData";
import { getAppState } from "../../base/ConfigProviderApp";
import { useTheme } from "antd-style";
import { ADMIN_ERROR_CODE } from "../../common/admin-error-code";
import loginPublishingWorkspace from "../../assets/login-publishing-workspace.webp";
import { KeyOutlined } from "@ant-design/icons";
import { authenticateWithPasskey, canUsePasskeys, isPasskeyCancellation } from "../../utils/passkey";

const md5 = require("md5");

const PREFIX = "login";

export const classes = {
    title: `${PREFIX}-title`,
    card: `${PREFIX}-card`,
    content: `${PREFIX}-content`,
    copyrightTips: `${PREFIX}-copyrightTips`,
    container: `${PREFIX}-container`,
    sideImage: `${PREFIX}-sideImage`,
    formSection: `${PREFIX}-formSection`,
};

interface StyledLoginPageProps {
    mainColor: string;
    dark: boolean;
    desk: boolean;
    colorBgContainer: string;
    colorBgLayout: string;
    theme: {
        borderRadiusLG: number;
        boxShadowSecondary: string;
        colorText: string;
        colorTextSecondary: string;
        colorWhite: string;
    };
}

export const StyledLoginPage = styled(Layout)<StyledLoginPageProps>(
    ({ mainColor, dark, desk, colorBgContainer, colorBgLayout, theme }) => {
        return {
            height: "100vh",
            background: colorBgLayout,
            backgroundImage: desk
                ? `
            linear-gradient(rgba(23, 32, 51, 0.035) 1px, transparent 1px),
            linear-gradient(90deg, rgba(23, 32, 51, 0.035) 1px, transparent 1px)
        `
                : dark
                ? `
            radial-gradient(at 0% 0%, rgba(24, 144, 255, 0.08) 0px, transparent 50%),
            radial-gradient(at 100% 100%, rgba(114, 46, 209, 0.08) 0px, transparent 50%),
            linear-gradient(135deg, ${colorBgLayout} 0%, #000000 100%)
        `
                : `
            radial-gradient(at 0% 0%, rgba(24, 144, 255, 0.15) 0px, transparent 50%),
            radial-gradient(at 100% 100%, rgba(114, 46, 209, 0.15) 0px, transparent 50%),
            linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)
        `,
            backgroundSize: desk ? "72px 72px" : undefined,
            position: "relative",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            overflow: "hidden",

            [`& .${classes.container}`]: {
                display: "flex",
                width: "1000px",
                height: "600px",
                background: colorBgContainer,
                borderRadius: theme.borderRadiusLG,
                boxShadow: desk
                    ? theme.boxShadowSecondary
                    : dark
                    ? "0 20px 40px rgba(0, 0, 0, 0.4), 0 10px 20px rgba(0, 0, 0, 0.3)"
                    : "0 20px 40px rgba(0, 0, 0, 0.08), 0 10px 20px rgba(0, 0, 0, 0.05)",
                overflow: "hidden",
                position: "relative",
                "@media (max-width: 1024px)": {
                    width: "90%",
                    height: "auto",
                    minHeight: "500px",
                },
                "@media (max-width: 768px)": {
                    flexDirection: "column",
                    width: "90%",
                    height: "auto",
                },
            },

            [`& .${classes.sideImage}`]: {
                flex: 1,
                background: `url(${loginPublishingWorkspace}) center center / cover no-repeat`,
                position: "relative",
                display: "flex",
                flexDirection: "column",
                justifyContent: "flex-end",
                padding: "40px",
                "&::before": {
                    content: '""',
                    position: "absolute",
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    background: `linear-gradient(to bottom, ${mainColor}40 0%, ${mainColor}cc 100%)`,
                },
                "& .side-content": {
                    position: "relative",
                    color: theme.colorWhite,
                    h2: {
                        fontSize: "28px",
                        fontWeight: 700,
                        marginBottom: "12px",
                        color: theme.colorWhite,
                        textShadow: "0 2px 4px rgba(0,0,0,0.2)",
                    },
                    p: {
                        fontSize: "16px",
                        opacity: 0.9,
                        lineHeight: 1.6,
                        color: "rgba(255, 255, 255, 0.9)",
                    },
                },
                "@media (max-width: 768px)": {
                    display: "none",
                },
            },

            [`& .${classes.formSection} `]: {
                flex: 1,
                display: "flex",
                flexDirection: "column",
                justifyContent: "center",
                padding: "60px 50px",
                background: colorBgContainer,
                [`& .${classes.title}`]: {
                    fontSize: "28px",
                    fontWeight: 700,
                    marginBottom: "8px",
                    textAlign: "left",
                    background: desk ? "none" : `linear-gradient(135deg, ${mainColor} 0%, #764ba2 100%)`,
                    color: theme.colorText,
                    WebkitBackgroundClip: desk ? undefined : "text",
                    WebkitTextFillColor: desk ? "currentColor" : "transparent",
                    padding: 0,
                },
                "& .subtitle": {
                    fontSize: "14px",
                    color: desk ? theme.colorTextSecondary : dark ? "rgba(255, 255, 255, 0.45)" : "#6b7280",
                    marginBottom: "32px",
                    textAlign: "left",
                    fontWeight: 500,
                },
                "@media (max-width: 768px)": {
                    padding: "30px 20px",
                    [`& .${classes.title}`]: {
                        textAlign: "center",
                        marginTop: "16px",
                    },
                    "& .subtitle": {
                        textAlign: "center",
                        marginBottom: "24px",
                    },
                },
            },

            [`& .${classes.card} `]: {
                // Reset card styles as we are moving to plain form
                border: "none",
                boxShadow: "none",
                background: "transparent",
                width: "100%",
                "&::before": { display: "none" },
            },

            [`& .ant-form-item-label`]: {
                textAlign: "left",
                paddingBottom: "6px",
                label: {
                    color: desk ? theme.colorTextSecondary : dark ? "rgba(255, 255, 255, 0.65)" : "#374151",
                    fontSize: "14px",
                    fontWeight: 600,
                },
            },
            [`& .ant-btn-primary`]: {
                width: "100%",
                fontWeight: 600,
                height: 44,
            },
        };
    }
);

const preloadApiCache = (axiosInstance: AxiosInstance, uris: string[]) => {
    void Promise.all(
        uris.map(async (e) => {
            const key = e.split("/api/admin")[1];
            if (!key) {
                return;
            }
            try {
                const { data } = await getCsrData(key, 0, axiosInstance);
                addToCache(key, data);
            } catch (error) {
                console.error("cache error:", error);
            }
        })
    );
};

const saveApiCache = (axiosInstance: AxiosInstance, responseBody: LoginResponse) => {
    getSsDate().pageBuildId = responseBody.pageBuildId;
    getSsDate().user = responseBody.data;
    getSsDate().key = responseBody.data.key;
    if (isStaticPage()) {
        localStorage.setItem(ssKeyStorageKey, responseBody.data.key);
    }
    addToCache("/user", responseBody.data);
    preloadApiCache(axiosInstance, responseBody.data.cacheableApiUris ?? []);
};

const Index = ({ offline }: { offline: boolean }) => {
    const [logging, setLogging] = useState<boolean>(false);
    const [passkeyLogging, setPasskeyLogging] = useState(false);
    const defaultUserInfo = getDefaultLoginInfo();
    const [loginState, setLoginState] = useState<LoginUserInfo>(defaultUserInfo);
    const [backendServerUrlConfigured, setBackendServerUrlConfigured] = useState(
        () => !isStaticPage() || hasConfiguredBackendServerUrl()
    );
    const [mfaCode, setMfaCode] = useState("");
    const [mfaStep, setMfaStep] = useState(false);
    const lastAutoSubmittedMfaCodeRef = useRef("");
    const passkeyLoginInFlightRef = useRef(false);

    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });

    const navigate = useNavigate();
    const theme = useTheme();

    const axiosInstance = useAxiosBaseInstance();
    const passkeyAvailable =
        getRes().passkeyLoginEnabled === true &&
        canUsePasskeys(loginState.backendServerUrl || getBackendServerUrl(), {
            backendServerUrlConfigured,
        });

    const completeLogin = (data: LoginResponse) => {
        if (isStaticPage() && loginState.backendServerUrl != null) {
            setBackendServerUrl(loginState.backendServerUrl);
        }
        saveApiCache(axiosInstance, data);
        const query = new URLSearchParams(window.location.search);
        const redirectFrom = query.get("redirectFrom") as string;
        if (redirectFrom !== null && redirectFrom !== "") {
            const jumpTo = decodeURIComponent(redirectFrom);
            if (jumpTo.startsWith(getContextPath() + "admin/plugins/")) {
                window.location.href = window.location.protocol + "//" + window.location.host + jumpTo;
            } else {
                const newUrl = jumpTo.replace(getContextPath() + "admin", "");
                const urlInfo = newUrl.split("?");
                if (urlInfo[0] === "/" || urlInfo[0] === "") {
                    urlInfo[0] = "/index";
                }
                navigate(getRealRouteUrl(urlInfo.join("?")), { replace: true });
            }
        } else {
            navigate(getRealRouteUrl("/index"), { replace: true });
        }
    };

    const submitLogin = async (otpCode?: string) => {
        setLogging(true);
        const loginForm = {
            userName: loginState.userName,
            password: md5(loginState.password),
            mfaCode: otpCode,
            https: window.location.protocol === "https:",
        };
        try {
            if (isStaticPage()) {
                axiosInstance.defaults.baseURL = loginState.backendServerUrl;
            }
            const { data } = await axiosInstance.post<LoginResponse>("/api/admin/login", loginForm);
            if (data.error) {
                if (data.error === ADMIN_ERROR_CODE.mfaCodeRequired || data.error === ADMIN_ERROR_CODE.mfaCodeInvalid) {
                    setMfaStep(true);
                    if (data.error === ADMIN_ERROR_CODE.mfaCodeInvalid) {
                        await messageApi.error(data.message);
                    }
                    return;
                }
                await messageApi.error(data.message);
                return;
            }
            if (data.error == 0) {
                completeLogin(data);
                return;
            }
            await messageApi.error(getRes().error.unknown);
        } finally {
            setLogging(false);
        }
    };

    const submitPasskeyLogin = async () => {
        if (passkeyLoginInFlightRef.current) {
            return;
        }
        passkeyLoginInFlightRef.current = true;
        setPasskeyLogging(true);
        try {
            if (isStaticPage()) {
                axiosInstance.defaults.baseURL = loginState.backendServerUrl;
            }
            let optionsResponse: ApiResponse<PasskeyAuthenticationOptionsResponse>;
            try {
                optionsResponse = (
                    await axiosInstance.post<ApiResponse<PasskeyAuthenticationOptionsResponse>>(
                        "/api/admin/passkey/authentication/options",
                        {}
                    )
                ).data;
            } catch {
                return;
            }
            if (optionsResponse.error) {
                void messageApi.error(optionsResponse.message);
                return;
            }
            let response: PasskeyAuthenticationVerifyRequest["response"];
            try {
                response = await authenticateWithPasskey(optionsResponse.data.options);
            } catch (error) {
                if (!isPasskeyCancellation(error)) {
                    void messageApi.error(getRes().login.passkeyFailed);
                }
                return;
            }
            const verifyRequest: PasskeyAuthenticationVerifyRequest = {
                requestId: optionsResponse.data.requestId,
                response,
            };
            let loginResponse: LoginResponse;
            try {
                loginResponse = (
                    await axiosInstance.post<LoginResponse>("/api/admin/passkey/authentication/verify", verifyRequest)
                ).data;
            } catch {
                return;
            }
            if (loginResponse.error) {
                void messageApi.error(loginResponse.message);
                return;
            }
            completeLogin(loginResponse);
        } finally {
            passkeyLoginInFlightRef.current = false;
            setPasskeyLogging(false);
        }
    };

    const handleLoginValuesChange = (changedValues: LoginUserInfo, values: LoginUserInfo) => {
        if (isStaticPage() && Object.prototype.hasOwnProperty.call(changedValues, "backendServerUrl")) {
            setBackendServerUrlConfigured(Boolean(changedValues.backendServerUrl?.trim()));
        }
        setLoginState(values);
    };

    const onFinish = async () => {
        await submitLogin();
    };

    const onFinishMfa = async () => {
        await submitLogin(mfaCode);
    };

    useEffect(() => {
        removeAllCaches();
    }, []);

    useEffect(() => {
        if (!mfaStep || logging || mfaCode.length !== 6) {
            return;
        }
        if (lastAutoSubmittedMfaCodeRef.current === mfaCode) {
            return;
        }
        lastAutoSubmittedMfaCodeRef.current = mfaCode;
        void onFinishMfa();
    }, [logging, mfaCode, mfaStep]);

    return (
        <PWAHandler>
            {contextHolder}
            <StyledLoginPage
                theme={theme}
                mainColor={getAppState().colorPrimary}
                dark={getAppState().dark}
                desk={getAppState().theme === "desk"}
                colorBgContainer={theme.colorBgContainer}
                colorBgLayout={theme.colorBgLayout}
            >
                <div className={classes.container}>
                    <div className={classes.sideImage}>
                        <div className="side-content">
                            <h2>{getRes().websiteTitle}</h2>
                            <p>{getRes().login.copyrightCurrentYear}. All Rights Reserved.</p>
                        </div>
                    </div>
                    <div className={classes.formSection}>
                        <Title level={3} className={classes.title}>
                            {getRes().login.title}
                        </Title>
                        {!mfaStep && <div className="subtitle">{getRes().login.userNameAndPassword}</div>}
                        {mfaStep && <div className="subtitle">{getRes().login.mfaStepHint}</div>}

                        {!mfaStep && (
                            <>
                                <Form
                                    layout="vertical"
                                    initialValues={defaultUserInfo}
                                    onFinish={() => onFinish()}
                                    onValuesChange={handleLoginValuesChange}
                                >
                                    {isStaticPage() && (
                                        <Form.Item
                                            label={getRes().login.backendServerUrl}
                                            name={"backendServerUrl"}
                                            rules={[{ required: true }]}
                                        >
                                            <Input autoComplete={"url"} />
                                        </Form.Item>
                                    )}
                                    <Form.Item
                                        label={getRes().login.userName}
                                        name="userName"
                                        rules={[{ required: true }]}
                                    >
                                        <Input autoComplete={"username"} />
                                    </Form.Item>

                                    <Form.Item
                                        label={getRes().login.password}
                                        name="password"
                                        rules={[{ required: true }]}
                                    >
                                        <Input.Password autoComplete={"current-password"} />
                                    </Form.Item>

                                    <Form.Item style={{ marginTop: "20px", marginBottom: 0 }}>
                                        <Button
                                            disabled={offline || passkeyLogging}
                                            loading={logging}
                                            type="primary"
                                            size={"large"}
                                            htmlType="submit"
                                        >
                                            {getRes().login.submit}
                                        </Button>
                                    </Form.Item>
                                </Form>
                                {passkeyAvailable && (
                                    <>
                                        <Divider plain>{getRes().login.passkeyDivider}</Divider>
                                        <Button
                                            block
                                            disabled={offline || logging}
                                            icon={<KeyOutlined />}
                                            loading={passkeyLogging}
                                            size="large"
                                            onClick={() => void submitPasskeyLogin()}
                                        >
                                            {getRes().login.passkeySubmit}
                                        </Button>
                                    </>
                                )}
                            </>
                        )}

                        {mfaStep && (
                            <Form layout="vertical" onFinish={() => onFinishMfa()}>
                                <Form.Item label={getRes().login.mfaCode} rules={[{ required: true }]}>
                                    <Input.OTP
                                        autoFocus
                                        inputMode={"numeric"}
                                        length={6}
                                        value={mfaCode}
                                        onChange={(value) => {
                                            if (value !== lastAutoSubmittedMfaCodeRef.current) {
                                                lastAutoSubmittedMfaCodeRef.current = "";
                                            }
                                            setMfaCode(value);
                                        }}
                                        formatter={(value) => value.replace(/\D/g, "")}
                                    />
                                </Form.Item>
                                <Form.Item style={{ marginTop: "20px", marginBottom: 0 }}>
                                    <Space direction="vertical" style={{ width: "100%" }} size={12}>
                                        <Button
                                            disabled={offline || mfaCode.length !== 6}
                                            loading={logging}
                                            type="primary"
                                            size={"large"}
                                            htmlType="submit"
                                            style={{ width: "100%" }}
                                        >
                                            {getRes().login.mfaSubmit}
                                        </Button>
                                        <Button
                                            disabled={logging}
                                            size={"large"}
                                            onClick={() => {
                                                setMfaStep(false);
                                                setMfaCode("");
                                                lastAutoSubmittedMfaCodeRef.current = "";
                                            }}
                                        >
                                            {getRes().login.mfaBack}
                                        </Button>
                                    </Space>
                                </Form.Item>
                            </Form>
                        )}
                    </div>
                </div>
            </StyledLoginPage>
        </PWAHandler>
    );
};

export default Index;
