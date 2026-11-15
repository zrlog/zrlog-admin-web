import { useMemo } from "react";
import { markdownToHtml } from "@zrlog/editor/dist/markdown";
import HtmlPreviewPanel from "@zrlog/editor/core/editor/html-preview-panel";
import { getAppState } from "../../../base/ConfigProviderApp";
import { getRes } from "../../../utils/constants";
import zhCN from "../../../i18n/help/assistant-trigger/zh_CN.md";
import enUS from "../../../i18n/help/assistant-trigger/en_US.md";
import { buildExternalTriggerMarkdown } from "./external-trigger-examples";

export default function ExternalTriggerGuide({ endpoint, curlExample }: { endpoint: string; curlExample: string }) {
    const template = getAppState().lang === "en_US" ? enUS : zhCN;
    const html = useMemo(
        () => markdownToHtml(buildExternalTriggerMarkdown(template, endpoint, curlExample)),
        [template, endpoint, curlExample]
    );
    return (
        <section aria-label={getRes().websiteAssistant.example} style={{ width: "100%", minWidth: 0 }}>
            <HtmlPreviewPanel htmlContent={html} dark={getAppState().dark} style={{ width: "100%", minWidth: 0 }} />
        </section>
    );
}
