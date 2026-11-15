import { createFencedCodeBlock } from "@zrlog/editor/dist/markdown/fenced-code";

const buildWranglerExample = (endpoint: string) => `name = "zrlog-assistant-trigger"
main = "src/index.js"
compatibility_date = "2026-10-30"

[triggers]
crons = ["* * * * *"]

[vars]
ZRLOG_ASSISTANT_TRIGGER_URL = ${JSON.stringify(endpoint)}`;

export function buildExternalTriggerMarkdown(template: string, endpoint: string, curlExample: string) {
    const values: Record<string, string> = {
        curlExample: createFencedCodeBlock(curlExample, "bash"),
        triggerEndpoint: createFencedCodeBlock(endpoint, "plaintext"),
        wranglerExample: createFencedCodeBlock(buildWranglerExample(endpoint), "toml"),
    };
    // Replace once and fence dynamic values so URLs and tokens remain literal code.
    return template.replace(/\{\{(curlExample|triggerEndpoint|wranglerExample)\}\}/g, (_, key: string) => values[key]);
}
