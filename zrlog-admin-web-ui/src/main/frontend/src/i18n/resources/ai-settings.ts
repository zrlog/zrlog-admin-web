import type { I18nShape } from "../types";

export const zhCN = {
    websiteAi: {
        title: "AI 设置",
        summary: "配置全站共用的 AI 提供方、模型、接口和提示词",
        privacyBoundaryTitle: "外部 AI 请求边界",
        privacyBoundaryDesc:
            "文章助手、封面生成和提示词建议会把本次请求所需的内容发送给所选 AI 提供方，可能包含对话、文章上下文和经授权读取的数据；API Key 只在服务端使用，不会写入 AI 对话或导出文件",
        aiApiKey: "API Key",
        aiApiKeyTip: "用于服务端请求 AI 提供方接口。自定义 Base URL 的服务不需要鉴权时可留空",
        aiApiKeyConfiguredPlaceholder: "已设置，留空表示不修改",
        aiApiKeyOptionalPlaceholder: "可选，留空时不发送 Authorization 请求头",
        aiProvider: "提供方",
        aiModel: "模型",
        aiModelPlaceholder: "选择或输入模型名称",
        aiModelRequired: "请选择或输入模型名称",
        aiModelRetired: "已下线",
        aiBaseUrl: "Base URL",
        aiBaseUrlTip: "OpenAI 兼容服务的基础地址，不包含 /chat/completions。留空时使用所选提供方的默认地址",
        aiBaseUrlInvalid: "请输入完整的 HTTP(S) Base URL",
        aiMaxCompletionTokens: "最大输出 token 数",
        aiMaxCompletionTokensPlaceholder: "使用提供方默认值",
        aiMaxCompletionTokensTip: "限制单次文本回复最多生成的 token 数。留空时使用所选提供方的默认值",
        aiReasoningEnabled: "推理过程",
        aiReasoningEnabledTip:
            "开启后会请求并展示模型提供的推理内容，OpenAI 返回推理摘要；关闭后不会保存或展示这些内容",
        textModel: "文本对话模型",
        imageModel: "图片模型",
        globalPrompt: "默认提示词",
        aiImageProvider: "图片提供方",
        aiImageProviderTip: "用于生成文章封面等图片内容，请选择支持图片生成的提供方",
        aiImageModel: "图片模型",
        aiImageModelTip: "默认地址只允许已知支持图片生成的模型；自定义 Base URL 时可输入兼容服务的模型名称",
        aiImageModelPlaceholder: "选择或输入图片模型名称",
        aiImageModelRequired: "请选择或输入图片模型名称",
        aiImageModelUnsupported: "默认地址不支持该图片模型，请选择已知模型或配置自定义图片 Base URL",
        aiImageBaseUrl: "图片 Base URL",
        aiImageBaseUrlTip: "OpenAI 兼容图片服务的基础地址，不包含 /images/generations。留空时使用所选提供方的默认地址",
        aiImageApiKey: "图片 API Key",
        aiImageApiKeyTip:
            "提供方和地址均未变更时，留空可保留已保存的图片密钥；与文本配置的提供方和地址均相同时，也可复用文本密钥。自定义图片 Base URL 不需要鉴权时可留空",
        aiImageApiKeyPlaceholder: "相同提供方和地址可复用已保存的密钥",
        aiPrompt: "提示词",
        aiPromptEmpty: "暂无提示词，点击编辑",
        editAiPrompt: "编辑提示词",
        optimizeAiPrompt: "生成提示词建议",
        aiPromptOptimizeSuccess: "已生成提示词建议，请确认后应用",
        aiPromptTip: "作为默认提示词参与请求，用于约束输出风格、语气和格式",
    },
} as const;

export const enUS: I18nShape<typeof zhCN> = {
    websiteAi: {
        title: "AI settings",
        summary: "Configure site-wide AI providers, models, endpoints and prompts",
        privacyBoundaryTitle: "External AI request boundary",
        privacyBoundaryDesc:
            "The article assistant, cover generation, and prompt suggestions send the content needed for each request to the selected AI provider. This may include conversations, article context, and data read with authorization. API keys are only used on the server and are not written to AI chat or export files",
        aiApiKey: "API Key",
        aiApiKeyTip:
            "Used by the server to call the selected AI provider. Optional when a custom Base URL does not require authentication",
        aiApiKeyConfiguredPlaceholder: "Already set. Leave blank to keep it unchanged",
        aiApiKeyOptionalPlaceholder: "Optional. No Authorization header is sent when left blank",
        aiProvider: "Provider",
        aiModel: "Model",
        aiModelPlaceholder: "Select or enter a model name",
        aiModelRequired: "Select or enter a model name",
        aiModelRetired: "Retired",
        aiBaseUrl: "Base URL",
        aiBaseUrlTip:
            "Base URL of an OpenAI-compatible service, excluding /chat/completions. Leave blank to use the selected provider default",
        aiBaseUrlInvalid: "Enter a complete HTTP(S) Base URL",
        aiMaxCompletionTokens: "Max Output Tokens",
        aiMaxCompletionTokensPlaceholder: "Use provider default",
        aiMaxCompletionTokensTip:
            "Limits the maximum tokens generated for each text response. Leave blank to use the selected provider default",
        aiReasoningEnabled: "Reasoning Process",
        aiReasoningEnabledTip:
            "When enabled, requests and shows reasoning provided by the model. OpenAI returns reasoning summaries. When disabled, this content is not stored or shown",
        textModel: "Text Chat Model",
        imageModel: "Image Model",
        globalPrompt: "Default Prompt",
        aiImageProvider: "Image Provider",
        aiImageProviderTip:
            "Used for generating article covers and other images. Choose a provider that supports image generation",
        aiImageModel: "Image Model",
        aiImageModelTip:
            "Default endpoints only allow known image-generation models. Enter a compatible service model when using a custom Base URL",
        aiImageModelPlaceholder: "Select or enter an image model name",
        aiImageModelRequired: "Select or enter an image model name",
        aiImageModelUnsupported:
            "The default endpoint does not support this image model. Select a known model or configure a custom image Base URL",
        aiImageBaseUrl: "Image Base URL",
        aiImageBaseUrlTip:
            "Base URL of an OpenAI-compatible image service, excluding /images/generations. Leave blank to use the selected provider default",
        aiImageApiKey: "Image API Key",
        aiImageApiKeyTip:
            "Leave blank to keep the saved image key when its provider and endpoint are unchanged. The text key can also be reused when both the provider and endpoint match the text configuration. Optional when a custom image Base URL does not require authentication",
        aiImageApiKeyPlaceholder: "Reuse a saved key for the same provider and endpoint",
        aiPrompt: "Prompt",
        aiPromptEmpty: "No prompt yet. Click to edit",
        editAiPrompt: "Edit Prompt",
        optimizeAiPrompt: "Generate Prompt Suggestion",
        aiPromptOptimizeSuccess: "A prompt suggestion was generated. Review it before applying",
        aiPromptTip: "Used as the default prompt context to constrain output style, tone, and format",
    },
};
