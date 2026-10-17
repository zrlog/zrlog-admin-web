import { Alert, Button, Descriptions, Flex, Form, Space, Typography } from "antd";
import Input from "@zrlog/ui/antd/Input";
import Tag from "@zrlog/ui/antd/Tag";
import { useUiApp } from "@zrlog/ui/feedback";
import { useEffect, useState } from "react";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getRes } from "../../utils/constants";
import SettingsLayout from "../common/SettingsLayout";

export type ProxyData = {
    url: string;
    noProxy: string;
    source: "environment" | "file" | "default";
    httpsSource: "environment" | "file" | "default";
    noProxySource: "environment" | "file" | "default";
    httpVariable: string;
    httpsVariable: string;
    noProxyVariable: string;
    activeUrl: string;
    activeHttpsUrl: string;
    activeNoProxy: string;
    restartRequired: boolean;
    writable: boolean;
};

export const validProxyUrl = (value: string) => {
    if (!value.trim()) return true;
    try {
        const url = new URL(value.trim());
        return (
            url.protocol === "http:" &&
            Boolean(url.hostname) &&
            !url.username &&
            !url.password &&
            !url.search &&
            !url.hash &&
            url.pathname === "/" &&
            url.port !== "0"
        );
    } catch {
        return false;
    }
};

export const validNoProxy = (value: string) =>
    value.split(/[,|]/).every((part) => {
        const token = part.trim();
        if (!token) return true;
        if (!/^[A-Za-z0-9.*_:[\]-]+$/.test(token) || token.includes("..") || token === ".") return false;
        if (!token.includes(":")) return true;
        try {
            const address = token.startsWith("[") && token.endsWith("]") ? token : `[${token}]`;
            return Boolean(new URL(`http://${address}/`).hostname);
        } catch {
            return false;
        }
    });

