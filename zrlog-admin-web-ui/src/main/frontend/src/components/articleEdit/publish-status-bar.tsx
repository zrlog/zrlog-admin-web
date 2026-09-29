import { Button, Drawer, Popover, Space, Tag, Typography } from "antd";
import {
    CheckCircleOutlined,
    CloseOutlined,
    ExclamationCircleOutlined,
    ExportOutlined,
    InfoCircleOutlined,
    LoadingOutlined,
    MessageOutlined,
} from "@ant-design/icons";
import { CSSProperties, FunctionComponent } from "react";
import { getLabelValueSeparator, getRes } from "../../utils/constants";
import { ArticleEditState, PublishCheckTarget, PublishStatusPopoverState } from "./index.types";
import PublishCheckResult from "./article-ai-assistant/tool/content/publish-check-result";
import { useTheme } from "antd-style";
import TimeAgo from "@zrlog/editor/dist/editor/TimeAgo";
import useArticleEditorScreens from "./use-article-editor-screens";

type PublishStatusBarProps = {
    saving: ArticleEditState["saving"];
    publishStatus: PublishStatusPopoverState;
    onOpenChange: (open: boolean) => void;
    onClose: () => void;
    onOpenAssistant?: () => void;
    onLocatePublishCheckTarget?: (target: PublishCheckTarget) => void;
    getContainer?: () => HTMLElement;
};

type DisplayStatus = "running" | "success" | "error" | "not-required";

