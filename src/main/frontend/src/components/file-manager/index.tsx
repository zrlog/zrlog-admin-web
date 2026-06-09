import {
    CSSProperties,
    FunctionComponent,
    KeyboardEvent,
    ReactNode,
    useCallback,
    useEffect,
    useMemo,
    useRef,
    useState,
} from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
    App,
    Button,
    Checkbox,
    Divider,
    Drawer,
    Dropdown,
    Empty,
    Grid,
    Input,
    Menu,
    Modal,
    Popconfirm,
    Select,
    Space,
    Tag,
    theme,
    Tooltip,
    Typography,
} from "antd";
import type { MenuProps } from "antd";
import {
    AppstoreOutlined,
    CopyOutlined,
    DeleteOutlined,
    DownloadOutlined,
    EditOutlined,
    FileImageOutlined,
    FileTextOutlined,
    FileZipOutlined,
    FolderAddOutlined,
    LinkOutlined,
    QuestionCircleOutlined,
    ReloadOutlined,
    UploadOutlined,
    VideoCameraOutlined,
    WarningOutlined,
} from "@ant-design/icons";
import { useTheme } from "antd-style";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { addToCache, getCacheByKey } from "../../utils/cache";
import { getLabelValueSeparator, getRealRouteUrl, getRes, tryAppendBackendServerUrl } from "../../utils/constants";
import TimeAgo from "@editor/dist/src/editor/TimeAgo";
import FileManagerView from "./view";
import { FileEntry, formatSize, getFileIcon, hasAction, hasDirectoryAction, isExternalPath, isImage } from "./shared";
import BaseDragger from "@editor/dist/src/editor/common/BaseDragger";
import SidebarNavItem from "../common/SidebarNavItem";
import BackendImage, { resolveBackendImageSrc } from "../../common/BackendImage";
import { postRefreshCacheSse } from "../../utils/sse-utils";
import type { ApiResponse } from "../../type";
import { buildBackgroundTaskResult } from "../../utils/background-task-result";

const { useBreakpoint } = Grid;
const { Paragraph, Text } = Typography;

const FILE_MANAGER_API = "/api/admin/file-manager";
const UPLOAD_API = "/api/admin/upload";
const FILE_MANAGER_VIEW_MODE_CACHE_KEY = "fileManager/viewMode";
const EXTERNAL_ROOT = "/external";

export type FileManagerData = {
    shortcuts?: FileEntry[];
    entries: FileEntry[];
    directoryActions?: ("UPLOAD" | "MKDIR")[];
};

type FileManagerProps = {
    data?: FileManagerData;
    style?: CSSProperties;
};

type ReplaceArticleResourceUrlResponse = {
    scannedArticles: number;
    updatedArticles: number;
    updatedFields: number;
};

type ResourceTypeItem = {
    key: string;
    label: string;
    icon: ReactNode;
    disabled?: boolean;
};

type FileManagerSortBy = "time" | "name" | "size";
type FileManagerSortOrder = "asc" | "desc";

const normalizeSortBy = (value: string | null): FileManagerSortBy => {
    return value === "name" || value === "size" || value === "time" ? value : "time";
};

const normalizeSortOrder = (value: string | null): FileManagerSortOrder | undefined => {
    return value === "asc" || value === "desc" ? value : undefined;
};

