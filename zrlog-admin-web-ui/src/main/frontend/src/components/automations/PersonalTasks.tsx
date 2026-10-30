import { Button, Dropdown, Empty, Pagination, Space, Typography } from "antd";
import Tag from "@zrlog/ui/antd/Tag";
import MoreIcon from "@zrlog/ui/icons/more";
import AddIcon from "@zrlog/ui/icons/add";
import RobotIcon from "@zrlog/ui/icons/robot";
import CalendarIcon from "@zrlog/ui/icons/calendar";
import { useUiApp } from "@zrlog/ui/feedback";
import { useTheme } from "antd-style";
import type { ReactNode } from "react";
import { getRes } from "../../utils/constants";
import { formatDateTime } from "../../utils/date-time";
import type { Overview, Plan, Task } from "./types";
import TaskIcon from "./TaskIcon";
import { useAssistantStyles } from "./assistant-styles";

export default function PersonalTasks({
    overview,
    disabled,
    page,
    onPage,
    onEdit,
    onPropose,
    onHistory,
    onMutate,
    renderStatus,
}: {
    overview: Overview;
    disabled: boolean;
    page: number;
    onPage: (page: number) => void;
    onEdit: (plan: Partial<Plan>) => void;
    onPropose: () => void;
    onHistory: (task: Task) => void;
    onMutate: (path: string, body: unknown) => Promise<void>;
    renderStatus: (status?: string) => ReactNode;
}) {
    const res = getRes().automations;
    const { styles, cx } = useAssistantStyles();
    const { modal } = useUiApp();
    const theme = useTheme();
    const date = (value?: number) => (value ? formatDateTime(value) : res.none);
    return (
        <div className={styles.workspace}>
            <section className={styles.panel} aria-label={res.scheduledTasks}>
                <div className={styles.panelHeader}>
                    <Space orientation="vertical" size={0}>
                        <Typography.Title level={4} style={{ margin: 0 }}>
                            {res.scheduledTasks}
                        </Typography.Title>
                        <Typography.Text type="secondary">
                            {res.taskCount.replace("{count}", String(overview.total))}
                        </Typography.Text>
                    </Space>
                    <Button type="primary" icon={<AddIcon />} disabled={disabled} onClick={() => onEdit({})}>
                        {res.create}
                    </Button>
                </div>
                {!overview.tasks.length && (
                    <div className={styles.taskRow}>
                        <Empty
                            image={<CalendarIcon style={{ fontSize: theme.fontSizeHeading1 }} />}
                            description={res.empty}
                        />
                    </div>
                )}
                {overview.tasks.map((task) => {
                    const review = ["needs_attention", "uncertain"].includes(task.lastStatus ?? "");
                    const definition = overview.definitions.find((item) => item.type === task.type);
                    return (
                        <article key={task.id} className={styles.taskRow}>
                            <div className={styles.taskHeading}>
                                <div className={styles.identity}>
                                    <TaskIcon type={task.type} />
                                    <div style={{ minWidth: 0 }}>
                                        <Space wrap size="small">
                                            <Typography.Text className={styles.title}>{task.name}</Typography.Text>
                                            {renderStatus(task.activeRunId ? "running" : task.lastStatus)}
                                            {!task.enabled && !review && <Tag>{res.paused}</Tag>}
                                        </Space>
                                        <br />
                                        <Typography.Text type="secondary">{definition?.title}</Typography.Text>
                                    </div>
                                </div>
                                <Dropdown
                                    trigger={["click"]}
                                    menu={{
                                        items: [
                                            {
                                                key: "run",
                                                label: res.run,
                                                disabled: disabled || !!task.activeRunId || review,
                                                onClick: () =>
                                                    void onMutate("run", { id: task.id, revision: task.revision }),
                                            },
                                            {
                                                key: "enabled",
                                                label: task.enabled ? res.disable : res.enable,
                                                disabled: disabled || (!task.enabled && review),
                                                onClick: () =>
                                                    void onMutate("enabled", {
                                                        id: task.id,
                                                        revision: task.revision,
                                                        enabled: !task.enabled,
                                                    }),
                                            },
                                            { type: "divider" },
                                            {
                                                key: "delete",
                                                label: res.delete,
                                                danger: true,
                                                disabled: disabled || !!task.activeRunId,
                                                onClick: () =>
                                                    modal.confirm({
                                                        title: task.name,
                                                        content: res.deleteHelp,
                                                        okText: res.delete,
                                                        cancelText: getRes().cancel,
                                                        okButtonProps: { danger: true },
                                                        onOk: () =>
                                                            onMutate("delete", {
                                                                id: task.id,
                                                                revision: task.revision,
                                                            }),
                                                    }),
                                            },
                                        ],
                                    }}
                                >
                                    <Button
                                        type="text"
                                        icon={<MoreIcon />}
                                        aria-label={`${res.moreActions}: ${task.name}`}
                                    />
                                </Dropdown>
                            </div>
                            <div className={styles.taskBody}>
                                {review && (
                                    <Typography.Paragraph type="warning" style={{ marginBottom: theme.marginXS }}>
                                        {res.review}
                                    </Typography.Paragraph>
                                )}
                                <div className={styles.footer}>
                                    <div className={styles.details}>
                                        <span className={styles.metadata}>
                                            {res.next}: {task.enabled ? date(task.nextRunAt) : res.none}
                                        </span>
                                        <span className={styles.metadata}>
                                            {res.last}: {task.lastRunAt ? date(task.lastRunAt) : res.never}
                                        </span>
                                    </div>
                                    <Space wrap className={styles.actions}>
                                        <Button type="link" onClick={() => onHistory(task)}>
                                            {res.history}
                                        </Button>
                                        <Button disabled={disabled || !!task.activeRunId} onClick={() => onEdit(task)}>
                                            {res.edit}
                                        </Button>
                                    </Space>
                                </div>
                            </div>
                        </article>
                    );
                })}
                {overview.total > 100 && (
                    <div className={styles.panelHeader}>
                        <Pagination
                            current={page}
                            pageSize={100}
                            total={overview.total}
                            showSizeChanger={false}
                            onChange={onPage}
                        />
                    </div>
                )}
            </section>
            <aside className={cx(styles.panel, styles.goals)} aria-label={res.templates}>
                <Typography.Title level={4} style={{ marginTop: 0 }}>
                    {res.templates}
                </Typography.Title>
                <Typography.Paragraph type="secondary">{res.templateHelp}</Typography.Paragraph>
                {overview.aiAvailable && (
                    <Button
                        block
                        icon={<RobotIcon />}
                        disabled={disabled}
                        onClick={onPropose}
                        style={{ marginBottom: theme.margin }}
                    >
                        {res.askAssistant}
                    </Button>
                )}
                {overview.definitions
                    .filter((item) => !item.systemTask)
                    .map((definition) => (
                        <div key={definition.type} className={styles.goal}>
                            <div className={styles.taskHeading}>
                                <Typography.Text strong>{definition.title}</Typography.Text>
                                <Button
                                    type="text"
                                    icon={<AddIcon />}
                                    aria-label={`${res.create}: ${definition.title}`}
                                    disabled={disabled || !definition.available}
                                    onClick={() =>
                                        onEdit({
                                            type: definition.type,
                                            name: definition.title,
                                            schedule: definition.defaultSchedule,
                                        })
                                    }
                                />
                            </div>
                            <Typography.Paragraph type="secondary" style={{ marginBottom: theme.marginXS }}>
                                {definition.description}
                            </Typography.Paragraph>
                            {definition.requiresAi && <Tag>{res.ai}</Tag>}
                            {!definition.available && (
                                <Typography.Text type="secondary">
                                    {definition.unavailableReason ?? res.unavailable}
                                </Typography.Text>
                            )}
                        </div>
                    ))}
            </aside>
        </div>
    );
}
