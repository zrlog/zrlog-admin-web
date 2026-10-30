import { Space, Typography } from "antd";
import Tag from "@zrlog/ui/antd/Tag";
import { getRes } from "../../utils/constants";

export default function TaskPermissions({ actions }: { actions: string[] }) {
    const res = getRes();
    if (!actions.length) return null;
    return (
        <Space wrap>
            <Typography.Text type="secondary">{res.automations.requiredPermissions}</Typography.Text>
            {actions.map((action) => (
                <Tag key={action}>{res.access.actions[action as keyof typeof res.access.actions] ?? action}</Tag>
            ))}
        </Space>
    );
}