const ProxySettings = ({ data, offline }: { data: ProxyData; offline: boolean }) => {
    const res = getRes();
    const text = res.system.proxy;
    const axios = useAxiosBaseInstance();
    const { message } = useUiApp();
    const [form] = Form.useForm();
    const [settings, setSettings] = useState(data);
    const [saving, setSaving] = useState(false);
    const [testing, setTesting] = useState(false);
    const [failure, setFailure] = useState("");
    useEffect(() => {
        setSettings(data);
        form.setFieldsValue(data);
    }, [data, form]);
    const readOnly = offline || !settings.writable;
    const hint = (variable: string) => text.environmentHint.replace("{name}", variable);
    const proxyVariables = [settings.httpVariable, settings.httpsVariable].filter(Boolean);

    const save = async (values: { url: string; noProxy: string }) => {
        setFailure("");
        setSaving(true);
        try {
            const response = await axios.post("/api/admin/system/proxy", values);
            if (response.data.error) throw new Error();
            setSettings(response.data.data);
            form.setFieldsValue(response.data.data);
            message.success(text.saved);
        } catch {
            setFailure(text.failed);
        } finally {
            setSaving(false);
        }
    };

    const test = async () => {
        const values = await form.validateFields();
        setFailure("");
        setTesting(true);
        try {
            const response = await axios.post("/api/admin/system/proxy/test", values, { timeout: 15000 });
            if (response.data.error || !response.data.data) throw new Error();
            message.success(text.testSuccess);
        } catch {
            setFailure(text.testFailed);
        } finally {
            setTesting(false);
        }
    };

    return (
        <SettingsLayout
            activeKey="proxy"
            title={text.title}
            summary={text.summary}
            groups={[
                {
                    label: res.website.nav.system,
                    items: [
                        { key: "info", label: res.system.info, path: "/system" },
                        { key: "proxy", label: text.title, path: "/system/proxy" },
                    ],
                },
            ]}
        >
            <Flex vertical gap="large" style={{ maxWidth: 800, paddingBottom: 24 }}>
                {settings.restartRequired && <Alert type="warning" showIcon title={text.pending} />}
                <Descriptions
                    title={text.active}
                    column={1}
                    size="small"
                    items={[
                        {
                            key: "http",
                            label: "HTTP",
                            children: settings.activeUrl ? (
                                <Typography.Text style={{ overflowWrap: "anywhere" }}>
                                    {settings.activeUrl}
                                </Typography.Text>
                            ) : (
                                text.disabled
                            ),
                        },
                        {
                            key: "https",
                            label: "HTTPS",
                            children: settings.activeHttpsUrl ? (
                                <Typography.Text style={{ overflowWrap: "anywhere" }}>
                                    {settings.activeHttpsUrl}
                                </Typography.Text>
                            ) : (
                                text.disabled
                            ),
                        },
                        { key: "bypass", label: text.noProxy, children: settings.activeNoProxy || text.loopbackOnly },
                    ]}
                />
                <Alert type="info" showIcon title={text.restartHint} description={text.recoveryHint} />
                {!settings.writable && <Alert type="warning" showIcon title={text.readOnly} />}
                {failure && <Alert type="error" showIcon title={failure} role="alert" />}
                <Form form={form} layout="vertical" initialValues={data} onFinish={save}>
                    <Form.Item
                        name="url"
                        label={
                            <Space wrap>
                                {text.url}
                                <Tag>{text.localFile}</Tag>
                            </Space>
                        }
                        extra={
                            <>
                                {text.urlHint}
                                {proxyVariables.map((variable) => (
                                    <div key={variable}>{hint(variable)}</div>
                                ))}
                            </>
                        }
                        rules={[
                            {
                                validator: (_, value) =>
                                    validProxyUrl(value || "")
                                        ? Promise.resolve()
                                        : Promise.reject(new Error(text.invalidUrl)),
                            },
                        ]}
                    >
                        <Input disabled={readOnly} maxLength={2048} placeholder="http://proxy.example.com:3128" />
                    </Form.Item>
                    <Form.Item
                        name="noProxy"
                        label={
                            <Space wrap>
                                {text.noProxy}
                                <Tag>{text.localFile}</Tag>
                            </Space>
                        }
                        extra={
                            <>
                                {text.noProxyHint}
                                {settings.noProxyVariable && <div>{hint(settings.noProxyVariable)}</div>}
                            </>
                        }
                        rules={[
                            {
                                validator: (_, value: string) => {
                                    const valid = validNoProxy(value || "");
                                    return valid ? Promise.resolve() : Promise.reject(new Error(text.invalidNoProxy));
                                },
                            },
                        ]}
                    >
                        <Input disabled={readOnly} maxLength={4096} placeholder=".example.com,*.internal" />
                    </Form.Item>
                    <Flex wrap gap="small">
                        <Button type="primary" htmlType="submit" loading={saving} disabled={readOnly || testing}>
                            {text.save}
                        </Button>
                        <Button
                            onClick={() => {
                                void test().catch(() => undefined);
                            }}
                            loading={testing}
                            disabled={offline || saving}
                        >
                            {text.test}
                        </Button>
                    </Flex>
                    <Typography.Paragraph type="secondary" style={{ marginTop: 12 }}>
                        {text.testHint}
                    </Typography.Paragraph>
                </Form>
                <div>
                    <Typography.Text code>conf/proxy.properties</Typography.Text>
                    <pre style={{ overflowX: "auto", whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>
                        {
                            "network.proxy.url=http://proxy.example.com:3128\nnetwork.proxy.noProxy=.example.com,*.internal"
                        }
                    </pre>
                    <Typography.Text code>HTTP_PROXY</Typography.Text>
                    {" / "}
                    <Typography.Text code>HTTPS_PROXY</Typography.Text>
                    {" / "}
                    <Typography.Text code>NO_PROXY</Typography.Text>
                    <Typography.Paragraph type="secondary">{text.precedenceHint}</Typography.Paragraph>
                </div>
            </Flex>
        </SettingsLayout>
    );
};

export default ProxySettings;
