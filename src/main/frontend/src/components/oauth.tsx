import { useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
import PersonalAccessTokens, { PersonalAccessToken } from "./personal-access-tokens";
import {
    Alert,
    Button,
    Card,
    Drawer,
    Empty,
    Form,
    Input,
    List,
    Popconfirm,
    Space,
    Tag,
    Typography,
    message,
} from "antd";
import { useAxiosBaseInstance } from "../base/AppBase";
import { getRes } from "../utils/constants";
import { resolveApplicationServerUrl } from "../utils/application-server-url";
import { getPageDataCacheKey } from "../utils/cache";
import type { AdminCommonProps } from "../type";
import { getSsDate } from "../base/SsData";
import { getPageApiUri, UserApplicationPage } from "../utils/account-page-routes";
type Client = { clientId: string; name: string; redirectUris: string[] };
type Grant = { id: string; clientName: string; scope: string; createdAt: number; revoked: boolean };
export type UserApplicationsData = {
    clients: Client[];
    grants: Grant[];
    administrator: boolean;
    issuer: string;
    resource: string;
    mcpResource?: string;
    personalTokens: PersonalAccessToken[];
    personalTokenScopes: string[];
    personalTokenPermissions?: string[];
    notificationEndpoint?: string;
};
type UserApplicationsProps = Pick<AdminCommonProps<UserApplicationsData>, "data" | "offline" | "updateCache"> & {
    activePage?: UserApplicationPage;
};

function OAuthConnections({
    data,
    updateCache,
    activePage = "tokens",
}: Pick<UserApplicationsProps, "data" | "updateCache" | "activePage">) {
    const [page, setPage] = useState(data);
    const location = useLocation();
    useEffect(() => setPage(data), [data]);
    const [open, setOpen] = useState(false);
    const [busy, setBusy] = useState(false);
    const [form] = Form.useForm();
    const [notice, contextHolder] = message.useMessage();
    const api = useAxiosBaseInstance();
    const res = getRes().oauth;
    const labels: Record<string, string> = {
        ...res.scopeLabels,
        ...getRes().access.actions,
        "account:inherit": res.personalTokens.inherit,
    };
    const oauthEndpoints = (
        <Space orientation="vertical" style={{ maxWidth: "100%" }}>
            <Typography.Text>{res.issuer}</Typography.Text>
            <Typography.Text copyable style={{ overflowWrap: "anywhere" }}>
                {resolveApplicationServerUrl(page.issuer, "")}
            </Typography.Text>
            <Typography.Text>{res.resource}</Typography.Text>
            <Typography.Text copyable style={{ overflowWrap: "anywhere" }}>
                {resolveApplicationServerUrl(page.resource, "api/oauth")}
            </Typography.Text>
        </Space>
    );
    const reload = async () => {
        const session = getSsDate().key;
        const response = await api.get(getPageApiUri(getPageDataCacheKey(location)));
        if (session !== getSsDate().key) return;
        if (!response.data.error) {
            setPage(response.data.data);
            updateCache?.(response.data.data, getPageDataCacheKey(location));
        }
    };
    const mutate = async (action: string, body: unknown) => {
        setBusy(true);
        try {
            const response = await api.post(`/api/admin/oauth/${action}`, body);
            if (response.data.error) {
                notice.error(response.data.message);
                return false;
            }
            await reload();
            notice.success(res.saved);
            return true;
        } finally {
            setBusy(false);
        }
    };
    return (
        <Space orientation="vertical" size="large" style={{ width: "100%" }}>
            {contextHolder}
            {
                [
                    {
                        key: "tokens",
                        label: res.personalTokens.title,
                        children: (
                            <Space orientation="vertical" size="large" style={{ width: "100%" }}>
                                <div>
                                    <Space orientation="vertical" style={{ maxWidth: "100%" }}>
                                        <Typography.Text>{res.personalTokens.siteUrl}</Typography.Text>
                                        <Typography.Text copyable style={{ overflowWrap: "anywhere" }}>
                                            {resolveApplicationServerUrl(page.issuer, "")}
                                        </Typography.Text>
                                        <Typography.Text>{res.mcpUrl}</Typography.Text>
                                        <Typography.Text copyable style={{ overflowWrap: "anywhere" }}>
                                            {resolveApplicationServerUrl(page.mcpResource, "mcp")}
                                        </Typography.Text>
                                        <Typography.Paragraph type="secondary">{res.mcpHelp}</Typography.Paragraph>
                                        <Typography.Text>{res.personalTokens.notificationUrl}</Typography.Text>
                                        <Typography.Text copyable style={{ overflowWrap: "anywhere" }}>
                                            {resolveApplicationServerUrl(
                                                page.notificationEndpoint,
                                                "api/webhook/message-center/notice"
                                            )}
                                        </Typography.Text>
                                        <Typography.Paragraph type="secondary">
                                            {res.personalTokens.notificationHelp}
                                        </Typography.Paragraph>
                                    </Space>
                                </div>
                                <PersonalAccessTokens
                                    tokens={page.personalTokens}
                                    permissions={page.personalTokenPermissions ?? []}
                                    siteUrl={page.issuer}
                                    onChange={reload}
                                />
                            </Space>
                        ),
                    },
                    {
                        key: "grants",
                        label: res.grants,
                        children: (
                            <Space orientation="vertical" size="large" style={{ width: "100%" }}>
                                {oauthEndpoints}
                                <Card title={res.grants}>
                                    <List
                                        locale={{ emptyText: <Empty description={res.empty} /> }}
                                        dataSource={page.grants}
                                        renderItem={(grant) => (
                                            <List.Item
                                                actions={
                                                    grant.revoked
                                                        ? []
                                                        : [
                                                              <Popconfirm
                                                                  key="revoke"
                                                                  title={res.confirmRevoke}
                                                                  onConfirm={() =>
                                                                      mutate("revokeGrant", { id: grant.id })
                                                                  }
                                                              >
                                                                  <Button loading={busy}>{res.revoke}</Button>
                                                              </Popconfirm>,
                                                          ]
                                                }
                                            >
                                                <List.Item.Meta
                                                    title={
                                                        <Space wrap>
                                                            {grant.clientName}
                                                            <Tag>{grant.revoked ? res.revoked : res.active}</Tag>
                                                        </Space>
                                                    }
                                                    description={
                                                        <Space orientation="vertical">
                                                            <Typography.Text type="secondary">
                                                                {new Date(grant.createdAt).toLocaleString()}
                                                            </Typography.Text>
                                                            {grant.scope.includes("articles:") && (
                                                                <Typography.Text>
                                                                    {res.range}:{" "}
                                                                    {grant.scope.split(" ").includes("articles:all")
                                                                        ? res.all
                                                                        : res.own}
                                                                </Typography.Text>
                                                            )}
                                                            <Space wrap>
                                                                {grant.scope
                                                                    .split(" ")
                                                                    .filter((s) => s !== "articles:all")
                                                                    .map((scope) => (
                                                                        <Tag key={scope}>{labels[scope] ?? scope}</Tag>
                                                                    ))}
                                                            </Space>
                                                        </Space>
                                                    }
                                                />
                                            </List.Item>
                                        )}
                                    />
                                </Card>
                            </Space>
                        ),
                    },
                    ...(page.administrator
                        ? [
                              {
                                  key: "clients",
                                  label: res.applications,
                                  children: (
                                      <Space orientation="vertical" size="large" style={{ width: "100%" }}>
                                          {oauthEndpoints}
                                          <Card
                                              title={res.applications}
                                              extra={
                                                  <Button
                                                      type="primary"
                                                      onClick={() => {
                                                          form.resetFields();
                                                          setOpen(true);
                                                      }}
                                                  >
                                                      {res.register}
                                                  </Button>
                                              }
                                          >
                                              <List
                                                  dataSource={page.clients}
                                                  renderItem={(client) => (
                                                      <List.Item
                                                          actions={[
                                                              <Popconfirm
                                                                  key="disable"
                                                                  title={res.confirmDisable}
                                                                  onConfirm={() =>
                                                                      mutate("disableClient", { id: client.clientId })
                                                                  }
                                                              >
                                                                  <Button loading={busy}>{res.disable}</Button>
                                                              </Popconfirm>,
                                                          ]}
                                                      >
                                                          <List.Item.Meta
                                                              title={client.name}
                                                              description={
                                                                  <Space
                                                                      orientation="vertical"
                                                                      style={{ maxWidth: "100%" }}
                                                                  >
                                                                      <Typography.Text
                                                                          copyable
                                                                          style={{ overflowWrap: "anywhere" }}
                                                                      >
                                                                          {client.clientId}
                                                                      </Typography.Text>
                                                                      {client.redirectUris.map((uri) => (
                                                                          <Typography.Text
                                                                              key={uri}
                                                                              type="secondary"
                                                                              style={{ overflowWrap: "anywhere" }}
                                                                          >
                                                                              {uri}
                                                                          </Typography.Text>
                                                                      ))}
                                                                  </Space>
                                                              }
                                                          />
                                                      </List.Item>
                                                  )}
                                              />
                                          </Card>
                                      </Space>
                                  ),
                              },
                          ]
                        : []),
                ].find((item) => item.key === activePage)?.children
            }
            <Drawer title={res.register} open={open} onClose={() => setOpen(false)} destroyOnHidden>
                <Form
                    form={form}
                    layout="vertical"
                    onFinish={async (values) => {
                        if (
                            await mutate("register", {
                                name: values.name,
                                redirectUris: values.redirectUris
                                    .split(/\r?\n/)
                                    .map((v: string) => v.trim())
                                    .filter(Boolean),
                            })
                        )
                            setOpen(false);
                    }}
                >
                    <Form.Item name="name" label={res.name} rules={[{ required: true, max: 128 }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item
                        name="redirectUris"
                        label={res.redirectUris}
                        rules={[{ required: true }]}
                        extra={res.redirectHelp}
                    >
                        <Input.TextArea autoSize={{ minRows: 3, maxRows: 8 }} />
                    </Form.Item>
                    <Alert type="info" title={res.registerHelp} style={{ marginBottom: 16 }} />
                    <Button type="primary" htmlType="submit" loading={busy}>
                        {res.register}
                    </Button>
                </Form>
            </Drawer>
        </Space>
    );
}

export function UserApplications({ offline, data, updateCache, activePage }: UserApplicationsProps) {
    if (offline) return <Alert type="info" title={getRes().oauth.offline} />;
    return <OAuthConnections data={data} updateCache={updateCache} activePage={activePage} />;
}
