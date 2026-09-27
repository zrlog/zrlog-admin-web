import { SearchOutlined } from "@ant-design/icons";
import { Avatar, Grid, Input, List, Modal, Space, Spin, Tag } from "antd";
import { useTheme } from "antd-style";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAxiosBaseInstance } from "../base/AppBase";
import { getRealRouteUrl, getRes } from "../utils/constants";
import {
    applySpotlightSourceId,
    createSpotlightCommandSource,
    createSpotlightSearchSources,
} from "./spotlight-search-sources";
import { readSpotlightRecentItems, saveSpotlightRecentItem, spotlightItemStorageKey } from "./spotlight-search-recent";
import HighlightText from "../common/HighlightText";
import type {
    SpotlightCommand,
    SpotlightItem,
    SpotlightItemType,
    SpotlightRenderImageIcon,
    SpotlightSource,
} from "./spotlight-search-types";

export { createSpotlightCommandSource };
export type {
    SpotlightCommand,
    SpotlightItem,
    SpotlightSearchContext,
    SpotlightSelectContext,
    SpotlightSource,
} from "./spotlight-search-types";

export type SpotlightSearchProps = {
    compact?: boolean;
    iconOnly?: boolean;
    commands?: SpotlightCommand[];
    extraSources?: SpotlightSource[];
};

type SpotlightTagMeta = {
    label: string;
    color: string;
};

const EMPTY_COMMANDS: SpotlightCommand[] = [];
const EMPTY_SOURCES: SpotlightSource[] = [];

const { useBreakpoint } = Grid;

const tagMeta = (type: SpotlightItemType): SpotlightTagMeta | undefined => {
    switch (type) {
        case "action":
            return { label: getRes().command, color: "magenta" };
        case "article":
            return { label: getRes().article.label, color: "processing" };
        case "file":
            return { label: getRes().fileManager.title, color: "green" };
        case "template":
            return { label: getRes().websiteTemplate.title, color: "purple" };
        case "plugin":
            return { label: getRes().plugin.title, color: "geekblue" };
        default:
            return undefined;
    }
};

const ResultTag = ({ label, color }: SpotlightTagMeta) => (
    <Tag
        bordered={false}
        color={color}
        style={{
            margin: 0,
            padding: "0 6px",
            lineHeight: "20px",
            fontSize: 12,
            flexShrink: 0,
        }}
    >
        {label}
    </Tag>
);

const ShortcutTag = ({ children, border }: { children: React.ReactNode; border: string }) => {
    const theme = useTheme();
    return (
        <Tag
            style={{
                margin: 0,
                padding: "0 4px",
                border,
                background: theme.colorFillQuaternary,
            }}
        >
            {children}
        </Tag>
    );
};

