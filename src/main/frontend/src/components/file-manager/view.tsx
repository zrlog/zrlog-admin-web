import { CSSProperties, FunctionComponent, HTMLAttributes, ReactNode, useEffect, useRef, useState } from "react";
import { Breadcrumb, Button, Empty, Grid, Input, Menu, Space, Spin, Tooltip } from "antd";
import { AppstoreOutlined, BarsOutlined } from "@ant-design/icons";
import { FileEntry, getShortcutIcon } from "./shared";
import SidebarNavItem from "../common/SidebarNavItem";

const { useBreakpoint } = Grid;
const SEARCH_DEBOUNCE_MS = 500;
const GRID_ITEM_MIN_WIDTH = 168;
const GRID_ITEM_MIN_WIDTH_MOBILE = 112;

export type FileManagerViewProps = {
    style?: CSSProperties;
    screens: ReturnType<typeof useBreakpoint>;
    token: any;
    themeVars: any;
    res: any;
    shortcuts: FileEntry[];
    activeRoot?: FileEntry;
    breadcrumbItems: any[];
    viewMode: "grid" | "list";
    visibleEntries: FileEntry[];
    renderGridItem: (entry: FileEntry) => any;
    renderListItem: (entry: FileEntry) => any;
    setCurrentPath: (path: string) => void;
    setViewMode: (view: "grid" | "list") => void;
    searchValue?: string;
    onSearchChange?: (value: string) => void;
    toolbarActions?: ReactNode;
    filterActions?: ReactNode;
    categoryActions?: ReactNode;
    contentNotice?: ReactNode;
    detailPanel?: ReactNode;
    overlays?: ReactNode;
    loading?: boolean;
    contentKeyboardProps?: Omit<HTMLAttributes<HTMLDivElement>, "ref">;
    contentKeyboardRef?: (el: HTMLDivElement | null) => void;
};

type FileManagerSearchInputProps = {
    value?: string;
    placeholder: string;
    onChange: (value: string) => void;
};

const FileManagerSearchInput: FunctionComponent<FileManagerSearchInputProps> = ({
    value = "",
    placeholder,
    onChange,
}) => {
    const [draft, setDraft] = useState(value);
    const onChangeRef = useRef(onChange);
    const timerRef = useRef<number | null>(null);
    const composingRef = useRef(false);

    const clearTimer = () => {
        if (timerRef.current !== null) {
            window.clearTimeout(timerRef.current);
            timerRef.current = null;
        }
    };

    useEffect(() => {
        onChangeRef.current = onChange;
    }, [onChange]);

    useEffect(() => {
        clearTimer();
        setDraft(value);
    }, [value]);

    useEffect(() => {
        return clearTimer;
    }, []);

    const commit = (nextValue: string) => {
        clearTimer();
        const nextKey = nextValue.trim();
        setDraft(nextKey);
        onChangeRef.current(nextKey);
    };

    const scheduleCommit = (nextValue: string) => {
        setDraft(nextValue);
        if (composingRef.current) {
            return;
        }
        clearTimer();
        const nextKey = nextValue.trim();
        if (!nextKey) {
            onChangeRef.current("");
            return;
        }
        timerRef.current = window.setTimeout(() => {
            onChangeRef.current(nextKey);
            timerRef.current = null;
        }, SEARCH_DEBOUNCE_MS);
    };

    return (
        <Input.Search
            allowClear
            value={draft}
            placeholder={placeholder}
            onChange={(e) => scheduleCommit(e.target.value)}
            onSearch={commit}
            style={{ width: "100%" }}
            onCompositionStart={() => {
                composingRef.current = true;
                clearTimer();
            }}
            onCompositionEnd={(e) => {
                composingRef.current = false;
                scheduleCommit(e.currentTarget.value);
            }}
        />
    );
};

