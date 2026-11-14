import { assistantComposerDrafts as composerDrafts } from "./assistant-composer-drafts";
import { AiFileCapabilities, AiFileList, AiFilePicker } from "./assistant-files";
import Tag from "@zrlog/ui/antd/Tag";
import Input from "@zrlog/ui/antd/Input";
import ArrowUpIcon from "@zrlog/ui/icons/arrow-up";
import BoltIcon from "@zrlog/ui/icons/bolt";
import CloseIcon from "@zrlog/ui/icons/close";
import InfoIcon from "@zrlog/ui/icons/info";
import { FunctionComponent, useEffect, useId, useRef, useState } from "react";
import { Button, Space, Tooltip, Typography } from "antd";
import type { TextAreaRef } from "antd/es/input/TextArea";
import type { AxiosInstance } from "axios";
import { useAssistantMentions } from "./use-assistant-mentions";
import { AiMentionInput, encodeAiMentions, withAiMentionLabels } from "./assistant-mentions";

import { getEditorRes } from "@zrlog/editor/dist/editor/lang/editor-lang";
import { getRes } from "../../../utils/constants";
import { WritingSkill, WritingSkillButton } from "../writing/writing-assistant.types";
import {
    buildWritingSkillButtons,
    buildWritingSkillGroups,
    getWritingSkillLabel,
} from "../writing/skills/writing-skills";
import WritingSkillPanel from "../writing/writing-skill-panel";
import { QueuedAiMessage } from "./use-assistant-queue";

const { TextArea } = Input;
const REWRITE_MIN_MARKDOWN_LENGTH = 120;

type AssistantComposerProps = {
    draftKey?: string;
    writingContext?: boolean;
    disabled: boolean;
    busy: boolean;
    waiting: boolean;
    onStop?: () => void;
    queuedMessages: QueuedAiMessage[];
    queuePaused: boolean;
    onRemoveQueued: (id: number) => void;
    onResumeQueue: () => void;
    theme: any;
    selectedText?: string;
    markdownLength: number;
    fileInput?: AiFileCapabilities;
    preparingFiles?: boolean;
    api?: AxiosInstance;
    onSubmit: (message: string, tool?: WritingSkill, files?: File[], references?: AiMentionInput[]) => boolean;
};

