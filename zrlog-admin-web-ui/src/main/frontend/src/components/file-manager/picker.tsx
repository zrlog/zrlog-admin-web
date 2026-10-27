import { ResourcePicker } from "@zrlog/ui/resources";
import { CSSProperties, FunctionComponent, useCallback, useEffect, useRef, useState } from "react";
import { theme } from "antd";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getRes, tryAppendBackendServerUrl } from "../../utils/constants";
import type { FileManagerData } from "./index";
import { FileEntry, formatSize, getFileIcon, hasAction, isImage } from "./shared";
import BackendImage from "../../common/BackendImage";
import { addToCache, getCacheByKey } from "../../utils/cache";

const FILE_MANAGER_API = "/api/admin/file-manager";

type FileManagerPickerProps = {
    data?: FileManagerData;
    onlyImage?: boolean;
    acceptExtensions?: string[];
    onSelectFile?: (path: string, entry: FileEntry) => void;
    disabled?: boolean;
    style?: CSSProperties;
    stateCacheKey?: string;
};

type FileManagerPickerUiState = {
    currentPath?: string;
    searchKey?: string;
    viewMode?: "grid" | "list";
};

const normalizeViewMode = (value: unknown): "grid" | "list" => {
    return value === "list" ? "list" : "grid";
};

const getCachedPickerUiState = (key?: string): FileManagerPickerUiState => {
    return key ? getCacheByKey<FileManagerPickerUiState>(key) || {} : {};
};

const FileManagerPicker: FunctionComponent<FileManagerPickerProps> = ({
    data,
    onlyImage = false,
    acceptExtensions,
    onSelectFile,
    disabled = false,
    style,
    stateCacheKey,
}) => {
    const { token } = theme.useToken();
    const axiosInstance = useAxiosBaseInstance();
    const res = getRes().fileManager;
    const cachedUiState = getCachedPickerUiState(stateCacheKey);

    const [currentPath, setCurrentPathState] = useState(cachedUiState.currentPath || "");
    const [searchKey, setSearchKeyState] = useState(cachedUiState.searchKey || "");
    const [viewMode, setViewModeState] = useState<"grid" | "list">(normalizeViewMode(cachedUiState.viewMode));
    const [shortcuts, setShortcuts] = useState<FileEntry[]>(() => data?.shortcuts ?? []);
    const [entries, setEntries] = useState<FileEntry[]>(() => data?.entries ?? []);
    const [loading, setLoading] = useState(!data);
    const [error, setError] = useState<string>();
    const hydratedFromDataRef = useRef(Boolean(data));
    const fetchSeqRef = useRef(0);
    useEffect(
        () => () => {
            fetchSeqRef.current++;
        },
        []
    );
    const effectivePath = currentPath || "";
    const persistUiState = useCallback(
        (patch: FileManagerPickerUiState) => {
            if (!stateCacheKey) {
                return;
            }
            addToCache(stateCacheKey, {
                ...getCachedPickerUiState(stateCacheKey),
                ...patch,
            });
        },
        [stateCacheKey]
    );
    const setCurrentPath = useCallback(
        (path: string) => {
            setCurrentPathState(path);
            persistUiState({ currentPath: path });
        },
        [persistUiState]
    );
    const setSearchKey = useCallback(
        (key: string) => {
            setSearchKeyState(key);
            persistUiState({ searchKey: key });
        },
        [persistUiState]
    );
    const setViewMode = useCallback(
        (view: "grid" | "list") => {
            setViewModeState(view);
            persistUiState({ viewMode: view });
        },
        [persistUiState]
    );

    useEffect(() => {
        const nextUiState = getCachedPickerUiState(stateCacheKey);
        setCurrentPathState(nextUiState.currentPath || "");
        setSearchKeyState(nextUiState.searchKey || "");
        setViewModeState(normalizeViewMode(nextUiState.viewMode));
    }, [stateCacheKey]);

    const fetchEntries = useCallback(
        async (path: string, key: string) => {
            const seq = ++fetchSeqRef.current;
            const nextKey = key.trim();
            setLoading(true);
            setError(undefined);
            setEntries([]);
            try {
                const { data } = await axiosInstance.get(FILE_MANAGER_API, {
                    params: nextKey ? { key: nextKey } : { path },
                });
                if (seq !== fetchSeqRef.current) {
                    return;
                }
                if (data.error) throw new Error(data.message || getRes().fileManager.readFailed);
                setShortcuts((data.data?.shortcuts ?? []) as FileEntry[]);
                setEntries((data.data?.entries ?? []) as FileEntry[]);
            } catch (error) {
                if (seq === fetchSeqRef.current)
                    setError(error instanceof Error ? error.message : getRes().fileManager.readFailed);
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

    const visibleEntries = entries.filter(
        (entry) =>
            entry.type === "directory" ||
            ((!onlyImage || isImage(entry)) &&
                (!acceptExtensions ||
                    acceptExtensions.some((extension) => entry.name.toLowerCase().endsWith(extension))))
    );

    return (
        <ResourcePicker
            style={style}
            entries={visibleEntries}
            roots={shortcuts}
            path={effectivePath}
            search={searchKey}
            viewMode={viewMode}
            loading={loading}
            disabled={disabled}
            error={error}
            onRetry={() => void fetchEntries(effectivePath, searchKey)}
            labels={{
                title: res.title,
                searchPlaceholder: res.searchPlaceholder,
                empty: res.empty,
                searchEmpty: res.searchEmpty,
                grid: res.view.grid,
                list: res.view.list,
                retry: res.retry,
            }}
            onPathChange={setCurrentPath}
            onSearchChange={(value) => {
                setSearchKey(value);
                if (value.trim()) setCurrentPath("");
            }}
            onViewModeChange={setViewMode}
            canOpen={(entry) => hasAction(entry, "OPEN")}
            canSelect={(entry) => hasAction(entry, "SELECT") && (!onlyImage || isImage(entry))}
            onSelect={(entry) => onSelectFile?.(tryAppendBackendServerUrl(entry.path), entry)}
            formatSize={formatSize}
            renderIcon={(entry) => getFileIcon(entry, token)}
            renderPreview={(entry) =>
                isImage(entry) ? (
                    <BackendImage
                        src={entry.path}
                        alt={entry.name}
                        preview={false}
                        loading="lazy"
                        style={{
                            maxWidth: "100%",
                            maxHeight: 60,
                            objectFit: "contain",
                            borderRadius: token.borderRadiusSM,
                        }}
                    />
                ) : undefined
            }
        />
    );
};

export default FileManagerPicker;