const FileManager: FunctionComponent<FileManagerProps> = ({ data, style }) => {
    const { token } = theme.useToken();
    const themeVars = useTheme();
    const screens = useBreakpoint();
    const axiosInstance = useAxiosBaseInstance();
    const { message: msgApi } = App.useApp();
    const borderSecondary = `${token.lineWidth}px ${token.lineType} ${token.colorBorderSecondary}`;
    const sidebarMenuItemStyle: CSSProperties = {
        height: token.controlHeight,
        marginInline: 0,
        paddingLeft: token.paddingSM,
        width: "100%",
    };

    const res = getRes().fileManager;

    const [searchParams, setSearchParams] = useSearchParams();
    const currentPath = searchParams.get("path") || "";
    const searchKey = searchParams.get("key") || "";
    const urlViewMode = searchParams.get("view");
    const cachedViewMode = getCacheByKey(FILE_MANAGER_VIEW_MODE_CACHE_KEY);
    const viewMode =
        urlViewMode === "grid" || urlViewMode === "list"
            ? urlViewMode
            : cachedViewMode === "grid" || cachedViewMode === "list"
            ? cachedViewMode
            : "grid";
    const setCurrentPath = (path: string) => {
        setSearchParams((prev) => {
            prev.delete("key");
            if (path) {
                prev.set("path", path);
            } else {
                prev.delete("path");
            }
            return prev;
        });
    };
    const setSearchKey = (key: string) => {
        setSearchParams(
            (prev) => {
                const nextKey = key.trim();
                if (nextKey) {
                    prev.set("key", nextKey);
                    prev.delete("path");
                } else {
                    prev.delete("key");
                }
                prev.delete("_refresh");
                return prev;
            },
            { replace: true }
        );
    };

    const setViewMode = (view: "grid" | "list") => {
        addToCache(FILE_MANAGER_VIEW_MODE_CACHE_KEY, view);
        setSearchParams((prev) => {
            prev.set("view", view);
            return prev;
        });
    };
    const setResourceTypeFilter = (type: string) => {
        setResourceType(type);
        setSearchParams(
            (prev) => {
                if (type === "all") {
                    prev.delete("resourceType");
                } else {
                    prev.set("resourceType", type);
                }
                if (type === "broken") {
                    prev.delete("path");
                }
                return prev;
            },
            { replace: true }
        );
    };
    const setSortByFilter = (value: FileManagerSortBy) => {
        setSortBy(value);
        setSearchParams(
            (prev) => {
                prev.set("sortBy", value);
                return prev;
            },
            { replace: true }
        );
    };
    const setSortOrderFilter = (value: FileManagerSortOrder) => {
        setSortOrder(value);
        setSearchParams(
            (prev) => {
                prev.set("sortOrder", value);
                return prev;
            },
            { replace: true }
        );
    };
    const shortcuts = data?.shortcuts ?? [];
    const entries = data?.entries ?? [];
    const directoryActions = data?.directoryActions ?? [];
    const effectivePath = currentPath || "";
    const [newFolderOpen, setNewFolderOpen] = useState(false);
    const [newFolderName, setNewFolderName] = useState("");
    const [renameTarget, setRenameTarget] = useState<FileEntry | null>(null);
    const [renameName, setRenameName] = useState("");
    const [renameSyncReferences, setRenameSyncReferences] = useState(true);
    const [renameSubmitting, setRenameSubmitting] = useState(false);
    const [reuploadTarget, setReuploadTarget] = useState<FileEntry | null>(null);
    const [reuploading, setReuploading] = useState(false);
    const [previewSrc, setPreviewSrc] = useState<string | null>(null);
    const [previewContent, setPreviewContent] = useState<{ name: string; content: string } | null>(null);
    const [selectedEntry, setSelectedEntry] = useState<FileEntry | null>(null);
    const [mobileDetailOpen, setMobileDetailOpen] = useState(false);
    const [resourceType, setResourceType] = useState(searchParams.get("resourceType") || "all");
    const [sortBy, setSortBy] = useState<FileManagerSortBy>(normalizeSortBy(searchParams.get("sortBy")));
    const [sortOrder, setSortOrder] = useState<FileManagerSortOrder>(
        normalizeSortOrder(searchParams.get("sortOrder")) ||
            (normalizeSortBy(searchParams.get("sortBy")) === "name" ? "asc" : "desc")
    );
    const [referenceRefreshing, setReferenceRefreshing] = useState(false);
    const [deleteTarget, setDeleteTarget] = useState<FileEntry | null>(null);
    const contentAreaRef = useRef<HTMLDivElement | null>(null);
    const entryRefMap = useRef<Record<string, HTMLDivElement | null>>({});
    const referenceResourceView = resourceType === "external" || resourceType === "broken" || resourceType === "unused";

    const refreshData = async () => {
        setSearchParams((prev) => {
            prev.set("_refresh", Date.now().toString());
            return prev;
        });
    };

    const refreshReferenceIndex = async () => {
        setReferenceRefreshing(true);
        try {
            const { data } = await axiosInstance.post<ApiResponse<boolean>>(
                FILE_MANAGER_API + "/refreshReferenceIndex"
            );
            if (data.error) {
                msgApi.error(data.message);
                return;
            }
            if (data.data === false) {
                msgApi.warning(res.rescanReferencesDisabled);
            } else {
                msgApi.success(res.rescanReferencesSuccess);
            }
            await refreshData();
        } finally {
            setReferenceRefreshing(false);
        }
    };

    useEffect(() => {
        addToCache(FILE_MANAGER_VIEW_MODE_CACHE_KEY, viewMode);
    }, [viewMode]);

    const handlePreview = async (entry: FileEntry) => {
        const isImg = entry.image === true;
        const isText = entry.textPreviewable === true;

        if (isImg) {
            setPreviewSrc(entry.path.startsWith("/") ? entry.path : entry.path);
        } else if (isText) {
            try {
                const { data } = await axiosInstance.get(FILE_MANAGER_API + "/read", {
                    params: { path: entry.path },
                });
                setPreviewContent({ name: entry.name, content: data.data });
            } catch (e) {
                msgApi.error(res.readFailed);
            }
        }
    };

    const getMatchedRoot = useCallback(
        (path: string) => {
            return [...shortcuts]
                .sort((a, b) => b.path.length - a.path.length)
                .find((root) => path.startsWith(root.path));
        },
        [shortcuts]
    );
    const activeRoot = getMatchedRoot(effectivePath);

    // 面包屑
    const breadcrumbItems = (() => {
        if (!effectivePath) {
            return [{ key: "", title: res.title }];
        }
        const matchedRoot = getMatchedRoot(effectivePath);
        if (!matchedRoot) {
            return [{ key: "", title: <a onClick={() => setCurrentPath("")}>{res.title}</a> }];
        }
        const rootKey = matchedRoot.path;
        const rootTitle = matchedRoot.name;
        const relativePath = effectivePath.substring(rootKey.length).replace(/^\/+/, "");
        const items = [
            {
                key: rootKey,
                title: <a onClick={() => setCurrentPath(rootKey)}>{rootTitle}</a>,
            },
        ];
        if (relativePath) {
            const parts = relativePath.split("/").filter(Boolean);
            parts.forEach((part, index) => {
                const path = `${rootKey}/${parts.slice(0, index + 1).join("/")}`;
                items.push({
                    key: path,
                    title: <a onClick={() => setCurrentPath(path)}>{part}</a>,
                });
            });
        }
        return [{ key: "", title: <a onClick={() => setCurrentPath("")}>{res.title}</a> }, ...items];
    })();

    const handleDelete = async (entry: FileEntry) => {
        const params = new URLSearchParams({ path: entry.path });
        const { data } = await axiosInstance.post(`${FILE_MANAGER_API}/delete?${params.toString()}`);
        msgApi.success(data.message || res.deleteSuccess);
        setDeleteTarget(null);
        if (selectedEntry?.path === entry.path) {
            setSelectedEntry(null);
        }
        await refreshData();
    };

    const isExternalReferenceEntry = (entry?: FileEntry | null) =>
        hasAction(entry, "UPDATE_REFERENCES") &&
        (entry?.path.startsWith(`${EXTERNAL_ROOT}/`) || isExternalPath(entry?.path || ""));

    const isMissingReferenceEntry = (entry?: FileEntry | null) =>
        hasAction(entry, "UPDATE_REFERENCES") && entry?.missing === true;

    const isReuploadMissingReferenceEntry = (entry?: FileEntry | null) =>
        hasAction(entry, "REUPLOAD") && entry?.missing === true;

    const isReplaceArticleResourceUrlEntry = (entry?: FileEntry | null) =>
        isExternalReferenceEntry(entry) || isMissingReferenceEntry(entry);

    const getReferenceReplacementValue = (entry: FileEntry) => {
        if (entry.type === "directory" && entry.path.startsWith(`${EXTERNAL_ROOT}/`)) {
            return entry.path.substring(EXTERNAL_ROOT.length + 1);
        }
        return entry.path;
    };

    const getReplaceArticleResourceUrlAction = (entry?: FileEntry | null) =>
        isMissingReferenceEntry(entry)
            ? res.replaceArticleResourceUrl.replaceMissingAction
            : res.replaceArticleResourceUrl.action;

    const getReplaceArticleResourceUrlTitle = (entry?: FileEntry | null) =>
        isMissingReferenceEntry(entry)
            ? res.replaceArticleResourceUrl.replaceMissingTitle
            : res.replaceArticleResourceUrl.title;

    const getReplaceArticleResourceUrlTip = (entry?: FileEntry | null) =>
        isMissingReferenceEntry(entry) ? res.replaceArticleResourceUrl.missingTip : res.replaceArticleResourceUrl.tip;

    const getReplaceArticleResourceUrlPlaceholder = (entry?: FileEntry | null) =>
        isMissingReferenceEntry(entry)
            ? res.replaceArticleResourceUrl.newReferencePlaceholder
            : res.replaceArticleResourceUrl.newDomainPlaceholder;

    const getEditableName = (entry: FileEntry) => {
        if (isReplaceArticleResourceUrlEntry(entry)) {
            return getReferenceReplacementValue(entry);
        }
        return entry.name;
    };

    const openRename = (entry: FileEntry) => {
        setRenameTarget(entry);
        setRenameName(getEditableName(entry));
        setRenameSyncReferences(hasAction(entry, "UPDATE_REFERENCES"));
    };

    const openReupload = (entry: FileEntry) => {
        setReuploadTarget(entry);
        setReuploading(false);
    };

    const resetReupload = () => {
        setReuploadTarget(null);
        setReuploading(false);
    };

    const resetRename = () => {
        setRenameTarget(null);
        setRenameName("");
        setRenameSyncReferences(true);
    };

    const getReplaceArticleResourceUrlSuccessText = (data?: ReplaceArticleResourceUrlResponse) => {
        const updatedArticles = data?.updatedArticles || 0;
        const updatedFields = data?.updatedFields || 0;
        return res.replaceArticleResourceUrl.success
            .replace("{articles}", updatedArticles.toString())
            .replace("{fields}", updatedFields.toString());
    };

    const handleRename = async () => {
        if (!renameTarget || !renameName.trim()) return;
        const nextName = renameName.trim();
        if (nextName === getEditableName(renameTarget)) {
            return;
        }
        setRenameSubmitting(true);
        try {
            if (hasAction(renameTarget, "RENAME")) {
                const params = new URLSearchParams({
                    path: renameTarget.path,
                    newName: nextName,
                    syncArticleReferences: String(renameSyncReferences && hasAction(renameTarget, "UPDATE_REFERENCES")),
                });
                const { data } = await axiosInstance.post(`${FILE_MANAGER_API}/rename?${params.toString()}`);
                msgApi.success(data.message || res.renameSuccess);
            } else if (hasAction(renameTarget, "UPDATE_REFERENCES")) {
                const data = await postRefreshCacheSse<ApiResponse<ReplaceArticleResourceUrlResponse>>(
                    `${FILE_MANAGER_API}/article-resource-url/replace`,
                    {
                        body: {
                            fromUrl: getReferenceReplacementValue(renameTarget),
                            toUrl: nextName,
                        },
                        messageApi: msgApi,
                        messageKey: "replaceArticleResourceUrl",
                        waitForComplete: true,
                        backgroundTaskTitle: getReplaceArticleResourceUrlTitle(renameTarget),
                        getBackgroundTaskResult: (response) =>
                            buildBackgroundTaskResult(response, {
                                successDescription: (item) => getReplaceArticleResourceUrlSuccessText(item.data),
                            }),
                    }
                );
                if (data.error) {
                    msgApi.error(data.message);
                    return;
                }
                msgApi.success(getReplaceArticleResourceUrlSuccessText(data.data));
            }
            resetRename();
        } finally {
            setRenameSubmitting(false);
        }
        await refreshData();
    };

    const handleMkdir = async () => {
        if (!newFolderName.trim()) return;
        const basePath = effectivePath;
        const newPath = basePath.endsWith("/")
            ? basePath + newFolderName.trim()
            : basePath + "/" + newFolderName.trim();
        const params = new URLSearchParams({ path: newPath });
        const { data } = await axiosInstance.post(`${FILE_MANAGER_API}/mkdir?${params.toString()}`);
        msgApi.success(data.message || res.newFolderSuccess);
        setNewFolderOpen(false);
        setNewFolderName("");
        await refreshData();
    };

    const handleReuploadSuccess = () => {
        msgApi.success(res.reuploadMissingSuccess);
        resetReupload();
        void refreshData();
    };

    const handleCopyUrl = (entry: FileEntry) => {
        const url = isExternalPath(entry.path) ? entry.path : tryAppendBackendServerUrl(entry.path);
        navigator.clipboard.writeText(url).then(() => {
            msgApi.success(res.urlCopied);
        });
    };

    const getEntryDisplayName = (entry: FileEntry) => {
        if (!isExternalPath(entry.path)) {
            return entry.name;
        }
        try {
            const url = new URL(entry.path.startsWith("//") ? `https:${entry.path}` : entry.path);
            const pathName = url.pathname.split("/").filter(Boolean).pop();
            return pathName || url.hostname || entry.name;
        } catch (e) {
            const parts = entry.path.split("/").filter(Boolean);
            return parts[parts.length - 1] || entry.name;
        }
    };

    const getReferenceText = (entry: FileEntry) => {
        const count = entry.referenceCount || 0;
        return count > 0 ? res.referenceCount.replace("{count}", count.toString()) : res.unreferenced;
    };

    const shouldShowReferenceCount = (entry: FileEntry) =>
        entry.access !== "ADMIN_ONLY" && (entry.type === "file" || hasAction(entry, "UPDATE_REFERENCES"));

    const getReferenceUsageText = (reference: NonNullable<FileEntry["references"]>[number]) => {
        const usages = [];
        if (reference.thumbnail) {
            usages.push(res.referenceUsage.thumbnail);
        }
        if (reference.content) {
            usages.push(res.referenceUsage.content);
        }
        return usages.length > 0 ? usages.join(" / ") : res.referenceUsage.unknown;
    };

    const renderReferenceImpactPreview = (entry: FileEntry) => {
        const references = entry.references || [];
        const syncDisabled =
            hasAction(entry, "RENAME") && hasAction(entry, "UPDATE_REFERENCES") && !renameSyncReferences;

        return (
            <div
                style={{
                    padding: token.paddingSM,
                    border: borderSecondary,
                    borderRadius: themeVars.borderRadius,
                    background: token.colorBgElevated,
                }}
            >
                <Space direction="vertical" size={4} style={{ width: "100%" }}>
                    <Text strong>{res.replaceArticleResourceUrl.impactTitle}</Text>
                    {syncDisabled ? (
                        <Text type="secondary">{res.replaceArticleResourceUrl.syncDisabled}</Text>
                    ) : references.length > 0 ? (
                        <>
                            <Text type="secondary">
                                {res.replaceArticleResourceUrl.impactSummary.replace(
                                    "{count}",
                                    references.length.toString()
                                )}
                            </Text>
                            <Space
                                direction="vertical"
                                size={4}
                                style={{ width: "100%", maxHeight: 180, overflowY: "auto" }}
                            >
                                {references.map((reference) => (
                                    <div
                                        key={reference.logId}
                                        style={{
                                            display: "flex",
                                            alignItems: "center",
                                            gap: token.marginXS,
                                            minWidth: 0,
                                        }}
                                    >
                                        <Link
                                            to={getRealRouteUrl(`/article-edit?id=${reference.logId}`)}
                                            style={{ flex: 1, minWidth: 0 }}
                                        >
                                            <Text ellipsis>{reference.title || reference.logId}</Text>
                                        </Link>
                                        <Tag style={{ marginInlineEnd: 0 }}>{getReferenceUsageText(reference)}</Tag>
                                    </div>
                                ))}
                            </Space>
                        </>
                    ) : (
                        <Text type="secondary">{res.replaceArticleResourceUrl.noImpact}</Text>
                    )}
                </Space>
            </div>
        );
    };

    const contextMenuItems = (entry: FileEntry): MenuProps["items"] => {
        const items: NonNullable<MenuProps["items"]> = [];

        if (hasAction(entry, "COPY_URL")) {
            items.push({
                key: "copy",
                label: res.copyUrl,
                icon: <CopyOutlined />,
                onClick: () => handleCopyUrl(entry),
            });
        }
        if (hasAction(entry, "DOWNLOAD")) {
            items.push({
                key: "download",
                icon: <DownloadOutlined />,
                label: res.download,
                onClick: () => {
                    const params = new URLSearchParams({ path: entry.path });
                    window.open(
                        tryAppendBackendServerUrl(`${FILE_MANAGER_API}/download?${params.toString()}`),
                        "_blank"
                    );
                },
            });
        }
        if (hasAction(entry, "PREVIEW")) {
            items.push({
                key: "preview",
                icon: <FileImageOutlined />,
                label: res.preview,
                onClick: () => handlePreview(entry),
                disabled: !(entry.image === true || entry.textPreviewable === true),
            });
        }

        if (isReplaceArticleResourceUrlEntry(entry)) {
            items.push({
                key: "replaceArticleResourceUrl",
                label: getReplaceArticleResourceUrlAction(entry),
                icon: <EditOutlined />,
                onClick: () => openRename(entry),
            });
        }

        if (isReuploadMissingReferenceEntry(entry)) {
            items.push({
                key: "reuploadMissingResource",
                label: res.reuploadMissingAction,
                icon: <UploadOutlined />,
                onClick: () => openReupload(entry),
            });
        }

        if (hasAction(entry, "RENAME") || hasAction(entry, "DELETE")) {
            items.push({ type: "divider" });
            if (hasAction(entry, "RENAME")) {
                items.push({
                    key: "rename",
                    label: res.rename,
                    icon: <EditOutlined />,
                    onClick: () => {
                        openRename(entry);
                    },
                });
            }
            if (hasAction(entry, "DELETE")) {
                items.push({
                    key: "delete",
                    label: (
                        <Popconfirm title={res.confirmDelete} onConfirm={() => handleDelete(entry)} okType="danger">
                            <span style={{ color: token.colorError }}>{res.delete}</span>
                        </Popconfirm>
                    ),
                    icon: <DeleteOutlined style={{ color: token.colorError }} />,
                });
            }
        }
        return items;
    };

    const handleEntryClick = (entry: FileEntry) => {
        setSelectedEntry(entry);
        if (entry.type === "directory" || hasAction(entry, "OPEN")) {
            setMobileDetailOpen(false);
            setCurrentPath(entry.path);
            return;
        }
        if (!screens.xl) {
            setMobileDetailOpen(true);
        }
    };

    const renderGridItem = (entry: FileEntry) => {
        const img = isImage(entry);
        const selected = selectedEntry?.path === entry.path;
        const compactGridItem = screens.sm !== true;

        return (
            <Dropdown key={entry.path} menu={{ items: contextMenuItems(entry) }} trigger={["contextMenu"]}>
                <div>
                    <div
                        ref={(el) => {
                            entryRefMap.current[entry.path] = el;
                        }}
                        tabIndex={selected ? 0 : -1}
                        role="option"
                        aria-selected={selected}
                        style={{
                            border: `${token.lineWidth}px ${token.lineType} ${
                                selected ? token.colorPrimaryBorder : token.colorBorderSecondary
                            }`,
                            borderRadius: themeVars.borderRadius,
                            padding: compactGridItem ? 8 : token.paddingSM,
                            cursor: "pointer",
                            display: "flex",
                            flexDirection: "column",
                            gap: token.marginXS,
                            minHeight: compactGridItem ? 120 : 156,
                            justifyContent: "flex-start",
                            background: selected ? token.colorPrimaryBg : token.colorBgContainer,
                            transition: "border-color 0.2s, background-color 0.2s",
                        }}
                        className="file-grid-item"
                        onClick={() => handleEntryClick(entry)}
                        onFocus={() => setSelectedEntry(entry)}
                    >
                        {img ? (
                            <BackendImage
                                src={entry.path}
                                alt={entry.name}
                                preview={false}
                                loading="lazy"
                                style={{
                                    width: "100%",
                                    aspectRatio: "16 / 10",
                                    objectFit: "cover",
                                    borderRadius: themeVars.borderRadius,
                                }}
                            />
                        ) : (
                            <div
                                style={{
                                    width: "100%",
                                    aspectRatio: "4 / 3",
                                    borderRadius: themeVars.borderRadius,
                                    display: "flex",
                                    alignItems: "center",
                                    justifyContent: "center",
                                    background: token.colorFillAlter,
                                }}
                            >
                                {getFileIcon(entry, token, compactGridItem ? 24 : 30)}
                            </div>
                        )}
                        <Text
                            ellipsis={{ tooltip: entry.name }}
                            style={{
                                width: "100%",
                                fontSize: compactGridItem ? token.fontSizeSM : token.fontSize,
                            }}
                        >
                            {entry.name}
                        </Text>
                        <Space size={6} style={{ width: "100%" }} wrap>
                            {entry.type !== "directory" && <Text type="secondary">{formatSize(entry.size)}</Text>}
                            {entry.lastModified > 0 && (
                                <Text type="secondary">
                                    <TimeAgo timestamp={entry.lastModified} />
                                </Text>
                            )}
                            {entry.missing === true && <Tag color="warning">{res.resourceType.broken}</Tag>}
                        </Space>
                        {shouldShowReferenceCount(entry) && (
                            <Tag color={entry.referenced ? "processing" : "default"} style={{ width: "fit-content" }}>
                                {getReferenceText(entry)}
                            </Tag>
                        )}
                    </div>
                </div>
            </Dropdown>
        );
    };

    const renderListItem = (entry: FileEntry) => {
        const isDir = entry.type === "directory";

        return (
            <Dropdown key={entry.path} menu={{ items: contextMenuItems(entry) }} trigger={["contextMenu"]}>
                <div
                    ref={(el) => {
                        entryRefMap.current[entry.path] = el;
                    }}
                    tabIndex={selectedEntry?.path === entry.path ? 0 : -1}
                    role="option"
                    aria-selected={selectedEntry?.path === entry.path}
                    style={{
                        display: "flex",
                        alignItems: "center",
                        gap: 10,
                        padding: "8px 4px",
                        borderBottom: borderSecondary,
                        background: selectedEntry?.path === entry.path ? token.colorPrimaryBg : token.colorBgContainer,
                        cursor: "pointer",
                    }}
                    onClick={() => handleEntryClick(entry)}
                    onFocus={() => setSelectedEntry(entry)}
                >
                    {getFileIcon(entry, token)}
                    <Text ellipsis style={{ flex: 1, minWidth: 0 }}>
                        {entry.name}
                    </Text>
                    {!isDir && (
                        <Text type="secondary" style={{ fontSize: 12, whiteSpace: "nowrap" }}>
                            {formatSize(entry.size)}
                        </Text>
                    )}
                    {shouldShowReferenceCount(entry) && (
                        <Tag color={entry.referenced ? "processing" : "default"} style={{ marginInlineEnd: 0 }}>
                            {getReferenceText(entry)}
                        </Tag>
                    )}
                    {entry.missing === true && <Tag color="warning">{res.resourceType.broken}</Tag>}
                    {entry.lastModified > 0 && (
                        <Text type="secondary" style={{ fontSize: 12, whiteSpace: "nowrap" }}>
                            <TimeAgo timestamp={entry.lastModified} />
                        </Text>
                    )}
                    <Space size={4}>
                        {hasAction(entry, "PREVIEW") && (
                            <Tooltip title={res.preview}>
                                <Button
                                    size="small"
                                    type="text"
                                    icon={<FileImageOutlined />}
                                    disabled={!(entry.image === true || entry.textPreviewable === true)}
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        void handlePreview(entry);
                                    }}
                                />
                            </Tooltip>
                        )}
                        {hasAction(entry, "COPY_URL") && (
                            <Tooltip title={res.copyUrl}>
                                <Button
                                    size="small"
                                    type="text"
                                    icon={<CopyOutlined />}
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        handleCopyUrl(entry);
                                    }}
                                />
                            </Tooltip>
                        )}
                        {isReuploadMissingReferenceEntry(entry) && (
                            <Tooltip title={res.reuploadMissingAction}>
                                <Button
                                    size="small"
                                    type="text"
                                    icon={<UploadOutlined />}
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        openReupload(entry);
                                    }}
                                />
                            </Tooltip>
                        )}
                        {(hasAction(entry, "RENAME") || hasAction(entry, "DELETE")) && (
                            <>
                                {hasAction(entry, "RENAME") && (
                                    <Tooltip title={res.rename}>
                                        <Button
                                            size="small"
                                            type="text"
                                            icon={<EditOutlined />}
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                openRename(entry);
                                            }}
                                        />
                                    </Tooltip>
                                )}
                                {hasAction(entry, "DELETE") && (
                                    <Popconfirm
                                        title={res.confirmDelete}
                                        onConfirm={() => handleDelete(entry)}
                                        okType="danger"
                                    >
                                        <Button
                                            size="small"
                                            type="text"
                                            danger
                                            icon={<DeleteOutlined />}
                                            onClick={(e) => e.stopPropagation()}
                                        />
                                    </Popconfirm>
                                )}
                            </>
                        )}
                    </Space>
                </div>
            </Dropdown>
        );
    };

    const isRootOverview = effectivePath === "";
    const canUpload = hasDirectoryAction(directoryActions, "UPLOAD");
    const canMkdir = hasDirectoryAction(directoryActions, "MKDIR");
    const getResourceType = useCallback((entry: FileEntry) => {
        if (entry.missing === true) return "broken";
        if (entry.type === "directory") return "directory";
        if (isExternalPath(entry.path)) return "external";
        if (isImage(entry)) return "image";
        const mimeType = (entry.mimeType || "").toLowerCase();
        if (mimeType.startsWith("video/")) return "video";
        if (mimeType.includes("zip") || mimeType.includes("rar") || mimeType.includes("7z")) return "archive";
        if (
            mimeType.startsWith("text/") ||
            mimeType.includes("pdf") ||
            mimeType.includes("word") ||
            mimeType.includes("excel")
        ) {
            return "document";
        }
        return "file";
    }, []);
    const categoryItems = useMemo<ResourceTypeItem[]>(
        () => [
            { key: "all", label: res.resourceType.all, icon: <AppstoreOutlined /> },
            { key: "image", label: res.resourceType.image, icon: <FileImageOutlined /> },
            { key: "document", label: res.resourceType.document, icon: <FileTextOutlined /> },
            { key: "video", label: res.resourceType.video, icon: <VideoCameraOutlined /> },
            { key: "archive", label: res.resourceType.archive, icon: <FileZipOutlined /> },
            { key: "external", label: res.resourceType.external, icon: <LinkOutlined /> },
            { key: "broken", label: res.resourceType.broken, icon: <WarningOutlined /> },
            { key: "unused", label: res.resourceType.unused, icon: <DeleteOutlined /> },
        ],
        [res.resourceType]
    );
    const visibleEntries = useMemo(() => {
        const filtered = entries.filter((entry) => {
            if (resourceType === "all") return true;
            if (resourceType === "unused") {
                return entry.type === "file" && !isExternalPath(entry.path) && entry.referenced !== true;
            }
            if (resourceType === "broken") {
                return entry.missing === true;
            }
            if (resourceType === "directory") return entry.type === "directory";
            return getResourceType(entry) === resourceType;
        });
        return filtered.sort((a, b) => {
            const direction = sortOrder === "asc" ? 1 : -1;
            if (a.type !== b.type) {
                return a.type === "directory" ? -1 : 1;
            }
            if (sortBy === "name") {
                return direction * a.name.localeCompare(b.name);
            }
            if (sortBy === "size") {
                const sizeCompare = (a.size || 0) - (b.size || 0);
                return sizeCompare === 0 ? a.name.localeCompare(b.name) : direction * sizeCompare;
            }
            const timeCompare = (a.lastModified || 0) - (b.lastModified || 0);
            return timeCompare === 0 ? a.name.localeCompare(b.name) : direction * timeCompare;
        });
    }, [entries, getResourceType, resourceType, sortBy, sortOrder]);
    const selectedEntryType = selectedEntry ? getResourceType(selectedEntry) : "file";
    const selectedEntryUrl = selectedEntry
        ? isExternalPath(selectedEntry.path)
            ? selectedEntry.path
            : tryAppendBackendServerUrl(selectedEntry.path)
        : "";
    const selectedIndex = selectedEntry ? visibleEntries.findIndex((entry) => entry.path === selectedEntry.path) : -1;

    useEffect(() => {
        if (visibleEntries.length === 0) {
            setSelectedEntry(null);
            setMobileDetailOpen(false);
            return;
        }
        if (!selectedEntry) {
            return;
        }
        const matched = visibleEntries.find((entry) => entry.path === selectedEntry.path);
        if (!matched) {
            setSelectedEntry(visibleEntries[0]);
            setMobileDetailOpen(false);
        }
    }, [selectedEntry, visibleEntries]);

    useEffect(() => {
        if (!selectedEntry) {
            return;
        }
        const target = entryRefMap.current[selectedEntry.path];
        if (target && document.activeElement === contentAreaRef.current) {
            target.focus();
        }
        target?.scrollIntoView({ block: "nearest" });
    }, [selectedEntry]);

    useEffect(() => {
        if (screens.xl) {
            setMobileDetailOpen(false);
        }
    }, [screens.xl]);

    const moveSelection = useCallback(
        (delta: number) => {
            if (visibleEntries.length === 0) {
                return;
            }
            const startIndex = selectedIndex >= 0 ? selectedIndex : delta > 0 ? -1 : 0;
            const nextIndex = Math.max(0, Math.min(visibleEntries.length - 1, startIndex + delta));
            setSelectedEntry(visibleEntries[nextIndex]);
        },
        [selectedIndex, visibleEntries]
    );

    const handleOpenSelectedEntry = useCallback(() => {
        if (!selectedEntry) {
            return;
        }
        if (selectedEntry.type === "directory" || hasAction(selectedEntry, "OPEN")) {
            setCurrentPath(selectedEntry.path);
        }
    }, [selectedEntry]);

    const isTypingTarget = (event: KeyboardEvent<HTMLDivElement>) => {
        const target = event.target as HTMLElement | null;
        if (!target) {
            return false;
        }
        const tag = target.tagName.toLowerCase();
        return tag === "input" || tag === "textarea" || target.isContentEditable;
    };

    const handleContentKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
        if (newFolderOpen || !!renameTarget || !!previewContent || !!deleteTarget) {
            return;
        }
        if (isTypingTarget(event)) {
            return;
        }

        const hasMeta = event.metaKey || event.ctrlKey;
        if (event.key === "ArrowDown" || event.key === "ArrowRight") {
            event.preventDefault();
            moveSelection(1);
            return;
        }
        if (event.key === "ArrowUp" || event.key === "ArrowLeft") {
            event.preventDefault();
            moveSelection(-1);
            return;
        }
        if (event.key === "Home") {
            event.preventDefault();
            if (visibleEntries[0]) {
                setSelectedEntry(visibleEntries[0]);
            }
            return;
        }
        if (event.key === "End") {
            event.preventDefault();
            if (visibleEntries.length > 0) {
                setSelectedEntry(visibleEntries[visibleEntries.length - 1]);
            }
            return;
        }
        if (event.key === "Enter") {
            event.preventDefault();
            handleOpenSelectedEntry();
            return;
        }
        if (event.key === " ") {
            event.preventDefault();
            if (selectedEntry && hasAction(selectedEntry, "PREVIEW")) {
                void handlePreview(selectedEntry);
            }
            return;
        }
        if (event.key === "F2") {
            event.preventDefault();
            if (selectedEntry && hasAction(selectedEntry, "RENAME")) {
                openRename(selectedEntry);
            }
            return;
        }
        if (event.key === "Delete") {
            event.preventDefault();
            if (hasAction(selectedEntry, "DELETE")) {
                setDeleteTarget(selectedEntry);
            }
            return;
        }
        if (hasMeta && event.key.toLowerCase() === "c" && selectedEntry && hasAction(selectedEntry, "COPY_URL")) {
            event.preventDefault();
            handleCopyUrl(selectedEntry);
            return;
        }
        if (event.key === "Escape") {
            event.preventDefault();
            setSelectedEntry(null);
            contentAreaRef.current?.focus();
        }
    };

    return (
        <FileManagerView
            style={style}
            screens={screens}
            token={token}
            themeVars={themeVars}
            res={res}
            shortcuts={shortcuts}
            activeRoot={activeRoot}
            breadcrumbItems={breadcrumbItems}
            viewMode={viewMode}
            visibleEntries={visibleEntries}
            renderGridItem={renderGridItem}
            renderListItem={renderListItem}
            setCurrentPath={setCurrentPath}
            setViewMode={setViewMode}
            searchValue={searchKey}
            onSearchChange={setSearchKey}
            categoryActions={
                <>
                    <Menu
                        mode="inline"
                        selectable
                        inlineIndent={0}
                        selectedKeys={[resourceType]}
                        style={{ borderInlineEnd: "none", background: "transparent" }}
                        items={categoryItems.map((item) => ({
                            key: item.key,
                            label: <SidebarNavItem icon={item.icon} label={item.label} />,
                            disabled: item.disabled,
                            title: item.disabled ? res.todo : undefined,
                            style: sidebarMenuItemStyle,
                        }))}
                        onClick={({ key }) => setResourceTypeFilter(String(key))}
                    />
                    <Divider style={{ margin: `${token.marginXS}px 0` }} />
                </>
            }
            filterActions={
                <>
                    <Select
                        value={resourceType}
                        onChange={setResourceTypeFilter}
                        options={categoryItems.map((item) => ({
                            value: item.key,
                            label: (
                                <Space size={8}>
                                    {item.icon}
                                    <span>{item.label}</span>
                                </Space>
                            ),
                            disabled: item.disabled,
                        }))}
                        style={{ minWidth: 140 }}
                    />
                    <Select
                        value={sortBy}
                        onChange={setSortByFilter}
                        options={[
                            { value: "time", label: res.sortBy.time },
                            { value: "name", label: res.sortBy.name },
                            { value: "size", label: res.sortBy.size },
                        ]}
                        style={{ minWidth: 140 }}
                    />
                    <Select
                        value={sortOrder}
                        onChange={setSortOrderFilter}
                        options={[
                            { value: "desc", label: res.sortOrder.desc },
                            { value: "asc", label: res.sortOrder.asc },
                        ]}
                        style={{ minWidth: 112 }}
                    />
                </>
            }
            toolbarActions={
                <>
                    <Tooltip title={!canUpload || isRootOverview ? res.readOnlyHint : undefined}>
                        <BaseDragger
                            onSuccess={() => {
                                void refreshData();
                            }}
                            onError={(e) => msgApi.error(e.message || res.uploadFailed)}
                            type={"any"}
                            disabled={!canUpload || isRootOverview}
                            style={{ border: 0, background: "transparent", padding: 0 }}
                            uploadConfig={{
                                buildUploadUrl: function (): string {
                                    const params = new URLSearchParams({ dir: effectivePath + "/" });
                                    return `${UPLOAD_API}?${params.toString()}`;
                                },
                                formName: "imgFile",
                                axiosInstance: axiosInstance,
                                tryAppendBackendServerUrl: tryAppendBackendServerUrl,
                            }}
                        >
                            <Button icon={<UploadOutlined />} type="primary" disabled={!canUpload || isRootOverview}>
                                {res.upload}
                            </Button>
                        </BaseDragger>
                    </Tooltip>
                    <Button
                        icon={<FolderAddOutlined />}
                        onClick={() => {
                            setNewFolderName("");
                            setNewFolderOpen(true);
                        }}
                        disabled={!canMkdir || isRootOverview}
                    >
                        {res.newFolder}
                    </Button>
                    {referenceResourceView && (
                        <Button
                            icon={<ReloadOutlined />}
                            loading={referenceRefreshing}
                            onClick={() => void refreshReferenceIndex()}
                        >
                            {res.rescanReferences}
                        </Button>
                    )}
                </>
            }
            detailPanel={
                <div style={{ padding: token.padding }}>
                    <Space align="center" size={6}>
                        <Text strong>{res.detailTitle}</Text>
                        <Tooltip title={res.keyboardTip}>
                            <QuestionCircleOutlined style={{ color: token.colorTextTertiary }} />
                        </Tooltip>
                    </Space>
                    <div style={{ marginTop: token.marginSM }}>
                        {!selectedEntry ? (
                            <div
                                style={{
                                    minHeight: 220,
                                    display: "flex",
                                    alignItems: "center",
                                    justifyContent: "center",
                                }}
                            >
                                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={res.selectTip} />
                            </div>
                        ) : (
                            <Space direction="vertical" style={{ width: "100%" }} size={token.marginXS}>
                                {isImage(selectedEntry) ? (
                                    <BackendImage
                                        src={selectedEntry.path}
                                        alt={selectedEntry.name}
                                        preview={false}
                                        style={{
                                            width: "100%",
                                            maxHeight: 180,
                                            objectFit: "cover",
                                            borderRadius: themeVars.borderRadius,
                                            border: borderSecondary,
                                        }}
                                    />
                                ) : (
                                    <div
                                        style={{
                                            height: 120,
                                            display: "flex",
                                            alignItems: "center",
                                            justifyContent: "center",
                                            borderRadius: themeVars.borderRadius,
                                            border: borderSecondary,
                                        }}
                                    >
                                        {getFileIcon(selectedEntry, token, 36)}
                                    </div>
                                )}
                                <Text strong ellipsis={{ tooltip: selectedEntry.name }} style={{ width: "100%" }}>
                                    {getEntryDisplayName(selectedEntry)}
                                </Text>
                                <Tag>{res.resourceType[selectedEntryType] || res.type.file}</Tag>
                                <div
                                    style={{
                                        padding: token.paddingSM,
                                        border: borderSecondary,
                                        borderRadius: themeVars.borderRadius,
                                        background: token.colorBgElevated,
                                    }}
                                >
                                    <Space direction="vertical" size={4}>
                                        <Text type="secondary">
                                            {res.size}
                                            {getLabelValueSeparator()}
                                            {formatSize(selectedEntry.size)}
                                        </Text>
                                        <Text type="secondary">
                                            {res.lastModified}
                                            {getLabelValueSeparator()}
                                            {selectedEntry.lastModified > 0 ? (
                                                <TimeAgo timestamp={selectedEntry.lastModified} />
                                            ) : (
                                                "-"
                                            )}
                                        </Text>
                                        {selectedEntry.type === "file" && selectedEntry.missing !== true ? (
                                            <div>
                                                <Text type="secondary">
                                                    {res.accessUrl}
                                                    {getLabelValueSeparator()}
                                                </Text>
                                                <Paragraph
                                                    copyable={{ text: selectedEntryUrl }}
                                                    style={{ marginBottom: 0, wordBreak: "break-all" }}
                                                >
                                                    {selectedEntryUrl || "-"}
                                                </Paragraph>
                                            </div>
                                        ) : (
                                            <Text type="secondary">
                                                {res.path}
                                                {getLabelValueSeparator()}
                                                {selectedEntry.path}
                                            </Text>
                                        )}
                                    </Space>
                                </div>
                                {shouldShowReferenceCount(selectedEntry) && (
                                    <div
                                        style={{
                                            padding: token.paddingSM,
                                            border: borderSecondary,
                                            borderRadius: themeVars.borderRadius,
                                            background: token.colorBgElevated,
                                        }}
                                    >
                                        <Space direction="vertical" size={token.marginXS} style={{ width: "100%" }}>
                                            <Text strong>{res.references}</Text>
                                            {selectedEntry.references && selectedEntry.references.length > 0 ? (
                                                selectedEntry.references.map((reference) => (
                                                    <div
                                                        key={reference.logId}
                                                        style={{
                                                            display: "flex",
                                                            alignItems: "center",
                                                            gap: token.marginXS,
                                                            minWidth: 0,
                                                        }}
                                                    >
                                                        <Link
                                                            to={getRealRouteUrl(`/article-edit?id=${reference.logId}`)}
                                                            style={{ flex: 1, minWidth: 0 }}
                                                        >
                                                            <Text ellipsis>{reference.title || reference.logId}</Text>
                                                        </Link>
                                                        <Tag style={{ marginInlineEnd: 0 }}>
                                                            {getReferenceUsageText(reference)}
                                                        </Tag>
                                                    </div>
                                                ))
                                            ) : (
                                                <Text type="secondary">{res.unreferenced}</Text>
                                            )}
                                        </Space>
                                    </div>
                                )}
                                <Space wrap>
                                    {hasAction(selectedEntry, "PREVIEW") && (
                                        <Button
                                            icon={<FileImageOutlined />}
                                            disabled={
                                                !(
                                                    selectedEntry.image === true ||
                                                    selectedEntry.textPreviewable === true
                                                )
                                            }
                                            onClick={() => {
                                                void handlePreview(selectedEntry);
                                            }}
                                        >
                                            {res.preview}
                                        </Button>
                                    )}
                                    {hasAction(selectedEntry, "COPY_URL") && (
                                        <Button icon={<CopyOutlined />} onClick={() => handleCopyUrl(selectedEntry)}>
                                            {res.copyUrl}
                                        </Button>
                                    )}
                                    {hasAction(selectedEntry, "DOWNLOAD") && (
                                        <Button
                                            icon={<DownloadOutlined />}
                                            onClick={() => {
                                                const params = new URLSearchParams({ path: selectedEntry.path });
                                                window.open(
                                                    tryAppendBackendServerUrl(
                                                        `${FILE_MANAGER_API}/download?${params.toString()}`
                                                    ),
                                                    "_blank"
                                                );
                                            }}
                                        >
                                            {res.download}
                                        </Button>
                                    )}
                                    {(hasAction(selectedEntry, "RENAME") ||
                                        isReplaceArticleResourceUrlEntry(selectedEntry)) && (
                                        <Button icon={<EditOutlined />} onClick={() => openRename(selectedEntry)}>
                                            {isReplaceArticleResourceUrlEntry(selectedEntry)
                                                ? getReplaceArticleResourceUrlAction(selectedEntry)
                                                : res.rename}
                                        </Button>
                                    )}
                                    {isReuploadMissingReferenceEntry(selectedEntry) && (
                                        <Button icon={<UploadOutlined />} onClick={() => openReupload(selectedEntry)}>
                                            {res.reuploadMissingAction}
                                        </Button>
                                    )}
                                    {hasAction(selectedEntry, "DELETE") && (
                                        <Popconfirm
                                            title={res.confirmDelete}
                                            onConfirm={() => handleDelete(selectedEntry)}
                                            okType="danger"
                                        >
                                            <Button danger icon={<DeleteOutlined />}>
                                                {res.delete}
                                            </Button>
                                        </Popconfirm>
                                    )}
                                </Space>
                            </Space>
                        )}
                    </div>
                </div>
            }
            overlays={
                <>
                    <Modal
                        title={res.confirmDelete}
                        open={!!deleteTarget}
                        onOk={() => {
                            if (deleteTarget) {
                                void handleDelete(deleteTarget);
                            }
                        }}
                        onCancel={() => setDeleteTarget(null)}
                        okButtonProps={{ danger: true }}
                        okText={res.delete}
                        cancelText={getRes().cancel}
                    >
                        {deleteTarget?.name}
                    </Modal>
                    <Drawer
                        title={res.detailTitle}
                        placement="bottom"
                        open={!screens.xl && mobileDetailOpen && !!selectedEntry}
                        onClose={() => setMobileDetailOpen(false)}
                    >
                        {selectedEntry && (
                            <Space direction="vertical" style={{ width: "100%" }}>
                                <Text strong>{getEntryDisplayName(selectedEntry)}</Text>
                                <Text type="secondary">
                                    {res.size}
                                    {getLabelValueSeparator()}
                                    {formatSize(selectedEntry.size)}
                                </Text>
                                <Text type="secondary">
                                    {res.lastModified}
                                    {getLabelValueSeparator()}
                                    {selectedEntry.lastModified > 0 ? (
                                        <TimeAgo timestamp={selectedEntry.lastModified} />
                                    ) : (
                                        "-"
                                    )}
                                </Text>
                                {selectedEntry.type === "file" && selectedEntry.missing !== true ? (
                                    <Text type="secondary">
                                        {res.accessUrl}
                                        {getLabelValueSeparator()}
                                        {selectedEntryUrl}
                                    </Text>
                                ) : (
                                    <Text type="secondary">
                                        {res.path}
                                        {getLabelValueSeparator()}
                                        {selectedEntry.path}
                                    </Text>
                                )}
                                {shouldShowReferenceCount(selectedEntry) && (
                                    <Tag color={selectedEntry.referenced ? "processing" : "default"}>
                                        {getReferenceText(selectedEntry)}
                                    </Tag>
                                )}
                                <Space wrap>
                                    {isReuploadMissingReferenceEntry(selectedEntry) && (
                                        <Button icon={<UploadOutlined />} onClick={() => openReupload(selectedEntry)}>
                                            {res.reuploadMissingAction}
                                        </Button>
                                    )}
                                    {isReplaceArticleResourceUrlEntry(selectedEntry) && (
                                        <Button icon={<EditOutlined />} onClick={() => openRename(selectedEntry)}>
                                            {getReplaceArticleResourceUrlAction(selectedEntry)}
                                        </Button>
                                    )}
                                </Space>
                            </Space>
                        )}
                    </Drawer>
                    <Modal
                        title={res.newFolder}
                        open={newFolderOpen}
                        onOk={handleMkdir}
                        onCancel={() => setNewFolderOpen(false)}
                        okText={getRes().confirm}
                        cancelText={getRes().cancel}
                    >
                        <Input
                            placeholder={res.newFolderPlaceholder}
                            value={newFolderName}
                            onChange={(e) => setNewFolderName(e.target.value)}
                            onPressEnter={handleMkdir}
                            autoFocus
                        />
                    </Modal>
                    <Modal
                        title={res.reuploadMissingTitle}
                        open={!!reuploadTarget}
                        onCancel={resetReupload}
                        footer={null}
                        destroyOnHidden
                    >
                        {reuploadTarget && (
                            <Space direction="vertical" style={{ width: "100%" }}>
                                <Text type="secondary">{res.reuploadMissingTip}</Text>
                                <div
                                    style={{
                                        padding: token.paddingSM,
                                        border: borderSecondary,
                                        borderRadius: themeVars.borderRadius,
                                        background: token.colorBgElevated,
                                    }}
                                >
                                    <Text type="secondary">
                                        {res.path}
                                        {getLabelValueSeparator()}
                                    </Text>
                                    <Paragraph
                                        copyable={{ text: reuploadTarget.path }}
                                        style={{ marginBottom: 0, wordBreak: "break-all" }}
                                    >
                                        {reuploadTarget.path}
                                    </Paragraph>
                                </div>
                                <BaseDragger
                                    type="any"
                                    disabled={reuploading}
                                    onProgress={() => setReuploading(true)}
                                    onSuccess={handleReuploadSuccess}
                                    onError={(e) => {
                                        setReuploading(false);
                                        msgApi.error(e.message || res.uploadFailed);
                                    }}
                                    uploadConfig={{
                                        buildUploadUrl: function (): string {
                                            const params = new URLSearchParams({ path: reuploadTarget.path });
                                            return `${FILE_MANAGER_API}/reupload?${params.toString()}`;
                                        },
                                        formName: "imgFile",
                                        axiosInstance: axiosInstance,
                                        tryAppendBackendServerUrl: tryAppendBackendServerUrl,
                                    }}
                                >
                                    <div
                                        style={{
                                            padding: token.padding,
                                            borderRadius: themeVars.borderRadius,
                                        }}
                                    >
                                        <Space direction="vertical" align="center" style={{ width: "100%" }}>
                                            <UploadOutlined />
                                            <Text>{res.uploadDrop}</Text>
                                            <Button
                                                type="primary"
                                                icon={<UploadOutlined />}
                                                loading={reuploading}
                                                disabled={reuploading}
                                            >
                                                {res.reuploadMissingAction}
                                            </Button>
                                        </Space>
                                    </div>
                                </BaseDragger>
                            </Space>
                        )}
                    </Modal>
                    <Modal
                        title={
                            renameTarget && !hasAction(renameTarget, "RENAME")
                                ? getReplaceArticleResourceUrlTitle(renameTarget)
                                : res.rename
                        }
                        open={!!renameTarget}
                        onOk={handleRename}
                        onCancel={resetRename}
                        okText={getRes().confirm}
                        cancelText={getRes().cancel}
                        confirmLoading={renameSubmitting}
                        okButtonProps={{
                            disabled:
                                !renameTarget ||
                                !renameName.trim() ||
                                renameName.trim() === getEditableName(renameTarget),
                        }}
                    >
                        <Space direction="vertical" style={{ width: "100%" }}>
                            {renameTarget && !hasAction(renameTarget, "RENAME") && (
                                <Text type="secondary">{getReplaceArticleResourceUrlTip(renameTarget)}</Text>
                            )}
                            <Input
                                value={renameName}
                                placeholder={getReplaceArticleResourceUrlPlaceholder(renameTarget)}
                                onChange={(e) => setRenameName(e.target.value)}
                                onPressEnter={handleRename}
                                autoFocus
                            />
                            {renameTarget &&
                                hasAction(renameTarget, "RENAME") &&
                                hasAction(renameTarget, "UPDATE_REFERENCES") && (
                                    <Checkbox
                                        checked={renameSyncReferences}
                                        onChange={(e) => setRenameSyncReferences(e.target.checked)}
                                    >
                                        {res.replaceArticleResourceUrl.syncReferences}
                                    </Checkbox>
                                )}
                            {renameTarget &&
                                hasAction(renameTarget, "UPDATE_REFERENCES") &&
                                renderReferenceImpactPreview(renameTarget)}
                        </Space>
                    </Modal>
                    <BackendImage
                        style={{ display: "none" }}
                        preview={{
                            visible: !!previewSrc,
                            src: resolveBackendImageSrc(previewSrc ?? undefined),
                            onVisibleChange: (v) => {
                                if (!v) setPreviewSrc(null);
                            },
                        }}
                    />
                    <Modal
                        title={previewContent?.name}
                        open={!!previewContent}
                        onCancel={() => setPreviewContent(null)}
                        footer={[
                            <Button key="close" onClick={() => setPreviewContent(null)}>
                                {getRes().common.close}
                            </Button>,
                        ]}
                        width={screens.lg ? 800 : screens.md ? 640 : "calc(100vw - 32px)"}
                        centered
                    >
                        <div
                            style={{
                                maxHeight: "60vh",
                                overflow: "auto",
                                background: token.colorFillAlter,
                                padding: 16,
                                borderRadius: themeVars.borderRadius,
                            }}
                        >
                            <pre style={{ margin: 0, whiteSpace: "pre-wrap", wordBreak: "break-all" }}>
                                {previewContent?.content}
                            </pre>
                        </div>
                    </Modal>
                </>
            }
            contentKeyboardProps={{
                tabIndex: 0,
                onKeyDown: handleContentKeyDown,
                onFocus: () => {
                    if (!selectedEntry && visibleEntries.length > 0) {
                        setSelectedEntry(visibleEntries[0]);
                    }
                },
                role: "listbox",
                "aria-label": res.title,
            }}
            contentKeyboardRef={(el) => {
                contentAreaRef.current = el;
            }}
        />
    );
};

export default FileManager;
