import { FunctionComponent, useEffect, useState } from "react";
import { Button, Checkbox, Input, Popconfirm, Popover, Space, Tag, Typography } from "antd";
import {
    ArrowUpOutlined,
    DeleteOutlined,
    DownloadOutlined,
    FileTextOutlined,
    InfoCircleOutlined,
} from "@ant-design/icons";
import AIIcon from "@editor/dist/ai/AIIcon";
import { getEditorRes } from "@editor/dist/editor/lang/editor-lang";
import { getRes } from "../../../utils/constants";
import {
    ArticleAiRequestField,
    ArticleAiRequestFieldSelection,
    ArticleAiRequestPreview,
    AssistantTool,
    AssistantToolButton,
} from "./article-ai-assistant.types";
import {
    buildAssistantToolButtons,
    buildAssistantToolGroups,
    getAssistantToolContextPolicy,
    getAssistantToolLabel,
    getAssistantToolOutcomeDescription,
} from "./tool/article-ai-assistant-tools";
import ArticleAiAssistantSkillPanel from "./article-ai-assistant-skill-panel";

const { TextArea } = Input;
const REQUEST_PREVIEW_SNIPPET_LIMIT = 180;
const REQUEST_PREVIEW_REFERENCE_LIMIT = 3;
const REWRITE_MIN_MARKDOWN_LENGTH = 120;

type ArticleAiAssistantSkillContentProps = {
    aiProvider: any;
    disabled: boolean;
    loadingKey?: string;
    aiMessageCount: number;
    aiMessagesExporting: boolean;
    aiMessagesClearing: boolean;
    includeArticleContextInChat: boolean;
    toolFieldSelection: ArticleAiRequestFieldSelection;
    theme: any;
    selectedText?: string;
    requestPreview: ArticleAiRequestPreview;
    articleContextAvailable: boolean;
    articleContextAdded: boolean;
    articleContextAppending: boolean;
    onAddArticleContext: () => void;
    onExportAiMessages: () => void;
    onClearAiMessages: () => void;
    onIncludeArticleContextInChatChange: (value: boolean) => void;
    onToolFieldSelectionChange: (field: ArticleAiRequestField, selected: boolean) => void;
    onSubmit: (message: string, tool?: AssistantTool) => void;
};

