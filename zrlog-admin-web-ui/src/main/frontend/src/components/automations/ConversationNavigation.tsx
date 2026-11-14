import {
    assistantComposerDrafts,
    assistantComposerScope,
} from "../articleEdit/article-ai-assistant/assistant-composer-drafts";
import { Alert, Button, Dropdown, Space, Spin, Tooltip, Typography } from "antd";
import Input from "@zrlog/ui/antd/Input";
import EditIcon from "@zrlog/ui/icons/edit";
import HistoryIcon from "@zrlog/ui/icons/history";
import MoreIcon from "@zrlog/ui/icons/more";
import CloseIcon from "@zrlog/ui/icons/close";
import { useUiApp } from "@zrlog/ui/feedback";
import { useTheme } from "antd-style";
import { useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getSsDate } from "../../base/SsData";
import { getRes } from "../../utils/constants";
import { getErrorMessage } from "../../utils/error-message";
import useArticleEditorScreens from "../articleEdit/use-article-editor-screens";
import type { ApiResponse } from "../../type";

export type ConversationSelection = { sessionId?: string; articleId?: number; taskId?: string };
export type ConversationSummary = ConversationSelection & { title: string; createdAt: number; updatedAt: number };
type SessionPage = { items: ConversationSummary[]; hasMore: boolean; before?: number; beforeKey?: string };
export const conversationKey = (value: ConversationSelection) =>
    `${value.taskId || (value.articleId === undefined ? "general" : `article/${value.articleId}`)}/${
        value.sessionId || "default"
    }`;