const AssistantComposer: FunctionComponent<AssistantComposerProps> = ({
    draftKey,
    writingContext = true,
    disabled,
    busy,
    waiting,
    onStop,
    queuedMessages,
    queuePaused,
    onRemoveQueued,
    onResumeQueue,
    theme,
    selectedText,
    markdownLength,
    onSubmit,
    fileInput,
    preparingFiles,
    api,
}) => {
    const initialDraft = draftKey ? composerDrafts.get(draftKey) : undefined;
    const [input, setInput] = useState(initialDraft?.input || "");
    const [files, setFiles] = useState<File[]>(initialDraft?.files || []);
    const [selectedTool, setSelectedTool] = useState<WritingSkill | undefined>(initialDraft?.selectedTool);
    const [skillPanelOpen, setSkillPanelOpen] = useState(false);
    const skillPanelId = useId();
    const inputRef = useRef<TextAreaRef>(null);
    const focusInput = (caret: number) => {
        requestAnimationFrame(() => {
            inputRef.current?.focus();
            inputRef.current?.resizableTextArea?.textArea.setSelectionRange(caret, caret);
        });
    };
    const mentions = useAssistantMentions(
        input,
        setInput,
        files,
        disabled,
        theme,
        focusInput,
        api,
        initialDraft?.mentions
    );
    useEffect(() => {
        if (!draftKey) return;
        if (input || files.length || selectedTool || mentions.mentions.length)
            composerDrafts.set(draftKey, { input, files, selectedTool, mentions: mentions.mentions });
        else composerDrafts.delete(draftKey);
    }, [draftKey, input, files, selectedTool, mentions.mentions]);
    useEffect(() => {
        if (draftKey && !disabled) inputRef.current?.focus();
    }, [draftKey]);

    const assistantRes = getRes().articleEdit.assistant;
    const selectedTextValue = selectedText?.trim() || "";
    const rewriteDisabled = markdownLength < REWRITE_MIN_MARKDOWN_LENGTH;
    const toolButtons = (writingContext ? buildWritingSkillButtons() : []).map((tool) =>
        tool.key === "rewrite" && rewriteDisabled
            ? { ...tool, disabled: true, disabledReason: assistantRes.rewriteDraftRequired }
            : tool
    );
    const toolGroups = buildWritingSkillGroups();

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

    const submit = (message: string, tool?: WritingSkill) => {
        const accepted = mentions.mentions.length
            ? onSubmit(
                  withAiMentionLabels(message, mentions.mentions),
                  tool,
                  files,
                  encodeAiMentions(mentions.mentions, files)
              )
            : files.length
            ? onSubmit(message, tool, files)
            : tool
            ? onSubmit(message, tool)
            : onSubmit(message);
        if (accepted) {
            setFiles([]);
            mentions.reset();
        }
        return accepted;
    };

    const submitInput = () => {
        if (disabled) return;
        const parsedSkillCommand = parseSkillCommand(input);
        setSkillPanelOpen(false);
        if (parsedSkillCommand) {
            if (parsedSkillCommand.disabled) {
                return;
            }
            if (!submit(parsedSkillCommand.prompt, parsedSkillCommand.tool)) return;
            setInput("");
            setSelectedTool(undefined);
            return;
        }
        if (selectedTool) {
            const matchedTool = toolButtons.find((tool) => tool.key === selectedTool);
            if (matchedTool?.disabled) {
                return;
            }
            if (!submit(getEffectiveInput(), selectedTool)) return;
            setInput("");
            setSelectedTool(undefined);
            return;
        }
        if (
            (!input.trim() && !files.length && !mentions.mentions.length) ||
            !submit(
                input.trim() ||
                    (mentions.mentions.length ? assistantRes.mentions.prompt : getRes().articleEdit.files.prompt)
            )
        )
            return;
        setInput("");
    };

    const selectSkill = (tool: WritingSkillButton) => {
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

    const showSkillPanel =
        writingContext && (skillPanelOpen || (input.trimStart().startsWith("/") && !parseSkillCommand(input)));
    const parsedSkillCommand = parseSkillCommand(input);
    const selectedToolButton = selectedTool ? toolButtons.find((tool) => tool.key === selectedTool) : undefined;
    const activeToolDisabledReason = selectedToolButton?.disabledReason || parsedSkillCommand?.disabledReason;
    const cannotSubmit =
        disabled ||
        Boolean(activeToolDisabledReason) ||
        (!selectedTool && !input.trim() && !files.length && !mentions.mentions.length);
    const willQueue = busy || waiting || queuedMessages.length > 0;
    const actionLabel = busy
        ? getRes().articleEdit.knowledge.stop
        : willQueue
        ? assistantRes.addToQueue
        : assistantRes.send;

    return (
        <>
            {showSkillPanel && !mentions.open && (
                <div
                    id={skillPanelId}
                    style={{
                        marginBottom: theme.marginXS,
                        paddingBottom: theme.paddingXS,
                        borderBottom: `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`,
                    }}
                >
                    <WritingSkillPanel
                        emptyDescription={getRes().articleEdit.assistant.skillEmpty}
                        groups={toolGroups}
                        tools={filteredToolButtons}
                        selectedTool={selectedTool}
                        theme={theme}
                        onSelect={selectSkill}
                    />
                </div>
            )}
            {queuedMessages.length > 0 && (
                <div style={{ marginBottom: theme.marginSM }}>
                    <Space wrap>
                        <Typography.Text type="secondary" role="status">
                            {queuePaused
                                ? assistantRes.queuePaused
                                : waiting
                                ? assistantRes.queueWaiting
                                : assistantRes.queuedMessages}
                        </Typography.Text>
                        {queuePaused && (
                            <Button size="small" disabled={disabled || busy || waiting} onClick={onResumeQueue}>
                                {assistantRes.resumeQueue}
                            </Button>
                        )}
                    </Space>
                    <div style={{ maxHeight: 144, overflowY: "auto" }}>
                        {queuedMessages.map((message) => (
                            <div key={message.id} style={{ display: "flex", gap: theme.marginXS, alignItems: "start" }}>
                                <div style={{ flex: 1, minWidth: 0, whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>
                                    {message.tool && <Tag>{getWritingSkillLabel(message.tool)}</Tag>}
                                    {message.input}
                                    <AiFileList files={message.files} compact />
                                </div>
                                <Button
                                    type="text"
                                    icon={<CloseIcon />}
                                    aria-label={assistantRes.removeQueued}
                                    title={assistantRes.removeQueued}
                                    onClick={() => onRemoveQueued(message.id)}
                                />
                            </div>
                        ))}
                    </div>
                </div>
            )}
            {preparingFiles && <Typography.Text role="status">{getRes().articleEdit.files.preparing}</Typography.Text>}
            <AiFilePicker
                capabilities={fileInput}
                files={files}
                disabled={disabled}
                onChange={setFiles}
                toolbar={
                    <Space size={theme.marginXXS}>
                        {writingContext && (
                            <Button
                                type="text"
                                icon={<BoltIcon />}
                                disabled={disabled}
                                aria-expanded={showSkillPanel}
                                aria-controls={showSkillPanel ? skillPanelId : undefined}
                                onClick={() => setSkillPanelOpen((prevState) => !prevState)}
                            >
                                {getRes().articleEdit.assistant.skill}
                            </Button>
                        )}
                    </Space>
                }
                action={
                    <Space size={theme.marginXS} style={{ flexShrink: 0 }}>
                        {busy && (
                            <Tooltip title={assistantRes.queueHint} trigger={["hover", "focus"]}>
                                <Button size="small" disabled={cannotSubmit} onClick={submitInput}>
                                    {assistantRes.addToQueue}
                                </Button>
                            </Tooltip>
                        )}
                        <Tooltip
                            title={willQueue && !busy ? assistantRes.queueHint : actionLabel}
                            trigger={["hover", "focus"]}
                        >
                            <Button
                                type="primary"
                                shape="circle"
                                icon={
                                    busy ? (
                                        <span
                                            aria-hidden="true"
                                            style={{
                                                display: "block",
                                                width: theme.fontSizeSM,
                                                height: theme.fontSizeSM,
                                                background: "currentColor",
                                                borderRadius: theme.borderRadiusXS,
                                            }}
                                        />
                                    ) : (
                                        <ArrowUpIcon />
                                    )
                                }
                                disabled={busy ? !onStop : cannotSubmit}
                                onClick={busy ? onStop : submitInput}
                                aria-label={actionLabel}
                            />
                        </Tooltip>
                    </Space>
                }
            >
                <div style={{ position: "relative" }}>
                    {mentions.panel}
                    {mentions.tags}
                    {selectedTool && (
                        <Space style={{ marginBottom: 6 }}>
                            <Tag closable color="processing" onClose={() => setSelectedTool(undefined)}>
                                {getWritingSkillLabel(selectedTool)}
                            </Tag>
                        </Space>
                    )}
                    {activeToolDisabledReason ? (
                        <Typography.Text type="secondary" style={{ display: "block", fontSize: 12, marginBottom: 6 }}>
                            {activeToolDisabledReason}
                        </Typography.Text>
                    ) : null}
                    <TextArea
                        ref={inputRef}
                        {...mentions.inputProps}
                        autoSize={{ minRows: 1, maxRows: 5 }}
                        variant="borderless"
                        disabled={disabled}
                        aria-label={assistantRes.messageInput}
                        value={input}
                        placeholder={
                            writingContext
                                ? getRes().articleEdit.assistant.inputPlaceholder
                                : getRes().assistant.messagePlaceholder
                        }
                        onChange={(e) => {
                            mentions.setCaret(e.target.selectionStart);
                            updateInput(e.target.value);
                        }}
                        onSelect={(e) => {
                            const target = e.currentTarget;
                            mentions.setCaret(
                                target.selectionStart === target.selectionEnd ? target.selectionStart : -1
                            );
                        }}
                        onKeyDown={(e) => {
                            if (e.shiftKey || e.nativeEvent.isComposing || e.keyCode === 229) {
                                return;
                            }
                            if (mentions.onKeyDown(e) || e.key !== "Enter") return;
                            e.preventDefault();
                            e.stopPropagation();
                            submitInput();
                        }}
                        style={{
                            boxShadow: "none",
                            background: "transparent",
                            resize: "none",
                        }}
                    />
                </div>
            </AiFilePicker>
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
                <InfoIcon />
                {getEditorRes("ai").contentTips}
            </Typography.Text>
        </>
    );
};

export default AssistantComposer;
