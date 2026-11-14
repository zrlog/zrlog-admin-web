import { newAssistantSessionId } from "../../utils/assistant-session";
import ConversationNavigation, { conversationKey } from "./ConversationNavigation";
import { getSsDate } from "../../base/SsData";
import AssistantChat from "./AssistantChat";
import ExecutionRecordsDrawer from "./tasks/ExecutionRecordsDrawer";
import { hasAction } from "../../utils/account-access";
import { isModuleEnabled } from "../../utils/module-capabilities";
import { Alert, Button, Popconfirm, Descriptions, Drawer, Space, Typography } from "antd";
import Tag from "@zrlog/ui/antd/Tag";
import { useEffect, useRef, useState } from "react";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { cacheIgnoreReloadTime, getRealRouteUrl, getRes } from "../../utils/constants";
import { AssistantPage } from "../../utils/assistant-page-routes";
import { formatDateTime } from "../../utils/date-time";
import type { AdminCommonProps } from "../../type";
import TaskForm from "./tasks/TaskForm";
import PersonalTasks from "./tasks/PersonalTasks";
import { useAssistantStyles } from "./assistant-styles";
import SystemTasks from "./tasks/SystemTasks";
import RunChildren from "./tasks/RunChildren";
import { useAssistantRequest } from "./use-assistant-request";
import { removeBackgroundTaskByKey } from "../../utils/background-task-store";
import { ASSISTANT_NOTICE_PREFIX } from "../../utils/message-center-notice-sync";
import ProposalForm from "./tasks/ProposalForm";
import type { Overview, Plan, Run, Task } from "./tasks/types";

export default function AssistantWorkspace({
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
    const filter = searchParams.has("sessionId") ? undefined : searchParams.get("taskId") || data.selectedTask?.id;
    const [feedbackRevision, setFeedbackRevision] = useState(0);
    const [conversationBusy, setConversationBusy] = useState(false);
    const [sessionRevision, setSessionRevision] = useState(0);
    const sessionId = searchParams.get("sessionId") || undefined;
    const selection = { taskId: filter, sessionId };
    const newConversation = () => {
        const id = newAssistantSessionId();
        window.localStorage.setItem(`${getSsDate().key}/assistant/general`, id);
        navigate(getRealRouteUrl(`/assistant?sessionId=${id}`));
    };
    useEffect(() => {
        if (!maintenanceOnly && !filter && !sessionId && !searchParams.has("tasks")) {
            const previous = window.localStorage.getItem(`${getSsDate().key}/assistant/general`);
            if (previous && /^[a-f0-9-]{36}$/.test(previous)) {
                setSearchParams(
                    (params) => {
                        params.set("sessionId", previous);
                        return params;
                    },
                    { replace: true }
                );
            }
        }
    }, [maintenanceOnly, filter, sessionId, setSearchParams, searchParams]);
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
        navigate(
            getRealRouteUrl(
                `/assistant?taskId=${encodeURIComponent(task.id)}${searchParams.get("tasks") === "1" ? "&tasks=1" : ""}`
            )
        );
    };
    const taskList = (compact = false, onSelected?: () => void) => (
        <PersonalTasks
            overview={overview}
            compact={compact}
            selectedId={filter}
            disabled={disabled || (compact && conversationBusy)}
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
            onOpen={(task) => {
                showHistory(task);
                onSelected?.();
            }}
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
                {!task.systemTask && !task.archived && (
                    <Button onClick={() => setEditing(task)} disabled={disabled}>
                        {res.edit}
                    </Button>
                )}
            </Space>
        </section>
    ) : undefined;
    return (
        <div className={cx(styles.page, maintenanceOnly ? styles.maintenancePage : styles.conversationPage)}>
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
                <div className={cx(styles.panel, styles.conversationPanel)} role="region" aria-label={res.title}>
                    {isModuleEnabled("ai") && hasAction("article.assist") ? (
                        <ConversationNavigation
                            selection={selection}
                            tasks={{
                                expanded: searchParams.get("tasks") === "1" || searchParams.get("history") === "1",
                                onExpandedChange: (expanded) =>
                                    setSearchParams(
                                        (params) => {
                                            params.delete("history");
                                            if (expanded) params.set("tasks", "1");
                                            else {
                                                params.delete("tasks");
                                                params.delete("offset");
                                            }
                                            return params;
                                        },
                                        { replace: true }
                                    ),
                                content: (onSelected) => taskList(true, onSelected),
                            }}
                            disabled={disabled || conversationBusy}
                            revision={sessionRevision}
                            onNew={newConversation}
                            onDeleted={(item) => {
                                if (conversationKey(item) === conversationKey(selection)) newConversation();
                            }}
                            onSelect={(item) => {
                                if (item.articleId !== undefined)
                                    navigate(
                                        getRealRouteUrl(
                                            `/article-edit${
                                                item.articleId ? `?id=${item.articleId}&` : "?"
                                            }assistantSession=${item.sessionId || "default"}`
                                        )
                                    );
                                else if (item.taskId)
                                    navigate(getRealRouteUrl(`/assistant?taskId=${encodeURIComponent(item.taskId)}`));
                                else {
                                    window.localStorage.setItem(
                                        `${getSsDate().key}/assistant/general`,
                                        item.sessionId || "default"
                                    );
                                    navigate(
                                        getRealRouteUrl(
                                            `/assistant${item.sessionId ? `?sessionId=${item.sessionId}` : ""}`
                                        )
                                    );
                                }
                            }}
                        >
                            {(toolbar) => (
                                <AssistantChat
                                    key={sessionId || filter || "general"}
                                    taskId={filter}
                                    sessionId={sessionId}
                                    toolbar={toolbar}
                                    onBusyChange={setConversationBusy}
                                    onSaved={() => setSessionRevision((value) => value + 1)}
                                    offline={offline}
                                    leading={taskHistory}
                                    revision={feedbackRevision}
                                />
                            )}
                        </ConversationNavigation>
                    ) : task ? (
                        <>
                            {taskHistory}
                            <Alert type="info" title={res.conversationUnavailable} />
                        </>
                    ) : (
                        taskList()
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
