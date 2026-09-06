import {
    CheckCircleOutlined,
    CloseCircleOutlined,
    DesktopOutlined,
    EditOutlined,
    ExclamationCircleOutlined,
    MobileOutlined,
} from "@ant-design/icons";
import { Alert, Button, Grid, Modal, Segmented, Space, Tooltip, Typography } from "antd";
import { useTheme } from "antd-style";
import { FunctionComponent, useEffect, useMemo, useRef, useState } from "react";
import { markdownToHtmlSyncWithCallback } from "@editor/dist/editor/utils/marked-utils";
import { getAppState } from "../../base/ConfigProviderApp";
import { getRes } from "../../utils/constants";
import ArticlePreviewSnapshot from "../article/article-preview-snapshot";
import { ArticleEntry } from "./index.types";
import {
    ArticlePublishReviewCheck,
    ArticlePublishReviewField,
    ArticlePublishReviewTarget,
    ArticlePublishTypeOption,
    buildArticlePublishReview,
} from "./article-publish-review";

type ArticlePublishReviewModalProps = {
    open: boolean;
    previewOnly?: boolean;
    article: ArticleEntry;
    typeOptions: ArticlePublishTypeOption[];
    offline: boolean;
    draftAiPending: boolean;
    saving: boolean;
    contentConflict: boolean;
    getContainer?: () => HTMLElement;
    onOpenChange: (open: boolean) => void;
    onLocate: (target: ArticlePublishReviewTarget) => void;
    onConfirm: () => Promise<boolean>;
};

type PreviewMode = "desktop" | "mobile";

