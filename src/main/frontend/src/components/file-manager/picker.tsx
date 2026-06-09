import { CSSProperties, FunctionComponent, useCallback, useEffect, useRef, useState } from "react";
import { Dropdown, Grid, Select, Space, theme, Typography } from "antd";
import { useTheme } from "antd-style";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getRes, tryAppendBackendServerUrl } from "../../utils/constants";
import { FileManagerData } from "./index";
import FileManagerView from "./view";
import { FileEntry, formatSize, getFileIcon, getShortcutIcon, hasAction, isImage } from "./shared";
import BackendImage from "../../common/BackendImage";

const { useBreakpoint } = Grid;
const { Text } = Typography;
const FILE_MANAGER_API = "/api/admin/file-manager";

type FileManagerPickerProps = {
    data?: FileManagerData;
    onlyImage?: boolean;
    onSelectFile?: (path: string) => void;
    style?: CSSProperties;
};

const FileManagerPicker: FunctionComponent<FileManagerPickerProps> = ({
    data,
    onlyImage = false,
    onSelectFile,
    style,
}) => {
    const { token } = theme.useToken();
    const themeVars = useTheme();
    const screens = useBreakpoint();
    const axiosInstance = useAxiosBaseInstance();
    const res = getRes().fileManager;
    const borderSecondary = `${token.lineWidth}px ${token.lineType} ${token.colorBorderSecondary}`;
    const compactItems = screens.sm !== true;
    const gridItemPadding = compactItems ? 6 : 8;
    const gridItemGap = compactItems ? 4 : 6;
    const gridItemMinHeight = compactItems ? 84 : 100;
    const gridImageMaxHeight = compactItems ? 44 : 60;
    const gridIconSize = compactItems ? 24 : undefined;

    const [currentPath, setCurrentPath] = useState("");
    const [searchKey, setSearchKey] = useState("");
    const [viewMode, setViewMode] = useState<"grid" | "list">("grid");
    const [shortcuts, setShortcuts] = useState<FileEntry[]>(() => data?.shortcuts ?? []);
    const [entries, setEntries] = useState<FileEntry[]>(() => data?.entries ?? []);
    const [loading, setLoading] = useState(!data);
    const hydratedFromDataRef = useRef(Boolean(data));
    const fetchSeqRef = useRef(0);
    const effectivePath = currentPath || "";

    const fetchEntries = useCallback(
        async (path: string, key: string) => {
            const seq = ++fetchSeqRef.current;
            const nextKey = key.trim();
            setLoading(true);
            setEntries([]);
            try {
                const { data } = await axiosInstance.get(FILE_MANAGER_API, {
                    params: nextKey ? { key: nextKey } : { path },
                });
                if (seq !== fetchSeqRef.current) {
                    return;
                }
                setShortcuts((data.data?.shortcuts ?? []) as FileEntry[]);
                setEntries((data.data?.entries ?? []) as FileEntry[]);
            } finally {
                if (seq === fetchSeqRef.current) {
                    setLoading(false);
                }
            }
        },
        [axiosInstance]
    );

    useEffect(() => {
        if (hydratedFromDataRef.current) {
            hydratedFromDataRef.current = false;
            return;
        }
        void fetchEntries(effectivePath, searchKey);
    }, [effectivePath, fetchEntries, searchKey]);

    const getMatchedRoot = useCallback(
        (path: string) => {
            return [...shortcuts]
                .sort((a, b) => b.path.length - a.path.length)
                .find((root) => path.startsWith(root.path));
        },
        [shortcuts]
    );
    const activeRoot = getMatchedRoot(effectivePath);
    const mobileRootSelect =
        screens.md === true ? null : (
            <Select
                value={activeRoot?.path || ""}
                onChange={(path) => {
                    setSearchKey("");
                    setCurrentPath(path);
                }}
                options={[
                    {
                        value: "",
                        label: res.title,
                    },
                    ...shortcuts.map((root) => ({
                        value: root.path,
                        label: (
                            <Space size={8}>
                                {getShortcutIcon(root, activeRoot?.path === root.path)}
                                <span>{root.name}</span>
                            </Space>
                        ),
                    })),
                ]}
                style={{ minWidth: 144, flex: "1 1 144px" }}
            />
        );
    const breadcrumbItems = (() => {
        if (!effectivePath) {
            return [{ key: "", title: res.title }];
        }
        const matchedRoot = getMatchedRoot(effectivePath);
        if (!matchedRoot) {
            return [{ key: "", title: <a onClick={() => setSearchKey("")}>{res.title}</a> }];
        }
        const rootKey = matchedRoot.path;
        const rootTitle = matchedRoot.name;
        const relativePath = effectivePath.substring(rootKey.length).replace(/^\/+/, "");
        const items = [{ key: rootKey, title: <a onClick={() => setCurrentPath(rootKey)}>{rootTitle}</a> }];
        if (relativePath) {
            const parts = relativePath.split("/").filter(Boolean);
            parts.forEach((part, index) => {
                const path = `${rootKey}/${parts.slice(0, index + 1).join("/")}`;
                items.push({ key: path, title: <a onClick={() => setCurrentPath(path)}>{part}</a> });
            });
        }
        return [{ key: "", title: <a onClick={() => setSearchKey("")}>{res.title}</a> }, ...items];
    })();

    const contextMenuItems = () => [] as any[];

    const renderGridItem = (entry: FileEntry) => {
        const selectable = hasAction(entry, "SELECT") && (!onlyImage || isImage(entry));
        return (
            <Dropdown key={entry.path} menu={{ items: contextMenuItems() }} trigger={["contextMenu"]}>
                <div>
                    <div
                        style={{
                            border: borderSecondary,
                            borderRadius: themeVars.borderRadius,
                            padding: gridItemPadding,
                            cursor: "pointer",
                            display: "flex",
                            flexDirection: "column",
                            alignItems: "center",
                            gap: gridItemGap,
                            minHeight: gridItemMinHeight,
                            justifyContent: "center",
                            background: token.colorBgContainer,
                            transition: "background-color 0.2s, border-color 0.2s",
                        }}
                        className="file-grid-item"
                        onClick={() => {
                            if (hasAction(entry, "OPEN")) {
                                setSearchKey("");
                                setCurrentPath(entry.path);
                            } else if (selectable) {
                                onSelectFile?.(tryAppendBackendServerUrl(entry.path));
                            }
                        }}
                    >
                        {isImage(entry) ? (
                            <BackendImage
                                src={entry.path}
                                alt={entry.name}
                                preview={false}
                                loading="lazy"
                                style={{
                                    maxWidth: "100%",
                                    maxHeight: gridImageMaxHeight,
                                    objectFit: "cover",
                                    borderRadius: themeVars.borderRadiusSM,
                                }}
                            />
                        ) : (
                            getFileIcon(entry, token, gridIconSize)
                        )}
                        <Text
                            ellipsis={{ tooltip: entry.name }}
                            style={{ width: "100%", textAlign: "center", fontSize: compactItems ? 11 : 12 }}
                        >
                            {entry.name}
                        </Text>
                    </div>
                </div>
            </Dropdown>
        );
    };

    const renderListItem = (entry: FileEntry) => {
        const isDir = entry.type === "directory";
        const selectable = hasAction(entry, "SELECT") && (!onlyImage || isImage(entry));
        return (
            <Dropdown key={entry.path} menu={{ items: contextMenuItems() }} trigger={["contextMenu"]}>
                <div
                    style={{
                        display: "flex",
                        alignItems: "center",
                        gap: 10,
                        padding: "8px 4px",
                        borderBottom: borderSecondary,
                        background: token.colorBgContainer,
                        cursor: "pointer",
                    }}
                    onClick={() => {
                        if (hasAction(entry, "OPEN")) {
                            setSearchKey("");
                            setCurrentPath(entry.path);
                        } else if (selectable) {
                            onSelectFile?.(tryAppendBackendServerUrl(entry.path));
                        }
                    }}
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
                </div>
            </Dropdown>
        );
    };

    const visibleEntries = onlyImage
        ? entries.filter((entry) => entry.type === "directory" || isImage(entry))
        : entries;

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
            filterActions={mobileRootSelect}
            loading={loading}
            searchValue={searchKey}
            onSearchChange={(value) => {
                setSearchKey(value);
                if (value.trim()) {
                    setCurrentPath("");
                }
            }}
        />
    );
};

export default FileManagerPicker;
