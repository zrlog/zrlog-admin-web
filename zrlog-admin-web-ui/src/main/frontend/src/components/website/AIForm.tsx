import { Alert } from "antd";
import Switch from "@zrlog/ui/antd/Switch";
import Input from "@zrlog/ui/antd/Input";
import InputNumber from "@zrlog/ui/antd/InputNumber";
import { useUiMessage } from "@zrlog/ui/feedback";
import AiWritingIcon from "@zrlog/ui/icons/ai-writing";
import EditIcon from "@zrlog/ui/icons/edit";
import Form from "antd/es/form";
import Button from "antd/es/button";
import Modal from "antd/es/modal";
import { useTheme } from "antd-style";
import { useEffect, useState } from "react";
import Editor from "@zrlog/editor/core";
import HtmlPreviewPanel from "@zrlog/editor/core/editor/html-preview-panel";
import { Locale } from "@zrlog/editor/dist/editor/lang/editor-lang";
import { markdownToHtmlSyncWithCallback } from "@zrlog/editor/dist/editor/utils/marked-utils";

import { getAppState } from "../../base/ConfigProviderApp";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getRes, tryAppendBackendServerUrl } from "../../utils/constants";
import { useResponsiveFormLayout } from "../../utils/responsive-form";
import type { AI } from "./index";
import AIConnections from "./AIConnections";
import type { AIConnectionMutation } from "./AIConnections";
import WebsiteSubmitBar from "./WebsiteSubmitBar";

const layout = {
    labelCol: { span: 8 },
    wrapperCol: { span: 16 },
};

export interface AIGlobalSettings {
    ai_prompt: string;
    ai_max_completion_tokens: number | null;
    ai_reasoning_enabled: boolean;
}

const AIForm = ({
    data,
    offline,
    offlineData,
    onSubmit,
    onConnectionChange,
    loading,
}: {
    data: AI;
    offline: boolean;
    offlineData: boolean;
    onSubmit: (settings: AIGlobalSettings) => void;
    onConnectionChange: (request: AIConnectionMutation) => Promise<boolean>;
    loading?: boolean;
}) => {
    const res = getRes().websiteAi;
    const disabled = offline || offlineData;
    const savedPrompt = data.ai_prompt ?? "";
    const savedTokenLimit = data.ai_max_completion_tokens ?? null;
    const savedReasoning = data.ai_reasoning_enabled ?? true;
    const [form] = Form.useForm<AIGlobalSettings>();
    const prompt = Form.useWatch("ai_prompt", form) ?? savedPrompt;
    const [promptEditorOpen, setPromptEditorOpen] = useState(false);
    const [promptDraft, setPromptDraft] = useState(savedPrompt);
    const [promptHtml, setPromptHtml] = useState("");
    const [optimizingPrompt, setOptimizingPrompt] = useState(false);
    const [promptEditorRevision, setPromptEditorRevision] = useState(0);
    const axiosInstance = useAxiosBaseInstance();
    const theme = useTheme();
    const [messageApi, contextHolder] = useUiMessage({ maxCount: 3 });
    const { formLayout, narrow } = useResponsiveFormLayout(layout);

    useEffect(() => {
        form.setFieldsValue({
            ai_prompt: savedPrompt,
            ai_max_completion_tokens: savedTokenLimit,
            ai_reasoning_enabled: savedReasoning,
        });
    }, [form, savedPrompt, savedTokenLimit, savedReasoning]);

    useEffect(() => {
        let current = true;
        const html = markdownToHtmlSyncWithCallback(prompt, (updatedHtml) => {
            if (current) setPromptHtml(updatedHtml);
        });
        setPromptHtml(html);
        return () => {
            current = false;
        };
    }, [prompt]);

    const openPromptEditor = () => {
        if (disabled || loading) return;
        setPromptDraft(prompt);
        setPromptEditorRevision((revision) => revision + 1);
        setPromptEditorOpen(true);
    };

    const commitPromptDraft = () => {
        form.setFieldValue("ai_prompt", promptDraft);
        setPromptEditorOpen(false);
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
            await messageApi.success(res.aiPromptOptimizeSuccess);
        } catch (e) {
            await messageApi.error(e instanceof Error ? e.message : getRes().error.unknown);
        } finally {
            setOptimizingPrompt(false);
        }
    };

    return (
        <>
            {contextHolder}
            <Alert
                type="info"
                showIcon
                title={res.privacyBoundaryTitle}
                description={res.privacyBoundaryDesc}
                style={{ marginBottom: theme.marginLG }}
            />
            <AIConnections data={data} disabled={disabled} pending={loading} onChange={onConnectionChange} />

            <Form
                form={form}
                {...formLayout}
                disabled={disabled || loading}
                initialValues={{
                    ai_prompt: savedPrompt,
                    ai_max_completion_tokens: savedTokenLimit,
                    ai_reasoning_enabled: savedReasoning,
                }}
                onFinish={(values) =>
                    onSubmit({
                        ai_prompt: values.ai_prompt ?? "",
                        ai_max_completion_tokens: values.ai_max_completion_tokens ?? null,
                        ai_reasoning_enabled: values.ai_reasoning_enabled ?? true,
                    })
                }
            >
                <h3
                    style={{
                        color: theme.colorText,
                        fontSize: theme.fontSizeLG,
                        fontWeight: 600,
                        margin: "24px 0 16px",
                    }}
                >
                    {res.globalSettings}
                </h3>
                <Form.Item
                    name="ai_max_completion_tokens"
                    label={res.aiMaxCompletionTokens}
                    tooltip={res.aiMaxCompletionTokensTip}
                >
                    <InputNumber
                        min={1}
                        precision={0}
                        style={{ width: 200, maxWidth: "100%" }}
                        placeholder={res.aiMaxCompletionTokensPlaceholder}
                    />
                </Form.Item>
                <Form.Item
                    valuePropName="checked"
                    name="ai_reasoning_enabled"
                    label={res.aiReasoningEnabled}
                    tooltip={res.aiReasoningEnabledTip}
                >
                    <Switch />
                </Form.Item>
                <Form.Item label={res.globalPrompt} tooltip={res.aiPromptTip}>
                    <Form.Item name="ai_prompt" hidden noStyle>
                        <Input />
                    </Form.Item>
                    <div
                        style={{
                            minHeight: 120,
                            maxHeight: 160,
                            overflow: "auto",
                            border: `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorder}`,
                            borderRadius: theme.borderRadius,
                            padding: 12,
                            background: theme.colorBgContainer,
                            cursor: disabled || loading ? "not-allowed" : "pointer",
                        }}
                        onClick={openPromptEditor}
                    >
                        {prompt ? (
                            <HtmlPreviewPanel
                                htmlContent={promptHtml}
                                dark={getAppState().dark}
                                style={{ overflow: "visible" }}
                            />
                        ) : (
                            <div style={{ color: theme.colorTextTertiary }}>{res.aiPromptEmpty}</div>
                        )}
                    </div>
                    <Button
                        type="link"
                        icon={<EditIcon />}
                        style={{ padding: 0, height: "auto", marginTop: 4, color: getAppState().colorPrimary }}
                        onClick={openPromptEditor}
                    >
                        {res.editAiPrompt}
                    </Button>
                </Form.Item>
                <WebsiteSubmitBar loading={loading} disabled={disabled} label={getRes().common.save} />
            </Form>
            <Modal
                title={res.editAiPrompt}
                open={promptEditorOpen && !disabled}
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
                            {res.optimizeAiPrompt}
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