const ArticlePublishReviewModal: FunctionComponent<ArticlePublishReviewModalProps> = ({
    open,
    previewOnly = false,
    article,
    typeOptions,
    offline,
    draftAiPending,
    saving,
    contentConflict,
    getContainer,
    onOpenChange,
    onLocate,
    onConfirm,
}) => {
    const theme = useTheme();
    const screens = Grid.useBreakpoint();
    const res = getRes().articleEdit.publishReview;
    const [previewMode, setPreviewMode] = useState<PreviewMode>("desktop");
    const [previewHtml, setPreviewHtml] = useState(article.content || "");
    const [confirming, setConfirming] = useState(false);
    const confirmingRef = useRef(false);
    const review = useMemo(() => buildArticlePublishReview(article, typeOptions), [article, typeOptions]);
    const borderSecondary = `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`;

    useEffect(() => {
        if (!open) {
            return;
        }
        setPreviewMode("desktop");
        const nextHtml = markdownToHtmlSyncWithCallback(
            article.markdown || "",
            (renderedHtml) => setPreviewHtml(renderedHtml || article.content || ""),
            { linkPreview: false }
        );
        setPreviewHtml(nextHtml || article.content || "");
    }, [article.content, article.markdown, open]);

    const fieldLabel = (field: ArticlePublishReviewField) => {
        switch (field) {
            case "title":
                return res.fields.title;
            case "category":
                return res.fields.category;
            case "markdown":
                return res.fields.markdown;
            case "alias":
                return res.fields.alias;
            case "digest":
                return res.fields.digest;
            case "tags":
                return res.fields.tags;
            case "cover":
                return res.fields.cover;
        }
    };

    const checkDetail = (check: ArticlePublishReviewCheck) => {
        if (check.status === "ready") {
            if (check.field === "title") {
                return article.title.trim();
            }
            if (check.field === "category") {
                return review.categoryLabel || res.ready;
            }
            return res.ready;
        }
        switch (check.field) {
            case "title":
                return res.details.titleMissing;
            case "category":
                return res.details.categoryMissing;
            case "markdown":
                return res.details.markdownMissing;
            case "alias":
                return res.details.aliasMissing;
            case "digest":
                return res.details.digestMissing;
            case "tags":
                return res.details.tagsMissing;
            case "cover":
                return res.details.coverMissing;
        }
    };

    const statusIcon = (check: ArticlePublishReviewCheck) => {
        if (check.status === "blocker") {
            return <CloseCircleOutlined style={{ color: theme.colorError }} />;
        }
        if (check.status === "warning") {
            return <ExclamationCircleOutlined style={{ color: theme.colorWarning }} />;
        }
        return <CheckCircleOutlined style={{ color: theme.colorSuccess }} />;
    };

    const handleCancel = () => {
        if (!confirmingRef.current) {
            onOpenChange(false);
        }
    };

    const handleConfirm = async () => {
        if (
            confirmingRef.current ||
            saving ||
            offline ||
            draftAiPending ||
            contentConflict ||
            review.blockers.length > 0
        ) {
            return;
        }
        confirmingRef.current = true;
        setConfirming(true);
        try {
            if (await onConfirm()) {
                onOpenChange(false);
            }
        } finally {
            confirmingRef.current = false;
            setConfirming(false);
        }
    };

    const handleLocate = (target: ArticlePublishReviewTarget) => {
        onOpenChange(false);
        onLocate(target);
    };

    const confirmDisabled =
        !previewOnly && (offline || draftAiPending || contentConflict || saving || review.blockers.length > 0);
    const blockingText = previewOnly
        ? undefined
        : offline
        ? res.offline
        : draftAiPending
        ? res.aiPending
        : contentConflict
        ? res.contentConflict
        : review.blockers.length > 0
        ? res.blocked
        : undefined;
    const previewFrameWidth = previewMode === "mobile" ? 390 : "100%";

    return (
        <Modal
            title={previewOnly ? res.contentPreview : res.title}
            open={open}
            onCancel={handleCancel}
            onOk={() => void handleConfirm()}
            okText={res.confirm}
            cancelText={getRes().cancel}
            footer={
                previewOnly ? (
                    <Button type="primary" onClick={handleCancel}>
                        {getRes().close}
                    </Button>
                ) : undefined
            }
            okButtonProps={{ disabled: confirmDisabled }}
            cancelButtonProps={{ disabled: confirming }}
            confirmLoading={confirming}
            closable={!confirming}
            keyboard={!confirming}
            maskClosable={!confirming}
            width={screens.lg ? 1080 : "calc(100vw - 24px)"}
            style={screens.lg ? undefined : { top: 8, paddingBottom: 8 }}
            getContainer={getContainer}
            destroyOnHidden
            styles={{
                body: {
                    maxHeight: screens.lg ? "72vh" : "calc(100dvh - 156px)",
                    overflowY: "auto",
                },
            }}
        >
            <Space direction="vertical" size={theme.margin} style={{ display: "flex" }}>
                {blockingText && <Alert showIcon type="error" message={blockingText} />}
                <div
                    style={{
                        display: "grid",
                        gridTemplateColumns: screens.lg ? "minmax(260px, 320px) minmax(0, 1fr)" : "minmax(0, 1fr)",
                        gap: theme.marginLG,
                        alignItems: "start",
                    }}
                >
                    <section aria-labelledby="article-publish-review-metadata-title">
                        <Typography.Title id="article-publish-review-metadata-title" level={5} style={{ marginTop: 0 }}>
                            {res.metadata}
                        </Typography.Title>
                        <div role="list" style={{ borderTop: borderSecondary }}>
                            {review.checks.map((check) => (
                                <div
                                    role="listitem"
                                    key={check.field}
                                    data-review-status={check.status}
                                    style={{
                                        display: "grid",
                                        gridTemplateColumns: "auto minmax(0, 1fr) auto",
                                        gap: theme.marginSM,
                                        alignItems: "center",
                                        minHeight: 52,
                                        paddingBlock: theme.paddingXS,
                                        borderBottom: borderSecondary,
                                    }}
                                >
                                    {statusIcon(check)}
                                    <div style={{ minWidth: 0 }}>
                                        <Typography.Text strong>{fieldLabel(check.field)}</Typography.Text>
                                        <Typography.Text
                                            type="secondary"
                                            style={{ display: "block", overflowWrap: "anywhere" }}
                                        >
                                            {checkDetail(check)}
                                        </Typography.Text>
                                    </div>
                                    {check.status !== "ready" && (
                                        <Tooltip title={getRes().edit}>
                                            <Button
                                                type="text"
                                                size="small"
                                                icon={<EditOutlined />}
                                                aria-label={`${getRes().edit} ${fieldLabel(check.field)}`}
                                                onClick={() => handleLocate(check.target)}
                                            />
                                        </Tooltip>
                                    )}
                                </div>
                            ))}
                        </div>
                    </section>

                    <section aria-labelledby="article-publish-review-preview-title" style={{ minWidth: 0 }}>
                        <div
                            style={{
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "space-between",
                                gap: theme.marginSM,
                                marginBottom: theme.marginSM,
                                flexWrap: "wrap",
                            }}
                        >
                            <Typography.Title id="article-publish-review-preview-title" level={5} style={{ margin: 0 }}>
                                {res.contentPreview}
                            </Typography.Title>
                            <Segmented<PreviewMode>
                                value={previewMode}
                                onChange={setPreviewMode}
                                options={[
                                    { value: "desktop", label: res.desktop, icon: <DesktopOutlined /> },
                                    { value: "mobile", label: res.mobile, icon: <MobileOutlined /> },
                                ]}
                            />
                        </div>
                        <div
                            data-testid="article-publish-preview-frame"
                            data-preview-mode={previewMode}
                            style={{
                                display: "flex",
                                flexDirection: "column",
                                width: "100%",
                                maxWidth: previewFrameWidth,
                                height: screens.md ? 520 : 440,
                                marginInline: "auto",
                                overflow: "hidden",
                                border: borderSecondary,
                                borderRadius: theme.borderRadiusLG,
                                background: theme.colorBgContainer,
                            }}
                        >
                            <div
                                style={{
                                    flex: "none",
                                    padding: theme.padding,
                                    borderBottom: borderSecondary,
                                }}
                            >
                                <Typography.Title level={3} style={{ margin: 0, overflowWrap: "anywhere" }}>
                                    {article.title.trim() || res.untitled}
                                </Typography.Title>
                            </div>
                            <div style={{ flex: 1, minHeight: 0, paddingInline: theme.padding }}>
                                <ArticlePreviewSnapshot
                                    htmlContent={previewHtml}
                                    dark={getAppState().dark}
                                    tagText={review.categoryLabel}
                                    digest={article.digest}
                                    digestLabel={getRes().article.previewSnapshot.digest}
                                    keywords={article.keywords}
                                    keywordsLabel={getRes().article.previewSnapshot.keywords}
                                    emptyDescription={getRes().article.previewSnapshot.empty}
                                />
                            </div>
                        </div>
                    </section>
                </div>
            </Space>
        </Modal>
    );
};

export default ArticlePublishReviewModal;
