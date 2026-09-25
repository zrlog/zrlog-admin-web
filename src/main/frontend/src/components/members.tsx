import { USER_ROUTES } from "../utils/account-page-routes";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { QuestionCircleOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Drawer, Form, Input, List, Select, Space, Switch, Tag, Typography, message } from "antd";
import { useAxiosBaseInstance } from "../base/AppBase";
import { getRealRouteUrl, getRes } from "../utils/constants";
import { getSsDate } from "../base/SsData";
import { AccountRole, hasAction } from "../utils/account-access";
import WebsiteSettingsLayout from "./common/WebsiteSettingsLayout";

type Member = { userId: number; userName: string; email: string; role: AccountRole; enabled: boolean };
type Page = { members: Member[]; currentRole: AccountRole };
export default function Members({ data }: { data: Page }) {
    const navigate = useNavigate();
    const [members, setMembers] = useState(data.members);
    const [editing, setEditing] = useState<Member | null | undefined>();
    const [transferring, setTransferring] = useState(false);
    const [busy, setBusy] = useState(false);
    const [form] = Form.useForm();
    const [transferForm] = Form.useForm();
    const api = useAxiosBaseInstance();
    const [notice, contextHolder] = message.useMessage();
    const res = getRes().members;
    const roles = getRes().access.roles;
    const allowedRoles: AccountRole[] = hasAction("member.appoint_admin")
        ? ["admin", "editor", "author", "contributor"]
        : ["editor", "author", "contributor"];
    const open = (member: Member | null) => {
        form.resetFields();
        form.setFieldsValue(member ?? { role: "contributor", enabled: true });
        setEditing(member);
    };
    const reload = async () => {
        const response = await api.get("/api/admin/members");
        if (!response.data.error) setMembers(response.data.data.members);
    };
    const save = async (values: any) => {
        setBusy(true);
        try {
            const response = await api.post(`/api/admin/members/${editing ? "update" : "create"}`, {
                ...values,
                userId: editing?.userId,
            });
            if (response.data.error) {
                notice.error(response.data.message);
                return;
            }
            setEditing(undefined);
            await reload();
            notice.success(res.saved);
        } finally {
            setBusy(false);
        }
    };
    const transfer = async (values: any) => {
        setBusy(true);
        try {
            const response = await api.post("/api/admin/members/transfer", values);
            if (response.data.error) notice.error(response.data.message);
            else window.location.reload();
        } finally {
            setBusy(false);
        }
    };
    return (
        <WebsiteSettingsLayout
            activeKey="members"
            extra={
                <Space wrap>
                    <Button
                        icon={<QuestionCircleOutlined />}
                        onClick={() => navigate(getRealRouteUrl(USER_ROUTES.permissions))}
                    >
                        {getRes().access.title}
                    </Button>
                    <Button type="primary" onClick={() => open(null)}>
                        {res.create}
                    </Button>
                </Space>
            }
        >
            <Space orientation="vertical" size="large" style={{ width: "100%" }}>
                {contextHolder}
                <List
                    dataSource={members}
                    renderItem={(member) => (
                        <List.Item
                            actions={[
                                member.role !== "owner" &&
                                member.userId !== getSsDate().user?.userId &&
                                (hasAction("member.appoint_admin") || member.role !== "admin") ? (
                                    <Button key="edit" onClick={() => open(member)}>
                                        {res.edit}
                                    </Button>
                                ) : null,
                            ]}
                        >
                            <List.Item.Meta
                                title={
                                    <Space wrap>
                                        <Typography.Text strong>{member.userName}</Typography.Text>
                                        <Tag>{roles[member.role]}</Tag>
                                        <Tag color={member.enabled ? "success" : "default"}>
                                            {member.enabled ? res.active : res.disabled}
                                        </Tag>
                                    </Space>
                                }
                                description={member.email || (member.role === "owner" ? res.ownerProtected : "")}
                            />
                        </List.Item>
                    )}
                />
                {hasAction("ownership.transfer") && (
                    <Card>
                        <Button
                            onClick={() => {
                                transferForm.resetFields();
                                setTransferring(true);
                            }}
                        >
                            {res.transfer}
                        </Button>
                    </Card>
                )}
                <Drawer
                    title={editing ? res.edit : res.create}
                    open={editing !== undefined}
                    onClose={() => setEditing(undefined)}
                    destroyOnHidden
                >
                    <Form form={form} layout="vertical" onFinish={save}>
                        {!editing && (
                            <>
                                <Form.Item
                                    name="userName"
                                    label={res.name}
                                    rules={[{ required: true }, { pattern: /^[A-Za-z0-9_.-]{2,16}$/ }]}
                                >
                                    <Input autoComplete="off" />
                                </Form.Item>
                                <Form.Item name="email" label={res.email} rules={[{ type: "email" }]}>
                                    <Input maxLength={64} />
                                </Form.Item>
                            </>
                        )}
                        <Form.Item name="role" label={res.role} rules={[{ required: true }]}>
                            <Select options={allowedRoles.map((value) => ({ value, label: roles[value] }))} />
                        </Form.Item>
                        <Form.Item noStyle shouldUpdate={(a, b) => a.role !== b.role}>
                            {({ getFieldValue }) => (
                                <Typography.Paragraph type="secondary">
                                    {getRes().access.ranges[getFieldValue("role") as AccountRole]}
                                </Typography.Paragraph>
                            )}
                        </Form.Item>
                        {editing && (
                            <Form.Item name="enabled" valuePropName="checked" label={res.enabled}>
                                <Switch />
                            </Form.Item>
                        )}
                        <Form.Item
                            name="password"
                            label={editing ? res.newPassword : res.password}
                            help={res.passwordHelp}
                            rules={[{ required: !editing }, { min: 12, max: 256 }]}
                        >
                            <Input.Password autoComplete="new-password" />
                        </Form.Item>
                        <Button type="primary" htmlType="submit" loading={busy}>
                            {res.save}
                        </Button>
                    </Form>
                </Drawer>
                <Drawer title={res.transfer} open={transferring} onClose={() => setTransferring(false)} destroyOnHidden>
                    <Alert type="warning" showIcon title={res.transferHelp} style={{ marginBottom: 16 }} />
                    <Form form={transferForm} layout="vertical" onFinish={transfer}>
                        <Form.Item name="userId" label={res.target} rules={[{ required: true }]}>
                            <Select
                                options={members
                                    .filter((m) => m.enabled && m.role !== "owner")
                                    .map((m) => ({ value: m.userId, label: m.userName }))}
                            />
                        </Form.Item>
                        <Form.Item name="password" label={res.currentPassword} rules={[{ required: true }]}>
                            <Input.Password autoComplete="current-password" />
                        </Form.Item>
                        <Form.Item name="mfaCode" label={res.mfa}>
                            <Input autoComplete="one-time-code" />
                        </Form.Item>
                        <Button type="primary" htmlType="submit" loading={busy}>
                            {res.transferConfirm}
                        </Button>
                    </Form>
                </Drawer>
            </Space>
        </WebsiteSettingsLayout>
    );
}