const FileManagerView: FunctionComponent<FileManagerViewProps> = ({
    style,
    screens,
    token,
    themeVars,
    res,
    shortcuts,
    activeRoot,
    breadcrumbItems,
    viewMode,
    visibleEntries,
    renderGridItem,
    renderListItem,
    setCurrentPath,
    setViewMode,
    searchValue,
    onSearchChange,
    toolbarActions,
    filterActions,
    categoryActions,
    contentNotice,
    detailPanel,
    overlays,
    loading = false,
    contentKeyboardProps,
    contentKeyboardRef,
}) => {
    const mobileMode = screens.sm !== true;
    const searchInput = onSearchChange ? (
        <FileManagerSearchInput value={searchValue} placeholder={res.searchPlaceholder} onChange={onSearchChange} />
    ) : null;
    const gridItemMinWidth = mobileMode ? GRID_ITEM_MIN_WIDTH_MOBILE : GRID_ITEM_MIN_WIDTH;
    const gridTemplateColumns = `repeat(auto-fill, minmax(${gridItemMinWidth}px, 1fr))`;
    const borderSecondary = `${token.lineWidth}px ${token.lineType} ${token.colorBorderSecondary}`;
    const sidebarBackground = token.colorFillQuaternary;
    const workspaceBackground = token.colorBgLayout;
    const sidebarMenuStyle = {
        borderInlineEnd: "none",
        background: "transparent",
    };
    const sidebarMenuItemStyle: CSSProperties = {
        height: token.controlHeight,
        marginInline: 0,
        paddingLeft: token.paddingSM,
        width: "100%",
    };

    return (
        <>
            <div
                style={{
                    display: "flex",
                    height: "100%",
                    border: borderSecondary,
                    borderRadius: themeVars.borderRadiusLG,
                    overflow: "hidden",
                    background: token.colorBgContainer,
                    ...style,
                }}
            >
                {screens.md && (
                    <div
                        style={{
                            width: 248,
                            flexShrink: 0,
                            background: sidebarBackground,
                            borderRight: borderSecondary,
                            padding: token.padding,
                            overflow: "auto",
                        }}
                    >
                        {loading && shortcuts.length === 0 ? (
                            <div
                                style={{
                                    minHeight: 120,
                                    display: "flex",
                                    alignItems: "center",
                                    justifyContent: "center",
                                }}
                            >
                                <Spin />
                            </div>
                        ) : (
                            <>
                                {categoryActions}
                                {categoryActions ? <div style={{ height: token.marginSM }} /> : null}

                                <Menu
                                    mode="inline"
                                    selectable
                                    inlineIndent={0}
                                    selectedKeys={activeRoot?.path ? [activeRoot.path] : [""]}
                                    style={sidebarMenuStyle}
                                    items={shortcuts.map((root) => ({
                                        key: root.path,
                                        label: (
                                            <SidebarNavItem
                                                icon={getShortcutIcon(root, activeRoot?.path === root.path)}
                                                label={root.name}
                                                active={activeRoot?.path === root.path}
                                            />
                                        ),
                                        style: sidebarMenuItemStyle,
                                    }))}
                                    onClick={({ key }) => setCurrentPath(String(key))}
                                />
                            </>
                        )}
                    </div>
                )}
                <div style={{ display: "flex", flexDirection: "column", flex: 1, minWidth: 0, minHeight: 0 }}>
                    <div
                        style={{
                            display: "flex",
                            justifyContent: "space-between",
                            alignItems: mobileMode ? "stretch" : "center",
                            flexWrap: "wrap",
                            gap: token.marginSM,
                            padding: token.padding,
                            borderBottom: borderSecondary,
                            background: token.colorBgContainer,
                        }}
                    >
                        <div
                            style={{
                                display: "flex",
                                alignItems: "center",
                                gap: 8,
                                flex: mobileMode ? "1 1 100%" : "1 1 280px",
                                minWidth: 0,
                            }}
                        >
                            <div
                                style={{
                                    flex: mobileMode ? "1 1 100%" : "0 0 320px",
                                    width: mobileMode ? "100%" : 320,
                                    minWidth: 0,
                                    maxWidth: mobileMode ? "100%" : 320,
                                }}
                            >
                                {searchInput}
                            </div>
                        </div>
                        <Space wrap size={8}>
                            {filterActions}
                            {toolbarActions}
                            <Button
                                icon={viewMode === "grid" ? <BarsOutlined /> : <AppstoreOutlined />}
                                onClick={() => setViewMode(viewMode === "grid" ? "list" : "grid")}
                            >
                                {viewMode === "grid" ? res.view.list : res.view.grid}
                            </Button>
                        </Space>
                    </div>
                    <div
                        style={{
                            padding: `${token.paddingSM}px ${token.padding}px`,
                            borderBottom: borderSecondary,
                            background: token.colorBgContainer,
                            minWidth: 0,
                        }}
                    >
                        <Tooltip title={res.title}>
                            <div style={{ minWidth: 0, overflow: "hidden", paddingLeft: token.padding }}>
                                <Breadcrumb items={breadcrumbItems} />
                            </div>
                        </Tooltip>
                    </div>
                    <div
                        style={{
                            flex: 1,
                            minHeight: 0,
                            padding: token.padding,
                            display: "flex",
                            flexDirection: "column",
                            overflow: "hidden",
                            background: workspaceBackground,
                        }}
                    >
                        {contentNotice ? <div style={{ marginBottom: token.marginSM }}>{contentNotice}</div> : null}
                        <div
                            ref={contentKeyboardRef}
                            style={{
                                flex: 1,
                                minHeight: 0,
                                overflow: "auto",
                                overscrollBehaviorY: "contain",
                                WebkitOverflowScrolling: "touch",
                            }}
                            {...contentKeyboardProps}
                        >
                            {loading && (
                                <div
                                    style={{
                                        height: "100%",
                                        minHeight: 160,
                                        display: "flex",
                                        alignItems: "center",
                                        justifyContent: "center",
                                    }}
                                >
                                    <Spin />
                                </div>
                            )}
                            {!loading && visibleEntries.length === 0 && (
                                <Empty
                                    description={searchValue ? res.searchEmpty : res.empty}
                                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                                />
                            )}
                            {!loading && viewMode === "grid" ? (
                                <div
                                    style={{
                                        display: "grid",
                                        gridTemplateColumns,
                                        gap: token.marginSM,
                                        alignItems: "start",
                                    }}
                                >
                                    {visibleEntries.map(renderGridItem)}
                                </div>
                            ) : null}
                            {!loading && viewMode === "list" ? <div>{visibleEntries.map(renderListItem)}</div> : null}
                        </div>
                    </div>
                </div>
                {detailPanel && screens.xl && (
                    <div
                        style={{
                            width: 320,
                            flexShrink: 0,
                            borderLeft: borderSecondary,
                            background: token.colorBgContainer,
                            overflow: "auto",
                            minHeight: 0,
                        }}
                    >
                        {detailPanel}
                    </div>
                )}
            </div>
            {overlays}
        </>
    );
};

export default FileManagerView;
