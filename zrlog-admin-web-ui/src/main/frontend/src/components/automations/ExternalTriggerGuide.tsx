import { Collapse, Input, Space, Tabs, Typography } from "antd";
import { useTheme } from "antd-style";
import { getRes } from "../../utils/constants";
import { buildWorkerExample, buildWranglerExample } from "./external-trigger-examples";

function CodeExample({ label, value }: { label: string; value: string }) {
    const theme = useTheme();
    return (
        <Space
            role="region"
            aria-label={label}
            orientation="vertical"
            size="small"
            style={{ width: "100%", minWidth: 0 }}
        >
            <Typography.Text strong copyable={{ text: value }}>
                {label}
            </Typography.Text>
            <Input.TextArea
                aria-label={label}
                readOnly
                autoSize={{ minRows: 1, maxRows: 14 }}
                value={value}
                style={{ fontFamily: theme.fontFamilyCode }}
            />
        </Space>
    );
}

export default function ExternalTriggerGuide({ endpoint, curlExample }: { endpoint: string; curlExample: string }) {
    const res = getRes().websiteAssistant;
    return (
        <section aria-label={res.example} style={{ width: "100%", minWidth: 0 }}>
            <Typography.Title level={4}>{res.example}</Typography.Title>
            <Tabs
                items={[
                    {
                        key: "curl",
                        label: "cURL",
                        children: (
                            <Space orientation="vertical" style={{ width: "100%" }}>
                                <Typography.Paragraph type="secondary">{res.curlHelp}</Typography.Paragraph>
                                <CodeExample label={res.curlCommand} value={curlExample} />
                            </Space>
                        ),
                    },
                    {
                        key: "cloudflare",
                        label: "Cloudflare Worker",
                        children: (
                            <Space orientation="vertical" size="large" style={{ width: "100%", minWidth: 0 }}>
                                <Typography.Paragraph style={{ marginBottom: 0 }}>
                                    {res.workerHelp}
                                </Typography.Paragraph>
                                <section>
                                    <Typography.Title level={5}>{res.workerCreate}</Typography.Title>
                                    <Typography.Paragraph>{res.workerCreateHelp}</Typography.Paragraph>
                                    <CodeExample label="src/index.js" value={buildWorkerExample(res)} />
                                </section>
                                <section>
                                    <Typography.Title level={5}>{res.workerVariables}</Typography.Title>
                                    <Typography.Paragraph>{res.workerVariablesHelp}</Typography.Paragraph>
                                    <Typography.Paragraph type="secondary">{res.workerUrlHelp}</Typography.Paragraph>
                                    <CodeExample label="ZRLOG_ASSISTANT_TRIGGER_URL" value={endpoint} />
                                    <Typography.Paragraph style={{ overflowWrap: "anywhere" }}>
                                        {res.workerSecretHelp}
                                    </Typography.Paragraph>
                                </section>
                                <section>
                                    <Typography.Title level={5}>{res.workerSchedule}</Typography.Title>
                                    <Typography.Paragraph>{res.workerScheduleHelp}</Typography.Paragraph>
                                    <CodeExample label={res.workerCron} value="* * * * *" />
                                    <Typography.Paragraph type="secondary">{res.workerVerify}</Typography.Paragraph>
                                </section>
                                <Collapse
                                    style={{ width: "100%" }}
                                    items={[
                                        {
                                            key: "wrangler",
                                            label: res.workerWrangler,
                                            children: (
                                                <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
                                                    <Typography.Paragraph>
                                                        {res.workerWranglerHelp}
                                                    </Typography.Paragraph>
                                                    <CodeExample
                                                        label="wrangler.toml"
                                                        value={buildWranglerExample(endpoint)}
                                                    />
                                                    <CodeExample
                                                        label={res.workerDeployCommands}
                                                        value={
                                                            "npx wrangler secret put ZRLOG_ASSISTANT_TASK_TOKEN\nnpx wrangler deploy"
                                                        }
                                                    />
                                                </Space>
                                            ),
                                        },
                                    ]}
                                />
                            </Space>
                        ),
                    },
                ]}
            />
        </section>
    );
}