/** The same navigation is embedded in the assistant page and the writing drawer. */
export default function ConversationNavigation({
    selection,
    articleFilter,
    compact = false,
    disabled = false,
    revision = 0,
    onSelect,
    onNew,
    onDeleted,
    children,
}: {
    selection: ConversationSelection;
    articleFilter?: number;
    compact?: boolean;
    disabled?: boolean;
    revision?: number | string;
    onSelect: (session: ConversationSummary) => void;
    onNew: () => void;
    onDeleted: (session: ConversationSummary) => void;
    children: (toolbar: ReactNode) => ReactNode;
}) {
    const res = getRes().assistant;
    const api = useAxiosBaseInstance();
    const theme = useTheme();
    const screens = useArticleEditorScreens();
    const overlay = compact || !screens.lg;
    const preferenceKey = `${getSsDate().key}/assistant/conversationNavigation`;
    const [open, setOpen] = useState(
        () => !compact && Boolean(screens.lg) && window.localStorage.getItem(preferenceKey) === "true"
    );
    const [query, setQuery] = useState("");
    const [data, setData] = useState<SessionPage>();
    const [error, setError] = useState<string>();
    const [loading, setLoading] = useState(false);
    const [retry, setRetry] = useState(0);
    const [cursor, setCursor] = useState<{ before?: number; beforeKey?: string }>({});
    const [renaming, setRenaming] = useState<string>();
    const [title, setTitle] = useState("");
    const [saving, setSaving] = useState(false);
    const toggleRef = useRef<HTMLButtonElement>(null);
    const listRef = useRef<HTMLDivElement>(null);
    const { modal } = useUiApp();
    const selectKey = conversationKey(selection);
    const changeOpen = (value: boolean) => {
        setOpen(value);
        if (!overlay) window.localStorage.setItem(preferenceKey, String(value));
        if (!value) requestAnimationFrame(() => toggleRef.current?.focus());
    };
    useEffect(() => {
        setCursor({});
    }, [query, articleFilter, revision]);
    useEffect(() => {
        if (!open) return;
        const controller = new AbortController();
        setLoading(true);
        setError(undefined);
        const timer = window.setTimeout(
            () => {
                const config = {
                    params: { id: articleFilter, query, ...cursor },
                    signal: controller.signal,
                    showError: false,
                };
                void api
                    .get<ApiResponse<SessionPage>>("/api/admin/assistant/chat/sessions", config)
                    .then(({ data: response }) => {
                        if (controller.signal.aborted) return;
                        if (response.error) throw new Error(response.message || getRes().error.unknown);
                        setData((previous) => ({
                            ...response.data,
                            items:
                                cursor.before && previous
                                    ? [...previous.items, ...response.data.items].filter(
                                          (item, index, all) =>
                                              all.findIndex(
                                                  (other) => conversationKey(other) === conversationKey(item)
                                              ) === index
                                      )
                                    : response.data.items,
                        }));
                    })
                    .catch((error) => {
                        if (!controller.signal.aborted) setError(getErrorMessage(error, getRes().error.unknown));
                    })
                    .finally(() => {
                        if (!controller.signal.aborted) setLoading(false);
                    });
            },
            query ? 250 : 0
        );
        return () => {
            window.clearTimeout(timer);
            controller.abort();
        };
    }, [api, open, articleFilter, query, cursor, retry, revision]);
    useEffect(() => {
        if (open && overlay) listRef.current?.focus();
    }, [open, overlay]);
    const choose = (item: ConversationSummary) => {
        if (disabled) return;
        onSelect(item);
        if (overlay) changeOpen(false);
    };
    const create = () => {
        if (!disabled) {
            onNew();
            if (overlay) changeOpen(false);
        }
    };
    const mutate = async (operation: "rename" | "delete", item: ConversationSummary) => {
        setSaving(true);
        try {
            const { data: response } = await api.post<ApiResponse<boolean>>(
                `/api/admin/assistant/chat/sessions/${operation}`,
                {
                    articleId: item.articleId,
                    taskId: item.taskId,
                    sessionId: item.sessionId,
                    ...(operation === "rename" ? { title: title.trim() } : {}),
                }
            );
            if (response.error) throw new Error(response.message || getRes().error.unknown);
            if (operation === "delete") {
                assistantComposerDrafts.delete(assistantComposerScope(getSsDate().key, item));
                onDeleted(item);
            }
            setRenaming(undefined);
            setCursor({});
            setRetry((value) => value + 1);
        } catch (error) {
            setError(getErrorMessage(error, getRes().error.unknown));
        } finally {
            setSaving(false);
        }
    };
    const toolbar = (
        <Space>
            <Tooltip title={res.historyConversations}>
                <Button
                    ref={toggleRef}
                    type="text"
                    icon={<HistoryIcon />}
                    aria-label={res.historyConversations}
                    aria-expanded={open}
                    onClick={() => changeOpen(!open)}
                />
            </Tooltip>
            <Tooltip title={disabled ? res.stopBeforeNewConversation : res.newConversation}>
                <Button type="text" icon={<EditIcon />} onClick={create} disabled={disabled}>
                    {res.newConversation}
                </Button>
            </Tooltip>
        </Space>
    );
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);
    const group = (at: number) =>
        at >= today.getTime()
            ? res.conversationsToday
            : at >= yesterday.getTime()
            ? res.conversationsYesterday
            : res.conversationsEarlier;
    const panel = (
        <div
            ref={listRef}
            tabIndex={-1}
            aria-label={res.historyConversations}
            onKeyDown={(event) => {
                if (event.key === "Escape") {
                    event.stopPropagation();
                    changeOpen(false);
                }
            }}
            style={{
                padding: theme.paddingSM,
                minHeight: 0,
                height: "100%",
                display: "flex",
                flexDirection: "column",
                gap: theme.marginSM,
            }}
        >
            <Space style={{ justifyContent: "space-between", width: "100%" }}>
                <Typography.Text strong>{res.historyConversations}</Typography.Text>
                <Button
                    type="text"
                    icon={<CloseIcon />}
                    aria-label={res.closeConversations}
                    onClick={() => changeOpen(false)}
                />
            </Space>
            {overlay && (
                <Button icon={<EditIcon />} onClick={create} disabled={disabled}>
                    {res.newConversation}
                </Button>
            )}
            <Input.Search
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                allowClear
                aria-label={res.searchConversations}
                placeholder={res.searchConversations}
            />
            {error && (
                <Alert
                    type="error"
                    title={error}
                    action={<Button onClick={() => setRetry((value) => value + 1)}>{getRes().common.retry}</Button>}
                />
            )}
            <div style={{ flex: 1, overflowY: "auto", minHeight: 0 }}>
                {!loading && !data?.items.length && (
                    <Typography.Paragraph type="secondary">
                        {query ? res.noMatchingConversations : res.noConversations}
                    </Typography.Paragraph>
                )}
                {data?.items.map((item, index, items) => (
                    <div key={conversationKey(item)}>
                        {(index === 0 || group(item.updatedAt) !== group(items[index - 1].updatedAt)) && (
                            <Typography.Paragraph type="secondary" style={{ marginBlock: theme.marginSM }}>
                                {group(item.updatedAt)}
                            </Typography.Paragraph>
                        )}
                        <div
                            style={{
                                display: "flex",
                                alignItems: "center",
                                borderRadius: theme.borderRadius,
                                background: selectKey === conversationKey(item) ? theme.colorFillSecondary : undefined,
                            }}
                        >
                            {renaming === conversationKey(item) ? (
                                <form
                                    style={{ flex: 1, minWidth: 0 }}
                                    onSubmit={(event) => {
                                        event.preventDefault();
                                        void mutate("rename", item);
                                    }}
                                >
                                    <Input
                                        value={title}
                                        onChange={(event) => setTitle(event.target.value)}
                                        maxLength={120}
                                        autoFocus
                                        aria-label={res.conversationTitle}
                                        disabled={saving}
                                        onKeyDown={(event) => {
                                            if (event.key === "Escape") {
                                                event.stopPropagation();
                                                setRenaming(undefined);
                                            }
                                        }}
                                    />
                                    <Button htmlType="submit" size="small" disabled={!title.trim()} loading={saving}>
                                        {getRes().common.save}
                                    </Button>
                                </form>
                            ) : (
                                <Button
                                    type="text"
                                    onClick={() => choose(item)}
                                    disabled={disabled}
                                    aria-current={selectKey === conversationKey(item) ? "page" : undefined}
                                    style={{
                                        flex: 1,
                                        minWidth: 0,
                                        height: "auto",
                                        textAlign: "start",
                                        justifyContent: "flex-start",
                                        paddingBlock: theme.paddingXS,
                                    }}
                                >
                                    <div style={{ minWidth: 0, overflow: "hidden" }}>
                                        <Typography.Text ellipsis title={item.title}>
                                            {item.title || res.newConversation}
                                        </Typography.Text>
                                        {(item.taskId || item.articleId !== undefined) && (
                                            <Typography.Text
                                                type="secondary"
                                                style={{ display: "block", fontSize: theme.fontSizeSM }}
                                            >
                                                {item.taskId ? res.taskConversation : res.writingAssistant}
                                            </Typography.Text>
                                        )}
                                    </div>
                                </Button>
                            )}
                            <Dropdown
                                trigger={["click"]}
                                menu={{
                                    items: [
                                        {
                                            key: "rename",
                                            label: res.renameConversation,
                                            disabled: disabled || saving,
                                            onClick: () => {
                                                setRenaming(conversationKey(item));
                                                setTitle(item.title);
                                            },
                                        },
                                        ...(!item.taskId
                                            ? [
                                                  {
                                                      key: "delete",
                                                      label: res.deleteConversation,
                                                      danger: true,
                                                      disabled: disabled || saving,
                                                      onClick: () =>
                                                          modal.confirm({
                                                              title: res.deleteConversation,
                                                              content: res.deleteConversationConfirm,
                                                              okText: res.deleteConversation,
                                                              cancelText: getRes().common.cancel,
                                                              okButtonProps: { danger: true },
                                                              onOk: () => mutate("delete", item),
                                                          }),
                                                  },
                                              ]
                                            : []),
                                    ],
                                }}
                            >
                                <Button
                                    type="text"
                                    icon={<MoreIcon />}
                                    aria-label={`${res.conversationMenu}: ${item.title || res.newConversation}`}
                                />
                            </Dropdown>
                        </div>
                    </div>
                ))}
                {loading && <Spin />}
                {data?.hasMore && (
                    <Button
                        type="text"
                        disabled={loading}
                        onClick={() => setCursor({ before: data.before, beforeKey: data.beforeKey })}
                    >
                        {res.moreConversations}
                    </Button>
                )}
            </div>
        </div>
    );
    return (
        <div style={{ display: "flex", height: "100%", minHeight: 0, minWidth: 0 }}>
            {open && (
                <aside
                    style={{
                        flex: overlay ? "1" : "0 0 260px",
                        minWidth: 0,
                        minHeight: 0,
                        overflow: "hidden",
                        borderInlineEnd: overlay
                            ? undefined
                            : `${theme.lineWidth}px solid ${theme.colorBorderSecondary}`,
                    }}
                >
                    {panel}
                </aside>
            )}
            <div style={{ display: open && overlay ? "none" : "block", flex: 1, minWidth: 0, minHeight: 0 }}>
                {children(toolbar)}
            </div>
        </div>
    );
}
