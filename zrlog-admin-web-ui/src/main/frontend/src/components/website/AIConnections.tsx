import { AutoComplete, Button, Collapse, Drawer, Form, Grid, List, Select, Space, Tabs, Typography, theme } from "antd";
import Input from "@zrlog/ui/antd/Input";
import Popconfirm from "@zrlog/ui/antd/Popconfirm";
import Tag from "@zrlog/ui/antd/Tag";
import AddIcon from "@zrlog/ui/icons/add";
import CloseIcon from "@zrlog/ui/icons/close";
import { useEffect, useId, useRef, useState } from "react";
import AIProviderIcon from "../../icons/AIProviderIcon";
import { AIProviderType } from "../../type";
import { getRes } from "../../utils/constants";
import type { AI, AIConnection, AIProvider } from "./index";

type ConnectionKind = AIConnection["ai_kind"];
type ConnectionFields = Pick<AIConnection, "ai_connection_name" | "ai_provider" | "ai_model" | "ai_base_url"> & {
    ai_api_key?: string;
};

export type AIConnectionMutation =
    | ({ action: "save"; ai_kind: ConnectionKind; ai_connection_id?: string } & ConnectionFields)
    | { action: "activate" | "delete"; ai_kind: ConnectionKind; ai_connection_id: string }
    | { action: "disableImage"; ai_connection_id: string };

const providerNames = new Map<string, string>([
    [AIProviderType.OPEN_AI, "OpenAI"],
    [AIProviderType.DEEP_SEEK, "DeepSeek"],
    [AIProviderType.QWEN, "Qwen"],
    [AIProviderType.GOOGLE_GEMINI, "Google Gemini"],
]);

const normalizeEndpoint = (url?: string) => {
    const value = url?.trim() ?? "";
    const query = value.indexOf("?");
    return query < 0 ? value.replace(/\/+$/, "") : value.slice(0, query).replace(/\/+$/, "") + value.slice(query);
};

const endpointHost = (address?: string) => {
    if (!address) return "";
    try {
        return new URL(address).host;
    } catch {
        return "";
    }
};

