import { Button, Collapse, Descriptions, Space, Typography } from "antd";
import Switch from "@zrlog/ui/antd/Switch";
import Tag from "@zrlog/ui/antd/Tag";
import { getRes } from "../../../utils/constants";
import { formatDateTime } from "../../../utils/date-time";
import TaskPermissions from "./TaskPermissions";
import TaskIcon from "./TaskIcon";
import { useAssistantSettingsStyles } from "./settings-styles";
import type { Definition, Plan, Task } from "./types";

export default function SystemTasks({
    tasks,
    definitions,
    disabled,
    onConfigure,
    onEnabled,
    onHistory,
}: {
    tasks: Task[];
    definitions: Definition[];
    disabled: boolean;
    onConfigure: (plan: Partial<Plan>) => void;
    onEnabled: (task: Task, enabled: boolean) => void;
    onHistory: (task: Task) => void;
}) {
    const res = getRes().assistant;
    const { styles } = useAssistantSettingsStyles();
    const date = (time?: number) => (time ? formatDateTime(time) : res.none);
    if (!tasks.length) return null;
    return (
        <section className={styles.section} aria-label={res.systemTasks}>
            <header className={styles.label}>
                <h3 className={styles.title}>{res.systemTasks}</h3>
                <p className={styles.help}>{res.systemHelp}</p>
            </header>
            <Collapse
                style={{ background: "transparent" }}
                items={tasks.map((task) => {
                    const definition = definitions.find((item) => item.type === task.type);
                    const title = definition?.title ?? task.name;
                    const available = !!definition?.available;
                    const review = ["needs_attention", "uncertain"].includes(task.lastStatus ?? "");
                    const status = task.activeRunId ? "running" : task.lastStatus;
                    return {
                        key: task.id,
                        label: (
                            <div className={styles.label}>
                                <div className={styles.taskTitle}>
                                    <TaskIcon type={task.type} />
                                    <Typography.Text strong>{title}</Typography.Text>
                                    {(task.activeRunId || review || status === "failed") && (
                                        <Tag color={review ? "warning" : status === "failed" ? "error" : undefined}>
                                            {res.statuses[status as keyof typeof res.statuses] ?? status}
                                        </Tag>
                                    )}
                                </div>
                                <p className={styles.help}>{definition?.description}</p>
                                {!available && (
                                    <Typography.Text type="secondary">
                                        {definition?.unavailableReason ?? res.unavailable}
                                    </Typography.Text>
                                )}
                                {review && <Typography.Text type="warning">{res.review}</Typography.Text>}
                            </div>
                        ),
                        extra: (
                            <span
                                onClick={(event) => event.stopPropagation()}
                                onKeyDown={(event) => event.stopPropagation()}
                            >
                                <Switch
                                    className={styles.toggle}
                                    aria-label={title}
                                    checked={task.enabled}
                                    disabled={
                                        disabled || (!task.enabled && (!available || review || !!task.activeRunId))
                                    }
                                    onChange={(enabled) => onEnabled(task, enabled)}
                                />
                            </span>
                        ),
                        children: (
                            <div className={styles.details}>
                                <Descriptions
                                    size="small"
                                    column={1}
                                    items={[
                                        {
                                            key: "next",
                                            label: res.next,
                                            children: task.enabled ? date(task.nextRunAt) : res.none,
                                        },
                                        {
                                            key: "last",
                                            label: res.last,
                                            children: (
                                                <Space wrap>
                                                    <span>{task.lastRunAt ? date(task.lastRunAt) : res.never}</span>
                                                    {task.lastStatus && (
                                                        <Tag>
                                                            {res.statuses[
                                                                task.lastStatus as keyof typeof res.statuses
                                                            ] ?? task.lastStatus}
                                                        </Tag>
                                                    )}
                                                </Space>
                                            ),
                                        },
                                    ]}
                                />
                                <TaskPermissions compact actions={definition?.requiredActions ?? []} />
                                <Space wrap>
                                    <Button
                                        disabled={disabled || !available || !!task.activeRunId}
                                        onClick={() => onConfigure(task)}
                                    >
                                        {res.edit}
                                    </Button>
                                    <Button type="link" onClick={() => onHistory(task)}>
                                        {res.conversation}
                                    </Button>
                                </Space>
                            </div>
                        ),
                    };
                })}
            />
        </section>
    );
}
