import { Alert, Button, Card, List, Space, Typography } from "antd";
import Switch from "@zrlog/ui/antd/Switch";
import Tag from "@zrlog/ui/antd/Tag";
import { getRes } from "../../utils/constants";
import { formatDateTime } from "../../utils/date-time";
import TaskPermissions from "./TaskPermissions";
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
    const res = getRes().automations;
    const date = (time?: number) => (time ? formatDateTime(time) : res.none);
    if (!tasks.length) return null;
    return (
        <Card title={res.systemTasks}>
            <Typography.Paragraph type="secondary">{res.systemHelp}</Typography.Paragraph>
            <List
                dataSource={tasks}
                renderItem={(task) => {
                    const definition = definitions.find((item) => item.type === task.type);
                    const available = !!definition?.available;
                    const review = ["needs_attention", "uncertain"].includes(task.lastStatus ?? "");
                    return (
                        <List.Item>
                            <Space orientation="vertical" style={{ width: "100%" }}>
                                <Space wrap>
                                    <Typography.Text strong>{definition?.title ?? task.name}</Typography.Text>
                                    <Switch
                                        aria-label={definition?.title ?? task.name}
                                        checked={task.enabled}
                                        checkedChildren={res.on}
                                        unCheckedChildren={res.off}
                                        disabled={
                                            disabled || (!task.enabled && (!available || review || !!task.activeRunId))
                                        }
                                        onChange={(enabled) => onEnabled(task, enabled)}
                                    />
                                    {task.activeRunId && <Tag>{res.statuses.running}</Tag>}
                                    {task.lastStatus && !task.activeRunId && (
                                        <Tag
                                            color={
                                                review ? "warning" : task.lastStatus === "failed" ? "error" : undefined
                                            }
                                        >
                                            {res.statuses[task.lastStatus as keyof typeof res.statuses] ??
                                                task.lastStatus}
                                        </Tag>
                                    )}
                                </Space>
                                <Typography.Text type="secondary">{definition?.description}</Typography.Text>
                                {!available && (
                                    <Typography.Text type="secondary">
                                        {definition?.unavailableReason ?? res.unavailable}
                                    </Typography.Text>
                                )}
                                {definition && !definition.enabledByDefault && (
                                    <Typography.Text type="secondary">{res.optInHelp}</Typography.Text>
                                )}
                                <TaskPermissions actions={definition?.requiredActions ?? []} />
                                {review && <Alert type="warning" title={res.review} />}
                                <Space wrap>
                                    <Typography.Text type="secondary">
                                        {res.next}: {task.enabled ? date(task.nextRunAt) : res.none} · {res.last}:{" "}
                                        {date(task.lastRunAt)}
                                    </Typography.Text>
                                    <Button
                                        disabled={disabled || !available || !!task.activeRunId}
                                        onClick={() => onConfigure(task)}
                                    >
                                        {res.configure}
                                    </Button>
                                    <Button type="link" onClick={() => onHistory(task)}>
                                        {res.history}
                                    </Button>
                                </Space>
                            </Space>
                        </List.Item>
                    );
                }}
            />
        </Card>
    );
}
