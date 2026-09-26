import { useState } from "react";
import {
    Alert,
    Button,
    Card,
    Checkbox,
    Drawer,
    Empty,
    Form,
    Input,
    List,
    Popconfirm,
    Radio,
    Select,
    Space,
    Tag,
    Typography,
    message,
} from "antd";
import { useAxiosBaseInstance } from "../base/AppBase";
import { getRes } from "../utils/constants";
import { getSsDate } from "../base/SsData";
import { resolveApplicationServerUrl } from "../utils/application-server-url";

export type PersonalAccessToken = {
    id: string;
    userId: number;
    name: string;
    scopes: string[];
    resource: string;
    createdAt: number;
    expiresAt: number;
    revoked: boolean;
    expired: boolean;
    invalidated: boolean;
};
type Created = { token: string; info: PersonalAccessToken };
type FormValues = { name: string; expiresInDays: number; range: "own" | "all"; permissions: string[] };

export default function PersonalAccessTokens({
    tokens,
    scopes,
    onChange,
}: {
    tokens: PersonalAccessToken[];
    scopes: string[];
    onChange: () => Promise<void>;
}) {
    const api = useAxiosBaseInstance();
    const res = getRes().oauth;
    const labels: Record<string, string> = res.scopeLabels;
    const [form] = Form.useForm<FormValues>();
    const [open, setOpen] = useState(false);
    const [created, setCreated] = useState<Created>();
    const [busy, setBusy] = useState(false);
    const [notice, contextHolder] = message.useMessage();
    const close = () => {
        if (busy) return;
        setOpen(false);
        setCreated(undefined);
        form.resetFields();
    };
    const create = async (values: FormValues) => {
        setBusy(true);
        try {
            const response = await api.post("/api/admin/oauth/createPersonalToken", {
                name: values.name.trim(),
                expiresInDays: values.expiresInDays,
                scopes: ["articles:read", ...values.permissions, ...(values.range === "all" ? ["articles:all"] : [])],
            });
            if (response.data.error) {
                notice.error(response.data.message);
                return;
            }
            // Keep the one-time secret visible even if the subsequent list refresh fails.
            setCreated(response.data.data);
            await onChange();
        } catch {
            notice.error(getRes().error.requestError);
        } finally {
            setBusy(false);
        }
    };
    const revoke = async (id: string) => {
        setBusy(true);
        try {
            const response = await api.post("/api/admin/oauth/revokePersonalToken", { id });
            if (response.data.error) {
                notice.error(response.data.message);
                return;
            }
            await onChange();
            notice.success(res.saved);
        } catch {
            notice.error(getRes().error.requestError);
        } finally {
            setBusy(false);
        }
    };
    return (
        <Card
            title={res.personalTokens.title}
            extra={
                <Button
                    type="primary"
                    disabled={busy || !scopes.includes("articles:read")}
                    onClick={() => {
                        setCreated(undefined);
                        form.resetFields();
                        setOpen(true);
                    }}
                >
                    {res.personalTokens.create}
                </Button>
            }
        >
            {contextHolder}
            <Typography.Paragraph>{res.personalTokens.description}</Typography.Paragraph>
            <Typography.Paragraph type="secondary">
                {res.personalTokens.account}: {getSsDate().user?.userName}
            </Typography.Paragraph>
            <List
                locale={{ emptyText: <Empty description={res.personalTokens.empty} /> }}
                dataSource={tokens}
                renderItem={(token) => (
                    <List.Item
                        actions={
                            token.revoked
                                ? []
                                : [
                                      <Popconfirm
                                          key="revoke"
                                          title={res.personalTokens.confirmRevoke}
                                          onConfirm={() => revoke(token.id)}
                                      >
                                          <Button disabled={busy}>{res.personalTokens.revoke}</Button>
                                      </Popconfirm>,
                                  ]
                        }
                    >
                        <List.Item.Meta
                            title={
                                <Space wrap>
                                    {token.name}
                                    <Tag>
                                        {token.revoked
                                            ? res.revoked
                                            : token.invalidated
                                            ? res.personalTokens.invalidated
                                            : token.expired
                                            ? res.personalTokens.expired
                                            : res.active}
                                    </Tag>
                                </Space>
                            }
                            description={
                                <Space orientation="vertical">
                                    <Typography.Text>
                                        {res.range}: {token.scopes.includes("articles:all") ? res.all : res.own}
                                    </Typography.Text>
                                    <Space wrap>
                                        {token.scopes
                                            .filter((scope) => scope !== "articles:all")
                                            .map((scope) => (
                                                <Tag key={scope}>{labels[scope] ?? scope}</Tag>
                                            ))}
                                    </Space>
                                    <Typography.Text type="secondary">
                                        {res.personalTokens.createdAt}: {new Date(token.createdAt).toLocaleString()}
                                    </Typography.Text>
                                    <Typography.Text type="secondary">
                                        {res.personalTokens.expiresAt}: {new Date(token.expiresAt).toLocaleString()}
                                    </Typography.Text>
                                </Space>
                            }
                        />
                    </List.Item>
                )}
            />
            <Drawer
                title={created ? res.personalTokens.created : res.personalTokens.create}
                open={open}
                onClose={close}
                destroyOnHidden
                closable={!busy}
                maskClosable={!busy}
                keyboard={!busy}
            >
                {created ? (
                    <Space orientation="vertical" size="large" style={{ width: "100%" }}>
                        <Alert type="info" showIcon title={res.personalTokens.once} />
                        <div>
                            <Typography.Paragraph strong>{res.mcpUrl}</Typography.Paragraph>
                            <Typography.Paragraph copyable style={{ overflowWrap: "anywhere" }}>
                                {resolveApplicationServerUrl(created.info.resource, "mcp")}
                            </Typography.Paragraph>
                        </div>
                        <div>
                            <Typography.Paragraph strong>{res.personalTokens.value}</Typography.Paragraph>
                            <Typography.Paragraph copyable code style={{ overflowWrap: "anywhere" }}>
                                {created.token}
                            </Typography.Paragraph>
                        </div>
                        <Typography.Paragraph>{res.personalTokens.connectionHelp}</Typography.Paragraph>
                        <Typography.Text type="secondary">
                            {res.personalTokens.expiresAt}: {new Date(created.info.expiresAt).toLocaleString()}
                        </Typography.Text>
                        <Button onClick={close} disabled={busy}>
                            {res.personalTokens.close}
                        </Button>
                    </Space>
                ) : (
                    <Form
                        form={form}
                        layout="vertical"
                        initialValues={{ expiresInDays: 30, range: "own", permissions: [] }}
                        onFinish={create}
                    >
                        <Form.Item
                            name="name"
                            label={res.personalTokens.name}
                            rules={[{ required: true, whitespace: true, max: 128 }]}
                        >
                            <Input
                                placeholder={res.personalTokens.namePlaceholder}
                                autoComplete="off"
                                disabled={busy}
                            />
                        </Form.Item>
                        <Form.Item name="expiresInDays" label={res.personalTokens.lifetime}>
                            <Select
                                disabled={busy}
                                options={[
                                    { value: 7, label: res.personalTokens.days7 },
                                    { value: 30, label: res.personalTokens.days30 },
                                    { value: 90, label: res.personalTokens.days90 },
                                ]}
                            />
                        </Form.Item>
                        {scopes.includes("articles:all") && (
                            <Form.Item name="range" label={res.range}>
                                <Radio.Group disabled={busy}>
                                    <Space orientation="vertical">
                                        <Radio value="own">{res.own}</Radio>
                                        <Radio value="all">{res.all}</Radio>
                                    </Space>
                                </Radio.Group>
                            </Form.Item>
                        )}
                        <Form.Item
                            name="permissions"
                            label={res.personalTokens.contents}
                            extra={res.personalTokens.readHelp}
                        >
                            <Checkbox.Group
                                disabled={busy}
                                options={["articles:read_drafts", "articles:read_private"]
                                    .filter((scope) => scopes.includes(scope))
                                    .map((scope) => ({ value: scope, label: labels[scope] }))}
                            />
                        </Form.Item>
                        <Button type="primary" htmlType="submit" loading={busy}>
                            {res.personalTokens.create}
                        </Button>
                    </Form>
                )}
            </Drawer>
        </Card>
    );
}
