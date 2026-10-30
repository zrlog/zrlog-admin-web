import { Alert, Button, Descriptions, Drawer, Pagination, Select, Space, Tabs, Typography, Tooltip } from "antd";
import Tag from "@zrlog/ui/antd/Tag";
import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { getRealRouteUrl, getRes } from "../../utils/constants";
import { formatDateTime } from "../../utils/date-time";
import type { AdminCommonProps } from "../../type";
import TaskForm from "./TaskForm";
import PersonalTasks from "./PersonalTasks";
import AddIcon from "@zrlog/ui/icons/add";
import SettingsIcon from "@zrlog/ui/icons/settings";
import RefreshIcon from "@zrlog/ui/icons/refresh";
import { useAssistantStyles } from "./assistant-styles";
import SystemTasks from "./SystemTasks";
import RunChildren from "./RunChildren";
import RunHistory from "./RunHistory";
import { useAssistantRequest } from "./use-assistant-request";
import { canAccessPage } from "../../utils/account-access";
import ProposalForm from "./ProposalForm";
import type { Overview, Plan, Run, Runs, Task } from "./types";

export default function Automations({
    data,
    offline = false,
    offlineData = false,
}: Pick<AdminCommonProps<Overview>, "data"> & { offline?: boolean; offlineData?: boolean }) {
    const [overview, setOverview] = useState(data);
    const [editing, setEditing] = useState<Partial<Plan>>();
    const [proposing, setProposing] = useState(false);
    const [busy, setBusy] = useState(false);
    const [tab, setTab] = useState("tasks");
    const [taskPage, setTaskPage] = useState(1);
    const [runs, setRuns] = useState<Runs>({ items: [], total: 0, page: 1, pageSize: 20 });
    const [filter, setFilter] = useState<string>();
    const [detail, setDetail] = useState<Run>();
    const historyTrigger = useRef<HTMLElement | null>(null);
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
    useEffect(() => {
        if (searchParams.get("tab") === "system") setTab("system");
    }, [searchParams]);
    const res = getRes().automations;
    const { styles, cx } = useAssistantStyles();
    const disabled = offline || offlineData || busy;
    useEffect(() => setOverview(data), [data]);
    const request = useAssistantRequest();
    const reload = useCallback(
        async () => setOverview(await request<Overview>(`?offset=${(taskPage - 1) * 100}`)),
        [request, taskPage]
    );
    const loadRuns = useCallback(
        async (page = 1) =>
            setRuns(await request<Runs>(`runs?page=${page}&taskId=${encodeURIComponent(filter ?? "")}`)),
        [request, filter]
    );
    useEffect(() => {
        if (tab === "history") void loadRuns().catch(() => undefined);
    }, [tab, loadRuns]);
    useEffect(() => {
        void reload().catch(() => undefined);
    }, [reload]);
    useEffect(() => {
        if (!selectedRun) return;
        let current = true;
        setTab("history");
        void request<Run>(`detail?id=${encodeURIComponent(selectedRun)}`)
            .then((run) => {
                if (current) setDetail(run);
            })
            .catch(() => undefined);
        return () => {
            current = false;
        };
    }, [request, selectedRun]);
    const mutate = async (path: string, body: unknown) => {
        if (disabled) return;
        setBusy(true);
        try {
            const result = await request<Run>(path, body);
            if (path === "run") setDetail(result);
            setEditing(undefined);
            await reload();
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
        setFilter(task.id);
        setTab("history");
    };
    const taskList = (
        <PersonalTasks
            overview={overview}
            disabled={disabled}
            page={taskPage}
            onPage={setTaskPage}
            onEdit={setEditing}
            onPropose={() => setProposing(true)}
            onHistory={showHistory}
            onMutate={mutate}
            renderStatus={status}
        />
    );
    const history = (
        <section className={cx(styles.panel, styles.goals)} aria-label={res.history}>
            <div className={styles.toolbar}>
                <Select
                    allowClear
                    style={{ minWidth: 240, maxWidth: "100%" }}
                    placeholder={res.allTasks}
                    aria-label={res.allTasks}
                    value={filter}
                    onChange={setFilter}
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
                    setDetail(run);
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
                    onChange={(page) => void loadRuns(page).catch(() => undefined)}
                />
            </div>
        </section>
    );
    return (
        <div className={styles.page}>
            <div className={styles.header}>
                <div>
                    <Typography.Title level={2} style={{ margin: 0 }}>
                        {res.title}
                    </Typography.Title>
                    <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                        {res.intro}
                    </Typography.Paragraph>
                </div>
                <Space wrap>
                    {canAccessPage("/website/assistant") && (
                        <Link className={styles.settingsLink} to={getRealRouteUrl("/website/assistant")}>
                            <SettingsIcon aria-hidden="true" />
                            {getRes().websiteAssistant.title}
                        </Link>
                    )}
                    {tab === "tasks" && (
                        <Button type="primary" icon={<AddIcon />} disabled={disabled} onClick={() => setEditing({})}>
                            {res.create}
                        </Button>
                    )}
                </Space>
            </div>
            {(offline || offlineData) && <Alert type="info" title={res.offline} />}
            <Tabs
                activeKey={tab}
                onChange={setTab}
                tabBarExtraContent={
                    <Tooltip title={res.reload}>
                        <Button
                            type="text"
                            icon={<RefreshIcon />}
                            aria-label={res.reload}
                            disabled={disabled}
                            onClick={() =>
                                void (tab === "history" ? loadRuns(runs.page) : reload()).catch(() => undefined)
                            }
                        />
                    </Tooltip>
                }
                items={[
                    { key: "tasks", label: res.tasks, children: taskList },
                    ...(overview.systemTasks.length
                        ? [
                              {
                                  key: "system",
                                  label: res.systemTasks,
                                  children: (
                                      <SystemTasks
                                          tasks={overview.systemTasks}
                                          definitions={overview.definitions}
                                          disabled={disabled}
                                          onConfigure={setEditing}
                                          onHistory={showHistory}
                                          onEnabled={(task, enabled) =>
                                              void mutate("enabled", {
                                                  id: task.id,
                                                  revision: task.revision,
                                                  enabled,
                                              })
                                          }
                                      />
                                  ),
                              },
                          ]
                        : []),
                    { key: "history", label: res.history, children: history },
                ]}
            />
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
                open={!!detail}
                focusable={{ focusTriggerAfterClose: !historyTrigger.current }}
                onClose={() => {
                    setDetail(undefined);
                    if (selectedRun)
                        setSearchParams(
                            (params) => {
                                params.delete("runId");
                                return params;
                            },
                            { replace: true }
                        );
                }}
                closable={{ placement: "end" }}
                size={560}
                destroyOnHidden
            >
                {detail && (
                    <Space orientation="vertical" size="large" style={{ width: "100%" }}>
                        <Typography.Title level={4}>{detail.taskName}</Typography.Title>
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
                                            ? date(detail.planSnapshot.schedule.at)
                                            : detail.planSnapshot.schedule.kind === "interval"
                                            ? `${res.minutes}: ${detail.planSnapshot.schedule.intervalMinutes}`
                                            : `${
                                                  detail.planSnapshot.schedule.kind === "daily" ? res.daily : res.weekly
                                              } ${detail.planSnapshot.schedule.time} (${
                                                  detail.planSnapshot.schedule.timezone
                                              })`,
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
