import { Collapse, Space, Tabs, Typography } from "antd";
import { useMemo } from "react";
import { markdownToHtml } from "@zrlog/editor/dist/markdown";
import { createFencedCodeBlock } from "@zrlog/editor/dist/markdown/fenced-code";
import HtmlPreviewPanel from "@zrlog/editor/core/editor/html-preview-panel";
import { getAppState } from "../../../base/ConfigProviderApp";
import { getRes } from "../../../utils/constants";
import { buildWorkerExample, buildWranglerExample } from "./external-trigger-examples";

function CodeExample({ label, value, language = "plaintext" }: { label: string; value: string; language?: string }) {
    const html = useMemo(() => markdownToHtml(createFencedCodeBlock(value, language)), [value, language]);
    return (
        <section aria-label={label} style={{ width: "100%", minWidth: 0 }}>
            <Typography.Text strong>{label}</Typography.Text>
            <HtmlPreviewPanel htmlContent={html} dark={getAppState().dark} style={{ width: "100%", minWidth: 0 }} />
        </section>
    );
}

export default function ExternalTriggerGuide({ endpoint, curlExample }: { endpoint: string; curlExample: string }) {
    const res = getRes().websiteAssistant;
    return (
        <section aria-label={res.example} style={{ width: "100%", minWidth: 0 }}>
            <Tabs
                items={[
                    {
                        key: "curl",
                        label: "cURL",
                        children: (
                            <Space orientation="vertical" style={{ width: "100%" }}>
                                <Typography.Paragraph type="secondary">{res.curlHelp}</Typography.Paragraph>
                                <CodeExample label={res.curlCommand} value={curlExample} language="bash" />
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
                                    <CodeExample
                                        label="src/index.js"
                                        value={buildWorkerExample(res)}
                                        language="javascript"
                                    />
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
                                                        language="toml"
                                                        value={buildWranglerExample(endpoint)}
                                                    />
                                                    <CodeExample
                                                        label={res.workerDeployCommands}
                                                        language="bash"
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
