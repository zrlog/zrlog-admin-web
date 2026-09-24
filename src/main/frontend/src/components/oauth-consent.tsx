import { useState } from "react";
import { Alert, Button, Card, Checkbox, Radio, Space, Typography, message } from "antd";
import { useAxiosBaseInstance } from "../base/AppBase";
import { getRes } from "../utils/constants";
type Consent = {
    requestId: string;
    csrf: string;
    clientName: string;
    redirectUri: string;
    resource: string;
    scopes: string[];
    availableScopes: string[];
};
export default function OAuthConsent({ data }: { data: Consent }) {
    const [selected, setSelected] = useState<string[]>(data.availableScopes.filter((s) => s === "articles:read"));
    const [busy, setBusy] = useState(false);
    const [notice, contextHolder] = message.useMessage();
    const api = useAxiosBaseInstance();
    const res = getRes().oauth;
    const labels: Record<string, string> = res.scopeLabels;
    const decide = async (approve: boolean) => {
        setBusy(true);
        try {
            const response = await api.post("/api/admin/oauth/decide", {
                requestId: data.requestId,
                csrf: data.csrf,
                approve,
                scopes: selected,
            });
            if (response.data.error) notice.error(response.data.message);
            else window.location.assign(response.data.data.redirectUri);
        } finally {
            setBusy(false);
        }
    };
    return (
        <Card title={res.authorizeTitle} style={{ maxWidth: 760, margin: "0 auto" }}>
            {contextHolder}
            <Space orientation="vertical" size="large" style={{ width: "100%" }}>
                <Typography.Paragraph>{res.authorizeDescription}</Typography.Paragraph>
                <Typography.Title level={4}>{data.clientName}</Typography.Title>
                <div>
                    <Typography.Text type="secondary">{res.redirectUris}</Typography.Text>
                    <Typography.Paragraph style={{ overflowWrap: "anywhere" }}>{data.redirectUri}</Typography.Paragraph>
                </div>
                {data.availableScopes.length !== data.scopes.length && (
                    <Alert type="warning" title={res.grantUnavailable} showIcon />
                )}
                <div>
                    <Typography.Paragraph strong>{res.range}</Typography.Paragraph>
                    <Radio.Group
                        value={selected.includes("articles:all") ? "all" : "own"}
                        onChange={(e) =>
                            setSelected((previous) =>
                                e.target.value === "all"
                                    ? [...previous, "articles:all"]
                                    : previous.filter((s) => s !== "articles:all")
                            )
                        }
                    >
                        <Space orientation="vertical">
                            <Radio value="own">{res.own}</Radio>
                            <Radio value="all" disabled={!data.availableScopes.includes("articles:all")}>
                                {res.all}
                            </Radio>
                        </Space>
                    </Radio.Group>
                </div>
                <div>
                    <Typography.Paragraph strong>{res.permissions}</Typography.Paragraph>
                    <Checkbox.Group
                        value={selected.filter((s) => s !== "articles:all")}
                        onChange={(values) =>
                            setSelected([...values.map(String), ...selected.filter((s) => s === "articles:all")])
                        }
                    >
                        <Space orientation="vertical">
                            {data.scopes
                                .filter((scope) => scope !== "articles:all")
                                .map((scope) => (
                                    <Checkbox
                                        key={scope}
                                        value={scope}
                                        disabled={!data.availableScopes.includes(scope)}
                                    >
                                        {labels[scope] ?? scope}
                                    </Checkbox>
                                ))}
                        </Space>
                    </Checkbox.Group>
                </div>
                <Typography.Paragraph type="secondary">{res.scopeHint}</Typography.Paragraph>
                <Space wrap>
                    <Button
                        type="primary"
                        loading={busy}
                        disabled={!selected.some((s) => !["offline_access", "articles:all"].includes(s))}
                        onClick={() => decide(true)}
                    >
                        {res.allow}
                    </Button>
                    <Button disabled={busy} onClick={() => decide(false)}>
                        {res.deny}
                    </Button>
                </Space>
            </Space>
        </Card>
    );
}
