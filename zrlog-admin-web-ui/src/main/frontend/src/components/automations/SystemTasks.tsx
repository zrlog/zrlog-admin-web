import { Button, Space, Typography } from "antd";
import Switch from "@zrlog/ui/antd/Switch";
import Tag from "@zrlog/ui/antd/Tag";
import { useTheme } from "antd-style";
import { getRes } from "../../utils/constants";
import { formatDateTime } from "../../utils/date-time";
import TaskPermissions from "./TaskPermissions";
import TaskIcon from "./TaskIcon";
import { useAssistantStyles } from "./assistant-styles";
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
    const { styles } = useAssistantStyles();
    const theme = useTheme();
    const date = (time?: number) => (time ? formatDateTime(time) : res.none);
    if (!tasks.length) return null;
    return (
        <section className={styles.panel} aria-label={res.systemTasks}>
            <div className={styles.panelHeader}>
                <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
                    {res.systemHelp}
                </Typography.Paragraph>
            </div>
            {tasks.map((task) => {
                const definition = definitions.find((item) => item.type === task.type);
                const available = !!definition?.available;
                const review = ["needs_attention", "uncertain"].includes(task.lastStatus ?? "");
                return (
                    <article key={task.id} className={styles.taskRow}>
                        <div className={styles.systemHeading}>
                            <div className={styles.systemIcon}>
                                <TaskIcon type={task.type} />
                            </div>
                            <Space wrap size="small" style={{ minWidth: 0 }}>
                                <Typography.Text className={styles.title}>
                                    {definition?.title ?? task.name}
                                </Typography.Text>
                                {task.activeRunId ? (
                                    <Tag>{res.statuses.running}</Tag>
                                ) : (
                                    task.lastStatus && (
                                        <Tag
                                            color={
                                                review
                                                    ? "warning"
                                                    : task.lastStatus === "failed"
                                                    ? "error"
                                                    : task.lastStatus === "success"
                                                    ? "success"
                                                    : undefined
                                            }
                                        >
                                            {res.statuses[task.lastStatus as keyof typeof res.statuses] ??
                                                task.lastStatus}
                                        </Tag>
                                    )
                                )}
                            </Space>
                            <Switch
                                aria-label={definition?.title ?? task.name}
                                checked={task.enabled}
                                checkedChildren={res.on}
                                unCheckedChildren={res.off}
                                disabled={disabled || (!task.enabled && (!available || review || !!task.activeRunId))}
                                onChange={(enabled) => onEnabled(task, enabled)}
                            />
                            <Typography.Paragraph
                                className={styles.systemDescription}
                                type="secondary"
                                style={{ margin: 0 }}
                            >
                                {definition?.description}
                            </Typography.Paragraph>
                        </div>
                        <div className={styles.taskBody}>
                            {!available && (
                                <Typography.Paragraph type="secondary" style={{ marginBottom: theme.marginXS }}>
                                    {definition?.unavailableReason ?? res.unavailable}
                                </Typography.Paragraph>
                            )}
                            {review && (
                                <Typography.Paragraph type="warning" style={{ marginBottom: theme.marginXS }}>
                                    {res.review}
                                </Typography.Paragraph>
                            )}
                            <div className={styles.footer}>
                                <div className={styles.details}>
                                    <div className={styles.metadata}>
                                        <span>
                                            {res.next}: {task.enabled ? date(task.nextRunAt) : res.none}
                                        </span>
                                        <span>
                                            {res.last}: {task.lastRunAt ? date(task.lastRunAt) : res.never}
                                        </span>
                                    </div>
                                    <TaskPermissions compact actions={definition?.requiredActions ?? []} />
                                </div>
                                <Space wrap className={styles.actions}>
                                    <Button type="link" onClick={() => onHistory(task)}>
                                        {res.history}
                                    </Button>
                                    <Button
                                        disabled={disabled || !available || !!task.activeRunId}
                                        onClick={() => onConfigure(task)}
                                    >
                                        {res.configure}
                                    </Button>
                                </Space>
                            </div>
                        </div>
                    </article>
                );
            })}
        </section>
    );
}
