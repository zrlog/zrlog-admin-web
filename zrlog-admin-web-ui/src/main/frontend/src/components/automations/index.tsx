import AssistantChat from "./AssistantChat";
import ExecutionRecordsDrawer from "./ExecutionRecordsDrawer";
import { hasAction } from "../../utils/account-access";
import { isModuleEnabled } from "../../utils/module-capabilities";
import { Alert, Button, Popconfirm, Descriptions, Drawer, Space, Typography, Tooltip } from "antd";
import Tag from "@zrlog/ui/antd/Tag";
import { useEffect, useRef, useState } from "react";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { cacheIgnoreReloadTime, getRealRouteUrl, getRes } from "../../utils/constants";
import { AssistantPage } from "../../utils/assistant-page-routes";
import { formatDateTime } from "../../utils/date-time";
import type { AdminCommonProps } from "../../type";
import TaskForm from "./TaskForm";
import PersonalTasks from "./PersonalTasks";
import SettingsIcon from "@zrlog/ui/icons/settings";
import { useAssistantStyles } from "./assistant-styles";
import SystemTasks from "./SystemTasks";
import RunChildren from "./RunChildren";
import { useAssistantRequest } from "./use-assistant-request";
import { canAccessPage } from "../../utils/account-access";
import { removeBackgroundTaskByKey } from "../../utils/background-task-store";
import { ASSISTANT_NOTICE_PREFIX } from "../../utils/message-center-notice-sync";
import ProposalForm from "./ProposalForm";
import type { Overview, Plan, Run, Task } from "./types";

