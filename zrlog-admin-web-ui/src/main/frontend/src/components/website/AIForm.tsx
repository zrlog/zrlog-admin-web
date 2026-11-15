import { Alert } from "antd";
import Tag from "@zrlog/ui/antd/Tag";
import Switch from "@zrlog/ui/antd/Switch";
import Input from "@zrlog/ui/antd/Input";
import InputNumber from "@zrlog/ui/antd/InputNumber";
import { useUiMessage } from "@zrlog/ui/feedback";
import AiWritingIcon from "@zrlog/ui/icons/ai-writing";
import EditIcon from "@zrlog/ui/icons/edit";
import Form from "antd/es/form";
import Button from "antd/es/button";

import { getRes, tryAppendBackendServerUrl } from "../../utils/constants";
import { useEffect, useRef, useState } from "react";

import { AI, AIConnection, AIProvider } from "./index";
import Select, { DefaultOptionType } from "antd/es/select";
import AutoComplete from "antd/es/auto-complete";

import AIIcon from "../../icons/AIProviderIcon";
import Editor from "@zrlog/editor/core";
import { getAppState } from "../../base/ConfigProviderApp";
import { Locale } from "@zrlog/editor/dist/editor/lang/editor-lang";
import { useAxiosBaseInstance } from "../../base/AppBase";
import Modal from "antd/es/modal";

import { markdownToHtmlSyncWithCallback } from "@zrlog/editor/dist/editor/utils/marked-utils";
import HtmlPreviewPanel from "@zrlog/editor/core/editor/html-preview-panel";
import { useTheme } from "antd-style";
import { useResponsiveFormLayout } from "../../utils/responsive-form";
import WebsiteSubmitBar from "./WebsiteSubmitBar";

const layout = {
    labelCol: { span: 8 },
    wrapperCol: { span: 16 },
};

const normalizeAiSettings = (settings: AI): AI => ({
    ...settings,
    ai_reasoning_enabled: settings.ai_reasoning_enabled ?? true,
    ai_connection_name:
        settings.ai_connections?.find((c) => c.ai_connection_id === settings.ai_connection_id)?.ai_connection_name ||
        "",
    ai_image_connection_name:
        settings.ai_connections?.find((c) => c.ai_connection_id === settings.ai_image_connection_id)
            ?.ai_connection_name || "",
});

const connectionFields = (connection?: AIConnection, image = false): Partial<AI> =>
    image
        ? {
              ai_image_connection_id: connection?.ai_connection_id || "",
              ai_image_connection_name: connection?.ai_connection_name || "",
              ai_image_provider: connection?.ai_provider,
              ai_image_model: connection?.ai_model || "",
              ai_image_base_url: connection?.ai_base_url || "",
              ai_image_api_key: "",
          }
        : {
              ai_connection_id: connection?.ai_connection_id || "",
              ai_connection_name: connection?.ai_connection_name || "",
              ai_provider: connection?.ai_provider || "",
              ai_model: connection?.ai_model || "",
              ai_base_url: connection?.ai_base_url || "",
              ai_api_key: "",
          };

const connectionDraft = (settings: AI, image: boolean): Partial<AI> =>
    Object.fromEntries(Object.keys(connectionFields(undefined, image)).map((key) => [key, settings[key as keyof AI]]));

const normalizedEndpoint = (url?: string) => {
    const value = (url || "").trim();
    const query = value.indexOf("?");
    return query < 0 ? value.replace(/\/+$/, "") : value.slice(0, query).replace(/\/+$/, "") + value.slice(query);
};

const validateAiServiceUrl = (_: unknown, value?: string) => {
    if (!value) {
        return Promise.resolve();
    }
    try {
        const url = new URL(value.trim());
        if ((url.protocol === "http:" || url.protocol === "https:") && !url.username && !url.password && !url.hash) {
            return Promise.resolve();
        }
    } catch (_e) {
        // Return the field validation message below.
    }
    return Promise.reject(new Error(getRes().websiteAi.aiBaseUrlInvalid));
};

