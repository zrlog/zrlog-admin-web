import { Button, Grid, Popover, Space, Tag, Typography } from "antd";
import {
    CheckCircleOutlined,
    CloseOutlined,
    ExclamationCircleOutlined,
    InfoCircleOutlined,
    LoadingOutlined,
    MessageOutlined,
} from "@ant-design/icons";
import { FunctionComponent } from "react";
import { getLabelValueSeparator, getRes } from "../../utils/constants";
import { ArticleEditState, PublishCheckTarget, PublishStatusPopoverState } from "./index.types";
import PublishCheckResult from "./article-ai-assistant/tool/content/publish-check-result";
import { useTheme } from "antd-style";
import TimeAgo from "@editor/dist/src/editor/TimeAgo";

type PublishStatusBarProps = {
    saving: ArticleEditState["saving"];
    publishStatus: PublishStatusPopoverState;
    onOpenChange: (open: boolean) => void;
    onClose: () => void;
    onOpenAssistant?: () => void;
    onLocatePublishCheckTarget?: (target: PublishCheckTarget) => void;
    getContainer?: () => HTMLElement;
};

const PublishStatusBar: FunctionComponent<PublishStatusBarProps> = ({
    saving,
    publishStatus,
    onOpenChange,
    onClose,
    onOpenAssistant,
    onLocatePublishCheckTarget,
    getContainer,
}) => {
    const screens = Grid.useBreakpoint();
    const theme = useTheme();
    const triggerColor = theme.colorTextSecondary;

    if (!publishStatus.visible) {
        return null;
    }

    const contentWidth = screens.xs && !screens.sm ? "calc(100vw - 32px)" : 320;

    const getStatusIcon = () => {
        if (publishStatus.publishError) {
            return <ExclamationCircleOutlined />;
        }
        if (publishStatus.checkStatus === "running") {
            return <LoadingOutlined />;
        }
        if (publishStatus.checkStatus === "error") {
            return <ExclamationCircleOutlined />;
        }
        if (publishStatus.checkStatus === "success") {
            return <CheckCircleOutlined />;
        }
        if (saving.releaseSaving) {
            return <LoadingOutlined />;
        }
        return <InfoCircleOutlined />;
    };

    const getTriggerText = () => {
        if (publishStatus.publishError) {
            return getRes().articleEdit.publishStatus.failed;
        }
        if (publishStatus.checkStatus === "running") {
            return getRes().articleEdit.publishStatus.checking;
        }
        if (publishStatus.checkStatus === "error") {
            return getRes().articleEdit.publishStatus.failed;
        }
        if (publishStatus.checkStatus === "success") {
            return getRes().articleEdit.publishStatus.completed;
        }
        if (saving.releaseSaving) {
            return getRes().articleEdit.publishStatus.publishing;
        }
        return getRes().articleEdit.publishStatus.title;
    };

    const buildStatusTag = (status: "running" | "success" | "error") => {
        if (status === "success") {
            return <Tag color="success">{getRes().backgroundTask.status.success}</Tag>;
        }
        if (status === "error") {
            return <Tag color="error">{getRes().backgroundTask.status.error}</Tag>;
        }
        return <Tag color="processing">{getRes().backgroundTask.status.running}</Tag>;
    };

    const content = (
        <Space direction="vertical" size={10} style={{ width: contentWidth }}>
            <Space style={{ width: "100%", justifyContent: "space-between", alignItems: "flex-start" }}>
                <Space direction="vertical" size={0}>
                    <Typography.Text strong>{getRes().articleEdit.publishStatus.title}</Typography.Text>
                    {publishStatus.updatedAt && (
                        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                            {getRes().backgroundTask.updatedAt}
                            {getLabelValueSeparator()}
                            <TimeAgo timestamp={publishStatus.updatedAt} />
                        </Typography.Text>
                    )}
                </Space>
                <Button size="small" type="text" icon={<CloseOutlined />} onClick={onClose} />
            </Space>
            <Space direction="vertical" size={6} style={{ width: "100%" }}>
                {publishStatus.publishText && (
                    <Space>
                        {buildStatusTag(
                            publishStatus.publishError ? "error" : saving.releaseSaving ? "running" : "success"
                        )}
                        <Typography.Text>{publishStatus.publishText}</Typography.Text>
                    </Space>
                )}
                {publishStatus.publishError && (
                    <Space>
                        {buildStatusTag("error")}
                        <Typography.Text>{publishStatus.publishError}</Typography.Text>
                    </Space>
                )}
                {publishStatus.staticText && (
                    <Space>
                        {buildStatusTag(saving.releaseSaving ? "running" : "success")}
                        <Typography.Text>{publishStatus.staticText}</Typography.Text>
                    </Space>
                )}
                {publishStatus.checkStatus !== "idle" && (
                    <Space>
                        {buildStatusTag(
                            publishStatus.checkStatus === "success"
                                ? "success"
                                : publishStatus.checkStatus === "error"
                                ? "error"
                                : "running"
                        )}
                        <Typography.Text>
                            {publishStatus.checkStatus === "success"
                                ? getRes().articleEdit.publishCheck.finished
                                : publishStatus.checkStatus === "error"
                                ? publishStatus.checkError || getRes().articleEdit.publishCheck.failed
                                : getRes().articleEdit.publishCheck.running}
                        </Typography.Text>
                    </Space>
                )}
            </Space>
            {publishStatus.checkPayload && (
                <PublishCheckResult
                    toolPayload={publishStatus.checkPayload}
                    style={{ maxHeight: 320, overflowY: "auto" }}
                    onLocateTarget={onLocatePublishCheckTarget}
                />
            )}
            {publishStatus.checkStatus === "success" && (
                <Space direction="vertical" size={4} style={{ width: "100%" }}>
                    <Typography.Text type="secondary">{getRes().articleEdit.publishCheck.reportHint}</Typography.Text>
                    {onOpenAssistant && (
                        <Button
                            size="small"
                            type="link"
                            icon={<MessageOutlined />}
                            style={{ paddingInline: 0 }}
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

    return (
        <Popover
            open={publishStatus.open}
            onOpenChange={onOpenChange}
            content={content}
            trigger="click"
            getPopupContainer={getContainer}
            placement="topRight"
            autoAdjustOverflow
        >
            <Space
                size={4}
                role="button"
                tabIndex={0}
                title={getRes().articleEdit.publishStatus.title}
                style={{ color: triggerColor, cursor: "pointer", paddingInline: 4 }}
                onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        onOpenChange(!publishStatus.open);
                    }
                }}
            >
                {getStatusIcon()}
                <Typography.Text style={{ color: triggerColor }}>{getTriggerText()}</Typography.Text>
            </Space>
        </Popover>
    );
};

export default PublishStatusBar;
