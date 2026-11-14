import AssistantChat from "./AssistantChat";
import { hasAction } from "../../utils/account-access";
import { isModuleEnabled } from "../../utils/module-capabilities";
import { Alert, Button, Popconfirm, Descriptions, Drawer, Pagination, Select, Space, Typography, Tooltip } from "antd";
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
import RefreshIcon from "@zrlog/ui/icons/refresh";
import { useAssistantStyles } from "./assistant-styles";
import SystemTasks from "./SystemTasks";
import RunChildren from "./RunChildren";
import RunHistory from "./RunHistory";
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
    const [editing, setEditing] = useState<Partial<Plan>>();
    const [tasksOpen, setTasksOpen] = useState(false);
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
    const filter = searchParams.get("taskId") || undefined;
    const runs = overview.runs ?? { items: [], total: 0, page: 1, pageSize: 20 };
    useEffect(() => {
        setDetail(selectedRun && data.selectedRun?.id === selectedRun ? data.selectedRun : undefined);
    }, [selectedRun, data.selectedRun]);
    const res = getRes().assistant;
    const { styles, cx } = useAssistantStyles();
    const disabled = offline || offlineData || busy;
    const request = useAssistantRequest();
    const reload = () => {
        if (!mounted.current) return;
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
            if (path === "run") setDetail(result);
            setEditing(undefined);
            if (!maintenanceOnly && path === "save" && result.id) {
                setTasksOpen(false);
                navigate(getRealRouteUrl(`/assistant?taskId=${encodeURIComponent(result.id)}`));
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
        setTasksOpen(false);
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
            onHistory={showHistory}
            onMutate={mutate}
            renderStatus={status}
        />
    );
    const history = (
        <section ref={historyPanel} tabIndex={-1} className={cx(styles.panel, styles.goals)} aria-label={res.history}>
            <div className={styles.toolbar}>
                <Select
                    allowClear
                    style={{ minWidth: 240, maxWidth: "100%" }}
                    placeholder={res.allTasks}
                    aria-label={res.allTasks}
                    value={filter}
                    onChange={(taskId) => updateHistory({ taskId, page: 1, runId: undefined })}
                    options={[...(overview.systemTasks ?? []), ...overview.tasks].map((task) => ({
                        value: task.id,
                        label: task.name,
                    }))}
                />
            </div>
            <RunHistory
                runs={runs.items}
                onDetail={(run) => {
                    historyTrigger.current =
                        document.activeElement instanceof HTMLElement ? document.activeElement : null;
                    updateHistory({ runId: run.id });
                }}
                renderStatus={status}
            />
            <div className={styles.footer}>
                <Typography.Text type="secondary">{res.retention}</Typography.Text>
                <Pagination
                    current={runs.page}
                    pageSize={runs.pageSize}
                    total={runs.total}
                    showSizeChanger={false}
                    onChange={(page) => updateHistory({ page, runId: undefined })}
                />
            </div>
        </section>
    );
    if (!maintenanceOnly && (activePage === "system" || searchParams.get("tab") === "system"))
        return <Navigate replace to={getRealRouteUrl("/website/assistant")} />;
    if (activePage === "history") {
        const params = new URLSearchParams(searchParams);
        params.set("history", "1");
        return <Navigate replace to={getRealRouteUrl(`/assistant?${params}`)} />;
    }
    const task = filter && overview.selectedTask?.id === filter ? overview.selectedTask : undefined;
    const taskHistory = task ? (
        <section aria-label={res.history}>
            <Space wrap style={{ marginBottom: 16 }}>
                <Typography.Text strong>{task.name}</Typography.Text>
                {status(task.lastStatus)}
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

            {runs.total > runs.pageSize && (
                <Pagination
                    size="small"
                    current={runs.page}
                    pageSize={runs.pageSize}
                    total={runs.total}
                    showSizeChanger={false}
                    onChange={(page) => updateHistory({ page, runId: undefined })}
                />
            )}
        </section>
    ) : undefined;
    return (
        <div className={cx(styles.page, !maintenanceOnly && styles.conversationPage)}>
            {!maintenanceOnly && (
                <div className={styles.header}>
                    {task && <Link to={getRealRouteUrl("/assistant")}>{res.generalConversation}</Link>}
                    <Button onClick={() => setTasksOpen(true)}>{res.tasks}</Button>
                    <Button
                        onClick={() =>
                            setSearchParams((params) => {
                                params.set("history", "1");
                                return params;
                            })
                        }
                    >
                        {res.history}
                    </Button>
                    <Tooltip title={getRes().common.refresh}>
                        <Button
                            type="text"
                            icon={<RefreshIcon />}
                            aria-label={getRes().common.refresh}
                            onClick={reload}
                            disabled={disabled}
                        />
                    </Tooltip>
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
                            key={task?.id || "general"}
                            taskId={filter}
                            offline={offline || offlineData}
                            leading={taskHistory}
                            activities={
                                task
                                    ? runs.items.map((run) => ({
                                          id: `execution-${run.id}`,
                                          createdAt: run.startedAt,
                                          content: (
                                              <RunHistory
                                                  conversation
                                                  runs={[run]}
                                                  onDetail={(item) => updateHistory({ runId: item.id })}
                                                  renderStatus={status}
                                              />
                                          ),
                                      }))
                                    : []
                            }
                        />
                    ) : task ? (
                        <>
                            {taskHistory}
                            <RunHistory
                                conversation
                                runs={runs.items}
                                onDetail={(run) => updateHistory({ runId: run.id })}
                                renderStatus={status}
                            />
                        </>
                    ) : (
                        taskList
                    )}
                </div>
            )}
            <Drawer
                title={res.tasks}
                open={tasksOpen}
                onClose={() => setTasksOpen(false)}
                closable={{ placement: "end" }}
                size={736}
            >
                {taskList}
            </Drawer>
            <Drawer
                title={res.history}
                open={searchParams.get("history") === "1"}
                onClose={() =>
                    setSearchParams((params) => {
                        params.delete("history");
                        return params;
                    })
                }
                closable={{ placement: "end" }}
                size={960}
            >
                {history}
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
                                    const lastPage = Math.max(1, Math.ceil((runs.total - 1) / runs.pageSize));
                                    if (searchParams.get("history") === "1" || task) {
                                        updateHistory({ runId: undefined, page: Math.min(runs.page, lastPage) }, true);
                                    } else reload();
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