const AIForm = ({
    data,
    offline,
    offlineData,
    onSubmit,
    loading,
}: {
    data: AI;
    offline: boolean;
    offlineData: boolean;
    onSubmit: (data: AI) => void;
    loading?: boolean;
}) => {
    const [state, setState] = useState<AI>(normalizeAiSettings(data));
    const [promptEditorOpen, setPromptEditorOpen] = useState(false);
    const [promptDraft, setPromptDraft] = useState(data.ai_prompt);
    const [promptHtml, setPromptHtml] = useState("");
    const [optimizingPrompt, setOptimizingPrompt] = useState(false);
    const [promptEditorRevision, setPromptEditorRevision] = useState(0);
    const [form] = Form.useForm();
    const connectionDrafts = useRef<Record<string, Partial<AI>>>({});
    const axiosInstance = useAxiosBaseInstance();
    const theme = useTheme();
    const border = `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorder}`;
    const [messageApi, contextHolder] = useUiMessage({ maxCount: 3 });
    const { formLayout, narrow } = useResponsiveFormLayout(layout);
    const selectedConnection = data.ai_connections?.find((c) => c.ai_connection_id === state.ai_connection_id);
    const selectedImageConnection = data.ai_connections?.find(
        (c) => c.ai_connection_id === state.ai_image_connection_id
    );
    const canReuseApiKey =
        Boolean(selectedConnection?.hasAiApiKey) &&
        selectedConnection?.ai_provider === state.ai_provider &&
        normalizedEndpoint(selectedConnection?.ai_base_url) === normalizedEndpoint(state.ai_base_url);
    const canReuseImageApiKey =
        Boolean(selectedImageConnection?.hasAiApiKey) &&
        selectedImageConnection?.ai_provider === state.ai_image_provider &&
        normalizedEndpoint(selectedImageConnection?.ai_base_url) === normalizedEndpoint(state.ai_image_base_url);
    const imageUsesTextEndpoint =
        Boolean(state.ai_image_provider) &&
        state.ai_image_provider === state.ai_provider &&
        normalizedEndpoint(state.ai_image_base_url) === normalizedEndpoint(state.ai_base_url);
    const canReuseTextApiKeyForImage = imageUsesTextEndpoint && (canReuseApiKey || Boolean(state.ai_api_key));
    const textModelSelectStyle = { width: 200, maxWidth: "100%" };
    const serviceUrlInputStyle = { width: 420, maxWidth: "100%" };
    const imageModelSelectStyle = { width: 260, maxWidth: "100%" };

    const rememberConnection = (image: boolean) => {
        const id = image ? state.ai_image_connection_id : state.ai_connection_id;
        const provider = image ? state.ai_image_provider : state.ai_provider;
        const draft = connectionDraft(state, image);
        if (id) connectionDrafts.current[id] = draft;
        if (provider) connectionDrafts.current[`${image}:provider:${provider}`] = draft;
    };

    const selectConnection = (id: string, image: boolean) => {
        rememberConnection(image);
        const connection = data.ai_connections?.find((c) => c.ai_connection_id === id);
        const fields = connectionDrafts.current[id] || connectionFields(connection, image);
        setState((current) => ({ ...current, ...fields }));
        form.setFieldsValue(fields);
    };

    const renderConnections = (image: boolean) => (
        <>
            <Form.Item
                label={image ? getRes().websiteAi.aiImageConnection : getRes().websiteAi.aiConnection}
                extra={getRes().websiteAi.aiConnectionTip}
            >
                <Select
                    aria-label={image ? getRes().websiteAi.aiImageConnection : getRes().websiteAi.aiConnection}
                    style={serviceUrlInputStyle}
                    value={(image ? state.ai_image_connection_id : state.ai_connection_id) || "new"}
                    onChange={(id) => selectConnection(id, image)}
                    options={[
                        ...(data.ai_connections || [])
                            .filter((c) => c.ai_kind === (image ? "image" : "text"))
                            .map((c) => ({
                                value: c.ai_connection_id,
                                label: `${c.ai_connection_name} · ${c.ai_model}`,
                            })),
                        { value: "new", label: getRes().websiteAi.aiConnectionNew },
                    ]}
                />
            </Form.Item>
            <Form.Item
                name={image ? "ai_image_connection_name" : "ai_connection_name"}
                label={getRes().websiteAi.aiConnectionName}
            >
                <Input
                    style={serviceUrlInputStyle}
                    maxLength={80}
                    placeholder={getRes().websiteAi.aiConnectionNamePlaceholder}
                />
            </Form.Item>
        </>
    );
    const getProviderModelOptions = (provider?: AIProvider): DefaultOptionType[] =>
        (provider?.models || []).map((name) => {
            const retired = provider?.modelEntries?.find((model) => model.name === name)?.retired;
            return {
                label: retired ? (
                    <span style={{ display: "flex", alignItems: "center", gap: theme.marginXS }}>
                        <span style={{ minWidth: 0, whiteSpace: "normal", overflowWrap: "anywhere" }}>{name}</span>
                        <Tag style={{ marginInlineEnd: 0, flexShrink: 0 }}>{getRes().websiteAi.aiModelRetired}</Tag>
                    </span>
                ) : (
                    name
                ),
                value: name,
            };
        });

    const getModelOptions = () =>
        getProviderModelOptions((data.allProviders || []).find((provider) => provider.name === state.ai_provider));

    const getImageModelOptions = () =>
        getProviderModelOptions(
            (data.allImageProviders || []).find((provider) => provider.name === state.ai_image_provider)
        );

    const getAiProviderOptions = (): DefaultOptionType[] => {
        return (data.allProviders || []).map((e) => {
            return {
                label: (
                    <div style={{ display: "flex", alignItems: "center", gap: 4 }}>
                        <AIIcon name={e.name} />
                        {e.name.toLowerCase().replace("_", "")}
                    </div>
                ),
                value: e.name,
            } as DefaultOptionType;
        });
    };

    const getSelectedProvider = () => {
        return (data.allProviders || []).find((provider) => provider.name === state.ai_provider);
    };

    const getSelectedImageProvider = () => {
        return (data.allImageProviders || []).find((provider) => provider.name === state.ai_image_provider);
    };

    const getAiImageProviderOptions = (): DefaultOptionType[] => {
        return (data.allImageProviders || []).map((e) => {
            return {
                label: (
                    <div style={{ display: "flex", alignItems: "center", gap: 4 }}>
                        <AIIcon name={e.name} />
                        {e.name.toLowerCase().replace("_", "")}
                    </div>
                ),
                value: e.name,
            } as DefaultOptionType;
        });
    };

    useEffect(() => {
        const normalizedData = normalizeAiSettings(data);
        connectionDrafts.current = {};
        setState(normalizedData);
        setPromptDraft(data.ai_prompt);
        form.setFieldsValue({
            ...normalizedData,
            ai_api_key: "",
            ai_image_api_key: "",
        });
    }, [data]);

    useEffect(() => {
        const html = markdownToHtmlSyncWithCallback(state.ai_prompt || "", (htmlStr) => {
            setPromptHtml(htmlStr);
        });
        setPromptHtml(html);
    }, [state.ai_prompt]);

    const commitPromptDraft = () => {
        const nextState = {
            ...state,
            ai_prompt: promptDraft,
        };
        setState(nextState);
        form.setFieldsValue({
            ai_prompt: promptDraft,
        });
        setPromptEditorOpen(false);
    };

    const openPromptEditor = () => {
        if (offline || offlineData) {
            return;
        }
        setPromptDraft(state.ai_prompt || "");
        setPromptEditorRevision((revision) => revision + 1);
        setPromptEditorOpen(true);
    };

    const optimizePromptDraft = async () => {
        try {
            setOptimizingPrompt(true);
            const { data } = await axiosInstance.post("/api/admin/website/ai/prompt/optimize", {
                prompt: promptDraft,
            });
            if (data.error) {
                await messageApi.error(data.message);
                return;
            }
            setPromptDraft(data.data.prompt);
            setPromptEditorRevision((revision) => revision + 1);
            await messageApi.success(getRes().websiteAi.aiPromptOptimizeSuccess);
        } catch (e) {
            await messageApi.error(e instanceof Error ? e.message : getRes().error.unknown);
        } finally {
            setOptimizingPrompt(false);
        }
    };

    return (
        <>
            {contextHolder}
            <Form
                form={form}
                {...formLayout}
                disabled={offline || offlineData}
                initialValues={data}
                onValuesChange={(changedValues, values) => {
                    let nextValues: AI = {
                        ...state,
                        ...values,
                    };
                    for (const image of [false, true]) {
                        const providerKey = image ? "ai_image_provider" : "ai_provider";
                        const urlKey = image ? "ai_image_base_url" : "ai_base_url";
                        const apiKey = image ? "ai_image_api_key" : "ai_api_key";
                        if (Object.prototype.hasOwnProperty.call(changedValues, providerKey)) {
                            rememberConnection(image);
                            const provider = values[providerKey];
                            // A blank form starts a new connection; changing an existing provider
                            // restores its draft or saved connection without carrying credentials.
                            const saved =
                                state[providerKey] && provider
                                    ? connectionDrafts.current[`${image}:provider:${provider}`] ||
                                      connectionFields(
                                          data.ai_connections?.find(
                                              (c) =>
                                                  c.ai_kind === (image ? "image" : "text") && c.ai_provider === provider
                                          ),
                                          image
                                      )
                                    : connectionFields(undefined, image);
                            const nameKey = image ? "ai_image_connection_name" : "ai_connection_name";
                            const fields = {
                                ...saved,
                                [providerKey]: provider,
                                ...(!state[providerKey] ? { [nameKey]: values[nameKey] } : {}),
                            };
                            nextValues = { ...nextValues, ...fields };
                            form.setFieldsValue(fields);
                        } else if (
                            Object.prototype.hasOwnProperty.call(changedValues, urlKey) &&
                            normalizedEndpoint(values[urlKey]) !== normalizedEndpoint(state[urlKey])
                        ) {
                            nextValues = { ...nextValues, [apiKey]: "" };
                            form.setFieldsValue({ [apiKey]: "" });
                        }
                    }
                    setState(nextValues);
                }}
                onFinish={(nv) =>
                    onSubmit({
                        ...state,
                        ...nv,
                        ai_max_completion_tokens: nv.ai_max_completion_tokens ?? null,
                        ai_reasoning_enabled: nv.ai_reasoning_enabled ?? true,
                    })
                }
            >
                <Alert
                    type="info"
                    showIcon
                    message={getRes().websiteAi.privacyBoundaryTitle}
                    description={getRes().websiteAi.privacyBoundaryDesc}
                    style={{ marginBottom: theme.marginMD }}
                />
                <div
                    style={{
                        color: theme.colorText,
                        fontSize: theme.fontSizeLG,
                        fontWeight: 600,
                        margin: "24px 0 16px 0",
                    }}
                >
                    {getRes().websiteAi.textModel}
                </div>
                {renderConnections(false)}
                <Form.Item name="ai_provider" label={getRes().websiteAi.aiProvider} required={true}>
                    <Select style={textModelSelectStyle} options={getAiProviderOptions()} />
                </Form.Item>
                <Form.Item
                    name={"ai_model"}
                    label={getRes().websiteAi.aiModel}
                    rules={[{ required: true, whitespace: true, message: getRes().websiteAi.aiModelRequired }]}
                >
                    <AutoComplete
                        style={textModelSelectStyle}
                        options={getModelOptions()}
                        placeholder={getRes().websiteAi.aiModelPlaceholder}
                    />
                </Form.Item>
                <Form.Item
                    name={"ai_base_url"}
                    label={getRes().websiteAi.aiBaseUrl}
                    tooltip={getRes().websiteAi.aiBaseUrlTip}
                    rules={[{ validator: validateAiServiceUrl }]}
                >
                    <Input style={serviceUrlInputStyle} placeholder={getSelectedProvider()?.baseUrl} />
                </Form.Item>
                <Form.Item
                    name={"ai_max_completion_tokens"}
                    label={getRes().websiteAi.aiMaxCompletionTokens}
                    tooltip={getRes().websiteAi.aiMaxCompletionTokensTip}
                >
                    <InputNumber
                        min={1}
                        precision={0}
                        style={textModelSelectStyle}
                        placeholder={getRes().websiteAi.aiMaxCompletionTokensPlaceholder}
                    />
                </Form.Item>
                <Form.Item
                    valuePropName="checked"
                    name={"ai_reasoning_enabled"}
                    label={getRes().websiteAi.aiReasoningEnabled}
                    tooltip={getRes().websiteAi.aiReasoningEnabledTip}
                >
                    <Switch />
                </Form.Item>
                <Form.Item
                    name={"ai_api_key"}
                    label={getRes().websiteAi.aiApiKey}
                    tooltip={getRes().websiteAi.aiApiKeyTip}
                    required={!state.ai_base_url && !canReuseApiKey}
                >
                    <Input.Password
                        autoComplete="new-password"
                        placeholder={
                            canReuseApiKey
                                ? getRes().websiteAi.aiApiKeyConfiguredPlaceholder
                                : state.ai_base_url
                                ? getRes().websiteAi.aiApiKeyOptionalPlaceholder
                                : undefined
                        }
                    />
                </Form.Item>
                <div
                    style={{
                        color: theme.colorText,
                        fontSize: theme.fontSizeLG,
                        fontWeight: 600,
                        margin: "24px 0 16px 0",
                    }}
                >
                    {getRes().websiteAi.imageModel}
                </div>
                {renderConnections(true)}
                <Form.Item
                    name="ai_image_provider"
                    label={getRes().websiteAi.aiImageProvider}
                    tooltip={getRes().websiteAi.aiImageProviderTip}
                >
                    <Select allowClear={true} style={imageModelSelectStyle} options={getAiImageProviderOptions()} />
                </Form.Item>
                <Form.Item
                    name={"ai_image_model"}
                    label={getRes().websiteAi.aiImageModel}
                    tooltip={getRes().websiteAi.aiImageModelTip}
                    dependencies={["ai_image_provider", "ai_image_base_url"]}
                    rules={[
                        {
                            validator: (_, value?: string) => {
                                const provider = form.getFieldValue("ai_image_provider");
                                if (!provider) {
                                    return Promise.resolve();
                                }
                                const model = value?.trim();
                                if (!model) {
                                    return Promise.reject(new Error(getRes().websiteAi.aiImageModelRequired));
                                }
                                const customBaseUrl = form.getFieldValue("ai_image_base_url");
                                const knownModels =
                                    (data.allImageProviders || []).find((item) => item.name === provider)?.models || [];
                                if (!customBaseUrl && !knownModels.includes(model)) {
                                    return Promise.reject(new Error(getRes().websiteAi.aiImageModelUnsupported));
                                }
                                return Promise.resolve();
                            },
                        },
                    ]}
                >
                    <AutoComplete
                        disabled={offline || offlineData || !state.ai_image_provider}
                        style={imageModelSelectStyle}
                        options={getImageModelOptions()}
                        placeholder={getRes().websiteAi.aiImageModelPlaceholder}
                    />
                </Form.Item>
                <Form.Item
                    name={"ai_image_base_url"}
                    label={getRes().websiteAi.aiImageBaseUrl}
                    tooltip={getRes().websiteAi.aiImageBaseUrlTip}
                    rules={[{ validator: validateAiServiceUrl }]}
                >
                    <Input
                        disabled={offline || offlineData || !state.ai_image_provider}
                        style={serviceUrlInputStyle}
                        placeholder={getSelectedImageProvider()?.baseUrl}
                    />
                </Form.Item>
                <Form.Item
                    name={"ai_image_api_key"}
                    label={getRes().websiteAi.aiImageApiKey}
                    tooltip={getRes().websiteAi.aiImageApiKeyTip}
                    required={
                        Boolean(state.ai_image_provider) &&
                        !state.ai_image_base_url &&
                        !canReuseImageApiKey &&
                        !canReuseTextApiKeyForImage
                    }
                >
                    <Input.Password
                        autoComplete="new-password"
                        placeholder={
                            canReuseImageApiKey
                                ? getRes().websiteAi.aiApiKeyConfiguredPlaceholder
                                : state.ai_image_base_url
                                ? getRes().websiteAi.aiApiKeyOptionalPlaceholder
                                : canReuseTextApiKeyForImage
                                ? getRes().websiteAi.aiImageApiKeyPlaceholder
                                : undefined
                        }
                    />
                </Form.Item>
                <div
                    style={{
                        color: theme.colorText,
                        fontSize: theme.fontSizeLG,
                        fontWeight: 600,
                        margin: "24px 0 16px 0",
                    }}
                >
                    {getRes().websiteAi.globalPrompt}
                </div>
                <Form.Item label={getRes().websiteAi.aiPrompt} tooltip={getRes().websiteAi.aiPromptTip}>
                    <Form.Item name={"ai_prompt"} hidden={true} noStyle={true}>
                        <Input />
                    </Form.Item>
                    <div
                        style={{
                            minHeight: 120,
                            maxHeight: 160,
                            overflow: "auto",
                            border,
                            borderRadius: theme.borderRadius,
                            padding: 12,
                            background: theme.colorBgContainer,
                            cursor: offline || offlineData ? "not-allowed" : "pointer",
                        }}
                        onClick={() => {
                            if (!offline && !offlineData) {
                                openPromptEditor();
                            }
                        }}
                    >
                        {state.ai_prompt ? (
                            <HtmlPreviewPanel
                                htmlContent={promptHtml}
                                dark={getAppState().dark}
                                style={{
                                    overflow: "visible",
                                }}
                            />
                        ) : (
                            <div style={{ color: theme.colorTextTertiary }}>{getRes().websiteAi.aiPromptEmpty}</div>
                        )}
                    </div>
                    <Button
                        type="link"
                        icon={<EditIcon />}
                        style={{ padding: 0, height: "auto", marginTop: 4, color: getAppState().colorPrimary }}
                        disabled={offline || offlineData}
                        onClick={openPromptEditor}
                    >
                        {getRes().websiteAi.editAiPrompt}
                    </Button>
                </Form.Item>

                <WebsiteSubmitBar loading={loading} disabled={offline || offlineData} />
            </Form>
            <Modal
                title={getRes().websiteAi.editAiPrompt}
                open={promptEditorOpen && !offline && !offlineData}
                width={narrow ? "calc(100vw - 32px)" : 860}
                style={{ top: narrow ? 12 : 24 }}
                onOk={commitPromptDraft}
                onCancel={() => setPromptEditorOpen(false)}
                footer={(_, { OkBtn, CancelBtn }) => (
                    <div
                        style={{
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "space-between",
                            gap: theme.marginSM,
                        }}
                    >
                        <Button
                            type="link"
                            icon={<AiWritingIcon />}
                            loading={optimizingPrompt}
                            onClick={optimizePromptDraft}
                            style={{ paddingInline: 0, color: getAppState().colorPrimary }}
                        >
                            {getRes().websiteAi.optimizeAiPrompt}
                        </Button>
                        <div style={{ display: "flex", gap: theme.marginXS }}>
                            <CancelBtn />
                            <OkBtn />
                        </div>
                    </div>
                )}
            >
                <Editor
                    key={promptEditorRevision}
                    height={narrow ? "calc(100vh - 214px)" : "calc(100vh - 238px)"}
                    fullscreen={false}
                    previewContent=""
                    value={promptDraft}
                    axiosInstance={axiosInstance}
                    config={{
                        lang: getAppState().lang as Locale,
                        dark: getAppState().dark,
                        colorPrimary: getAppState().colorPrimary,
                        preview: false,
                        disableToolbar: true,
                        disableStatusBar: true,
                        uploadConfig: {
                            buildUploadUrl: (type: string) => {
                                return "/api/admin/upload?dir=" + type;
                            },
                            axiosInstance: axiosInstance,
                            formName: "imgFile",
                            tryAppendBackendServerUrl: tryAppendBackendServerUrl,
                        },
                    }}
                    onChange={(v) => {
                        setPromptDraft(v.value);
                    }}
                />
            </Modal>
        </>
    );
};

export default AIForm;
