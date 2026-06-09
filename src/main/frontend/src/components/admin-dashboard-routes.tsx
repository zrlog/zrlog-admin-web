import { ComponentType, lazy, ReactNode } from "react";
import {
    ApiFilled,
    ApiOutlined,
    AppstoreFilled,
    AppstoreOutlined,
    BarsOutlined,
    ContainerFilled,
    ContainerOutlined,
    CopyrightCircleFilled,
    CopyrightCircleOutlined,
    DashboardFilled,
    DashboardOutlined,
    DatabaseFilled,
    DatabaseOutlined,
    EditFilled,
    EditOutlined,
    ExperimentFilled,
    ExperimentOutlined,
    FileTextFilled,
    FileTextOutlined,
    FolderOpenFilled,
    FolderOpenOutlined,
    HomeFilled,
    HomeOutlined,
    InfoCircleFilled,
    InfoCircleOutlined,
    LinkOutlined,
    LockFilled,
    LockOutlined,
    MessageFilled,
    MessageOutlined,
    ReadFilled,
    ReadOutlined,
    RobotFilled,
    RobotOutlined,
    SafetyCertificateFilled,
    SafetyCertificateOutlined,
    SearchOutlined,
    SettingFilled,
    SettingOutlined,
    SkinFilled,
    SkinOutlined,
    SlidersFilled,
    SlidersOutlined,
    SyncOutlined,
    TagsFilled,
    TagsOutlined,
    UserOutlined,
} from "@ant-design/icons";
import { buildUriPaths } from "../base/AppBase";
import { getRes } from "../utils/constants";
import Index from "./index";
import Comment from "./comment";
import Plugin from "./plugin";
import WebSite, { WebSiteProps } from "./website";
import Template from "./template";
import Type from "./type";
import TagManagement from "./tag";
import Link from "./link";
import Nav from "./nav";
import Article from "./article";
import ArticleEdit from "./articleEdit";
import { ArticleEditProps } from "./articleEdit/index.types";
import User from "./user";
import TemplateCenter from "./template/template-center";
import AccountSecurity from "./account-security";
import Upgrade from "./upgrade";
import TemplateConfig from "./template/template-config";
import UnknownErrorPage, { ErrorPageProps } from "./unknown-error-page";
import Offline from "../common/Offline";
import System from "./system";
import Version from "./website/version";
import StaticSite from "./StaticSite";
import Dev from "./dev";
import FileManagerPage from "./file-manager-page";
import { RiWebhookFill } from "../icons/ri/RiWebhookFill";
import { RiWebhookLine } from "../icons/ri/RiWebhookLine";

const AsyncArticleEdit = lazy(() => import("components/articleEdit"));
const AsyncOffline = lazy(() => import("common/Offline"));
const AsyncComment = lazy(() => import("components/comment"));
const AsyncPlugin = lazy(() => import("components/plugin"));
const AsyncIndex = lazy(() => import("components/index"));
const AsyncWebSite = lazy(() => import("components/website"));
const AsyncType = lazy(() => import("components/type"));
const AsyncTagManagement = lazy(() => import("components/tag"));
const AsyncLink = lazy(() => import("components/link"));
const AsyncNav = lazy(() => import("components/nav"));
const AsyncUpgrade = lazy(() => import("components/upgrade"));
const AsyncTemplateCenter = lazy(() => import("components/template/template-center"));
const AsyncTemplate = lazy(() => import("components/template"));
const AsyncTemplateConfig = lazy(() => import("components/template/template-config"));
const AsyncAccountSecurity = lazy(() => import("components/account-security"));
const AsyncArticle = lazy(() => import("components/article"));
const AsyncUser = lazy(() => import("components/user"));
const AsyncError = lazy(() => import("components/unknown-error-page"));
const AsyncSystem = lazy(() => import("components/system"));
const AsyncStaticSite = lazy(() => import("components/StaticSite"));
const DevAsync = lazy(() => import("components/dev"));
const AsyncFileManagerPage = lazy(() => import("components/file-manager-page"));

export type AdminDashboardRouteSearchItem = {
    id: string;
    title: string;
    path: string;
    icon: ReactNode;
    keywords: string[];
};