const PublishStatusBar: FunctionComponent<PublishStatusBarProps> = ({
    saving,
    publishStatus,
    onOpenChange,
    onClose,
    onOpenAssistant,
    onLocatePublishCheckTarget,
    getContainer,
}) => {
    const screens = useArticleEditorScreens();
    const theme = useTheme();
    const isMobile = screens.xs === true;
    const triggerColor = theme.colorTextSecondary;

    if (!publishStatus.visible) {
        return null;
    }

    const popoverWidth = (() => {
        if (screens.xxl) {
            return 520;
        }
        if (screens.xl) {
            return 480;
        }
        if (screens.md) {
            return 420;
        }
        if (screens.sm) {
            return 360;
        }
        return "calc(100vw - 24px)";
    })();
    const statusTextStyle: CSSProperties = {
        flex: 1,
        minWidth: 0,
        overflowWrap: "anywhere",
    };

    const renderStatusRow = (status: DisplayStatus, text: string) => (
        <div style={{ display: "flex", alignItems: "flex-start", gap: theme.marginXS, width: "100%" }}>
            {buildStatusTag(status)}
            <Typography.Text style={statusTextStyle}>{text}</Typography.Text>
        </div>
    );

    const getStatusIcon = () => {
        if (
            publishStatus.publishState === "failed" ||
            publishStatus.staticStatus === "failed" ||
            publishStatus.checkStatus === "error"
        ) {
            return <ExclamationCircleOutlined />;
        }
        if (
            publishStatus.publishState === "running" ||
            publishStatus.staticStatus === "running" ||
            publishStatus.checkStatus === "running" ||
            saving.releaseSaving
        ) {
            return <LoadingOutlined />;
        }
        if (publishStatus.publishState === "success" || publishStatus.checkStatus === "success") {
            return <CheckCircleOutlined />;
        }
        return <InfoCircleOutlined />;
    };

    const getTriggerText = () => {
        if (publishStatus.publishState === "failed") {
            return getRes().articleEdit.publishStatus.failed;
        }
        if (publishStatus.staticStatus === "failed") {
            return getRes().articleEdit.publishStatus.staticFailed;
        }
        if (publishStatus.checkStatus === "error") {
            return getRes().articleEdit.publishStatus.checkFailed;
        }
        if (publishStatus.publishState === "running" || saving.releaseSaving) {
            return getRes().articleEdit.publishStatus.publishing;
        }
        if (publishStatus.staticStatus === "running") {
            return getRes().articleEdit.publishStatus.syncing;
        }
        if (publishStatus.checkStatus === "running") {
            return getRes().articleEdit.publishStatus.checking;
        }
        if (publishStatus.publishState === "success" || publishStatus.checkStatus === "success") {
            return getRes().articleEdit.publishStatus.completed;
        }
        return getRes().articleEdit.publishStatus.title;
    };

    const buildStatusTag = (status: DisplayStatus) => {
        if (status === "success") {
            return <Tag color="success">{getRes().backgroundTask.status.success}</Tag>;
        }
        if (status === "error") {
            return <Tag color="error">{getRes().backgroundTask.status.error}</Tag>;
        }
        if (status === "not-required") {
            return <Tag>{getRes().articleEdit.publishStatus.notRequired}</Tag>;
        }
        return <Tag color="processing">{getRes().backgroundTask.status.running}</Tag>;
    };

    const renderStaticStatus = () => {
        if (publishStatus.staticStatus === "running") {
            return renderStatusRow("running", publishStatus.staticText || getRes().staticSite.syncing);
        }
        if (publishStatus.staticStatus === "success") {
            return renderStatusRow("success", getRes().staticSite.syncComplete);
        }
        if (publishStatus.staticStatus === "failed") {
            return renderStatusRow("error", publishStatus.staticError || getRes().staticSite.syncFailed);
        }
        if (publishStatus.staticStatus === "not-required") {
            return renderStatusRow("not-required", getRes().staticSite.syncNotRequired);
        }
        return null;
    };

    const title = (
        <Space direction="vertical" size={0} style={{ minWidth: 0 }}>
            <Typography.Text strong>{getRes().articleEdit.publishStatus.title}</Typography.Text>
            {publishStatus.updatedAt && (
                <Typography.Text type="secondary" style={{ fontSize: theme.fontSizeSM }}>
                    {getRes().backgroundTask.updatedAt}
                    {getLabelValueSeparator()}
                    <TimeAgo timestamp={publishStatus.updatedAt} />
                </Typography.Text>
            )}
        </Space>
    );

    const content = (
        <Space direction="vertical" size={10} style={{ width: "100%", minWidth: 0, overflowWrap: "anywhere" }}>
            {!isMobile && (
                <div
                    style={{
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "flex-start",
                        gap: theme.marginXS,
                    }}
                >
                    {title}
                    <Button size="small" type="text" icon={<CloseOutlined />} onClick={onClose} />
                </div>
            )}
            <Space direction="vertical" size={6} style={{ width: "100%" }}>
                {publishStatus.publishState !== "idle" &&
                    renderStatusRow(
                        publishStatus.publishState === "failed"
                            ? "error"
                            : publishStatus.publishState === "running"
                            ? "running"
                            : "success",
                        publishStatus.publishState === "failed"
                            ? publishStatus.publishError || getRes().articleEdit.saveFailed
                            : publishStatus.publishText ||
                                  (publishStatus.publishState === "running"
                                      ? getRes().staticSite.publishStart
                                      : getRes().staticSite.publishComplete)
                    )}
                {renderStaticStatus()}
                {publishStatus.checkStatus !== "idle" &&
                    renderStatusRow(
                        publishStatus.checkStatus === "success"
                            ? "success"
                            : publishStatus.checkStatus === "error"
                            ? "error"
                            : "running",
                        publishStatus.checkStatus === "success"
                            ? getRes().articleEdit.publishCheck.finished
                            : publishStatus.checkStatus === "error"
                            ? publishStatus.checkError || getRes().articleEdit.publishCheck.failed
                            : getRes().articleEdit.publishCheck.running
                    )}
            </Space>
            {publishStatus.publicUrl && (
                <Space direction="vertical" size={2} style={{ width: "100%", minWidth: 0 }}>
                    <Typography.Text type="secondary">{getRes().articleEdit.publishStatus.publicUrl}</Typography.Text>
                    <Typography.Link
                        href={publishStatus.publicUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        style={{ overflowWrap: "anywhere" }}
                    >
                        <ExportOutlined style={{ marginInlineEnd: theme.marginXXS }} />
                        {publishStatus.publicUrl}
                    </Typography.Link>
                </Space>
            )}
            {publishStatus.checkPayload && (
                <PublishCheckResult
                    toolPayload={publishStatus.checkPayload}
                    style={isMobile ? undefined : { maxHeight: 320, overflowY: "auto" }}
                    onLocateTarget={onLocatePublishCheckTarget}
                />
            )}
            {publishStatus.checkStatus === "success" && (
                <Space direction="vertical" size={4} style={{ width: "100%", minWidth: 0 }}>
                    <Typography.Text type="secondary">{getRes().articleEdit.publishCheck.reportHint}</Typography.Text>
                    {onOpenAssistant && (
                        <Button
                            size="small"
                            type="link"
                            icon={<MessageOutlined />}
                            style={{ paddingInline: 0, whiteSpace: "normal", height: "auto", textAlign: "start" }}
                            onClick={() => {
                                onOpenAssistant();
                                onOpenChange(false);
                            }}
                        >
                            {getRes().articleEdit.publishCheck.openAssistantHistory}
                        </Button>
                    )}
                    <Typography.Text type="secondary">{getRes().articleEdit.publishCheck.notBlocking}</Typography.Text>
                </Space>
            )}
        </Space>
    );

    const trigger = (
        <Button
            size="small"
            type="text"
            icon={getStatusIcon()}
            aria-label={`${getRes().articleEdit.publishStatus.title}${getLabelValueSeparator()}${getTriggerText()}`}
            aria-expanded={publishStatus.open}
            title={getTriggerText()}
            style={{ color: triggerColor, flexShrink: 0, paddingInline: theme.paddingXXS }}
            onClick={isMobile ? () => onOpenChange(!publishStatus.open) : undefined}
        >
            {isMobile ? null : getTriggerText()}
        </Button>
    );

    if (isMobile) {
        return (
            <>
                {trigger}
                <Drawer
                    title={title}
                    placement="bottom"
                    size="min(80vh, 640px)"
                    open={publishStatus.open}
                    onClose={onClose}
                    getContainer={getContainer}
                    styles={{
                        wrapper: { maxHeight: "80dvh" },
                        section: {
                            borderStartStartRadius: theme.borderRadiusLG,
                            borderStartEndRadius: theme.borderRadiusLG,
                        },
                        body: {
                            padding: theme.padding,
                            paddingBottom: `max(${theme.padding}px, env(safe-area-inset-bottom))`,
                        },
                    }}
                >
                    {content}
                </Drawer>
            </>
        );
    }

    return (
        <Popover
            open={publishStatus.open}
            onOpenChange={onOpenChange}
            content={content}
            trigger="click"
            getPopupContainer={getContainer}
            placement="topRight"
            autoAdjustOverflow
            styles={{
                container: {
                    boxSizing: "border-box",
                    width: popoverWidth,
                    maxWidth: "calc(100vw - 24px)",
                    maxHeight: "calc(100dvh - 80px)",
                    overflowY: "auto",
                },
            }}
        >
            {trigger}
        </Popover>
    );
};

export default PublishStatusBar;
