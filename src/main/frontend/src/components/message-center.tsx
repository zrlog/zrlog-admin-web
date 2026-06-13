import { Badge, Button, Card, Drawer, Empty, Grid, Popover, Skeleton, Space, Tag, Typography } from "antd";
import {
    BellOutlined,
    CheckCircleOutlined,
    ClockCircleOutlined,
    CloseCircleOutlined,
    ExclamationCircleOutlined,
    LoadingOutlined,
    StopOutlined,
} from "@ant-design/icons";
import { useEffect, useMemo, useState } from "react";
import { useTheme } from "antd-style";
import { useLocation, useNavigate } from "react-router-dom";
import TimeAgo from "@editor/dist/src/editor/TimeAgo";
import {
    BackgroundTaskStatus,
    clearFinishedBackgroundTasks,
    getBackgroundTasks,
    isBackgroundTaskActive,
    removeBackgroundTask,
    subscribeBackgroundTasks,
} from "../utils/background-task-store";
import { getLabelValueSeparator, getRealRouteUrl, getRes } from "../utils/constants";
import { getAppState } from "../base/ConfigProviderApp";
import { useAxiosBaseInstance } from "../base/AppBase";

const MessageCenter = ({
    compact = false,
    loading = false,
    hasUnread = false,
    onRefresh,
}: {
    compact?: boolean;
    loading?: boolean;
    hasUnread?: boolean;
    onRefresh?: (force?: boolean) => void;
}) => {
    const { useBreakpoint } = Grid;
    const screens = useBreakpoint();
    const theme = useTheme();
    const borderSecondary = `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`;
    const dashedBorderSecondary = `${theme.lineWidth}px dashed ${theme.colorBorderSecondary}`;
    const navigate = useNavigate();
    const location = useLocation();
    const axiosInstance = useAxiosBaseInstance();
    const [open, setOpen] = useState(false);
    const [tasks, setTasks] = useState(getBackgroundTasks());
    const mobileMode = screens.xs === true;

    useEffect(() => {
        setOpen(false);
    }, [location.pathname, location.search]);

    useEffect(() => {
        return subscribeBackgroundTasks(() => {
            setTasks([...getBackgroundTasks()]);
        });
    }, []);

    const runningCount = useMemo(() => tasks.filter((task) => task.status === "running").length, [tasks]);
    const activeCount = useMemo(() => tasks.filter((task) => isBackgroundTaskActive(task.status)).length, [tasks]);
    const hasFinishedTask = useMemo(() => tasks.some((task) => !isBackgroundTaskActive(task.status)), [tasks]);

    const getStatusMeta = (status: BackgroundTaskStatus) => {
        if (status === "success") {
            return {
                color: "success",
                label: getRes().backgroundTask.status.success,
                icon: <CheckCircleOutlined />,
            };
        }
        if (status === "error") {
            return {
                color: "error",
                label: getRes().backgroundTask.status.error,
                icon: <CloseCircleOutlined />,
            };
        }
        if (status === "warning") {
            return {
                color: "warning",
                label: getRes().backgroundTask.status.warning,
                icon: <ExclamationCircleOutlined />,
            };
        }
        if (status === "cancelled") {
            return {
                color: "default",
                label: getRes().backgroundTask.status.cancelled,
                icon: <StopOutlined />,
            };
        }
        if (status === "notice") {
            return {
                color: "gold",
                label: getRes().backgroundTask.status.notice,
                icon: <BellOutlined />,
            };
        }
        if (status === "pending") {
            return {
                color: "default",
                label: getRes().backgroundTask.status.pending,
                icon: <ClockCircleOutlined />,
            };
        }
        return {
            color: "processing",
            label: getRes().backgroundTask.status.running,
            icon: <LoadingOutlined />,
        };
    };

    const triggerIcon =
        runningCount > 0 ? <LoadingOutlined style={{ fontSize: 18 }} /> : <BellOutlined style={{ fontSize: 18 }} />;

    const handleRemoveTask = async (taskId: string, dismissPath?: string, dismissPayload?: Record<string, unknown>) => {
        if (dismissPath) {
            const { data } = await axiosInstance.post(dismissPath, dismissPayload || {});
            if (data?.error !== 0) {
                onRefresh?.(true);
                return;
            }
        }
        removeBackgroundTask(taskId);
        onRefresh?.(true);
    };

    const handleClearFinishedTasks = async () => {
        const finishedTasks = tasks.filter((task) => !isBackgroundTaskActive(task.status));
        for (const task of finishedTasks) {
            if (!task.dismissPath) {
                continue;
            }
            const { data } = await axiosInstance.post(task.dismissPath, task.dismissPayload || {});
            if (data?.error !== 0) {
                onRefresh?.(true);
                return;
            }
        }
        clearFinishedBackgroundTasks();
        onRefresh?.(true);
    };

    const openMessageCenter = () => {
        setOpen(true);
        onRefresh?.();
    };

    const handleOpenChange = (nextOpen: boolean) => {
        setOpen(nextOpen);
        if (nextOpen) {
            onRefresh?.();
        }
    };

    const trigger = (
        <Button
            type="text"
            onClick={openMessageCenter}
            style={{
                width: compact ? 32 : undefined,
                height: compact ? 32 : 44,
                minWidth: compact ? 32 : undefined,
                borderRadius: theme.borderRadiusLG,
                color: theme.colorText,
                paddingInline: compact ? 7 : 14,
            }}
        >
            <Space size={6}>
                <Badge
                    dot={runningCount === 0 && (activeCount > 0 || hasUnread)}
                    color={getAppState().colorPrimary}
                    offset={compact ? [-1, 3] : [-2, 4]}
                    styles={{
                        indicator: {
                            boxShadow: `0 0 0 2px ${theme.colorBgContainer}`,
                        },
                    }}
                >
                    {triggerIcon}
                </Badge>
                {!compact ? (
                    <span>
                        {getRes().backgroundTask.entry}
                        {activeCount > 0 ? ` ${activeCount}` : ""}
                    </span>
                ) : null}
            </Space>
        </Button>
    );

    const popoverTitle = (
        <div
            style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                gap: 12,
            }}
        >
            <Typography.Text strong>{getRes().backgroundTask.title}</Typography.Text>
            {hasFinishedTask ? (
                <Button type="link" size="small" onClick={() => void handleClearFinishedTasks()}>
                    {getRes().backgroundTask.clearFinished}
                </Button>
            ) : null}
        </div>
    );

    const contentBody = (
        <div
            style={{
                display: "flex",
                flexDirection: "column",
                gap: 16,
                width: "100%",
                height: mobileMode ? "100%" : undefined,
                minHeight: 0,
            }}
        >
            <Space direction="vertical" size={4}>
                <Typography.Text type="secondary">{getRes().backgroundTask.leaveHint}</Typography.Text>
                <Typography.Text type="secondary">{getRes().backgroundTask.retentionHint}</Typography.Text>
            </Space>
            {loading ? (
                <Space direction="vertical" size={12} style={{ width: "100%" }}>
                    {[0, 1].map((key) => (
                        <Card
                            key={key}
                            style={{
                                borderRadius: theme.borderRadiusLG,
                                border: borderSecondary,
                                background: theme.colorBgContainer,
                            }}
                            styles={{ body: { padding: 14 } }}
                        >
                            <Skeleton active title={{ width: "40%" }} paragraph={{ rows: 2 }} />
                        </Card>
                    ))}
                </Space>
            ) : tasks.length === 0 ? (
                <div
                    style={{
                        borderRadius: theme.borderRadiusLG,
                        border: dashedBorderSecondary,
                        background: theme.colorFillQuaternary,
                    }}
                >
                    <Empty
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description={
                            <Space direction="vertical" size={4}>
                                <Typography.Text strong>{getRes().backgroundTask.emptyTitle}</Typography.Text>
                                <Typography.Text type="secondary">
                                    {getRes().backgroundTask.emptyDetail}
                                </Typography.Text>
                            </Space>
                        }
                        styles={{
                            image: {
                                marginBottom: 12,
                            },
                        }}
                    />
                </div>
            ) : (
                <div
                    style={{
                        flex: mobileMode ? 1 : undefined,
                        minHeight: 0,
                        maxHeight: mobileMode ? undefined : 420,
                        overflowY: "auto",
                        paddingRight: 4,
                    }}
                >
                    <Space direction="vertical" size={12} style={{ width: "100%" }}>
                        {tasks.map((task) => {
                            const statusMeta = getStatusMeta(task.status);
                            const descriptionLines = task.description
                                ?.split("\n")
                                .map((line) => line.trim())
                                .filter(Boolean);
                            const primaryDescription = descriptionLines?.[0];
                            const detailDescriptionLines = descriptionLines?.slice(1) || [];
                            const hasTaskActions =
                                !!(task.actionPath && task.actionLabel) ||
                                (task.status !== "running" && task.closable !== false);
                            const taskTitle = (
                                <Space size={8} wrap style={{ maxWidth: "100%" }}>
                                    <Typography.Text
                                        strong
                                        style={{ wordBreak: "break-word", overflowWrap: "anywhere" }}
                                    >
                                        {task.title}
                                    </Typography.Text>
                                    <Tag color={statusMeta.color} icon={statusMeta.icon}>
                                        {statusMeta.label}
                                    </Tag>
                                </Space>
                            );
                            const taskActions = hasTaskActions ? (
                                <Space
                                    size={4}
                                    wrap
                                    style={{
                                        width: mobileMode ? "100%" : undefined,
                                        justifyContent: mobileMode ? "flex-start" : undefined,
                                    }}
                                >
                                    {task.actionPath && task.actionLabel && (
                                        <Button
                                            type="link"
                                            size="small"
                                            style={{
                                                paddingInline: mobileMode ? undefined : 0,
                                                whiteSpace: "normal",
                                                textAlign: "start",
                                            }}
                                            onClick={() => {
                                                setOpen(false);
                                                navigate(getRealRouteUrl(task.actionPath as string));
                                            }}
                                        >
                                            {task.actionLabel}
                                        </Button>
                                    )}
                                    {task.status !== "running" && task.closable !== false && (
                                        <Button
                                            type="text"
                                            size="small"
                                            onClick={() =>
                                                void handleRemoveTask(task.id, task.dismissPath, task.dismissPayload)
                                            }
                                        >
                                            {getRes().close}
                                        </Button>
                                    )}
                                </Space>
                            ) : null;
                            return (
                                <Card
                                    key={task.id}
                                    style={{
                                        borderRadius: theme.borderRadiusLG,
                                        border: borderSecondary,
                                        background: theme.colorBgContainer,
                                    }}
                                    styles={{ body: { padding: 14 } }}
                                >
                                    <div
                                        style={{
                                            display: "flex",
                                            flexDirection: mobileMode ? "column" : "row",
                                            justifyContent: "space-between",
                                            gap: mobileMode ? 8 : 12,
                                            alignItems: mobileMode ? "flex-start" : "center",
                                            marginBottom: descriptionLines?.length ? 8 : 6,
                                        }}
                                    >
                                        <div style={{ minWidth: 0, flex: 1 }}>{taskTitle}</div>
                                        {taskActions}
                                    </div>
                                    {primaryDescription ? (
                                        <Typography.Paragraph
                                            style={{
                                                marginBottom: detailDescriptionLines.length ? 4 : 0,
                                                wordBreak: "break-word",
                                                overflowWrap: "anywhere",
                                            }}
                                            type="secondary"
                                        >
                                            {primaryDescription}
                                        </Typography.Paragraph>
                                    ) : null}
                                    <Space direction="vertical" size={2} style={{ width: "100%" }}>
                                        {detailDescriptionLines.length
                                            ? detailDescriptionLines.map((line, index) => (
                                                  <Typography.Text
                                                      key={`${task.id}-description-${index}`}
                                                      type="secondary"
                                                      style={{
                                                          display: "block",
                                                          fontSize: theme.fontSizeSM,
                                                          wordBreak: "break-word",
                                                          overflowWrap: "anywhere",
                                                      }}
                                                  >
                                                      {line}
                                                  </Typography.Text>
                                              ))
                                            : null}
                                        <Typography.Text
                                            type="secondary"
                                            style={{ display: "block", fontSize: theme.fontSizeSM }}
                                        >
                                            {task.timeLabel || getRes().backgroundTask.updatedAt}
                                            {getLabelValueSeparator()}
                                            <TimeAgo timestamp={task.updatedAt} />
                                        </Typography.Text>
                                    </Space>
                                </Card>
                            );
                        })}
                    </Space>
                </div>
            )}
        </div>
    );

    const popoverContent = <div style={{ width: 360, maxWidth: "min(360px, calc(100vw - 32px))" }}>{contentBody}</div>;

    if (mobileMode) {
        return (
            <>
                {trigger}
                <Drawer
                    open={open}
                    onClose={() => setOpen(false)}
                    title={getRes().backgroundTask.title}
                    placement="bottom"
                    height="78vh"
                    styles={{
                        body: {
                            display: "flex",
                            flexDirection: "column",
                            overflow: "hidden",
                        },
                    }}
                    extra={
                        hasFinishedTask ? (
                            <Button type="link" size="small" onClick={() => clearFinishedBackgroundTasks()}>
                                {getRes().backgroundTask.clearFinished}
                            </Button>
                        ) : null
                    }
                >
                    {contentBody}
                </Drawer>
            </>
        );
    }

    return (
        <Popover
            trigger="click"
            placement="bottomRight"
            open={open}
            onOpenChange={handleOpenChange}
            title={popoverTitle}
            content={popoverContent}
        >
            {trigger}
        </Popover>
    );
};

export default MessageCenter;