type AdminDashboardRouteSearchConfig = {
    id: string;
    title: () => string;
    path?: string;
    iconKey: AdminDashboardRouteIconKey;
    keywords: string[];
    visible?: () => boolean;
};

export type AdminDashboardRouteIconKey =
    | "api"
    | "appstore"
    | "bars"
    | "comment"
    | "container"
    | "copyright"
    | "dashboard"
    | "database"
    | "edit"
    | "experiment"
    | "file-text"
    | "folder"
    | "home"
    | "info"
    | "link"
    | "lock"
    | "read"
    | "robot"
    | "safety-certificate"
    | "search"
    | "setting"
    | "skin"
    | "sliders"
    | "sync"
    | "tags"
    | "user"
    | "webhook";

export const renderAdminDashboardRouteIcon = (
    iconKey: AdminDashboardRouteIconKey,
    selected = false,
    fontSize?: number
): ReactNode => {
    const style = fontSize ? { fontSize } : undefined;
    switch (iconKey) {
        case "api":
            return selected ? <ApiFilled style={style} /> : <ApiOutlined style={style} />;
        case "appstore":
            return selected ? <AppstoreFilled style={style} /> : <AppstoreOutlined style={style} />;
        case "bars":
            return <BarsOutlined style={style} />;
        case "comment":
            return selected ? <MessageFilled style={style} /> : <MessageOutlined style={style} />;
        case "container":
            return selected ? <ContainerFilled style={style} /> : <ContainerOutlined style={style} />;
        case "copyright":
            return selected ? <CopyrightCircleFilled style={style} /> : <CopyrightCircleOutlined style={style} />;
        case "dashboard":
            return selected ? <DashboardFilled style={style} /> : <DashboardOutlined style={style} />;
        case "database":
            return selected ? <DatabaseFilled style={style} /> : <DatabaseOutlined style={style} />;
        case "edit":
            return selected ? <EditFilled style={style} /> : <EditOutlined style={style} />;
        case "experiment":
            return selected ? <ExperimentFilled style={style} /> : <ExperimentOutlined style={style} />;
        case "file-text":
            return selected ? <FileTextFilled style={style} /> : <FileTextOutlined style={style} />;
        case "folder":
            return selected ? <FolderOpenFilled style={style} /> : <FolderOpenOutlined style={style} />;
        case "home":
            return selected ? <HomeFilled style={style} /> : <HomeOutlined style={style} />;
        case "info":
            return selected ? <InfoCircleFilled style={style} /> : <InfoCircleOutlined style={style} />;
        case "link":
            return <LinkOutlined style={style} />;
        case "lock":
            return selected ? <LockFilled style={style} /> : <LockOutlined style={style} />;
        case "read":
            return selected ? <ReadFilled style={style} /> : <ReadOutlined style={style} />;
        case "robot":
            return selected ? <RobotFilled style={style} /> : <RobotOutlined style={style} />;
        case "safety-certificate":
            return selected ? <SafetyCertificateFilled style={style} /> : <SafetyCertificateOutlined style={style} />;
        case "search":
            return <SearchOutlined style={style} />;
        case "setting":
            return selected ? <SettingFilled style={style} /> : <SettingOutlined style={style} />;
        case "skin":
            return selected ? <SkinFilled style={style} /> : <SkinOutlined style={style} />;
        case "sliders":
            return selected ? <SlidersFilled style={style} /> : <SlidersOutlined style={style} />;
        case "sync":
            return <SyncOutlined style={style} />;
        case "tags":
            return selected ? <TagsFilled style={style} /> : <TagsOutlined style={style} />;
        case "user":
            return <UserOutlined style={style} />;
        case "webhook":
            return selected ? <RiWebhookFill style={style} /> : <RiWebhookLine style={style} />;
    }
};

export type AdminDashboardRouteDefinition<P = any> = {
    paths: string[];
    lazy: ComponentType<P>;
    fallback: ComponentType<P>;
    props?: Partial<P>;
    getComponentKey?: (data: any, cacheKey: string) => string | undefined;
    search?: AdminDashboardRouteSearchConfig[];
};