const validateServiceUrl = (_: unknown, value?: string) => {
    const address = value?.trim();
    if (!address) return Promise.resolve();
    try {
        const url = new URL(address);
        if (/^https?:\/\/[^/\s#][^\s#]*$/i.test(address) && !url.username && !url.password) {
            return Promise.resolve();
        }
    } catch {
        // Return the field validation message below.
    }
    return Promise.reject(new Error(getRes().websiteAi.aiBaseUrlInvalid));
};

function ProviderLabel({ provider }: { provider: string }) {
    const brand = Object.values(AIProviderType).find((entry) => entry === provider);
    return (
        <Space size={4}>
            <AIProviderIcon name={brand} />
            {providerNames.get(provider) ?? provider}
        </Space>
    );
}

function ConnectionEditor({
    connection,
    formId,
    image,
    disabled,
    providers,
    onSave,
}: {
    connection?: AIConnection;
    formId: string;
    image: boolean;
    disabled: boolean;
    providers: AIProvider[];
    onSave: (fields: ConnectionFields) => Promise<void>;
}) {
    const [form] = Form.useForm<ConnectionFields>();
    const res = getRes().websiteAi;
    const provider = Form.useWatch("ai_provider", form) ?? connection?.ai_provider;
    const baseUrl = Form.useWatch("ai_base_url", form) ?? connection?.ai_base_url;
    const endpoint = normalizeEndpoint(baseUrl);
    const preset = providers.find((entry) => entry.name === provider);
    const canKeepKey =
        Boolean(connection?.hasAiApiKey) &&
        connection?.ai_provider === provider &&
        normalizeEndpoint(connection?.ai_base_url) === endpoint;
    const modelOptions = preset?.models.map((model) => ({
        value: model,
        label: preset.modelEntries?.find((entry) => entry.name === model)?.retired ? (
            <Space wrap size="small">
                <span style={{ overflowWrap: "anywhere" }}>{model}</span>
                <Tag>{res.aiModelRetired}</Tag>
            </Space>
        ) : (
            model
        ),
    }));

    return (
        <Form<ConnectionFields>
            form={form}
            id={formId}
            layout="vertical"
            initialValues={{ ...connection, ai_api_key: "" }}
            disabled={disabled}
            onFinish={onSave}
            onValuesChange={(changed) => {
                if ("ai_provider" in changed) {
                    form.setFieldsValue({ ai_model: "", ai_base_url: "", ai_api_key: "" });
                } else if ("ai_base_url" in changed && normalizeEndpoint(changed.ai_base_url) !== endpoint) {
                    form.setFieldValue("ai_api_key", "");
                }
            }}
        >
            <Form.Item
                name="ai_connection_name"
                label={res.aiConnectionName}
                rules={[{ required: true, whitespace: true, message: res.aiConnectionNameRequired }]}
            >
                <Input maxLength={80} placeholder={res.aiConnectionNamePlaceholder} />
            </Form.Item>
            <Form.Item
                name="ai_provider"
                label={res.aiProvider}
                rules={[{ required: true, message: res.aiConnectionProviderRequired }]}
            >
                <Select
                    options={providers.map(({ name }) => ({ value: name, label: <ProviderLabel provider={name} /> }))}
                />
            </Form.Item>
            <Form.Item
                name="ai_model"
                label={res.aiModel}
                tooltip={image ? res.aiImageModelTip : undefined}
                dependencies={["ai_provider", "ai_base_url"]}
                rules={[
                    { required: true, whitespace: true, message: res.aiModelRequired },
                    ({ getFieldValue }) => ({
                        validator: (_, value?: string) => {
                            const selected = providers.find((entry) => entry.name === getFieldValue("ai_provider"));
                            if (
                                image &&
                                selected &&
                                value?.trim() &&
                                !normalizeEndpoint(getFieldValue("ai_base_url")) &&
                                !selected.models.includes(value.trim())
                            ) {
                                return Promise.reject(new Error(res.aiImageModelUnsupported));
                            }
                            return Promise.resolve();
                        },
                    }),
                ]}
            >
                <AutoComplete
                    options={modelOptions}
                    disabled={disabled || !provider}
                    placeholder={image ? res.aiImageModelPlaceholder : res.aiModelPlaceholder}
                    filterOption={(input, option) => Boolean(option?.value.toLowerCase().includes(input.toLowerCase()))}
                />
            </Form.Item>
            <Form.Item
                name="ai_api_key"
                label={res.aiApiKey}
                required={Boolean(provider) && !canKeepKey && !endpoint}
                tooltip={res.aiApiKeyTip}
                dependencies={["ai_provider", "ai_base_url"]}
                rules={[
                    ({ getFieldValue }) => ({
                        validator: (_, value?: string) => {
                            const selected = getFieldValue("ai_provider");
                            const url = normalizeEndpoint(getFieldValue("ai_base_url"));
                            if (
                                value?.trim() ||
                                url ||
                                !selected ||
                                (connection?.hasAiApiKey &&
                                    selected === connection.ai_provider &&
                                    !connection.ai_base_url)
                            ) {
                                return Promise.resolve();
                            }
                            return Promise.reject(new Error(res.aiConnectionKeyRequired));
                        },
                    }),
                ]}
            >
                <Input.Password
                    autoComplete="new-password"
                    placeholder={
                        canKeepKey
                            ? res.aiApiKeyConfiguredPlaceholder
                            : endpoint
                            ? res.aiApiKeyOptionalPlaceholder
                            : undefined
                    }
                />
            </Form.Item>
            <Collapse
                defaultActiveKey={connection?.ai_base_url ? ["endpoint"] : []}
                items={[
                    {
                        key: "endpoint",
                        label: res.aiConnectionCustomEndpoint,
                        forceRender: true,
                        children: (
                            <Form.Item
                                name="ai_base_url"
                                label={res.aiBaseUrl}
                                tooltip={image ? res.aiImageBaseUrlTip : res.aiBaseUrlTip}
                                rules={[{ validator: validateServiceUrl }]}
                                style={{ marginBottom: 0 }}
                            >
                                <Input placeholder={preset?.baseUrl} />
                            </Form.Item>
                        ),
                    },
                ]}
            />
        </Form>
    );
}

export function AIConnections({
    data,
    disabled,
    pending,
    onChange,
}: {
    data: AI;
    disabled: boolean;
    pending?: boolean;
    onChange: (request: AIConnectionMutation) => Promise<boolean>;
}) {
    const [kind, setKind] = useState<ConnectionKind>("text");
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [editing, setEditing] = useState<AIConnection>();
    const formId = useId();
    const tabsId = useId();
    const addButton = useRef<HTMLButtonElement>(null);
    const trigger = useRef<HTMLElement>();
    const { token } = theme.useToken();
    const screens = Grid.useBreakpoint();
    const { common, edit, websiteAi: res } = getRes();
    const image = kind === "image";
    const currentId = image ? data.ai_image_connection_id : data.ai_connection_id;
    const addLabel = image ? res.aiConnectionAddImage : res.aiConnectionAddText;
    const locked = disabled || Boolean(pending);

    useEffect(() => {
        const button = trigger.current;
        if (drawerOpen || !button) return;
        const frame = requestAnimationFrame(() => {
            // The drawer releases its focus trap after the closed state commits.
            const target = button.isConnected && !button.matches(":disabled") ? button : addButton.current;
            target?.focus({ preventScroll: true });
            trigger.current = undefined;
        });
        return () => cancelAnimationFrame(frame);
    }, [drawerOpen]);

    const open = (button: HTMLElement, connection?: AIConnection) => {
        trigger.current = button;
        setEditing(connection);
        setDrawerOpen(true);
    };

    const save = async (fields: ConnectionFields) => {
        if (locked) return;
        const saved = await onChange({
            action: "save",
            ai_kind: kind,
            ai_connection_id: editing?.ai_connection_id,
            ...fields,
            ai_connection_name: fields.ai_connection_name.trim(),
            ai_model: fields.ai_model.trim(),
            ai_base_url: fields.ai_base_url?.trim() ?? "",
            ai_api_key: fields.ai_api_key?.trim() ?? "",
        });
        if (saved) setDrawerOpen(false);
    };

    const remove = async (connection: AIConnection) => {
        const removed = await onChange({
            action: "delete",
            ai_kind: kind,
            ai_connection_id: connection.ai_connection_id,
        });
        if (removed) setDrawerOpen(false);
    };

    const addConnection = (
        <Button
            ref={addButton}
            type="primary"
            icon={<AddIcon />}
            disabled={locked}
            style={screens.sm ? undefined : { paddingInline: token.paddingXS }}
            onClick={(event) => open(event.currentTarget)}
        >
            {addLabel}
        </Button>
    );

    const list = (
        <List
            aria-label={res.aiConnections}
            dataSource={data.ai_connections?.filter((connection) => connection.ai_kind === kind) ?? []}
            locale={{ emptyText: image ? res.aiConnectionEmptyImage : res.aiConnectionEmptyText }}
            rowKey="ai_connection_id"
            renderItem={(connection) => {
                const current = connection.ai_connection_id === currentId;
                const details = [connection.ai_model, endpointHost(connection.ai_base_url)].filter(Boolean).join(" · ");
                return (
                    <List.Item style={{ flexWrap: "wrap", gap: token.marginXS, alignItems: "center" }}>
                        <div style={{ flex: "1 1 175px", minWidth: 0, overflowWrap: "anywhere" }}>
                            <Space align="center" wrap size={token.marginXS}>
                                <Typography.Text strong>{connection.ai_connection_name}</Typography.Text>
                                {current && <Tag color="success">{res.aiConnectionCurrent}</Tag>}
                            </Space>
                            <Typography.Paragraph type="secondary" style={{ margin: 0, marginTop: token.marginXXS }}>
                                <ProviderLabel provider={connection.ai_provider} />
                                {` · ${details}`}
                            </Typography.Paragraph>
                        </div>
                        <Space wrap size={token.marginXXS}>
                            <Button
                                type="text"
                                disabled={locked}
                                onClick={(event) => open(event.currentTarget, connection)}
                            >
                                {edit}
                            </Button>
                            {!current && (
                                <Button
                                    type="text"
                                    disabled={locked}
                                    onClick={() =>
                                        void onChange({
                                            action: "activate",
                                            ai_kind: kind,
                                            ai_connection_id: connection.ai_connection_id,
                                        })
                                    }
                                >
                                    {res.aiConnectionActivate}
                                </Button>
                            )}
                            {current && image && (
                                <Button
                                    type="text"
                                    disabled={locked}
                                    onClick={() =>
                                        void onChange({
                                            action: "disableImage",
                                            ai_connection_id: connection.ai_connection_id,
                                        })
                                    }
                                >
                                    {res.aiConnectionDisableImage}
                                </Button>
                            )}
                        </Space>
                    </List.Item>
                );
            }}
        />
    );
    return (
        <section aria-labelledby={tabsId}>
            <Typography.Title id={tabsId} level={5} style={{ marginBlock: 0 }}>
                {res.aiConnections}
            </Typography.Title>
            <Tabs
                activeKey={kind}
                onChange={(key) => setKind(key === "image" ? "image" : "text")}
                tabBarGutter={screens.sm ? undefined : token.marginSM}
                renderTabBar={(props, DefaultTabBar) => (
                    <div
                        style={{
                            display: "flex",
                            flexWrap: "wrap",
                            alignItems: "center",
                            justifyContent: "flex-end",
                            gap: token.marginXS,
                            marginBottom: token.marginXS,
                        }}
                    >
                        <DefaultTabBar
                            {...props}
                            style={{ margin: 0, flex: "1 0 max-content", minWidth: 0, maxWidth: "100%" }}
                        />
                        {addConnection}
                    </div>
                )}
                items={[
                    {
                        key: "text",
                        label: res.aiConnectionTextType,
                        children: kind === "text" ? list : undefined,
                    },
                    { key: "image", label: res.imageModel, children: kind === "image" ? list : undefined },
                ]}
            />
            <Drawer
                open={drawerOpen}
                title={editing ? res.aiConnectionEdit : addLabel}
                size={screens.sm ? 480 : "100%"}
                destroyOnHidden
                closable={{ placement: "end", closeIcon: <CloseIcon />, "aria-label": common.close }}
                onClose={() => !pending && setDrawerOpen(false)}
                focusable={{ focusTriggerAfterClose: false }}
                footer={
                    <div style={{ display: "flex", flexWrap: "wrap", justifyContent: "flex-end", gap: token.marginXS }}>
                        {editing && editing.ai_connection_id !== currentId && (
                            <Popconfirm
                                title={res.aiConnectionDeleteConfirm}
                                okText={common.delete}
                                cancelText={common.cancel}
                                okButtonProps={{ danger: true }}
                                onConfirm={() => remove(editing)}
                            >
                                <Button type="text" danger disabled={locked} style={{ marginInlineEnd: "auto" }}>
                                    {common.delete}
                                </Button>
                            </Popconfirm>
                        )}
                        <Button disabled={pending} onClick={() => setDrawerOpen(false)}>
                            {common.cancel}
                        </Button>
                        <Button type="primary" htmlType="submit" form={formId} loading={pending} disabled={disabled}>
                            {res.aiConnectionSave}
                        </Button>
                    </div>
                }
            >
                <ConnectionEditor
                    key={editing?.ai_connection_id ?? kind}
                    connection={editing}
                    formId={formId}
                    image={image}
                    providers={(image ? data.allImageProviders : data.allProviders) ?? []}
                    disabled={locked}
                    onSave={save}
                />
            </Drawer>
        </section>
    );
}

export default AIConnections;
