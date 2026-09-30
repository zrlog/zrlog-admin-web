import { FunctionComponent, useEffect, useState } from "react";
import { Button, Input, Space, Tag, Typography } from "antd";
import { ArrowUpOutlined, InfoCircleOutlined } from "@ant-design/icons";
import AIIcon from "@zrlog/editor/dist/ai/AIIcon";
import { getEditorRes } from "@zrlog/editor/dist/editor/lang/editor-lang";
import { getRes } from "../../../utils/constants";
import { AssistantTool, AssistantToolButton } from "./article-ai-assistant.types";
import {
    buildAssistantToolButtons,
    buildAssistantToolGroups,
    getAssistantToolLabel,
} from "./tool/article-ai-assistant-tools";
import ArticleAiAssistantSkillPanel from "./article-ai-assistant-skill-panel";

const { TextArea } = Input;
const REWRITE_MIN_MARKDOWN_LENGTH = 120;

type ArticleAiAssistantSkillContentProps = {
    aiProvider: any;
    disabled: boolean;
    loadingKey?: string;
    chatStatus?: string;
    onStopChat?: () => void;
    theme: any;
    selectedText?: string;
    markdownLength: number;
    onSubmit: (message: string, tool?: AssistantTool) => void;
};

const ArticleAiAssistantSkillContent: FunctionComponent<ArticleAiAssistantSkillContentProps> = ({
    aiProvider,
    disabled,
    loadingKey,
    chatStatus,
    onStopChat,
    theme,
    selectedText,
    markdownLength,
    onSubmit,
}) => {
    const [input, setInput] = useState("");
    const [selectedTool, setSelectedTool] = useState<AssistantTool>();
    const [skillPanelOpen, setSkillPanelOpen] = useState(false);

    const assistantRes = getRes().articleEdit.assistant;
    const selectedTextValue = selectedText?.trim() || "";
    const rewriteDisabled = markdownLength < REWRITE_MIN_MARKDOWN_LENGTH;
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

    const inputContainsOnlySelectedText = () => Boolean(selectedTextValue) && input.trim() === selectedTextValue;

    const getEffectiveInput = () => {
        const parsedSkillCommand = parseSkillCommand(input);
        if (parsedSkillCommand) return parsedSkillCommand.prompt;
        const prompt = toolButtons.find((tool) => tool.key === selectedTool)?.prompt || "";
        return inputContainsOnlySelectedText() ? prompt : input.trim() || prompt;
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
            {chatStatus && (
                <Space style={{ marginBottom: theme.marginXS }}>
                    <Typography.Text role="status" type="secondary">
                        {chatStatus}
                    </Typography.Text>
                    {onStopChat && (
                        <Button size="small" onClick={onStopChat}>
                            {getRes().articleEdit.knowledge.stop}
                        </Button>
                    )}
                </Space>
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
            <Typography.Paragraph
                type="secondary"
                style={{ fontSize: theme.fontSizeSM, marginTop: theme.marginXS, marginBottom: 0 }}
            >
                {getRes().articleEdit.knowledge.sessionHint}
            </Typography.Paragraph>
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