export const createAdminDashboardRoutes = (
    articleEditProps: Partial<ArticleEditProps> = {}
): AdminDashboardRouteDefinition[] => [
    {
        paths: [...buildUriPaths("index"), ...buildUriPaths("")],
        lazy: AsyncIndex,
        fallback: Index,
        search: [
            {
                id: "dashboard",
                title: () => getRes().index.title,
                path: "/",
                iconKey: "dashboard",
                keywords: ["dashboard", "home", "index", "主页", "首页", "仪表盘"],
            },
        ],
    },
    {
        paths: buildUriPaths("comment"),
        lazy: AsyncComment,
        fallback: Comment,
        search: [
            {
                id: "comment",
                title: () => getRes().comment.title,
                iconKey: "comment",
                keywords: ["comment", "reply", "评论", "回复", "留言"],
            },
        ],
    },
    {
        paths: buildUriPaths("plugin"),
        lazy: AsyncPlugin,
        fallback: Plugin,
        search: [
            {
                id: "plugin",
                title: () => getRes().plugin.title,
                iconKey: "api",
                keywords: ["plugin", "插件", "chajian"],
            },
            {
                id: "backup",
                title: () => getRes().index.quickAction.backupFiles,
                path: "/plugin?page=backup-sql-file/files",
                iconKey: "database",
                keywords: ["backup", "restore", "sql", "备份", "恢复", "数据库"],
            },
        ],
    },
    {
        paths: buildUriPaths("website"),
        lazy: AsyncWebSite,
        fallback: WebSite,
        props: { activeKey: "basic" } as WebSiteProps,
        search: [
            {
                id: "website",
                title: () => getRes().website.title,
                iconKey: "home",
                keywords: ["setting", "base", "basic", "网站", "基本", "设置", "配置"],
            },
        ],
    },
    {
        paths: buildUriPaths("website/admin"),
        lazy: AsyncWebSite,
        fallback: WebSite,
        props: { activeKey: "admin" } as WebSiteProps,
        search: [
            {
                id: "website_admin",
                title: () => getRes().websiteAdmin.title,
                iconKey: "sliders",
                keywords: ["admin", "setting", "管理", "后台", "设置"],
            },
        ],
    },
    {
        paths: buildUriPaths("website/webhook"),
        lazy: AsyncWebSite,
        fallback: WebSite,
        props: { activeKey: "webhook" } as WebSiteProps,
        search: [
            {
                id: "website_webhook",
                title: () => getRes().websiteWebhook.title,
                iconKey: "webhook",
                keywords: ["webhook", "token", "message", "notice", "站内信", "通知", "令牌", "外部"],
                visible: () => getRes().feature_webhook_enabled === true,
            },
        ],
    },
    {
        paths: buildUriPaths("website/template"),
        lazy: AsyncTemplate,
        fallback: Template,
    },
    {
        paths: buildUriPaths("website/other"),
        lazy: AsyncWebSite,
        fallback: WebSite,
        props: { activeKey: "other" } as WebSiteProps,
        search: [
            {
                id: "website_seo",
                title: () => getRes().websiteOther.title,
                iconKey: "file-text",
                keywords: ["seo", "other", "setting", "优化"],
            },
        ],
    },
    {
        paths: buildUriPaths("website/blog"),
        lazy: AsyncWebSite,
        fallback: WebSite,
        props: { activeKey: "blog" } as WebSiteProps,
        search: [
            {
                id: "website_blog",
                title: () => getRes().websiteBlog.title,
                iconKey: "read",
                keywords: ["blog", "setting", "博客", "设置"],
            },
        ],
    },
    {
        paths: buildUriPaths("website/ai"),
        lazy: AsyncWebSite,
        fallback: WebSite,
        props: { activeKey: "ai" } as WebSiteProps,
        search: [
            {
                id: "website_ai",
                title: () => getRes().websiteAi.title,
                iconKey: "robot",
                keywords: ["ai", "gemini", "chatgpt", "人工智能"],
            },
        ],
    },
    {
        paths: buildUriPaths("website/privacy"),
        lazy: AsyncWebSite,
        fallback: WebSite,
        props: { activeKey: "privacy" } as WebSiteProps,
        search: [
            {
                id: "website_privacy",
                title: () => getRes().websitePrivacy.title,
                iconKey: "safety-certificate",
                keywords: ["privacy", "personal", "data", "gdpr", "隐私", "个人", "数据"],
                visible: () => getRes().feature_personal_data_enabled === true,
            },
        ],
    },
    {
        paths: buildUriPaths("website/article-edit"),
        lazy: AsyncWebSite,
        fallback: WebSite,
        props: { activeKey: "article-edit" } as WebSiteProps,
        search: [
            {
                id: "website_article_content",
                title: () => getRes().websiteArticleEdit.title,
                iconKey: "edit",
                keywords: ["editor", "markdown", "article", "content", "cover", "编辑器", "文章", "封面"],
            },
        ],
    },
    {
        paths: buildUriPaths("website/content-protector"),
        lazy: AsyncWebSite,
        fallback: WebSite,
        props: { activeKey: "content-protector" } as WebSiteProps,
        search: [
            {
                id: "website_content_protector",
                title: () => getRes().websiteContentProtector.title,
                iconKey: "copyright",
                keywords: ["copyright", "license", "content", "版权", "协议", "保护"],
            },
        ],
    },
    {
        paths: buildUriPaths("website/upgrade"),
        lazy: AsyncWebSite,
        fallback: WebSite,
        props: { activeKey: "upgrade" } as WebSiteProps,
    },
    {
        paths: buildUriPaths("website/lab"),
        lazy: AsyncWebSite,
        fallback: WebSite,
        props: { activeKey: "lab" } as WebSiteProps,
        search: [
            {
                id: "website_lab",
                title: () => getRes().websiteLab.title,
                iconKey: "experiment",
                keywords: ["lab", "feature", "experiment", "实验室", "新特性", "开关"],
            },
        ],
    },
    {
        paths: buildUriPaths("website/version"),
        lazy: Version,
        fallback: Version,
        search: [
            {
                id: "version",
                title: () => getRes().websiteVersion.title,
                iconKey: "info",
                keywords: ["version", "update", "版本", "更新", "banben", "gengxin"],
            },
        ],
    },
    {
        paths: buildUriPaths("article-type"),
        lazy: AsyncType,
        fallback: Type,
        search: [
            {
                id: "category",
                title: () => getRes().articleType.title,
                iconKey: "appstore",
                keywords: ["category", "type", "分类", "类别"],
            },
        ],
    },
    {
        paths: buildUriPaths("tag"),
        lazy: AsyncTagManagement,
        fallback: TagManagement,
        search: [
            {
                id: "tag",
                title: () => getRes().tagManage.title,
                iconKey: "tags",
                keywords: ["tag", "tags", "标签", "biaoqian"],
            },
        ],
    },
    {
        paths: buildUriPaths("link"),
        lazy: AsyncLink,
        fallback: Link,
        search: [
            {
                id: "link",
                title: () => getRes().link.title,
                iconKey: "link",
                keywords: ["link", "friend", "友链", "链接"],
            },
        ],
    },
    {
        paths: buildUriPaths("nav"),
        lazy: AsyncNav,
        fallback: Nav,
        search: [
            {
                id: "nav",
                title: () => getRes().nav.title,
                iconKey: "bars",
                keywords: ["nav", "menu", "导航", "菜单"],
            },
        ],
    },
    {
        paths: buildUriPaths("article"),
        lazy: AsyncArticle,
        fallback: Article,
        search: [
            {
                id: "article",
                title: () => getRes().article.title,
                iconKey: "container",
                keywords: ["article", "list", "文章", "列表", "管理"],
            },
            {
                id: "article_draft",
                title: () => getRes().article.status.draft,
                path: "/article?status=draft",
                iconKey: "container",
                keywords: ["draft", "article", "草稿", "文章", "caogao"],
            },
        ],
    },
    {
        paths: buildUriPaths("article-edit"),
        lazy: AsyncArticleEdit,
        fallback: ArticleEdit,
        props: articleEditProps as ArticleEditProps,
        getComponentKey: (data, cacheKey) => {
            const article = (data as ArticleEditProps["data"] | undefined)?.article;
            if (!article) {
                return cacheKey;
            }
            return `${cacheKey}:${article.logId || "draft"}:${article.version}:${article.lastUpdateDate || 0}`;
        },
        search: [
            {
                id: "write",
                title: () => getRes().index.quickAction.writeArticle,
                iconKey: "edit",
                keywords: ["write", "post", "new", "写文章", "新建", "发布"],
            },
        ],
    },
    {
        paths: buildUriPaths("user"),
        lazy: AsyncUser,
        fallback: User,
        search: [
            {
                id: "user",
                title: () => getRes().user.title,
                iconKey: "user",
                keywords: ["user", "profile", "个人", "信息", "用户", "头像"],
            },
        ],
    },
    {
        paths: buildUriPaths("template-center"),
        lazy: AsyncTemplateCenter,
        fallback: TemplateCenter,
        search: [
            {
                id: "theme_center",
                title: () => getRes().templateCenter.title,
                iconKey: "skin",
                keywords: ["theme", "template", "主题", "模板", "外观"],
            },
        ],
    },
    {
        paths: buildUriPaths("template"),
        lazy: AsyncTemplate,
        fallback: Template,
        search: [
            {
                id: "theme_setting",
                title: () => getRes().websiteTemplate.title,
                iconKey: "skin",
                keywords: ["theme", "setting", "主题", "设置"],
            },
        ],
    },
    {
        paths: [...buildUriPaths("account-security"), ...buildUriPaths("user-update-password")],
        lazy: AsyncAccountSecurity,
        fallback: AccountSecurity,
        search: [
            {
                id: "account-security",
                title: () => getRes().accountSecurity.title,
                path: "/account-security",
                iconKey: "lock",
                keywords: ["password", "security", "mfa", "account", "密码", "修改", "安全", "验证"],
            },
        ],
    },
    {
        paths: buildUriPaths("upgrade"),
        lazy: AsyncUpgrade,
        fallback: Upgrade,
    },
    {
        paths: buildUriPaths("template-config"),
        lazy: AsyncTemplateConfig,
        fallback: TemplateConfig,
    },
    {
        paths: buildUriPaths("403"),
        lazy: AsyncError,
        fallback: UnknownErrorPage,
        props: {
            code: 403,
        } as ErrorPageProps,
    },
    {
        paths: buildUriPaths("500"),
        lazy: AsyncError,
        fallback: UnknownErrorPage,
        props: {
            code: 500,
        } as ErrorPageProps,
    },
    {
        paths: buildUriPaths("offline"),
        lazy: AsyncOffline,
        fallback: Offline,
    },
    {
        paths: buildUriPaths("system"),
        lazy: AsyncSystem,
        fallback: System,
        search: [
            {
                id: "system",
                title: () => getRes().system.info,
                iconKey: "info",
                keywords: ["system", "info", "系统", "信息", "环境"],
            },
        ],
    },
    {
        paths: buildUriPaths("static-site"),
        lazy: AsyncStaticSite,
        fallback: StaticSite,
    },
    {
        paths: buildUriPaths("dev"),
        lazy: DevAsync,
        fallback: Dev,
    },
    {
        paths: buildUriPaths("file-manager"),
        lazy: AsyncFileManagerPage,
        fallback: FileManagerPage,
        search: [
            {
                id: "file-manager",
                title: () => getRes().fileManager.title,
                iconKey: "folder",
                keywords: ["file", "manager", "asset", "resource", "image", "upload", "文件", "资源", "图片", "素材"],
            },
        ],
    },
];

const toSearchPath = (route: AdminDashboardRouteDefinition, search: AdminDashboardRouteSearchConfig) => {
    if (search.path) {
        return search.path;
    }
    const firstPath = route.paths[0];
    if (firstPath === "" || firstPath === "/") {
        return "/";
    }
    return firstPath.startsWith("/") ? firstPath : `/${firstPath}`;
};

export const getAdminDashboardRouteSearchItems = (): AdminDashboardRouteSearchItem[] =>
    createAdminDashboardRoutes().flatMap((route) =>
        (route.search || [])
            .filter((search) => !search.visible || search.visible())
            .map((search) => ({
                id: search.id,
                title: search.title(),
                path: toSearchPath(route, search),
                icon: renderAdminDashboardRouteIcon(search.iconKey),
                keywords: search.keywords,
            }))
    );
