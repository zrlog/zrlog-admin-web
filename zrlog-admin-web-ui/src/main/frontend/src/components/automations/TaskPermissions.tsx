import { Space, Typography } from "antd";
import Tag from "@zrlog/ui/antd/Tag";
import { getRes } from "../../utils/constants";

export default function TaskPermissions({ actions, compact = false }: { actions: string[]; compact?: boolean }) {
    const res = getRes();
    if (!actions.length) return null;
    if (compact)
        return (
            <Typography.Text type="secondary">
                {res.assistant.requiredPermissions}:{" "}
                {actions
                    .map((action) => res.access.actions[action as keyof typeof res.access.actions] ?? action)
                    .join(" / ")}
            </Typography.Text>
        );
    return (
        <Space wrap>
            <Typography.Text type="secondary">{res.assistant.requiredPermissions}</Typography.Text>
            {actions.map((action) => (
                <Tag key={action}>{res.access.actions[action as keyof typeof res.access.actions] ?? action}</Tag>
            ))}
        </Space>
    );
}
