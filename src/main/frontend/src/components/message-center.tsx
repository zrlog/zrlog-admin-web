import { Badge, Button, Drawer, Empty, Grid, Popover, Skeleton, Space, Tag, Typography } from "antd";
import { BellOutlined, CheckCircleOutlined, CloseCircleOutlined, LoadingOutlined } from "@ant-design/icons";
import { useEffect, useMemo, useState } from "react";
import { useTheme } from "antd-style";
import { useLocation, useNavigate } from "react-router-dom";
import TimeAgo from "@editor/dist/src/editor/TimeAgo";
import {
    BackgroundTaskStatus,
    clearFinishedBackgroundTasks,
    getBackgroundTasks,
    removeBackgroundTask,
    subscribeBackgroundTasks,
} from "../utils/background-task-store";
import { getRealRouteUrl, getRes } from "../utils/constants";
import { getAppState } from "../base/ConfigProviderApp";

const MessageCenter = ({ compact = false, loading = false }: { compact?: boolean; loading?: boolean }) => {
    const { useBreakpoint } = Grid;
    const screens = useBreakpoint();
    const theme = useTheme();
    const navigate = useNavigate();
    const location = useLocation();
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
    const activeCount = useMemo(
        () => tasks.filter((task) => task.status === "running" || task.status === "notice").length,
        [tasks]
    );
    const hasFinishedTask = useMemo(
        () => tasks.some((task) => task.status !== "running" && task.status !== "notice"),
        [tasks]
    );

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
        if (status === "notice") {
            return {
                color: "gold",
                label: getRes().backgroundTask.status.notice,
                icon: <BellOutlined />,
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

    const trigger = (
        <Button
            type="text"
            onClick={() => setOpen(true)}
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
                    dot={runningCount === 0 && activeCount > 0}
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
                <Button type="link" size="small" onClick={() => clearFinishedBackgroundTasks()}>
                    {getRes().backgroundTask.clearFinished}
                </Button>
            ) : null}
        </div>
    );

    const contentBody = (
        <Space direction="vertical" size={16} style={{ width: "100%" }}>
            <Typography.Text type="secondary">{getRes().backgroundTask.leaveHint}</Typography.Text>
            {loading ? (
                <Space direction="vertical" size={12} style={{ width: "100%" }}>
                    {[0, 1].map((key) => (
                        <div
                            key={key}
                            style={{
                                padding: 14,
                                borderRadius: theme.borderRadiusLG,
                                border: `1px solid ${theme.colorBorderSecondary}`,
                                background: theme.colorBgContainer,
                            }}
                        >
                            <Skeleton active title={{ width: "40%" }} paragraph={{ rows: 2 }} />
                        </div>
                    ))}
                </Space>
            ) : tasks.length === 0 ? (
                <div
                    style={{
                        borderRadius: theme.borderRadiusLG,
                        border: `1px dashed ${theme.colorBorderSecondary}`,
                        background: getAppState().dark
                            ? "linear-gradient(180deg, rgba(255,255,255,0.03), rgba(255,255,255,0.01))"
                            : "linear-gradient(180deg, rgba(24,144,255,0.04), rgba(24,144,255,0.01))",
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
                        maxHeight: mobileMode ? "calc(100vh - 220px)" : 420,
                        overflowY: "auto",
                        paddingRight: 4,
                    }}
                >
                    <Space direction="vertical" size={12} style={{ width: "100%" }}>
                        {tasks.map((task) => {
                            const statusMeta = getStatusMeta(task.status);
                            return (
                                <div
                                    key={task.id}
                                    style={{
                                        padding: 14,
                                        borderRadius: theme.borderRadiusLG,
                                        border: `1px solid ${theme.colorBorderSecondary}`,
                                        background: theme.colorBgContainer,
                                    }}
                                >
                                    <div
                                        style={{
                                            display: "flex",
                                            flexDirection: mobileMode ? "column" : "row",
                                            justifyContent: "space-between",
                                            gap: 12,
                                            alignItems: "flex-start",
                                        }}
                                    >
                                        <div style={{ minWidth: 0, flex: 1 }}>
                                            <Space size={8} wrap>
                                                <Typography.Text strong>{task.title}</Typography.Text>
                                                <Tag color={statusMeta.color} icon={statusMeta.icon}>
                                                    {statusMeta.label}
                                                </Tag>
                                            </Space>
                                            {task.description && (
                                                <Typography.Paragraph
                                                    style={{ marginTop: 8, marginBottom: 0 }}
                                                    type="secondary"
                                                >
                                                    {task.description}
                                                </Typography.Paragraph>
                                            )}
                                            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                                                {task.timeLabel || getRes().backgroundTask.updatedAt}:{" "}
                                                <TimeAgo timestamp={task.updatedAt} />
                                            </Typography.Text>
                                        </div>
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
                                                    style={{ paddingInline: mobileMode ? undefined : 0 }}
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
                                                    onClick={() => removeBackgroundTask(task.id)}
                                                >
                                                    {getRes().close}
                                                </Button>
                                            )}
                                        </Space>
                                    </div>
                                </div>
                            );
                        })}
                    </Space>
                </div>
            )}
        </Space>
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
            onOpenChange={setOpen}
            title={popoverTitle}
            content={popoverContent}
        >
            {trigger}
        </Popover>
    );
};

export default MessageCenter;
