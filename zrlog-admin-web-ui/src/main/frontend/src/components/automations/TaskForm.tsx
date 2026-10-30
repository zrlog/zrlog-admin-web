import { Alert, Button, Checkbox, Drawer, Form, InputNumber, Select, Space, Typography } from "antd";
import Input from "@zrlog/ui/antd/Input";
import Switch from "@zrlog/ui/antd/Switch";
import { useEffect } from "react";
import { getRes } from "../../utils/constants";
import { hasAction } from "../../utils/account-access";
import ArticleChoice from "./ArticleChoice";
import type { Definition, Plan, Schedule } from "./types";

type Values = Omit<Plan, "schedule"> & { schedule: Schedule; atLocal?: string; authorized?: boolean };
const localInput = (at: number) => {
    const date = new Date(at);
    return new Date(at - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
};
export default function TaskForm({
    plan,
    definitions,
    busy,
    onClose,
    onSave,
}: {
    plan: Partial<Plan>;
    definitions: Definition[];
    busy: boolean;
    onClose: () => void;
    onSave: (plan: Plan) => Promise<void>;
}) {
    const [form] = Form.useForm<Values>();
    const type = Form.useWatch("type", form);
    const articleTitle = Form.useWatch(["parameters", "title"], form);
    const articleVersion = Form.useWatch(["parameters", "version"], form);
    const kind = Form.useWatch(["schedule", "kind"], form);
    const definition = definitions.find((item) => item.type === type);
    const res = getRes().automations;
    useEffect(() => {
        form.resetFields();
        const defaults = definitions.find((item) => item.type === plan.type)?.defaultParameters ?? {};
        form.setFieldsValue({
            ...plan,
            enabled: plan.enabled ?? true,
            parameters: { ...plan.parameters, ...defaults },
            authorized: false,
            schedule: {
                kind: "daily",
                time: "09:00",
                timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
                weekdays: [1],
                ...plan.schedule,
            },
            atLocal: localInput(plan.schedule?.at ?? Date.now() + 3600000),
        });
    }, [plan, form, definitions]);
    const save = async (values: Values) => {
        if (!definition) return;
        await onSave({
            id: plan.id,
            revision: plan.revision,
            name: values.name,
            type: values.type,
            enabled: values.enabled,
            parameters: values.parameters ?? {},
            authorizedActions: [
                ...(definition.requiresAuthorization ? definition.requiredActions : []),
                ...(values.authorizedActions ?? []).filter((action) => definition.optionalActions.includes(action)),
            ],
            schedule: {
                ...values.schedule,
                at: ["once", "interval"].includes(values.schedule.kind)
                    ? new Date(values.atLocal!).getTime()
                    : undefined,
            },
        });
    };
    return (
        <Drawer
            title={plan.id ? res.edit : res.create}
            open
            onClose={onClose}
            closable={{ placement: "end" }}
            destroyOnHidden
            size={480}
        >
            <Form form={form} layout="vertical" disabled={busy} onFinish={save}>
                <Form.Item name="type" label={res.capability} rules={[{ required: true }]}>
                    <Select
                        disabled={!!plan.id}
                        options={definitions.map((item) => ({
                            value: item.type,
                            label: item.title,
                            disabled: !item.available,
                        }))}
                        onChange={(value) => {
                            const item = definitions.find((item) => item.type === value);
                            form.setFieldsValue({
                                name: item?.title,
                                parameters: item?.defaultParameters ?? {},
                                authorized: false,
                                authorizedActions: [],
                            });
                            if (item?.defaultSchedule)
                                form.setFieldValue("schedule", {
                                    ...form.getFieldValue("schedule"),
                                    ...item.defaultSchedule,
                                });
                        }}
                    />
                </Form.Item>
                <Typography.Paragraph type="secondary">{definition?.description}</Typography.Paragraph>
                <Form.Item name="name" label={res.name} rules={[{ required: true, whitespace: true }, { max: 160 }]}>
                    <Input maxLength={160} />
                </Form.Item>
                {definition?.fields.map((field) => (
                    <Form.Item
                        key={field.name}
                        name={["parameters", field.name]}
                        label={field.label}
                        valuePropName={field.type === "boolean" ? "checked" : "value"}
                        rules={[{ required: field.required }]}
                    >
                        {field.type === "article" ? (
                            <ArticleChoice
                                title={typeof articleTitle === "string" ? articleTitle : undefined}
                                version={typeof articleVersion === "number" ? articleVersion : undefined}
                                onConfirm={(title, version) => {
                                    form.setFieldsValue({
                                        parameters: { ...form.getFieldValue("parameters"), title, version },
                                        authorized: false,
                                    });
                                }}
                            />
                        ) : field.type === "boolean" ? (
                            <Switch />
                        ) : field.type === "number" ? (
                            <InputNumber
                                min={field.minimum}
                                max={field.maximum}
                                precision={0}
                                style={{ width: "100%" }}
                            />
                        ) : field.type === "textarea" ? (
                            <Input.TextArea rows={4} maxLength={4000} />
                        ) : (
                            <Input maxLength={2000} readOnly={field.readOnly} />
                        )}
                    </Form.Item>
                ))}
                {definition?.fields.some((field) => field.type === "article") && (
                    <>
                        <Form.Item
                            name={["parameters", "version"]}
                            hidden
                            rules={[{ required: true, message: res.confirmDraft }]}
                        >
                            <InputNumber />
                        </Form.Item>
                        <Form.Item name={["parameters", "title"]} hidden>
                            <Input />
                        </Form.Item>
                    </>
                )}
                <Form.Item name={["schedule", "kind"]} label={res.kind} rules={[{ required: true }]}>
                    <Select
                        options={(["once", "interval", "daily", "weekly"] as const).map((value) => ({
                            value,
                            label: res[value],
                        }))}
                    />
                </Form.Item>
                {["once", "interval"].includes(kind) && (
                    <Form.Item name="atLocal" label={res.at} rules={[{ required: true }]}>
                        <Input type="datetime-local" />
                    </Form.Item>
                )}
                {kind === "interval" && (
                    <Form.Item name={["schedule", "intervalMinutes"]} label={res.minutes} rules={[{ required: true }]}>
                        <InputNumber min={1} max={525600} precision={0} />
                    </Form.Item>
                )}
                {["daily", "weekly"].includes(kind) && (
                    <>
                        <Form.Item name={["schedule", "time"]} label={res.time} rules={[{ required: true }]}>
                            <Input type="time" />
                        </Form.Item>
                        <Form.Item name={["schedule", "timezone"]} label={res.timezone} rules={[{ required: true }]}>
                            <Input />
                        </Form.Item>
                    </>
                )}
                {kind === "weekly" && (
                    <Form.Item name={["schedule", "weekdays"]} label={res.weekdays} rules={[{ required: true }]}>
                        <Select
                            mode="multiple"
                            options={res.weekLabels.map((label, index) => ({ label, value: index + 1 }))}
                        />
                    </Form.Item>
                )}
                <Typography.Paragraph type="secondary">{res.scheduleHelp}</Typography.Paragraph>
                {!!definition?.optionalActions.length && (
                    <Form.Item name="authorizedActions" label={res.optionalActions} extra={res.optionalActionsHelp}>
                        <Checkbox.Group
                            options={definition.optionalActions.map((action) => ({
                                value: action,
                                label:
                                    getRes().access.actions[
                                        action as keyof ReturnType<typeof getRes>["access"]["actions"]
                                    ] ?? action,
                                disabled: !hasAction(action),
                            }))}
                        />
                    </Form.Item>
                )}
                {definition?.requiresAuthorization && (
                    <>
                        <Alert type="info" title={res.authorizeHelp} style={{ marginBottom: 16 }} />
                        <Form.Item
                            name="authorized"
                            valuePropName="checked"
                            rules={[
                                {
                                    validator: (_, checked) =>
                                        checked
                                            ? Promise.resolve()
                                            : Promise.reject(new Error(res.authorizationRequired)),
                                },
                            ]}
                        >
                            <Checkbox>{res.authorize}</Checkbox>
                        </Form.Item>
                    </>
                )}
                <Form.Item name="enabled" label={res.enabled} valuePropName="checked">
                    <Switch />
                </Form.Item>
                <Space>
                    <Button type="primary" htmlType="submit" loading={busy} disabled={!definition?.available}>
                        {res.save}
                    </Button>
                    <Button onClick={onClose}>{res.close}</Button>
                </Space>
            </Form>
        </Drawer>
    );
}
