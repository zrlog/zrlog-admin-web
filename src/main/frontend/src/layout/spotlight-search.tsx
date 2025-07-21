import {
    SearchOutlined,
    SettingOutlined,
    BarsOutlined,
    DashboardOutlined,
    UserOutlined,
    EditOutlined,
    ContainerOutlined,
    CommentOutlined,
    KeyOutlined,
    DatabaseOutlined,
    ApiOutlined,
    InfoCircleOutlined,
    InboxOutlined,
    LinkOutlined,
    TagsOutlined,
} from "@ant-design/icons";
import { Modal, Input, List, Space, Tag, Spin, Grid } from "antd";
import { useEffect, useState, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { getAppState } from "../base/ConfigProviderApp";
import { useTheme } from "antd-style";
import { getRealRouteUrl, getRes } from "../utils/constants";
import { useAxiosBaseInstance } from "../base/AppBase";

type SearchItem = {
    id: string;
    title: string;
    subTitle?: string;
    path: string;
    icon: React.ReactNode;
    keywords: string[];
    type: "route" | "article" | "action";
};

const { useBreakpoint } = Grid;

const SpotlightSearch = ({ compact = false, iconOnly = false }: { compact?: boolean; iconOnly?: boolean }) => {
    const [open, setOpen] = useState(false);
    const [keyword, setKeyword] = useState("");
    const [activeIndex, setActiveIndex] = useState(0);
    const [articleResults, setArticleResults] = useState<SearchItem[]>([]);
    const [loading, setLoading] = useState(false);

    const theme = useTheme();
    const navigate = useNavigate();
    const inputRef = useRef<any>(null);
    const axiosInstance = useAxiosBaseInstance();

    // 静态路由指令字典
    const staticCommands: SearchItem[] = [
        {
            id: "dashboard",
            title: getRes().index.title,
            path: "/",
            icon: <DashboardOutlined />,
            keywords: ["dashboard", "home", "index", "主页", "首页", "仪表盘"],
            type: "route",
        },
        {
            id: "write",
            title: getRes().index.quickAction.writeArticle,
            path: "/article-edit",
            icon: <EditOutlined />,
            keywords: ["write", "post", "new", "写文章", "新建", "发布"],
            type: "route",
        },
        {
            id: "article",
            title: getRes().article.title,
            path: "/article",
            icon: <ContainerOutlined />,
            keywords: ["article", "list", "文章", "列表", "管理"],
            type: "route",
        },
        {
            id: "article_draft",
            title: getRes().article.status.draft,
            path: "/article?status=draft",
            icon: <InboxOutlined />,
            keywords: ["draft", "article", "草稿", "文章", "caogao"],
            type: "route",
        },
        {
            id: "category",
            title: getRes().articleType.title,
            path: "/article-type",
            icon: <TagsOutlined />,
            keywords: ["category", "type", "分类", "类别"],
            type: "route",
        },
        {
            id: "comment",
            title: getRes().comment.title,
            path: "/comment",
            icon: <CommentOutlined />,
            keywords: ["comment", "reply", "评论", "回复", "留言"],
            type: "route",
        },
        {
            id: "nav",
            title: getRes().nav.title,
            path: "/nav",
            icon: <BarsOutlined />,
            keywords: ["nav", "menu", "导航", "菜单"],
            type: "route",
        },
        {
            id: "link",
            title: getRes().link.title,
            path: "/link",
            icon: <LinkOutlined />,
            keywords: ["link", "friend", "友链", "链接"],
            type: "route",
        },
        {
            id: "website_base",
            title: getRes().website.title,
            path: "/website/admin",
            icon: <SettingOutlined />,
            keywords: ["setting", "base", "基本", "设置", "配置"],
            type: "route",
        },
        {
            id: "website_blog",
            title: getRes().websiteBlog.title,
            path: "/website/blog",
            icon: <SettingOutlined />,
            keywords: ["blog", "setting", "博客", "设置"],
            type: "route",
        },
        {
            id: "website_seo",
            title: getRes().websiteOther.title,
            path: "/website/other",
            icon: <SettingOutlined />,
            keywords: ["seo", "other", "setting", "优化"],
            type: "route",
        },
        {
            id: "website_ai",
            title: getRes().websiteAi.title,
            path: "/website/ai",
            icon: <SettingOutlined />,
            keywords: ["ai", "gemini", "chatgpt", "人工智能"],
            type: "route",
        },
        {
            id: "theme_center",
            title: getRes().templateCenter.title,
            path: "/template-center",
            icon: <SettingOutlined />,
            keywords: ["theme", "template", "主题", "模板", "外观"],
            type: "route",
        },
        {
            id: "theme_setting",
            title: getRes().websiteTemplate.title,
            path: "/website/template",
            icon: <SettingOutlined />,
            keywords: ["theme", "setting", "主题", "设置"],
            type: "route",
        },
        {
            id: "system",
            title: getRes().system.info,
            path: "/system",
            icon: <DashboardOutlined />,
            keywords: ["system", "info", "系统", "信息", "环境"],
            type: "route",
        },
        {
            id: "user",
            title: getRes().user.title,
            path: "/user",
            icon: <UserOutlined />,
            keywords: ["user", "profile", "个人", "信息", "用户", "头像"],
            type: "route",
        },
        {
            id: "pwd",
            title: getRes().accountSecurity.title,
            path: "/account-security",
            icon: <KeyOutlined />,
            keywords: ["password", "security", "mfa", "account", "密码", "修改", "安全", "验证"],
            type: "route",
        },
        {
            id: "backup",
            title: getRes().index.quickAction.backupFiles,
            path: "/plugin?page=backup-sql-file/files",
            icon: <DatabaseOutlined />,
            keywords: ["backup", "restore", "sql", "备份", "恢复", "数据库"],
            type: "route",
        },
        {
            id: "plugin",
            title: getRes().plugin.title,
            path: "/plugin",
            icon: <ApiOutlined />,
            keywords: ["plugin", "插件", "chajian"],
            type: "route",
        },
        {
            id: "version",
            title: getRes().websiteVersion.title,
            path: "/website/version",
            icon: <InfoCircleOutlined />,
            keywords: ["version", "update", "版本", "更新", "banben", "gengxin"],
            type: "route",
        },
    ];

    // 全局快捷键监听 Cmd+K / Ctrl+K
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

    // 每次打开时自动聚焦并加载最近记录
    const [recentItems, setRecentItems] = useState<SearchItem[]>([]);
    useEffect(() => {
        if (open) {
            setKeyword("");
            setArticleResults([]);
            setActiveIndex(0);

            try {
                const stored = JSON.parse(localStorage.getItem("zrlog_spotlight_recent") || "[]");
                const parsedRecent = stored.map((item: any) => {
                    let icon: React.ReactNode = <SearchOutlined />;
                    if (item.type === "article") {
                        icon = <EditOutlined />;
                    } else {
                        const staticCmd = staticCommands.find((c) => c.id === item.id);
                        if (staticCmd) icon = staticCmd.icon;
                    }
                    return { ...item, icon, isRecent: true };
                });
                setRecentItems(parsedRecent);
            } catch (e) {
                setRecentItems([]);
            }

            setTimeout(() => inputRef.current?.focus(), 100);
        }
    }, [open]);

    // 搜索过滤与后端请求
    useEffect(() => {
        if (!keyword.trim()) {
            setArticleResults([]);
            setActiveIndex(0);
            return;
        }

        const timer = setTimeout(async () => {
            setLoading(true);
            try {
                // 向后端发起文章搜索请求
                const { data } = await axiosInstance.get("/api/admin/article", {
                    params: { key: keyword, page: 1, size: 5 },
                });
                if (data && data.data && data.data.rows) {
                    const articles: SearchItem[] = data.data.rows.map((row: any) => ({
                        id: `article-${row.id}`,
                        title: row.title,
                        subTitle: row.typeName,
                        path: `/article-edit?id=${row.id}`,
                        icon: <EditOutlined />,
                        keywords: [],
                        type: "article",
                    }));
                    setArticleResults(articles);
                }
            } catch (error) {
                console.error("Failed to fetch articles", error);
            } finally {
                setLoading(false);
            }
        }, 300); // 300ms 防抖

        return () => clearTimeout(timer);
    }, [keyword]);

    // 合并静态指令与文章结果
    let displayResults: SearchItem[] = [];
    if (keyword.trim() === "") {
        // 输入为空时，显示最近记录 + 静态路由（去重）
        const recentIds = new Set(recentItems.map((r) => r.id));
        const remainingStatic = staticCommands.filter((c) => !recentIds.has(c.id));
        displayResults = [...recentItems, ...remainingStatic];
    } else {
        const filteredCommands = staticCommands.filter(
            (cmd) =>
                cmd.title.toLowerCase().includes(keyword.toLowerCase()) ||
                cmd.keywords.some((k) => k.toLowerCase().includes(keyword.toLowerCase()))
        );
        displayResults = [...filteredCommands, ...articleResults];
    }

    const handleSelect = (item: SearchItem) => {
        // 保存到最近记录 (最多保留 5 条)
        try {
            const stored = JSON.parse(localStorage.getItem("zrlog_spotlight_recent") || "[]");
            const newStored = [
                { id: item.id, title: item.title, subTitle: item.subTitle, path: item.path, type: item.type },
                ...stored.filter((r: any) => r.id !== item.id),
            ].slice(0, 5);
            localStorage.setItem("zrlog_spotlight_recent", JSON.stringify(newStored));
        } catch (e) {
            console.error(e);
        }

        navigate(getRealRouteUrl(item.path));
        setOpen(false);
    };

    // 键盘导航
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
                handleSelect(displayResults[activeIndex]);
            }
        } else if (e.key === "Escape") {
            e.preventDefault();
            setOpen(false);
        }
    };

    // 监听 activeIndex 变化并自动滚动到对应选项
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
                        border: iconOnly ? `1px solid ${getAppState().dark ? "#3a3a3a" : "#d9d9d9"}` : undefined,
                        backgroundColor: iconOnly
                            ? getAppState().dark
                                ? "rgba(255,255,255,0.04)"
                                : "rgba(0,0,0,0.02)"
                            : undefined,
                        color: getAppState().dark ? "#aaa" : "#555",
                    }}
                >
                    <SearchOutlined style={{ fontSize: 18 }} />
                </div>
            ) : (
                <Input
                    onClick={() => setOpen(true)}
                    onMouseDown={(e) => e.preventDefault()}
                    prefix={<SearchOutlined style={{ color: getAppState().dark ? "#888" : "#999" }} />}
                    suffix={
                        <div
                            style={{
                                fontSize: 12,
                                border: `1px solid ${getAppState().dark ? "#444" : "#ddd"}`,
                                borderRadius: theme.borderRadiusSM,
                                padding: "0 4px",
                                backgroundColor: getAppState().dark ? "rgba(255,255,255,0.05)" : "#fafafa",
                                color: getAppState().dark ? "#888" : "#999",
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
                width={600}
                style={{ top: isMobile ? 20 : 100 }}
                styles={{
                    mask: {
                        backdropFilter: "blur(4px)",
                        WebkitBackdropFilter: "blur(4px)",
                        backgroundColor: getAppState().dark ? "rgba(0, 0, 0, 0.45)" : "rgba(255, 255, 255, 0.45)",
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
                        backgroundColor: getAppState().dark ? "#1f1f1f" : "#ffffff",
                        borderRadius: theme.borderRadiusLG,
                    }}
                >
                    <div
                        style={{
                            padding: "16px 20px",
                            borderBottom: `1px solid ${getAppState().dark ? "#333" : "#f0f0f0"}`,
                        }}
                    >
                        <Input
                            ref={inputRef}
                            variant="borderless"
                            prefix={
                                <SearchOutlined
                                    style={{ fontSize: 20, color: getAppState().colorPrimary, marginRight: 8 }}
                                />
                            }
                            placeholder={getRes().searchTip}
                            value={keyword}
                            onChange={(e) => setKeyword(e.target.value)}
                            onKeyDown={handleInputKeyDown}
                            style={{ fontSize: 18, padding: 0 }}
                        />
                    </div>

                    <div style={{ maxHeight: isMobile ? "70vh" : "60vh", overflowY: "auto", padding: "8px 0" }}>
                        {displayResults.length === 0 && !loading ? (
                            <div
                                style={{
                                    padding: 32,
                                    textAlign: "center",
                                    color: getAppState().dark ? "#888" : "#999",
                                }}
                            >
                                {getRes().notFound}
                            </div>
                        ) : (
                            <List
                                dataSource={displayResults}
                                renderItem={(item, index) => (
                                    <List.Item
                                        id={`spotlight-item-${index}`}
                                        onClick={() => handleSelect(item)}
                                        onMouseEnter={() => setActiveIndex(index)}
                                        style={{
                                            padding: "12px 16px",
                                            margin: "4px 12px",
                                            cursor: "pointer",
                                            borderBottom: "none",
                                            borderRadius: theme.borderRadius,
                                            backgroundColor:
                                                index === activeIndex
                                                    ? getAppState().dark
                                                        ? "rgba(255,255,255,0.08)"
                                                        : `${getAppState().colorPrimary}15`
                                                    : "transparent",
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
                                                            ? getAppState().colorPrimary
                                                            : getAppState().dark
                                                            ? "#aaa"
                                                            : "#555",
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
                                                                    ? getAppState().colorPrimary
                                                                    : getAppState().dark
                                                                    ? "#eee"
                                                                    : "#333",
                                                            lineHeight: 1.4,
                                                            whiteSpace: "nowrap",
                                                            overflow: "hidden",
                                                            textOverflow: "ellipsis",
                                                        }}
                                                        dangerouslySetInnerHTML={{ __html: item.title }}
                                                    />
                                                    {(item as any).isRecent && (
                                                        <Tag
                                                            bordered={false}
                                                            color="default"
                                                            style={{
                                                                margin: 0,
                                                                padding: "0 6px",
                                                                lineHeight: "20px",
                                                                fontSize: 12,
                                                                flexShrink: 0,
                                                            }}
                                                        >
                                                            {getRes().recent}
                                                        </Tag>
                                                    )}
                                                    {item.type === "article" && (
                                                        <Tag
                                                            bordered={false}
                                                            color="blue"
                                                            style={{
                                                                margin: 0,
                                                                padding: "0 6px",
                                                                lineHeight: "20px",
                                                                fontSize: 12,
                                                                flexShrink: 0,
                                                            }}
                                                        >
                                                            {getRes().article.label}
                                                        </Tag>
                                                    )}
                                                </div>
                                                {item.subTitle && (
                                                    <div
                                                        style={{
                                                            fontSize: 12,
                                                            color: getAppState().dark ? "#888" : "#aaa",
                                                            marginTop: 2,
                                                            lineHeight: 1.2,
                                                        }}
                                                    >
                                                        {item.subTitle}
                                                    </div>
                                                )}
                                            </div>
                                            {index === activeIndex && (
                                                <div
                                                    style={{
                                                        color:
                                                            index === activeIndex
                                                                ? getAppState().colorPrimary
                                                                : getAppState().dark
                                                                ? "#888"
                                                                : "#999",
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
                                )}
                            />
                        )}
                        {loading && (
                            <div style={{ padding: 16, textAlign: "center" }}>
                                <Spin size="small" />
                            </div>
                        )}
                    </div>

                    {!isMobile && (
                        <div
                            style={{
                                padding: "12px 20px",
                                borderTop: `1px solid ${getAppState().dark ? "#333" : "#f0f0f0"}`,
                                display: "flex",
                                alignItems: "center",
                                gap: 16,
                            }}
                        >
                            <Space size={4}>
                                <Tag
                                    style={{
                                        margin: 0,
                                        padding: "0 4px",
                                        border: `1px solid ${getAppState().dark ? "#555" : "#ddd"}`,
                                        background: getAppState().dark ? "#2a2a2a" : "#fff",
                                    }}
                                >
                                    ↑
                                </Tag>
                                <Tag
                                    style={{
                                        margin: 0,
                                        padding: "0 4px",
                                        border: `1px solid ${getAppState().dark ? "#555" : "#ddd"}`,
                                        background: getAppState().dark ? "#2a2a2a" : "#fff",
                                    }}
                                >
                                    ↓
                                </Tag>
                            </Space>
                            <Space size={4}>
                                <Tag
                                    style={{
                                        margin: 0,
                                        padding: "0 4px",
                                        border: `1px solid ${getAppState().dark ? "#555" : "#ddd"}`,
                                        background: getAppState().dark ? "#2a2a2a" : "#fff",
                                    }}
                                >
                                    ↵
                                </Tag>
                            </Space>
                            <Space size={4}>
                                <Tag
                                    style={{
                                        margin: 0,
                                        padding: "0 4px",
                                        border: `1px solid ${getAppState().dark ? "#555" : "#ddd"}`,
                                        background: getAppState().dark ? "#2a2a2a" : "#fff",
                                    }}
                                >
                                    Esc
                                </Tag>
                                <span style={{ fontSize: 12, color: getAppState().dark ? "#888" : "#999" }}>
                                    {getRes().close}
                                </span>
                            </Space>
                        </div>
                    )}
                </div>
            </Modal>
        </>
    );
};

export default SpotlightSearch;
