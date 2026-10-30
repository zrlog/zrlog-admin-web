import {
    Alert,
    Button,
    Card,
    Col,
    Descriptions,
    Drawer,
    Empty,
    List,
    Pagination,
    Popconfirm,
    Row,
    Select,
    Space,
    Tabs,
    Typography,
} from "antd";
import Tag from "@zrlog/ui/antd/Tag";
import { useUiMessage } from "@zrlog/ui/feedback";
import { useCallback, useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getSsDate } from "../../base/SsData";
import { getRealRouteUrl, getRes } from "../../utils/constants";
import { formatDateTime } from "../../utils/date-time";
import type { AdminCommonProps } from "../../type";
import TaskForm from "./TaskForm";
import SystemTasks from "./SystemTasks";
import RunChildren from "./RunChildren";
import TaskSettings from "./TaskSettings";
import ProposalForm from "./ProposalForm";
import type { Overview, Plan, Request, Run, Runs, Task } from "./types";

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
    const [searchParams, setSearchParams] = useSearchParams();
    const selectedRun = searchParams.get("runId");
    useEffect(() => {
        if (searchParams.get("tab") === "system") setTab("system");
    }, [searchParams]);
    const [notice, contextHolder] = useUiMessage();
    const api = useAxiosBaseInstance();
    const res = getRes().automations;
    const disabled = offline || offlineData || busy;
    useEffect(() => setOverview(data), [data]);
    const request: Request = useCallback(
        async <T,>(path: string, body?: unknown): Promise<T> => {
            const session = getSsDate().key;
            try {
                const url = `/api/admin/assistant${path ? (path.startsWith("?") ? path : "/" + path) : ""}`;
                const response = body === undefined ? await api.get(url) : await api.post(url, body);
                if (session !== getSsDate().key) throw new Error(res.failedRequest);
                if (response.data.error) throw new Error(response.data.message || res.failedRequest);
                return response.data.data as T;
            } catch (error) {
                if (session === getSsDate().key)
                    notice.error(error instanceof Error ? error.message : res.failedRequest);
                throw error;
            }
        },
        [api, notice, res.failedRequest]
    );
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
        <Space orientation="vertical" size="large" style={{ width: "100%" }}>
            <Space wrap>
                <Typography.Title level={4} style={{ margin: 0 }}>
                    {res.tasks}
                </Typography.Title>
                <Button type="primary" disabled={disabled} onClick={() => setEditing({})}>
                    {res.create}
                </Button>
                {overview.aiAvailable && (
                    <Button disabled={disabled} onClick={() => setProposing(true)}>
                        {res.askAssistant}
                    </Button>
                )}
            </Space>
            <List
                dataSource={overview.tasks}
                locale={{ emptyText: <Empty description={res.empty} /> }}
                renderItem={(task) => (
                    <List.Item>
                        <Space orientation="vertical" style={{ width: "100%" }}>
                            <Space wrap>
                                <Typography.Text strong>{task.name}</Typography.Text>
                                {status(task.activeRunId ? "running" : task.lastStatus)}
                                {!task.enabled && <Tag>{res.paused}</Tag>}
                            </Space>
                            <Typography.Text type="secondary">
                                {res.next}: {task.enabled ? date(task.nextRunAt) : res.none} · {res.last}:{" "}
                                {date(task.lastRunAt)}
                            </Typography.Text>
                            {["needs_attention", "uncertain"].includes(task.lastStatus ?? "") && (
                                <Alert type="warning" title={res.review} />
                            )}
                            <Space wrap>
                                <Button disabled={disabled || !!task.activeRunId} onClick={() => setEditing(task)}>
                                    {res.edit}
                                </Button>
                                <Button
                                    disabled={
                                        disabled ||
                                        !!task.activeRunId ||
                                        ["needs_attention", "uncertain"].includes(task.lastStatus ?? "")
                                    }
                                    onClick={() => void mutate("run", { id: task.id, revision: task.revision })}
                                >
                                    {res.run}
                                </Button>
                                <Button
                                    disabled={disabled}
                                    onClick={() =>
                                        void mutate("enabled", {
                                            id: task.id,
                                            revision: task.revision,
                                            enabled: !task.enabled,
                                        })
                                    }
                                >
                                    {task.enabled ? res.disable : res.enable}
                                </Button>
                                <Button type="link" onClick={() => showHistory(task)}>
                                    {res.history}
                                </Button>
                                <Popconfirm
                                    title={res.deleteHelp}
                                    onConfirm={() => mutate("delete", { id: task.id, revision: task.revision })}
                                >
                                    <Button danger disabled={disabled || !!task.activeRunId}>
                                        {res.delete}
                                    </Button>
                                </Popconfirm>
                            </Space>
                        </Space>
                    </List.Item>
                )}
            />
            {overview.total > 100 && (
                <Pagination
                    current={taskPage}
                    pageSize={100}
                    total={overview.total}
                    showSizeChanger={false}
                    onChange={setTaskPage}
                />
            )}
            <Typography.Title level={4}>{res.templates}</Typography.Title>
            <Row gutter={[16, 16]}>
                {overview.definitions
                    .filter((definition) => !definition.systemTask)
                    .map((definition) => (
                        <Col key={definition.type} xs={24} md={12} xl={8}>
                            <Card title={definition.title} style={{ height: "100%" }}>
                                <Typography.Paragraph>{definition.description}</Typography.Paragraph>
                                {!definition.available && definition.unavailableReason && (
                                    <Typography.Paragraph type="secondary">
                                        {definition.unavailableReason}
                                    </Typography.Paragraph>
                                )}
                                <Space wrap>
                                    <Tag>{definition.requiresAi ? res.ai : res.aiOptional}</Tag>
                                    <Button
                                        disabled={disabled || !definition.available}
                                        onClick={() =>
                                            setEditing({
                                                type: definition.type,
                                                name: definition.title,
                                                schedule: definition.defaultSchedule,
                                            })
                                        }
                                    >
                                        {definition.available ? res.create : res.unavailable}
                                    </Button>
                                </Space>
                            </Card>
                        </Col>
                    ))}
            </Row>
        </Space>
    );
    const history = (
        <Space orientation="vertical" size="large" style={{ width: "100%" }}>
            <Space wrap>
                <Select
                    allowClear
                    style={{ minWidth: 240, maxWidth: "100%" }}
                    placeholder={res.allTasks}
                    value={filter}
                    onChange={setFilter}
                    options={[...(overview.systemTasks ?? []), ...overview.tasks].map((task) => ({
                        value: task.id,
                        label: task.name,
                    }))}
                />
                <Button onClick={() => void loadRuns(runs.page).catch(() => undefined)}>{res.reload}</Button>
            </Space>
            <Typography.Text type="secondary">{res.retention}</Typography.Text>
            <List
                dataSource={runs.items}
                locale={{ emptyText: <Empty description={res.emptyRuns} /> }}
                renderItem={(run) => (
                    <List.Item>
                        <Space orientation="vertical" style={{ width: "100%" }}>
                            <Space wrap>
                                <Typography.Text strong>{run.taskName}</Typography.Text>
                                {status(run.status)}
                            </Space>
                            <Typography.Text type="secondary">{date(run.startedAt)}</Typography.Text>
                            <Typography.Text>{run.summary}</Typography.Text>
                            <Button style={{ alignSelf: "start" }} onClick={() => setDetail(run)}>
                                {res.detail}
                            </Button>
                        </Space>
                    </List.Item>
                )}
            />
            <Pagination
                current={runs.page}
                pageSize={runs.pageSize}
                total={runs.total}
                showSizeChanger={false}
                onChange={(page) => void loadRuns(page).catch(() => undefined)}
            />
        </Space>
    );
    return (
        <div style={{ maxWidth: 1200, margin: "0 auto", padding: 16 }}>
            {contextHolder}
            <Space orientation="vertical" size="large" style={{ width: "100%" }}>
                <div>
                    <Typography.Title level={2}>{res.title}</Typography.Title>
                    <Typography.Paragraph type="secondary">{res.intro}</Typography.Paragraph>
                </div>
                {(offline || offlineData) && <Alert type="info" title={res.offline} />}
                <Space wrap>
                    <Button disabled={disabled} onClick={() => void reload().catch(() => undefined)}>
                        {res.reload}
                    </Button>
                </Space>
                <Tabs
                    activeKey={tab}
                    onChange={setTab}
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
                        ...(overview.canManageScheduler
                            ? [
                                  {
                                      key: "settings",
                                      label: res.settings,
                                      children: <TaskSettings request={request} disabled={disabled} />,
                                  },
                              ]
                            : []),
                    ]}
                />
            </Space>
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
