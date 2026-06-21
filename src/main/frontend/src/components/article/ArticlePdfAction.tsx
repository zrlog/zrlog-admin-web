import { FilePdfOutlined } from "@ant-design/icons";
import { markdownToHtmlSyncWithCallback } from "@editor/dist/editor/utils/marked-utils";
import { App, Button, Tooltip } from "antd";
import type { ButtonProps } from "antd";
import { getAppState } from "../../base/ConfigProviderApp";
import { getBackendServerUrl, getRes, tryAppendBackendServerUrl } from "../../utils/constants";
import type { ArticlePrintableEntry } from "./ArticlePreviewAction";

const escapeHtml = (value?: string) =>
    (value || "")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#39;");

const escapeAttribute = (value?: string) => escapeHtml(value).replace(/`/g, "&#96;");

const isAbsoluteOrSpecialUrl = (value: string) => {
    return /^(?:[a-z][a-z\d+.-]*:|#|\/\/)/i.test(value);
};

const normalizePrintableResourceUrl = (value: string) => {
    const trimmedValue = value.trim();
    if (!trimmedValue || isAbsoluteOrSpecialUrl(trimmedValue)) {
        return value;
    }
    if (trimmedValue.startsWith("/")) {
        return tryAppendBackendServerUrl(trimmedValue);
    }
    return value;
};

const getPrintBaseHref = () => {
    return new URL(getBackendServerUrl(), window.location.origin).toString();
};

const sanitizeArticleHtml = (html: string) => {
    const doc = new DOMParser().parseFromString(`<div>${html}</div>`, "text/html");
    doc.querySelectorAll("script").forEach((node) => node.remove());
    doc.body.querySelectorAll("*").forEach((element) => {
        Array.from(element.attributes).forEach((attribute) => {
            const name = attribute.name.toLowerCase();
            const normalizedValue = attribute.value.replace(/\s+/g, "").toLowerCase();
            if (name.startsWith("on")) {
                element.removeAttribute(attribute.name);
            }
            if ((name === "href" || name === "src") && normalizedValue.startsWith("javascript:")) {
                element.removeAttribute(attribute.name);
                return;
            }
            if (name === "href" || name === "src") {
                element.setAttribute(attribute.name, normalizePrintableResourceUrl(attribute.value));
            }
        });
    });
    return doc.body.innerHTML;
};

const buildKeywordHtml = (keywords?: string) => {
    if (!keywords) {
        return "";
    }
    const items = keywords
        .split(/[,，]/)
        .map((keyword) => keyword.trim())
        .filter((keyword) => keyword.length > 0);
    if (items.length === 0) {
        return "";
    }
    return `<div class="article-print-keywords">${items
        .map((keyword) => `<span>${escapeHtml(keyword)}</span>`)
        .join("")}</div>`;
};

const buildPrintDocument = (article: ArticlePrintableEntry, bodyHtml: string) => {
    const res = getRes();
    const title = article.title || res.article.label;
    const digest = article.digest?.trim();
    const generatedAt = new Date().toLocaleString();
    const safeBodyHtml = sanitizeArticleHtml(bodyHtml).trim();
    const keywordHtml = buildKeywordHtml(article.keywords);
    const baseHref = getPrintBaseHref();

    return `<!doctype html>
<html>
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width,initial-scale=1" />
<base href="${escapeAttribute(baseHref)}" />
<title>${escapeHtml(title)}</title>
<style>
@page {
    margin: 16mm 15mm;
}
* {
    box-sizing: border-box;
}
html,
body {
    margin: 0;
    padding: 0;
    background: #fff;
    color: #1f2328;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
    font-size: 14px;
    line-height: 1.72;
}
.article-print {
    max-width: 860px;
    margin: 0 auto;
    padding: 28px 0;
}
.article-print-site {
    color: #6b7280;
    font-size: 12px;
    margin-bottom: 10px;
}
.article-print h1 {
    margin: 0 0 12px;
    color: #111827;
    font-size: 30px;
    line-height: 1.25;
    font-weight: 700;
}
.article-print-meta {
    margin-bottom: 24px;
    color: #6b7280;
    font-size: 12px;
}
.article-print-digest {
    margin: 18px 0;
    padding: 12px 14px;
    border-left: 3px solid #d0d7de;
    background: #f6f8fa;
    color: #4b5563;
}
.article-print-keywords {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin: 0 0 24px;
}
.article-print-keywords span {
    border: 1px solid #d0d7de;
    border-radius: 4px;
    padding: 1px 7px;
    color: #4b5563;
    font-size: 12px;
}
.markdown-body {
    overflow-wrap: break-word;
    word-break: break-word;
}
.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4,
.markdown-body h5,
.markdown-body h6 {
    margin: 1.35em 0 0.65em;
    color: #111827;
    line-height: 1.35;
}
.markdown-body h1 {
    font-size: 26px;
}
.markdown-body h2 {
    padding-bottom: 0.25em;
    border-bottom: 1px solid #e5e7eb;
    font-size: 22px;
}
.markdown-body h3 {
    font-size: 18px;
}
.markdown-body p,
.markdown-body ul,
.markdown-body ol,
.markdown-body blockquote,
.markdown-body pre,
.markdown-body table {
    margin: 0 0 14px;
}
.markdown-body a {
    color: #1677ff;
    text-decoration: none;
}
.markdown-body img {
    max-width: 100%;
    height: auto;
    break-inside: avoid;
}
.markdown-body blockquote {
    padding: 0 14px;
    border-left: 4px solid #d0d7de;
    color: #57606a;
}
.markdown-body code {
    padding: 0.15em 0.35em;
    border-radius: 4px;
    background: #f6f8fa;
    font-family: ui-monospace, SFMono-Regular, "SFMono-Regular", Consolas, "Liberation Mono", monospace;
    font-size: 0.92em;
}
.markdown-body pre {
    overflow: visible;
    white-space: pre-wrap;
    padding: 14px;
    border-radius: 6px;
    background: #f6f8fa;
    break-inside: avoid;
}
.markdown-body pre code {
    padding: 0;
    background: transparent;
}
.markdown-body table {
    width: 100%;
    border-collapse: collapse;
}
.markdown-body th,
.markdown-body td {
    padding: 7px 9px;
    border: 1px solid #d0d7de;
    vertical-align: top;
}
.markdown-body tr,
.markdown-body blockquote,
.markdown-body pre {
    break-inside: avoid;
}
@media print {
    .article-print {
        max-width: none;
        padding: 0;
    }
    .markdown-body a {
        color: inherit;
    }
}
</style>
</head>
<body>
<article class="article-print">
    <div class="article-print-site">${escapeHtml(res.websiteTitle || "")}</div>
    <h1>${escapeHtml(title)}</h1>
    <div class="article-print-meta">${escapeHtml(res.article.exportPdfGeneratedAt)}${escapeHtml(generatedAt)}</div>
    ${digest ? `<section class="article-print-digest">${escapeHtml(digest)}</section>` : ""}
    ${keywordHtml}
    <section class="markdown-body">${
        safeBodyHtml || `<p>${escapeHtml(res.article.previewSnapshot.empty)}</p>`
    }</section>
</article>
</body>
</html>`;
};

const buildLoadingDocument = () => `<!doctype html>
<html>
<head>
<meta charset="utf-8" />
<title>${escapeHtml(getRes().article.exportPdf)}</title>
<style>
body {
    margin: 0;
    min-height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #6b7280;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
    font-size: 14px;
}
</style>
</head>
<body>${escapeHtml(getRes().article.exportPdf)}...</body>
</html>`;

const openPrintWindow = () => {
    const printWindow = window.open("", "_blank", "width=960,height=720");
    if (!printWindow) {
        return null;
    }
    printWindow.opener = null;
    printWindow.document.open();
    printWindow.document.write(buildLoadingDocument());
    printWindow.document.close();
    return printWindow;
};

const writePrintDocument = (printWindow: Window, article: ArticlePrintableEntry, bodyHtml: string) => {
    if (printWindow.closed) {
        return;
    }
    printWindow.document.open();
    printWindow.document.write(buildPrintDocument(article, bodyHtml));
    printWindow.document.close();

    let printed = false;
    const print = () => {
        if (printed || printWindow.closed) {
            return;
        }
        printed = true;
        printWindow.focus();
        printWindow.print();
    };
    printWindow.addEventListener("load", () => window.setTimeout(print, 250));
    window.setTimeout(print, 1200);
};

export const exportArticlePdf = (article: ArticlePrintableEntry, onPopupBlocked?: () => void) => {
    const printWindow = openPrintWindow();
    if (!printWindow) {
        onPopupBlocked?.();
        return;
    }
    let rendered = false;
    const render = (html: string) => {
        if (rendered) {
            return;
        }
        rendered = true;
        writePrintDocument(printWindow, article, html);
    };
    const fallbackHtml = markdownToHtmlSyncWithCallback(article.markdown || "", render);
    window.setTimeout(() => render(fallbackHtml), 3000);
};

type ArticlePdfActionProps = {
    article: ArticlePrintableEntry;
    buttonClassName?: string;
    buttonSize?: ButtonProps["size"];
    buttonType?: ButtonProps["type"];
    disabled?: boolean;
    showText?: boolean;
};

export const ArticlePdfAction = ({
    article,
    buttonClassName,
    buttonSize = "small",
    buttonType = "text",
    disabled,
    showText,
}: ArticlePdfActionProps) => {
    const { message } = App.useApp();

    const handleExportPdf = () => {
        exportArticlePdf(article, () => message.warning(getRes().article.exportPdfPopupBlocked));
    };

    return (
        <Tooltip title={getRes().article.exportPdf}>
            <Button
                className={buttonClassName}
                type={buttonType}
                size={buttonSize}
                title={getRes().article.exportPdf}
                disabled={disabled}
                icon={<FilePdfOutlined style={{ color: getAppState().colorPrimary }} />}
                onClick={handleExportPdf}
            >
                {showText && <span>{getRes().article.exportPdf}</span>}
            </Button>
        </Tooltip>
    );
};
