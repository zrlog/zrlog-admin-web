import { Card, Grid, Select, Space, Table, Typography } from "antd";
import { useState } from "react";
import { getRes } from "../utils/constants";
import type { AccountRole } from "../utils/account-access";
type Route = { path: string; descriptionKey: string };
type Action = { id: string; roles: AccountRole[]; routes: string[]; routeDetails?: Route[]; scope?: string };
type Page = { currentRole: AccountRole; roles: AccountRole[]; actions: Action[] };
export default function Access({ data }: { data: Page }) {
    const res = getRes().access;
    const [role, setRole] = useState<AccountRole>(data.currentRole);
    const screens = Grid.useBreakpoint();
    const labels: Record<string, string> = res.actions;
    const descriptions: Readonly<Record<string, string>> = res.endpointDescriptions;
    const shownRoles = screens.lg ? data.roles : [role];
    return (
        <Card title={res.title}>
            <Typography.Paragraph>{res.description}</Typography.Paragraph>
            <Space orientation="vertical" style={{ width: "100%" }} size="large">
                <Select
                    aria-label={getRes().members.role}
                    value={role}
                    onChange={setRole}
                    options={data.roles.map((value) => ({ value, label: res.roles[value] }))}
                    style={{ minWidth: 180 }}
                />
                <Typography.Paragraph>{res.ranges[role]}</Typography.Paragraph>
                <Table
                    rowKey="id"
                    dataSource={data.actions}
                    pagination={false}
                    size="small"
                    columns={[
                        { title: res.action, dataIndex: "id", render: (id: string) => labels[id] ?? id },
                        ...shownRoles.map((value) => ({
                            title: res.roles[value],
                            key: value,
                            render: (_: unknown, action: Action) => (action.roles.includes(value) ? res.allowed : "—"),
                        })),
                    ]}
                    expandable={{
                        expandedRowRender: (action) => (
                            <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
                                <Space wrap>
                                    <Typography.Text code>{action.id}</Typography.Text>
                                    {action.scope && <Typography.Text code>{action.scope}</Typography.Text>}
                                </Space>
                                <div>
                                    <Typography.Text strong>{res.routes}</Typography.Text>
                                    <Typography.Paragraph type="secondary">{res.routesHelp}</Typography.Paragraph>
                                </div>
                                {(
                                    action.routeDetails ?? action.routes.map((path) => ({ path, descriptionKey: "" }))
                                ).map((route) => (
                                    <div key={route.path}>
                                        <Typography.Text>
                                            {descriptions[route.descriptionKey] ?? res.unknownEndpoint}
                                        </Typography.Text>
                                        <div>
                                            <Typography.Text type="secondary" style={{ overflowWrap: "anywhere" }}>
                                                {route.path}
                                            </Typography.Text>
                                        </div>
                                    </div>
                                ))}
                            </Space>
                        ),
                    }}
                />
            </Space>
        </Card>
    );
}