const SpotlightSearch = ({
    compact = false,
    iconOnly = false,
    commands = EMPTY_COMMANDS,
    extraSources = EMPTY_SOURCES,
}: SpotlightSearchProps) => {
    const [open, setOpen] = useState(false);
    const [keyword, setKeyword] = useState("");
    const [activeIndex, setActiveIndex] = useState(0);
    const [defaultItems, setDefaultItems] = useState<SpotlightItem[]>([]);
    const [searchResults, setSearchResults] = useState<SpotlightItem[]>([]);
    const [recentItems, setRecentItems] = useState<SpotlightItem[]>([]);
    const [defaultLoading, setDefaultLoading] = useState(false);
    const [loading, setLoading] = useState(false);
    const [sourceRevision, setSourceRevision] = useState(0);

    const theme = useTheme();
    const border = `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorder}`;
    const borderSecondary = `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`;
    const navigate = useNavigate();
    const inputRef = useRef<any>(null);
    const axiosInstance = useAxiosBaseInstance();
    const handleSourceRefresh = useCallback(() => setSourceRevision((revision) => revision + 1), []);

    const renderImageIcon = useCallback<SpotlightRenderImageIcon>(
        (src, title, fallback) => {
            if (!src) {
                return fallback;
            }
            return (
                <Avatar
                    shape="square"
                    src={src}
                    icon={fallback}
                    alt={title}
                    size={28}
                    style={{ borderRadius: theme.borderRadiusSM }}
                />
            );
        },
        [theme.borderRadiusSM]
    );

    const baseSources = useMemo(() => createSpotlightSearchSources(), []);
    const commandSources = useMemo(
        () => (commands.length > 0 ? [createSpotlightCommandSource(commands)] : EMPTY_SOURCES),
        [commands]
    );
    const searchSources = useMemo(
        () => [...commandSources, ...baseSources, ...extraSources],
        [baseSources, commandSources, extraSources]
    );

    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if ((e.metaKey || e.ctrlKey) && e.key === "k") {
                e.preventDefault();
                setOpen(true);
            }
        };
        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, []);

    useEffect(() => {
        if (!open) {
            return;
        }

        setKeyword("");
        setSearchResults([]);
        setDefaultItems([]);
        setRecentItems([]);
        setActiveIndex(0);
        setTimeout(() => inputRef.current?.focus(), 100);
    }, [open]);

    useEffect(() => {
        if (!open || keyword.trim()) {
            return;
        }

        let cancelled = false;
        setDefaultLoading(true);
        const context = {
            keyword: "",
            normalizedKeyword: "",
            axiosInstance,
            renderImageIcon,
            onSourceRefresh: handleSourceRefresh,
        };
        void Promise.all(
            searchSources.map(async (source) => {
                try {
                    const sourceItems = await source.empty?.(context);
                    return applySpotlightSourceId(source.id, sourceItems || []);
                } catch (error) {
                    console.error(`Failed to load spotlight default source ${source.id}`, error);
                    return [];
                }
            })
        ).then((results) => {
            if (cancelled) {
                return;
            }
            const items = results.flat();
            setDefaultItems(items);
            setRecentItems(readSpotlightRecentItems(items, renderImageIcon));
            setDefaultLoading(false);
        });

        return () => {
            cancelled = true;
        };
    }, [axiosInstance, handleSourceRefresh, keyword, open, renderImageIcon, searchSources, sourceRevision]);

    useEffect(() => {
        if (!open) {
            setLoading(false);
            return;
        }
        if (!keyword.trim()) {
            setSearchResults([]);
            setActiveIndex(0);
            setLoading(false);
            return;
        }

        let cancelled = false;
        const timer = setTimeout(async () => {
            const normalizedKeyword = keyword.trim().toLowerCase();
            setLoading(true);

            const results = await Promise.all(
                searchSources.map(async (source) => {
                    try {
                        const sourceResults = await source.search({
                            keyword,
                            normalizedKeyword,
                            axiosInstance,
                            renderImageIcon,
                            onSourceRefresh: handleSourceRefresh,
                        });
                        return applySpotlightSourceId(source.id, sourceResults);
                    } catch (error) {
                        console.error(`Failed to run spotlight source ${source.id}`, error);
                        return [];
                    }
                })
            );

            if (!cancelled) {
                setSearchResults(results.flat());
                setActiveIndex(0);
                setLoading(false);
            }
        }, 300);

        return () => {
            cancelled = true;
            clearTimeout(timer);
        };
    }, [axiosInstance, handleSourceRefresh, keyword, open, renderImageIcon, searchSources, sourceRevision]);

    const displayResults = useMemo(() => {
        if (keyword.trim()) {
            return searchResults;
        }

        const recentIds = new Set(recentItems.map((item) => spotlightItemStorageKey(item)));
        const remainingEmptyItems = defaultItems.filter((item) => !recentIds.has(spotlightItemStorageKey(item)));
        return [...recentItems, ...remainingEmptyItems];
    }, [defaultItems, keyword, recentItems, searchResults]);

    const displayLoading = loading || defaultLoading;

    useEffect(() => {
        if (activeIndex > displayResults.length - 1) {
            setActiveIndex(Math.max(displayResults.length - 1, 0));
        }
    }, [activeIndex, displayResults.length]);

    const handleSelect = useCallback(
        async (item: SpotlightItem) => {
            saveSpotlightRecentItem(item);

            const close = () => setOpen(false);
            const navigateTo = (path: string) => navigate(getRealRouteUrl(path));
            if (item.onSelect) {
                await item.onSelect({ keyword, close, navigate: navigateTo });
            } else if (item.path) {
                navigateTo(item.path);
            }
            close();
        },
        [keyword, navigate]
    );

    const handleInputKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === "ArrowDown") {
            e.preventDefault();
            setActiveIndex((prev) => (prev < displayResults.length - 1 ? prev + 1 : prev));
        } else if (e.key === "ArrowUp") {
            e.preventDefault();
            setActiveIndex((prev) => (prev > 0 ? prev - 1 : prev));
        } else if (e.key === "Enter") {
            e.preventDefault();
            if (displayResults.length > 0 && displayResults[activeIndex]) {
                void handleSelect(displayResults[activeIndex]);
            }
        } else if (e.key === "Escape") {
            e.preventDefault();
            setOpen(false);
        }
    };

    useEffect(() => {
        if (open) {
            const el = document.getElementById(`spotlight-item-${activeIndex}`);
            if (el) {
                el.scrollIntoView({ block: "nearest" });
            }
        }
    }, [activeIndex, open]);

    const isMac = typeof window !== "undefined" && /macintosh|mac os x/i.test(navigator.userAgent);
    const screens = useBreakpoint();
    const isMobile = screens.xs;
    const compactModal = screens.md !== true;

    return (
        <>
            {isMobile || iconOnly ? (
                <div
                    onClick={() => setOpen(true)}
                    style={{
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        width: 32,
                        height: 32,
                        borderRadius: theme.borderRadiusLG,
                        cursor: "pointer",
                        border: iconOnly ? border : undefined,
                        backgroundColor: iconOnly ? theme.colorFillQuaternary : undefined,
                        color: theme.colorTextSecondary,
                    }}
                >
                    <SearchOutlined style={{ fontSize: 18 }} />
                </div>
            ) : (
                <Input
                    onClick={() => setOpen(true)}
                    onMouseDown={(e) => e.preventDefault()}
                    prefix={<SearchOutlined style={{ color: theme.colorTextTertiary }} />}
                    suffix={
                        <div
                            style={{
                                fontSize: 12,
                                border,
                                borderRadius: theme.borderRadiusSM,
                                padding: "0 4px",
                                backgroundColor: theme.colorFillQuaternary,
                                color: theme.colorTextTertiary,
                                lineHeight: "18px",
                                userSelect: "none",
                            }}
                        >
                            {isMac ? "⌘ K" : "Ctrl K"}
                        </div>
                    }
                    placeholder={getRes().globalSearchTip}
                    readOnly
                    style={{
                        width: compact ? 180 : 220,
                        cursor: "pointer",
                        caretColor: "transparent",
                        borderRadius: theme.borderRadiusLG,
                    }}
                />
            )}

            <Modal
                open={open}
                onCancel={() => setOpen(false)}
                footer={null}
                closable={false}
                width={compactModal ? "calc(100vw - 32px)" : 600}
                style={{ top: isMobile ? 20 : 100 }}
                styles={{
                    mask: {
                        backdropFilter: "blur(4px)",
                        WebkitBackdropFilter: "blur(4px)",
                        backgroundColor: theme.colorBgMask,
                    },
                    body: {
                        padding: 0,
                    },
                }}
            >
                <div
                    style={{
                        padding: 0,
                        overflow: "hidden",
                        backgroundColor: theme.colorBgElevated,
                        borderRadius: theme.borderRadiusLG,
                    }}
                >
                    <div
                        style={{
                            padding: "16px 20px",
                            borderBottom: borderSecondary,
                        }}
                    >
                        <Input
                            ref={inputRef}
                            variant="borderless"
                            prefix={
                                <SearchOutlined style={{ fontSize: 20, color: theme.colorPrimary, marginRight: 8 }} />
                            }
                            placeholder={getRes().searchTip}
                            value={keyword}
                            onChange={(e) => setKeyword(e.target.value)}
                            onKeyDown={handleInputKeyDown}
                            style={{ fontSize: 18, padding: 0 }}
                        />
                    </div>

                    <div style={{ maxHeight: isMobile ? "70vh" : "60vh", overflowY: "auto", padding: "8px 0" }}>
                        {displayResults.length === 0 && !displayLoading ? (
                            <div
                                style={{
                                    padding: 32,
                                    textAlign: "center",
                                    color: theme.colorTextTertiary,
                                }}
                            >
                                {getRes().notFound}
                            </div>
                        ) : (
                            <List
                                dataSource={displayResults}
                                renderItem={(item, index) => {
                                    const typeTag = tagMeta(item.type);
                                    return (
                                        <List.Item
                                            id={`spotlight-item-${index}`}
                                            onClick={() => void handleSelect(item)}
                                            onMouseEnter={() => setActiveIndex(index)}
                                            style={{
                                                padding: "12px 16px",
                                                margin: "4px 12px",
                                                cursor: "pointer",
                                                borderBottom: "none",
                                                borderRadius: theme.borderRadius,
                                                backgroundColor:
                                                    index === activeIndex ? theme.colorFillSecondary : "transparent",
                                                transition: "background-color 0.1s",
                                            }}
                                        >
                                            <div style={{ display: "flex", alignItems: "center", width: "100%" }}>
                                                <div
                                                    style={{
                                                        display: "flex",
                                                        alignItems: "center",
                                                        justifyContent: "center",
                                                        width: 32,
                                                        height: 32,
                                                        fontSize: 20,
                                                        color:
                                                            index === activeIndex
                                                                ? theme.colorPrimary
                                                                : theme.colorTextSecondary,
                                                        marginRight: 12,
                                                    }}
                                                >
                                                    {item.icon}
                                                </div>
                                                <div
                                                    style={{
                                                        flex: 1,
                                                        display: "flex",
                                                        flexDirection: "column",
                                                        justifyContent: "center",
                                                        minWidth: 0,
                                                    }}
                                                >
                                                    <div
                                                        style={{
                                                            display: "flex",
                                                            alignItems: "center",
                                                            gap: 8,
                                                            overflow: "hidden",
                                                        }}
                                                    >
                                                        <span
                                                            style={{
                                                                fontSize: 15,
                                                                fontWeight: index === activeIndex ? 600 : 400,
                                                                color:
                                                                    index === activeIndex
                                                                        ? theme.colorPrimary
                                                                        : theme.colorText,
                                                                lineHeight: 1.4,
                                                                whiteSpace: "nowrap",
                                                                overflow: "hidden",
                                                                textOverflow: "ellipsis",
                                                            }}
                                                        >
                                                            <HighlightText text={item.title} keyword={keyword} />
                                                        </span>
                                                        {item.recent && (
                                                            <ResultTag label={getRes().recent} color="default" />
                                                        )}
                                                        {typeTag && <ResultTag {...typeTag} />}
                                                    </div>
                                                    {item.subTitle && (
                                                        <div
                                                            style={{
                                                                fontSize: 12,
                                                                color: theme.colorTextTertiary,
                                                                marginTop: 2,
                                                                lineHeight: 1.2,
                                                            }}
                                                        >
                                                            <HighlightText text={item.subTitle} keyword={keyword} />
                                                        </div>
                                                    )}
                                                </div>
                                                {index === activeIndex && (
                                                    <div
                                                        style={{
                                                            color:
                                                                index === activeIndex
                                                                    ? theme.colorPrimary
                                                                    : theme.colorTextTertiary,
                                                            fontSize: 12,
                                                            marginLeft: 8,
                                                            opacity: 0.8,
                                                        }}
                                                    >
                                                        ↵ {getRes().pleaseChoose}
                                                    </div>
                                                )}
                                            </div>
                                        </List.Item>
                                    );
                                }}
                            />
                        )}
                        {displayLoading && (
                            <div style={{ padding: 16, textAlign: "center" }}>
                                <Spin size="small" />
                            </div>
                        )}
                    </div>

                    {!isMobile && (
                        <div
                            style={{
                                padding: "12px 20px",
                                borderTop: borderSecondary,
                                display: "flex",
                                alignItems: "center",
                                gap: 16,
                            }}
                        >
                            <Space size={4}>
                                <ShortcutTag border={border}>↑</ShortcutTag>
                                <ShortcutTag border={border}>↓</ShortcutTag>
                            </Space>
                            <Space size={4}>
                                <ShortcutTag border={border}>↵</ShortcutTag>
                            </Space>
                            <Space size={4}>
                                <ShortcutTag border={border}>Esc</ShortcutTag>
                                <span style={{ fontSize: 12, color: theme.colorTextTertiary }}>{getRes().close}</span>
                            </Space>
                        </div>
                    )}
                </div>
            </Modal>
        </>
    );
};

export default SpotlightSearch;