const ArticleAiAssistantSkillContent: FunctionComponent<ArticleAiAssistantSkillContentProps> = ({
    aiProvider,
    disabled,
    loadingKey,
    aiMessageCount,
    aiMessagesExporting,
    aiMessagesClearing,
    includeArticleContextInChat,
    toolFieldSelection,
    theme,
    selectedText,
    requestPreview,
    articleContextAvailable,
    articleContextAdded,
    articleContextAppending,
    onAddArticleContext,
    onExportAiMessages,
    onClearAiMessages,
    onIncludeArticleContextInChatChange,
    onToolFieldSelectionChange,
    onSubmit,
}) => {
    const [input, setInput] = useState("");
    const [selectedTool, setSelectedTool] = useState<AssistantTool>();
    const [skillPanelOpen, setSkillPanelOpen] = useState(false);

    const assistantRes = getRes().articleEdit.assistant;
    const selectedTextValue = selectedText?.trim() || "";
    const rewriteDisabled = requestPreview.markdownLength < REWRITE_MIN_MARKDOWN_LENGTH;
    const toolButtons = buildAssistantToolButtons().map((tool) =>
        tool.key === "rewrite" && rewriteDisabled
            ? { ...tool, disabled: true, disabledReason: assistantRes.rewriteDraftRequired }
            : tool
    );
    const toolGroups = buildAssistantToolGroups();

    useEffect(() => {
        if (selectedTextValue && !input) {
            setInput(selectedTextValue);
        }
    }, [selectedTextValue, input]);

    const getSkillQuery = () => {
        const value = input.trimStart();
        if (!value.startsWith("/")) {
            return "";
        }
        return value.substring(1).split(/\s+/)[0].toLowerCase();
    };

    const getToolByCommand = (command: string) => toolButtons.find((tool) => tool.command === command);

    const filteredToolButtons = toolButtons.filter((tool) => {
        const query = getSkillQuery();
        if (!query) {
            return true;
        }
        return (
            tool.command.includes(query) ||
            tool.label.toLowerCase().includes(query) ||
            tool.prompt.toLowerCase().includes(query)
        );
    });

    const parseSkillCommand = (messageInput: string) => {
        const normalizedInput = messageInput.trim();
        if (!normalizedInput.startsWith("/")) {
            return undefined;
        }
        const content = normalizedInput.substring(1);
        const firstSpaceIndex = content.search(/\s/);
        const command = (firstSpaceIndex >= 0 ? content.substring(0, firstSpaceIndex) : content).toLowerCase();
        const matchedTool = getToolByCommand(command);
        if (!matchedTool) {
            return undefined;
        }
        const nextInput = firstSpaceIndex >= 0 ? content.substring(firstSpaceIndex).trim() : "";
        return {
            tool: matchedTool.key,
            prompt: nextInput || matchedTool.prompt,
            disabled: matchedTool.disabled === true,
            disabledReason: matchedTool.disabledReason,
        };
    };

    const getEffectiveTool = () => selectedTool || parseSkillCommand(input)?.tool;

    const inputContainsOnlySelectedText = () => Boolean(selectedTextValue) && input.trim() === selectedTextValue;

    const buildInputPreviewSnippet = (value: string) => {
        const normalizedValue = value.trim().replace(/\s+/g, " ");
        if (normalizedValue.length <= REQUEST_PREVIEW_SNIPPET_LIMIT) {
            return normalizedValue;
        }
        return `${normalizedValue.substring(0, REQUEST_PREVIEW_SNIPPET_LIMIT)}...`;
    };

    const getEffectiveInput = () => {
        const parsedSkillCommand = parseSkillCommand(input);
        if (parsedSkillCommand) {
            return parsedSkillCommand.prompt;
        }
        if (selectedTool) {
            const matchedTool = toolButtons.find((tool) => tool.key === selectedTool);
            if (inputContainsOnlySelectedText()) {
                return matchedTool?.prompt || "";
            }
            return input.trim() || matchedTool?.prompt || "";
        }
        return input.trim();
    };

    const renderPreviewField = (label: string, length: number, snippet: string, field?: ArticleAiRequestField) => {
        const excluded = field ? !toolFieldSelection[field] : false;
        return (
            <Space key={label} direction="vertical" size={4} style={{ width: "100%" }}>
                <Space size={6} style={{ justifyContent: "space-between", width: "100%" }}>
                    {field ? (
                        <Checkbox
                            checked={!excluded}
                            onChange={(e) => onToolFieldSelectionChange(field, e.target.checked)}
                        >
                            {label}
                        </Checkbox>
                    ) : (
                        <Typography.Text>{label}</Typography.Text>
                    )}
                    <Typography.Text type="secondary">
                        {excluded
                            ? getRes().articleEdit.assistant.requestPreviewFieldExcluded
                            : `${length} ${getRes().articleEdit.assistant.requestPreviewChars}`}
                    </Typography.Text>
                </Space>
                <div
                    style={{
                        background: theme.colorFillTertiary,
                        border: `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`,
                        borderRadius: theme.borderRadius,
                        maxHeight: 88,
                        overflow: "hidden",
                        padding: "6px 8px",
                    }}
                >
                    <Typography.Text
                        type={snippet && !excluded ? undefined : "secondary"}
                        style={{ display: "block", fontSize: 12, whiteSpace: "pre-wrap", wordBreak: "break-word" }}
                    >
                        {excluded
                            ? getRes().articleEdit.assistant.requestPreviewFieldExcluded
                            : snippet || getRes().articleEdit.assistant.requestPreviewEmpty}
                    </Typography.Text>
                </div>
            </Space>
        );
    };

    const renderCountRow = (label: string, count: number) => (
        <Space key={label} size={6} style={{ justifyContent: "space-between", width: "100%" }}>
            <Typography.Text>{label}</Typography.Text>
            <Typography.Text type="secondary">
                {count} {getRes().articleEdit.assistant.requestPreviewItems}
            </Typography.Text>
        </Space>
    );

    const renderReferenceList = (references: string[]) => {
        const assistantRes = getRes().articleEdit.assistant;
        if (!references.length) {
            return (
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {assistantRes.requestPreviewNoReferences}
                </Typography.Text>
            );
        }
        const visibleReferences = references.slice(0, REQUEST_PREVIEW_REFERENCE_LIMIT);
        const restCount = references.length - visibleReferences.length;
        return (
            <Space direction="vertical" size={2} style={{ width: "100%" }}>
                {visibleReferences.map((reference) => (
                    <Typography.Text
                        code
                        ellipsis={{ tooltip: reference }}
                        key={reference}
                        style={{ maxWidth: "100%" }}
                    >
                        {reference}
                    </Typography.Text>
                ))}
                {restCount > 0 ? (
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        {assistantRes.requestPreviewMoreReferences.replace("{count}", `${restCount}`)}
                    </Typography.Text>
                ) : null}
            </Space>
        );
    };

    const renderReferenceSection = (label: string, count: number, references: string[]) => (
        <Space key={label} direction="vertical" size={4} style={{ width: "100%" }}>
            {renderCountRow(label, count)}
            {renderReferenceList(references)}
        </Space>
    );

    const getResourceSourceDescription = () => {
        if (!toolFieldSelection.markdown) {
            return assistantRes.requestPreviewResourceSourceExcluded;
        }
        if (requestPreview.markdownLength === 0) {
            return assistantRes.requestPreviewResourceSourceEmpty;
        }
        return assistantRes.requestPreviewResourceSourceBody;
    };

    const renderContextSourceSummary = () => (
        <Space direction="vertical" size={4} style={{ width: "100%" }}>
            <Typography.Text type="secondary">{assistantRes.requestPreviewContextSources}</Typography.Text>
            {renderCountRow(assistantRes.requestPreviewChatMessages, requestPreview.chatMessageCount)}
            {renderCountRow(assistantRes.requestPreviewToolMessages, requestPreview.toolMessageCount)}
            {renderCountRow(assistantRes.requestPreviewArticleSnapshots, requestPreview.articleContextMessageCount)}
            {renderCountRow(assistantRes.requestPreviewSystemPrompts, requestPreview.systemMessageCount)}
            {renderCountRow(assistantRes.requestPreviewErrorMessages, requestPreview.errorMessageCount)}
        </Space>
    );

    const getConversationContextHint = (tool?: AssistantTool) => {
        if (requestPreview.conversationMessageCount === 0) {
            return assistantRes.requestPreviewConversationEmpty;
        }
        if (!tool) {
            return assistantRes.requestPreviewConversationIncluded;
        }
        const policy = getAssistantToolContextPolicy(tool);
        if (policy === "chatOnly") {
            if (requestPreview.chatMessageCount === 0) {
                return assistantRes.requestPreviewToolContextChatOnlyEmpty;
            }
            return assistantRes.requestPreviewToolContextChatOnly;
        }
        if (policy === "none") {
            return assistantRes.requestPreviewToolContextNone;
        }
        return assistantRes.requestPreviewToolContextFull;
    };

    const renderRequestPreview = () => {
        const effectiveTool = getEffectiveTool();
        const effectiveInput = getEffectiveInput();
        const effectiveInputSnippet = buildInputPreviewSnippet(effectiveInput);
        return (
            <Space direction="vertical" size={8} style={{ maxHeight: 560, overflowY: "auto", width: 360 }}>
                <Typography.Text strong>{assistantRes.requestPreviewTitle}</Typography.Text>
                <Space size={6} wrap>
                    <Typography.Text type="secondary">{assistantRes.requestPreviewMode}</Typography.Text>
                    <Tag color={effectiveTool ? "processing" : "default"}>
                        {effectiveTool ? getAssistantToolLabel(effectiveTool) : assistantRes.requestPreviewChat}
                    </Tag>
                </Space>
                <Space direction="vertical" size={4} style={{ width: "100%" }}>
                    <Space size={6}>
                        <Typography.Text type="secondary">{assistantRes.requestPreviewProvider}</Typography.Text>
                        <Typography.Text>
                            {requestPreview.provider || assistantRes.requestPreviewNotConfigured}
                        </Typography.Text>
                    </Space>
                    <Space size={6}>
                        <Typography.Text type="secondary">{assistantRes.requestPreviewModel}</Typography.Text>
                        <Typography.Text>
                            {requestPreview.model || assistantRes.requestPreviewNotConfigured}
                        </Typography.Text>
                    </Space>
                </Space>
                <Typography.Text type="secondary">{assistantRes.requestPreviewWillSend}</Typography.Text>
                {effectiveTool ? (
                    <Space direction="vertical" size={4} style={{ width: "100%" }}>
                        <Space direction="vertical" size={2} style={{ width: "100%" }}>
                            <Typography.Text type="secondary">
                                {assistantRes.requestPreviewResultHandling}
                            </Typography.Text>
                            <Typography.Text>{getAssistantToolOutcomeDescription(effectiveTool)}</Typography.Text>
                        </Space>
                        {renderPreviewField(
                            assistantRes.requestPreviewInputField,
                            effectiveInput.length,
                            effectiveInputSnippet
                        )}
                        {renderPreviewField(
                            assistantRes.requestPreviewSelectedTextField,
                            requestPreview.selectedTextLength,
                            requestPreview.selectedTextSnippet
                        )}
                        {renderPreviewField(
                            assistantRes.requestPreviewTitleField,
                            requestPreview.titleLength,
                            requestPreview.titleSnippet,
                            "title"
                        )}
                        {effectiveTool === "publishCheck" &&
                            renderPreviewField(
                                assistantRes.requestPreviewAliasField,
                                requestPreview.aliasLength,
                                requestPreview.aliasSnippet
                            )}
                        {renderPreviewField(
                            assistantRes.requestPreviewDigestField,
                            requestPreview.digestLength,
                            requestPreview.digestSnippet,
                            "digest"
                        )}
                        {renderPreviewField(
                            assistantRes.requestPreviewKeywordsField,
                            requestPreview.keywordsLength,
                            requestPreview.keywordsSnippet,
                            "keywords"
                        )}
                        {renderPreviewField(
                            assistantRes.requestPreviewMarkdownField,
                            requestPreview.markdownLength,
                            requestPreview.markdownSnippet,
                            "markdown"
                        )}
                        {effectiveTool === "publishCheck" &&
                            renderPreviewField(
                                assistantRes.requestPreviewCoverField,
                                requestPreview.coverLength,
                                requestPreview.coverSnippet
                            )}
                        <Typography.Text type="secondary">{assistantRes.requestPreviewResourceSummary}</Typography.Text>
                        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                            {getResourceSourceDescription()}
                        </Typography.Text>
                        {renderReferenceSection(
                            assistantRes.requestPreviewImageReferences,
                            requestPreview.imageReferenceCount,
                            requestPreview.imageReferences
                        )}
                        {renderReferenceSection(
                            assistantRes.requestPreviewLinkReferences,
                            requestPreview.linkReferenceCount,
                            requestPreview.linkReferences
                        )}
                        {renderReferenceSection(
                            assistantRes.requestPreviewExternalLinks,
                            requestPreview.externalLinkCount,
                            requestPreview.externalLinks
                        )}
                        <Typography.Text type="secondary">
                            {requestPreview.articleContextAdded
                                ? assistantRes.requestPreviewSnapshotExcludedForTool
                                : assistantRes.requestPreviewToolWithoutSnapshot}
                        </Typography.Text>
                        <Typography.Text type="secondary">{getConversationContextHint(effectiveTool)}</Typography.Text>
                        {renderContextSourceSummary()}
                    </Space>
                ) : (
                    <Space direction="vertical" size={4} style={{ width: "100%" }}>
                        {renderPreviewField(
                            assistantRes.requestPreviewCurrentInput,
                            effectiveInput.length,
                            effectiveInputSnippet
                        )}
                        {renderContextSourceSummary()}
                        <Typography.Text>{assistantRes.requestPreviewHistory}</Typography.Text>
                        {requestPreview.articleContextAdded ? (
                            <Space direction="vertical" size={4} style={{ width: "100%" }}>
                                <Checkbox
                                    checked={includeArticleContextInChat}
                                    onChange={(e) => onIncludeArticleContextInChatChange(e.target.checked)}
                                >
                                    {assistantRes.requestPreviewIncludeSnapshot}
                                </Checkbox>
                                <Typography.Text type="secondary">
                                    {includeArticleContextInChat
                                        ? assistantRes.requestPreviewSnapshotIncluded
                                        : assistantRes.requestPreviewSnapshotExcludedByChoice}
                                </Typography.Text>
                            </Space>
                        ) : (
                            <Typography.Text type="secondary">
                                {assistantRes.requestPreviewNoArticleAuto}
                            </Typography.Text>
                        )}
                    </Space>
                )}
            </Space>
        );
    };

    const submitInput = () => {
        const parsedSkillCommand = parseSkillCommand(input);
        setSkillPanelOpen(false);
        if (parsedSkillCommand) {
            if (parsedSkillCommand.disabled) {
                return;
            }
            onSubmit(parsedSkillCommand.prompt, parsedSkillCommand.tool);
            setInput("");
            setSelectedTool(undefined);
            return;
        }
        if (selectedTool) {
            const matchedTool = toolButtons.find((tool) => tool.key === selectedTool);
            if (matchedTool?.disabled) {
                return;
            }
            onSubmit(getEffectiveInput(), selectedTool);
            setInput("");
            setSelectedTool(undefined);
            return;
        }
        onSubmit(input);
        setInput("");
    };

    const selectSkill = (tool: AssistantToolButton) => {
        if (tool.disabled) {
            return;
        }
        setSelectedTool(tool.key);
        if (inputContainsOnlySelectedText()) {
            setInput("");
        }
        setSkillPanelOpen(false);
    };

    const updateInput = (nextValue: string) => {
        const trimmedStartValue = nextValue.trimStart();
        if (trimmedStartValue.startsWith("/")) {
            const content = trimmedStartValue.substring(1);
            const firstSpaceIndex = content.search(/\s/);
            const command = (firstSpaceIndex >= 0 ? content.substring(0, firstSpaceIndex) : content).toLowerCase();
            const matchedTool = getToolByCommand(command);
            if (matchedTool && firstSpaceIndex >= 0) {
                if (matchedTool.disabled) {
                    setSelectedTool(undefined);
                    setInput(trimmedStartValue);
                    setSkillPanelOpen(false);
                    return;
                }
                setSelectedTool(matchedTool.key);
                setInput(content.substring(firstSpaceIndex).trimStart());
                setSkillPanelOpen(false);
                return;
            }
            if (matchedTool && content === command) {
                if (matchedTool.disabled) {
                    setSelectedTool(undefined);
                    setInput(trimmedStartValue);
                    setSkillPanelOpen(false);
                    return;
                }
                setSelectedTool(matchedTool.key);
                setInput("");
                setSkillPanelOpen(false);
                return;
            }
            setSelectedTool(undefined);
            setInput(nextValue);
            setSkillPanelOpen(false);
            return;
        }
        setInput(nextValue);
    };

    const showSkillPanel = skillPanelOpen || (input.trimStart().startsWith("/") && !parseSkillCommand(input));
    const parsedSkillCommand = parseSkillCommand(input);
    const selectedToolButton = selectedTool ? toolButtons.find((tool) => tool.key === selectedTool) : undefined;
    const activeToolDisabledReason = selectedToolButton?.disabledReason || parsedSkillCommand?.disabledReason;

    return (
        <>
            <Space wrap style={{ marginBottom: 8, width: "100%", justifyContent: "space-between", rowGap: 6 }}>
                <Space wrap size={8}>
                    <Button
                        size="small"
                        icon={<AIIcon name={aiProvider} />}
                        disabled={disabled}
                        onClick={() => setSkillPanelOpen((prevState) => !prevState)}
                    >
                        {getRes().articleEdit.assistant.skill}
                    </Button>
                    <Button
                        size="small"
                        icon={<FileTextOutlined />}
                        disabled={disabled || articleContextAppending || !articleContextAvailable}
                        loading={articleContextAppending}
                        onClick={onAddArticleContext}
                    >
                        {articleContextAdded
                            ? getRes().articleEdit.assistant.updateArticleContext
                            : getRes().articleEdit.assistant.addArticleContext}
                    </Button>
                    <Popover
                        trigger="click"
                        placement="topLeft"
                        content={renderRequestPreview()}
                        overlayInnerStyle={{ maxWidth: 360 }}
                    >
                        <Button size="small" icon={<InfoCircleOutlined />}>
                            {getRes().articleEdit.assistant.requestPreview}
                        </Button>
                    </Popover>
                    <Button
                        size="small"
                        icon={<DownloadOutlined />}
                        disabled={disabled || aiMessagesExporting || aiMessageCount === 0}
                        loading={aiMessagesExporting}
                        onClick={onExportAiMessages}
                    >
                        {getRes().articleEdit.assistant.exportAiMessages}
                    </Button>
                    <Popconfirm
                        title={getRes().articleEdit.assistant.clearAiMessagesConfirmTitle}
                        description={getRes().articleEdit.assistant.clearAiMessagesConfirmDescription}
                        okText={getRes().confirm}
                        cancelText={getRes().cancel}
                        disabled={disabled || aiMessagesClearing || aiMessageCount === 0}
                        onConfirm={onClearAiMessages}
                    >
                        <Button
                            size="small"
                            danger
                            icon={<DeleteOutlined />}
                            disabled={disabled || aiMessagesClearing || aiMessageCount === 0}
                            loading={aiMessagesClearing}
                        >
                            {getRes().articleEdit.assistant.clearAiMessages}
                        </Button>
                    </Popconfirm>
                </Space>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {getRes().articleEdit.assistant.skillHint}
                </Typography.Text>
            </Space>
            {showSkillPanel && (
                <div
                    style={{
                        background: theme.colorFillQuaternary,
                        borderRadius: theme.borderRadius,
                        marginBottom: 8,
                        padding: 8,
                    }}
                >
                    <ArticleAiAssistantSkillPanel
                        emptyDescription={getRes().articleEdit.assistant.skillEmpty}
                        groups={toolGroups}
                        tools={filteredToolButtons}
                        selectedTool={selectedTool}
                        theme={theme}
                        onSelect={selectSkill}
                    />
                </div>
            )}
            <div style={{ position: "relative" }}>
                {selectedTool && (
                    <Space style={{ marginBottom: 6 }}>
                        <Tag closable color="processing" onClose={() => setSelectedTool(undefined)}>
                            {getAssistantToolLabel(selectedTool)}
                        </Tag>
                    </Space>
                )}
                {activeToolDisabledReason ? (
                    <Typography.Text type="secondary" style={{ display: "block", fontSize: 12, marginBottom: 6 }}>
                        {activeToolDisabledReason}
                    </Typography.Text>
                ) : null}
                <TextArea
                    autoSize={{ minRows: 2, maxRows: 5 }}
                    disabled={disabled}
                    value={input}
                    placeholder={getRes().articleEdit.assistant.inputPlaceholder}
                    onChange={(e) => updateInput(e.target.value)}
                    onPressEnter={(e) => {
                        if (e.shiftKey) {
                            return;
                        }
                        e.preventDefault();
                        e.stopPropagation();
                        submitInput();
                    }}
                    style={{
                        paddingRight: 48,
                        resize: "none",
                    }}
                />
                <Button
                    type="primary"
                    shape="circle"
                    icon={<ArrowUpOutlined />}
                    disabled={
                        disabled || Boolean(activeToolDisabledReason) || (!selectedTool && input.trim().length === 0)
                    }
                    loading={loadingKey === "chat"}
                    onClick={submitInput}
                    style={{
                        position: "absolute",
                        right: 8,
                        bottom: 8,
                    }}
                    title={getRes().articleEdit.assistant.send}
                />
            </div>
            <Typography.Text
                type="secondary"
                style={{
                    display: "flex",
                    alignItems: "center",
                    gap: 4,
                    fontSize: 12,
                    justifyContent: "center",
                    marginTop: 8,
                }}
            >
                <InfoCircleOutlined />
                {getEditorRes("ai").contentTips}
            </Typography.Text>
        </>
    );
};

export default ArticleAiAssistantSkillContent;
