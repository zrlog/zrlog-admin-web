import { useEffect, useId, useRef, useState } from "react";
import type { KeyboardEvent } from "react";
import type { AxiosInstance } from "axios";
import { Alert, Button, Space, Typography } from "antd";
import Tag from "@zrlog/ui/antd/Tag";
import CloseIcon from "@zrlog/ui/icons/close";
import { getRes } from "../../../utils/constants";
import { ApiResponse } from "../../../type";
import { formatSize } from "../../file-manager/shared";
import {
    AiMention,
    AiMentionArticle,
    MAX_AI_MENTIONS,
    aiMentionLabel,
    findAiMention,
    sameAiMention,
} from "./article-ai-mentions";

export const useArticleAiMentions = (
    input: string,
    setInput: (input: string) => void,
    files: File[],
    disabled: boolean,
    theme: any,
    focus: (caret: number) => void,
    api?: AxiosInstance
) => {
    const res = getRes().articleEdit.assistant.mentions;
    const id = useId();
    const [caret, setCaret] = useState(0);
    const [dismissed, setDismissed] = useState<string>();
    const [selected, setSelected] = useState<AiMention[]>([]);
    const [active, setActive] = useState(0);
    const [retry, setRetry] = useState(0);
    const [search, setSearch] =
        useState<{ key: string; articles: AiMentionArticle[]; loading?: boolean; failed?: boolean }>();
    const optionRefs = useRef<(HTMLElement | null)[]>([]);
    const trigger = findAiMention(input, caret);
    const key = trigger ? `${trigger.start}:${trigger.query}` : "";
    const queryText = trigger?.query || "";
    const open = Boolean(trigger && key !== dismissed && !disabled);
    const mentions = selected.filter((mention) => mention.type === "article" || files.includes(mention.file));

    useEffect(() => {
        setDismissed(undefined);
    }, [input, caret]);

    useEffect(() => {
        setSelected((current) =>
            current.filter((mention) => mention.type === "article" || files.includes(mention.file))
        );
    }, [files]);

    useEffect(() => {
        if (!open) return;
        setActive(0);
        if (!api) {
            setSearch({ key, articles: [] });
            return;
        }
        const controller = new AbortController();
        setSearch({ key, articles: [], loading: true });
        const timer = window.setTimeout(async () => {
            try {
                const config = { params: { query: queryText }, signal: controller.signal, showError: false };
                const response = await api.get<ApiResponse<{ articles: AiMentionArticle[] }>>(
                    "/api/admin/article/ai/references",
                    config
                );
                if (controller.signal.aborted) return;
                if (response.data.error || !Array.isArray(response.data.data?.articles)) throw new Error();
                setSearch({ key, articles: response.data.data.articles });
            } catch {
                if (!controller.signal.aborted) setSearch({ key, articles: [], failed: true });
            }
        }, 200);
        return () => {
            window.clearTimeout(timer);
            controller.abort();
        };
    }, [api, open, key, queryText, retry]);

    const results = search?.key === key ? search : undefined;
    const query = (trigger?.query || "").toLowerCase();
    const candidates: AiMention[] = [
        ...files
            .filter((file) => file.name.toLowerCase().includes(query))
            .map((file): AiMention => ({ type: "file", file })),
        ...(results?.articles || []).map((article): AiMention => ({ type: "article", article })),
    ].filter((candidate) => !mentions.some((mention) => sameAiMention(mention, candidate)));
    const activeIndex = Math.min(active, Math.max(0, candidates.length - 1));
    const atLimit = mentions.length >= MAX_AI_MENTIONS;

    useEffect(() => {
        if (open) optionRefs.current[activeIndex]?.scrollIntoView?.({ block: "nearest" });
    }, [open, activeIndex]);

    const choose = (mention: AiMention) => {
        if (!trigger || disabled || atLimit) return;
        setSelected([...mentions, mention]);
        setInput(input.slice(0, trigger.start) + input.slice(trigger.end));
        setCaret(trigger.start);
        focus(trigger.start);
    };

    const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
        if (!open || event.nativeEvent.isComposing || event.keyCode === 229 || event.shiftKey) return false;
        if (event.key === "Escape") setDismissed(key);
        else if (event.key === "ArrowDown" || event.key === "ArrowUp") {
            if (candidates.length)
                setActive((activeIndex + (event.key === "ArrowDown" ? 1 : -1) + candidates.length) % candidates.length);
        } else if (event.key === "Enter" || (event.key === "Tab" && candidates.length && !atLimit)) {
            if (candidates[activeIndex] && !atLimit) choose(candidates[activeIndex]);
        } else return false;
        event.preventDefault();
        event.stopPropagation();
        return true;
    };

    return {
        mentions,
        open,
        onKeyDown,
        setCaret,
        reset: () => {
            setSelected([]);
            setDismissed(undefined);
            setCaret(0);
        },
        insert: () => {
            const position = Math.max(0, Math.min(caret, input.length));
            const prefix = input.slice(0, position);
            const text = `${prefix}${prefix && !/\s$/.test(prefix) ? " " : ""}@`;
            setDismissed(undefined);
            setInput(text + input.slice(position));
            setCaret(text.length);
            focus(text.length);
        },
        inputProps: {
            "aria-autocomplete": "list" as const,
            "aria-controls": open && !atLimit ? id : undefined,
            "aria-activedescendant": open && candidates.length && !atLimit ? `${id}-${activeIndex}` : undefined,
        },
        tags: mentions.length > 0 && (
            <Space wrap size={theme.marginXXS}>
                {mentions.map((mention, index) => (
                    <Tag key={index} style={{ maxWidth: "100%", whiteSpace: "normal", overflowWrap: "anywhere" }}>
                        @{aiMentionLabel(mention)}
                        <Button
                            type="text"
                            size="small"
                            icon={<CloseIcon />}
                            disabled={disabled}
                            aria-label={`${res.remove} ${aiMentionLabel(mention)}`}
                            onClick={() => {
                                setSelected(mentions.filter((_, position) => position !== index));
                                focus(Math.max(0, caret));
                            }}
                        />
                    </Tag>
                ))}
            </Space>
        ),
        panel: open && (
            <div
                style={{
                    padding: theme.paddingXS,
                    borderBottom: `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`,
                }}
            >
                <Typography.Text type="secondary">{res.choose}</Typography.Text>
                {atLimit ? (
                    <div role="status">{res.limit}</div>
                ) : (
                    <>
                        <div
                            id={id}
                            role="listbox"
                            aria-label={res.choose}
                            style={{ maxHeight: 240, overflowY: "auto" }}
                        >
                            {candidates.map((candidate, index) => (
                                <Button
                                    key={index}
                                    id={`${id}-${index}`}
                                    role="option"
                                    type="text"
                                    block
                                    ref={(element) => {
                                        optionRefs.current[index] = element;
                                    }}
                                    aria-selected={index === activeIndex}
                                    onMouseDown={(event) => event.preventDefault()}
                                    onClick={() => choose(candidate)}
                                    style={{
                                        height: "auto",
                                        minHeight: theme.controlHeight,
                                        textAlign: "start",
                                        justifyContent: "flex-start",
                                        whiteSpace: "normal",
                                        background: index === activeIndex ? theme.colorFillSecondary : undefined,
                                    }}
                                >
                                    <span style={{ overflowWrap: "anywhere", minWidth: 0 }}>
                                        <Typography.Text type="secondary">
                                            {candidate.type === "article" ? res.article : res.file} ·{" "}
                                        </Typography.Text>
                                        {aiMentionLabel(candidate)}
                                        <Typography.Text
                                            type="secondary"
                                            style={{
                                                display: "block",
                                                fontSize: theme.fontSizeSM,
                                                overflowWrap: "anywhere",
                                            }}
                                        >
                                            {candidate.type === "article"
                                                ? candidate.article.url
                                                : `${files.indexOf(candidate.file) + 1} · ${formatSize(
                                                      candidate.file.size
                                                  )}`}
                                        </Typography.Text>
                                    </span>
                                </Button>
                            ))}
                        </div>
                        {results?.loading && <div role="status">{res.loading}</div>}
                        {results?.failed && (
                            <Alert
                                type="error"
                                title={res.failed}
                                action={
                                    <Button size="small" aria-label={res.retry} onClick={() => setRetry(retry + 1)}>
                                        {res.retry}
                                    </Button>
                                }
                            />
                        )}
                        {!candidates.length && !results?.loading && !results?.failed && (
                            <div role="status">{res.empty}</div>
                        )}
                    </>
                )}
            </div>
        ),
    };
};
