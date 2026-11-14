import { Button, Dropdown, Empty, Pagination, Space, Tooltip, Typography } from "antd";
import Tag from "@zrlog/ui/antd/Tag";
import MoreIcon from "@zrlog/ui/icons/more";
import AddIcon from "@zrlog/ui/icons/add";
import RobotIcon from "@zrlog/ui/icons/robot";
import { useUiApp } from "@zrlog/ui/feedback";
import type { ReactNode } from "react";
import { getRes } from "../../../utils/constants";
import { formatDateTime } from "../../../utils/date-time";
import type { Overview, Plan, Task } from "./types";
import TaskIcon from "./TaskIcon";
import { useAssistantStyles } from "../assistant-styles";

export default function PersonalTasks({
    overview,
    compact = false,
    selectedId,
    disabled,
    page,
    onPage,
    onEdit,
    onPropose,
    onOpen,
    onMutate,
    renderStatus,
}: {
    overview: Overview;
    compact?: boolean;
    selectedId?: string;
    disabled: boolean;
    page: number;
    onPage: (page: number) => void;
    onEdit: (plan: Partial<Plan>) => void;
    onPropose: () => void;
    onOpen: (task: Task) => void;
    onMutate: (path: string, body: unknown) => Promise<void>;
    renderStatus: (status?: string) => ReactNode;
}) {
    const res = getRes().assistant;
    const { styles } = useAssistantStyles();
    const { modal } = useUiApp();
    return (
        <section aria-label={res.tasks}>
            <div className={compact ? styles.compactTaskActions : styles.panelHeader}>
                {!compact && (
                    <Typography.Text type="secondary">
                        {res.taskCount.replace("{count}", String(overview.total))}
                    </Typography.Text>
                )}
                <Space size={compact ? 4 : undefined} wrap>
                    <Button
                        type={compact ? "text" : "primary"}
                        icon={<AddIcon />}
                        disabled={disabled}
                        onClick={() => onEdit({})}
                    >
                        {res.create}
                    </Button>
                    {overview.aiAvailable && (
                        <Tooltip title={compact ? res.askAssistant : undefined}>
                            <Button
                                type={compact ? "text" : "default"}
                                icon={<RobotIcon />}
                                aria-label={res.askAssistant}
                                disabled={disabled}
                                onClick={onPropose}
                            >
                                {!compact && res.askAssistant}
                            </Button>
                        </Tooltip>
                    )}
                </Space>
            </div>
            {!overview.tasks.length &&
                (compact ? (
                    <Typography.Paragraph type="secondary">{res.empty}</Typography.Paragraph>
                ) : (
                    <Empty description={res.empty} />
                ))}
            {overview.tasks.map((task) => {
                const review = ["needs_attention", "uncertain"].includes(task.lastStatus ?? "");
                return (
                    <article
                        key={task.id}
                        className={compact ? styles.compactTaskRow : styles.taskRow}
                        data-selected={selectedId === task.id || undefined}
                    >
                        <div className={styles.taskHeading}>
                            <button
                                type="button"
                                className={styles.taskOpen}
                                onClick={() => onOpen(task)}
                                disabled={disabled}
                                aria-current={selectedId === task.id ? "page" : undefined}
                            >
                                <span className={styles.identity}>
                                    {!compact && <TaskIcon type={task.type} />}
                                    <span className={styles.details}>
                                        <span
                                            className={compact ? styles.compactTaskTitle : styles.title}
                                            title={task.name}
                                        >
                                            {task.name}
                                        </span>
                                        {!compact && (
                                            <>
                                                <span>
                                                    {renderStatus(task.activeRunId ? "running" : task.lastStatus)}
                                                    {task.archived ? (
                                                        <Tag>{res.archived}</Tag>
                                                    ) : (
                                                        !task.enabled && !review && <Tag>{res.paused}</Tag>
                                                    )}
                                                </span>
                                                <span className={styles.metadata}>
                                                    {task.lastRunAt
                                                        ? `${res.last}: ${formatDateTime(task.lastRunAt)}`
                                                        : res.never}
                                                </span>
                                                {!task.archived && task.enabled && task.nextRunAt && (
                                                    <span className={styles.metadata}>
                                                        {res.next}: {formatDateTime(task.nextRunAt)}
                                                    </span>
                                                )}
                                            </>
                                        )}
                                    </span>
                                </span>
                            </button>
                            {!task.archived && (
                                <Dropdown
                                    trigger={["click"]}
                                    menu={{
                                        items: [
                                            {
                                                key: "edit",
                                                label: res.edit,
                                                disabled: disabled || !!task.activeRunId,
                                                onClick: () => onEdit(task),
                                            },
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
                                                        cancelText: getRes().common.cancel,
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
                            )}
                        </div>
                    </article>
                );
            })}
            {overview.total > 100 && (
                <Pagination
                    simple={compact}
                    size={compact ? "small" : undefined}
                    current={page}
                    pageSize={100}
                    total={overview.total}
                    showSizeChanger={false}
                    onChange={onPage}
                />
            )}
        </section>
    );
}