export default function Automations({
    data,
    offline = false,
    offlineData = false,
    activePage = "tasks",
    maintenanceOnly = false,
}: Pick<AdminCommonProps<Overview>, "data"> & {
    offline?: boolean;
    offlineData?: boolean;
    activePage?: AssistantPage;
    maintenanceOnly?: boolean;
}) {
    const overview = data;
    const navigate = useNavigate();
    const mounted = useRef(true);
    useEffect(() => {
        mounted.current = true;
        return () => {
            mounted.current = false;
        };
    }, []);
    const [recordsOpen, setRecordsOpen] = useState(false);
    const [editing, setEditing] = useState<Partial<Plan>>();
    const [proposing, setProposing] = useState(false);
    const [busy, setBusy] = useState(false);
    const [detail, setDetail] = useState<Run | undefined>(data.selectedRun);
    const historyTrigger = useRef<HTMLElement | null>(null);
    const historyPanel = useRef<HTMLElement | null>(null);
    useEffect(() => {
        if (detail || !historyTrigger.current) return;
        const trigger = historyTrigger.current;
        historyTrigger.current = null;
        // Also restore focus when Escape interrupts the opening animation.
        const frame = requestAnimationFrame(() => {
            if (trigger.isConnected) trigger.focus({ preventScroll: true });
        });
        return () => cancelAnimationFrame(frame);
    }, [detail]);
    const [searchParams, setSearchParams] = useSearchParams();
    const selectedRun = searchParams.get("runId");
    const taskPage = Math.floor(Math.max(0, Number(searchParams.get("offset")) || 0) / 100) + 1;
    const filter = searchParams.get("taskId") || data.selectedTask?.id;
    const [feedbackRevision, setFeedbackRevision] = useState(0);
    useEffect(() => {
        setDetail(selectedRun && data.selectedRun?.id === selectedRun ? data.selectedRun : undefined);
    }, [selectedRun, data.selectedRun]);
    const res = getRes().assistant;
    const { styles, cx } = useAssistantStyles();
    const disabled = offline || offlineData || busy;
    const request = useAssistantRequest();
    const reload = () => {
        if (!mounted.current) return;
        setFeedbackRevision((value) => value + 1);
        setSearchParams(
            (params) => {
                params.set(cacheIgnoreReloadTime, String(Date.now()));
                return params;
            },
            { replace: true }
        );
    };
    const updateHistory = (changes: { taskId?: string; page?: number; runId?: string }, replace = false) => {
        if (!mounted.current) return;
        const params = new URLSearchParams(searchParams);
        if (replace) params.set(cacheIgnoreReloadTime, String(Date.now()));
        for (const [key, value] of Object.entries(changes)) {
            if (value === undefined || value === "" || (key === "page" && value === 1)) params.delete(key);
            else params.set(key, String(value));
        }
        setSearchParams(params, { replace });
    };
    const mutate = async (path: string, body: unknown) => {
        if (disabled) return;
        setBusy(true);
        try {
            const result = await request<Run>(path, body);
            if (path === "run") setFeedbackRevision((value) => value + 1);
            setEditing(undefined);
            if (!maintenanceOnly && path === "save" && result.id) {
                navigate(getRealRouteUrl(`/assistant?taskId=${encodeURIComponent(result.id)}`));
            } else if (!maintenanceOnly && path === "delete") {
                navigate(getRealRouteUrl("/assistant?tasks=1"));
            } else if (!maintenanceOnly && path === "run" && result.taskId) {
                navigate(getRealRouteUrl(`/assistant?taskId=${encodeURIComponent(result.taskId)}`));
            } else reload();
        } catch {
            /* The request boundary displays the error. */
        } finally {
            setBusy(false);
        }
    };
    const status = (value?: string) => {
        const labels: Record<string, string> = res.statuses;
        return (
            <Tag
                color={
                    value === "success"
                        ? "success"
                        : value === "failed"
                        ? "error"
                        : ["needs_attention", "uncertain"].includes(value ?? "")
                        ? "warning"
                        : undefined
                }
            >
                {labels[value ?? ""] ?? res.never}
            </Tag>
        );
    };
    const date = (value?: number) => (value ? formatDateTime(value) : res.none);
    const showHistory = (task: Task) => {
        navigate(getRealRouteUrl(`/assistant?taskId=${encodeURIComponent(task.id)}`));
    };
    const taskList = (
        <PersonalTasks
            overview={overview}
            disabled={disabled}
            page={taskPage}
            onPage={(page) =>
                setSearchParams((params) => {
                    if (page === 1) params.delete("offset");
                    else params.set("offset", String((page - 1) * 100));
                    return params;
                })
            }
            onEdit={setEditing}
            onPropose={() => setProposing(true)}
            onOpen={showHistory}
            onMutate={mutate}
            renderStatus={status}
        />
    );
    if (!maintenanceOnly && (activePage === "system" || searchParams.get("tab") === "system"))
        return <Navigate replace to={getRealRouteUrl("/website/assistant")} />;
    if (activePage === "history") {
        const params = new URLSearchParams(searchParams);
        params.delete("history");
        const taskId = filter || data.selectedRun?.taskId;
        if (taskId) {
            params.set("taskId", taskId);
            params.delete("tasks");
        } else params.set("tasks", "1");
        return <Navigate replace to={getRealRouteUrl(`/assistant?${params}`)} />;
    }
    const task = filter && overview.selectedTask?.id === filter ? overview.selectedTask : undefined;
    const taskHistory = task ? (
        <section ref={historyPanel} tabIndex={-1} aria-label={res.conversation}>
            <Space wrap style={{ marginBottom: 16 }}>
                <Typography.Text strong>{task.name}</Typography.Text>
                {task.archived && <Tag>{res.archived}</Tag>}
                <Button type="text" onClick={() => setRecordsOpen(true)} disabled={disabled}>
                    {res.executionRecords}
                </Button>
                {task.systemTask
                    ? canAccessPage("/website/assistant") && (
                          <Link to={getRealRouteUrl("/website/assistant")}>{getRes().websiteAssistant.title}</Link>
                      )
                    : !task.archived && (
                          <Button onClick={() => setEditing(task)} disabled={disabled}>
                              {res.edit}
                          </Button>
                      )}
            </Space>
        </section>
    ) : undefined;
    return (
        <div className={cx(styles.page, maintenanceOnly ? styles.maintenancePage : styles.conversationPage)}>
            {!maintenanceOnly && (
                <div className={styles.header}>
                    <Button
                        onClick={() =>
                            setSearchParams((params) => {
                                params.set("tasks", "1");
                                return params;
                            })
                        }
                    >
                        {res.tasks}
                    </Button>
                    {canAccessPage("/website/assistant") && (
                        <Tooltip title={getRes().websiteAssistant.title} trigger={["hover", "focus"]}>
                            <Link
                                className={styles.settingsLink}
                                to={getRealRouteUrl("/website/assistant")}
                                aria-label={getRes().websiteAssistant.title}
                            >
                                <SettingsIcon aria-hidden="true" />
                            </Link>
                        </Tooltip>
                    )}
                </div>
            )}
            {offline && <Alert type="info" title={res.offline} />}
            {maintenanceOnly ? (
                <SystemTasks
                    tasks={overview.systemTasks}
                    definitions={overview.definitions}
                    disabled={disabled}
                    onConfigure={setEditing}
                    onHistory={showHistory}
                    onEnabled={(task, enabled) =>
                        void mutate("enabled", { id: task.id, revision: task.revision, enabled })
                    }
                />
            ) : (
                <div className={cx(styles.panel, styles.conversationPanel)}>
                    {isModuleEnabled("ai") && hasAction("article.assist") ? (
                        <AssistantChat
                            key={filter || "general"}
                            taskId={filter}
                            offline={offline}
                            leading={taskHistory}
                            revision={feedbackRevision}
                        />
                    ) : task ? (
                        <>
                            {taskHistory}
                            <Alert type="info" title={res.conversationUnavailable} />
                        </>
                    ) : (
                        taskList
                    )}
                </div>
            )}
            {recordsOpen && filter && (
                <ExecutionRecordsDrawer
                    key={filter}
                    taskId={filter}
                    revision={feedbackRevision}
                    onClose={() => setRecordsOpen(false)}
                    onDetail={(run) => updateHistory({ runId: run.id })}
                    renderStatus={status}
                />
            )}
            <Drawer
                title={res.tasks}
                open={searchParams.get("tasks") === "1" || searchParams.get("history") === "1"}
                onClose={() => {
                    setSearchParams(
                        (params) => {
                            params.delete("tasks");
                            params.delete("history");
                            return params;
                        },
                        { replace: true }
                    );
                }}
                closable={{ placement: "end" }}
                size={736}
            >
                {taskList}
            </Drawer>
            {editing && (
                <TaskForm
                    plan={editing}
                    definitions={overview.definitions}
                    busy={disabled}
                    onClose={() => setEditing(undefined)}
                    onSave={(plan) => mutate("save", plan)}
                />
            )}
            {proposing && (
                <ProposalForm
                    request={request}
                    definitions={overview.definitions.filter((definition) => !definition.systemTask)}
                    onClose={() => setProposing(false)}
                    onChoose={(plan) => {
                        setProposing(false);
                        setEditing(plan);
                    }}
                />
            )}
            <Drawer
                title={res.detail}
                extra={
                    detail &&
                    detail.finishedAt &&
                    detail.status !== "running" &&
                    (detail.ownerId !== 0 || overview.canManageScheduler) ? (
                        <Popconfirm
                            title={res.deleteRun}
                            description={res.deleteRunHelp}
                            okText={res.deleteRun}
                            cancelText={getRes().common.cancel}
                            okButtonProps={{ danger: true, loading: busy }}
                            onConfirm={async () => {
                                if (disabled) return;
                                setBusy(true);
                                try {
                                    await request("deleteRun", { id: detail.id, version: detail.version });
                                    removeBackgroundTaskByKey(ASSISTANT_NOTICE_PREFIX + detail.id);
                                    historyTrigger.current = historyPanel.current;
                                    setDetail(undefined);
                                    updateHistory({ runId: undefined, page: undefined }, true);
                                    setFeedbackRevision((value) => value + 1);
                                } catch {
                                    /* The request boundary displays the error. */
                                } finally {
                                    setBusy(false);
                                }
                            }}
                        >
                            <Button danger disabled={disabled}>
                                {res.deleteRun}
                            </Button>
                        </Popconfirm>
                    ) : undefined
                }
                open={!!detail}
                focusable={{ focusTriggerAfterClose: !historyTrigger.current }}
                onClose={() => {
                    setDetail(undefined);
                    if (selectedRun) updateHistory({ runId: undefined }, true);
                }}
                closable={{ placement: "end" }}
                size={560}
                destroyOnHidden
            >
                {detail && (
                    <Space orientation="vertical" size="large" style={{ width: "100%" }}>
                        <Typography.Title level={4}>{detail.taskName}</Typography.Title>
                        <Link to={getRealRouteUrl(`/assistant?taskId=${encodeURIComponent(detail.taskId)}`)}>
                            {res.conversation}
                        </Link>
                        {status(detail.status)}
                        <Descriptions
                            column={1}
                            items={[
                                { key: "started", label: res.started, children: date(detail.startedAt) },
                                { key: "finished", label: res.finished, children: date(detail.finishedAt) },
                                {
                                    key: "trigger",
                                    label: res.trigger,
                                    children:
                                        (res.triggers as Record<string, string>)[detail.trigger] ?? detail.trigger,
                                },
                            ]}
                        />
                        <Typography.Paragraph>{detail.summary}</Typography.Paragraph>
                        <Typography.Text type="secondary">{res.retention}</Typography.Text>
                        {detail.result?.text && (
                            <Typography.Paragraph style={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>
                                {detail.result.text}
                            </Typography.Paragraph>
                        )}
                        {detail.result?.actionPath?.startsWith("/") && !detail.result.actionPath.startsWith("//") && (
                            <Link to={getRealRouteUrl(detail.result.actionPath)}>
                                {detail.result?.actionLabel || res.detail}
                            </Link>
                        )}
                        <RunChildren children={detail.result?.children} />
                        <Typography.Title level={5}>{res.planSnapshot}</Typography.Title>
                        <Descriptions
                            column={1}
                            items={[
                                { key: "name", label: res.name, children: detail.planSnapshot.name },
                                {
                                    key: "schedule",
                                    label: res.kind,
                                    children:
                                        detail.planSnapshot.schedule.kind === "once"
                                            ? detail.planSnapshot.schedule.atLocal?.replace("T", " ") || res.none
                                            : detail.planSnapshot.schedule.kind === "interval"
                                            ? `${res.minutes}: ${detail.planSnapshot.schedule.intervalMinutes}`
                                            : `${
                                                  detail.planSnapshot.schedule.kind === "daily" ? res.daily : res.weekly
                                              } ${detail.planSnapshot.schedule.time}`,
                                },
                                ...(
                                    overview.definitions.find((item) => item.type === detail.taskType)?.fields ?? []
                                ).map((field) => ({
                                    key: field.name,
                                    label: field.label,
                                    children: (
                                        <span style={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>
                                            {field.type === "boolean"
                                                ? detail.planSnapshot.parameters[field.name]
                                                    ? getRes().yes
                                                    : getRes().no
                                                : String(
                                                      field.type === "article"
                                                          ? detail.planSnapshot.parameters.title
                                                          : detail.planSnapshot.parameters[field.name] ?? ""
                                                  )}
                                        </span>
                                    ),
                                })),
                            ]}
                        />
                    </Space>
                )}
            </Drawer>
        </div>
    );
}
