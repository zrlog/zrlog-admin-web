import { DeleteOutlined, MenuOutlined, PlusOutlined, QuestionCircleOutlined, SettingOutlined } from "@ant-design/icons";
import {
    Button,
    Collapse,
    Drawer,
    Grid,
    Input,
    InputNumber,
    message,
    Modal,
    Select,
    Space,
    Switch,
    theme,
    Tooltip,
    Typography,
} from "antd";
import { AxiosInstance } from "axios";
import { FunctionComponent, useEffect, useMemo, useRef, useState } from "react";
import {
    AdminDashboardCardConfig,
    AdminDashboardConfig,
    AdminDashboardLayoutItem,
    AdminDashboardPluginPanelConfig,
    ApiResponse,
    IndexData,
} from "../../type";
import { getRes } from "../../utils/constants";

type DashboardConfigDrawerProps = {
    axiosInstance: AxiosInstance;
    config: AdminDashboardConfig;
    subtle?: boolean;
    onSaved: (data: IndexData) => void;
};

type LayoutItem =
    | { type: "card"; card: AdminDashboardCardConfig }
    | { type: "plugin"; panel: AdminDashboardPluginPanelConfig };
type DragPayload = { source: "enabled" | "disabled"; index: number };

const sortValue = (item: { sort?: number; order?: number }, index: number) => item.sort ?? item.order ?? index * 10;
const sortBySort = <T extends { sort?: number; order?: number }>(items: T[] = []) =>
    [...items].sort((a, b) => sortValue(a, 0) - sortValue(b, 0));
const panelEnabled = (panel: AdminDashboardPluginPanelConfig) => panel.enabled !== false;
const randomSuffix = () => Math.random().toString(36).slice(2, 6);
const buildPluginId = () => `plugin-${randomSuffix()}`;
const toPanelConfig = (panel: AdminDashboardPluginPanelConfig): AdminDashboardPluginPanelConfig => ({
    id: panel.id || buildPluginId(),
    enabled: panel.enabled,
    type: panel.type,
    pluginName: panel.pluginName,
    title: panel.title,
    surfaceUrl: panel.surfaceUrl,
    actionUrl: panel.actionUrl,
    viewUrl: panel.viewUrl,
    maxItems: panel.maxItems,
    height: panel.height,
    sort: panel.sort,
    order: panel.order,
});

const defaultPanel = (sort: number): AdminDashboardPluginPanelConfig => ({
    id: buildPluginId(),
    enabled: true,
    type: "surface",
    maxItems: 5,
    height: 360,
    sort,
});

const moveItem = <T,>(items: T[], fromIndex: number, toIndex: number) => {
    if (fromIndex === toIndex || fromIndex < 0 || toIndex < 0) {
        return items;
    }
    const next = [...items];
    const [removed] = next.splice(fromIndex, 1);
    next.splice(toIndex, 0, removed);
    return next;
};
const insertItem = <T,>(items: T[], item: T, index: number) => {
    const next = [...items];
    next.splice(index, 0, item);
    return next;
};

const splitItems = (items: AdminDashboardLayoutItem[] = []) => {
    const cards = items
        .filter((item): item is Extract<AdminDashboardLayoutItem, { kind: "card" }> => item.kind === "card")
        .map((item) => ({ id: item.id, enabled: item.enabled, sort: item.sort, title: item.title, data: item.data }));
    const panels = items
        .filter((item): item is Extract<AdminDashboardLayoutItem, { kind: "plugin" }> => item.kind === "plugin")
        .map((item) => ({
            id: item.id,
            enabled: item.enabled,
            type: item.type,
            pluginName: item.pluginName,
            title: item.title,
            surfaceUrl: item.surfaceUrl,
            actionUrl: item.actionUrl,
            viewUrl: item.viewUrl,
            maxItems: item.maxItems,
            height: item.height,
            sort: item.sort,
            order: item.order,
            data: item.data,
        }));
    return { cards, panels };
};

const buildItems = (
    cards: AdminDashboardCardConfig[],
    panels: AdminDashboardPluginPanelConfig[]
): AdminDashboardLayoutItem[] => {
    const items: AdminDashboardLayoutItem[] = [
        ...cards.map((card) => ({ kind: "card" as const, ...card })),
        ...panels.map((panel) => ({ kind: "plugin" as const, ...panel })),
    ];
    return items.sort((a, b) => sortValue(a, 0) - sortValue(b, 0));
};

const normalizeConfig = (value: AdminDashboardConfig): AdminDashboardConfig => {
    const { cards: savedCards, panels: savedPanels } = splitItems(value.cards);
    const welcomeCard = savedCards.find((card) => card.id === "welcome") || { id: "welcome", enabled: true, sort: 0 };
    const cards = sortBySort(savedCards.filter((card) => card.id !== "pluginPanels" && card.id !== "welcome"));
    const panels = sortBySort(savedPanels);
    const enabledPanels = panels.filter(panelEnabled);
    const disabledPanels = panels.filter((panel) => !panelEnabled(panel));
    const layoutItems: LayoutItem[] = [
        ...cards.map((card) => ({ type: "card" as const, card })),
        ...enabledPanels.map((panel) => ({ type: "plugin" as const, panel })),
    ].sort(
        (a, b) =>
            sortValue(a.type === "card" ? a.card : a.panel, 0) - sortValue(b.type === "card" ? b.card : b.panel, 0)
    );

    const normalizedCards = cards.map((card) => {
        const index = layoutItems.findIndex((item) => item.type === "card" && item.card.id === card.id);
        return { ...card, sort: (index + 1) * 10 };
    });
    const normalizedEnabledPanels = enabledPanels.map((panel) => {
        const index = layoutItems.findIndex((item) => item.type === "plugin" && item.panel === panel);
        return { ...toPanelConfig(panel), sort: (index + 1) * 10 };
    });
    const normalizedDisabledPanels = disabledPanels.map((panel, index) => {
        return { ...toPanelConfig(panel), sort: (index + 1) * 10 };
    });

    return {
        autoRefreshEnabled: value.autoRefreshEnabled === true,
        autoRefreshIntervalSeconds:
            value.autoRefreshIntervalSeconds && value.autoRefreshIntervalSeconds >= 10
                ? value.autoRefreshIntervalSeconds
                : 60,
        cards: buildItems(
            [{ ...welcomeCard, enabled: true, sort: 0 }, ...normalizedCards],
            [...normalizedEnabledPanels, ...normalizedDisabledPanels]
        ),
    };
};

const DashboardConfigDrawer: FunctionComponent<DashboardConfigDrawerProps> = ({
    axiosInstance,
    config,
    subtle,
    onSaved,
}) => {
    const [open, setOpen] = useState(false);
    const [panelSettingsOpen, setPanelSettingsOpen] = useState(false);
    const [selectedPanel, setSelectedPanel] = useState<AdminDashboardPluginPanelConfig>();
    const [editingPanelId, setEditingPanelId] = useState<string>();
    const [addingPanel, setAddingPanel] = useState(false);
    const [draftConfig, setDraftConfig] = useState<AdminDashboardConfig>(() => normalizeConfig(config));
    const skipAutoSaveRef = useRef(false);
    const dirtyRef = useRef(false);
    const autoSaveTimerRef = useRef<number>();
    const { token } = theme.useToken();
    const screens = Grid.useBreakpoint();
    const compactLayout = screens.sm !== true;
    const configGridColumns = screens.lg ? "repeat(2, minmax(0, 1fr))" : "minmax(0, 1fr)";
    const settingsGridColumns = compactLayout ? "minmax(0, 1fr)" : "repeat(2, minmax(0, 1fr))";
    const drawerWidth = compactLayout ? "100vw" : 760;
    const panelSettingsWidth = compactLayout ? "calc(100vw - 32px)" : 640;
    const [messageApi, contextHolder] = message.useMessage({ maxCount: 1 });
    const res = getRes().index.dashboardConfig;

    useEffect(() => {
        if (open) {
            skipAutoSaveRef.current = true;
            setDraftConfig(normalizeConfig(config));
            setSelectedPanel(undefined);
            setEditingPanelId(undefined);
            setAddingPanel(false);
            setPanelSettingsOpen(false);
        }
    }, [open]);

    const { cards: draftCards, panels: draftPanels } = useMemo(
        () => splitItems(draftConfig.cards),
        [draftConfig.cards]
    );
    const welcomeCard = useMemo(
        () => draftCards.find((card) => card.id === "welcome") || { id: "welcome", enabled: true, sort: 0 },
        [draftCards]
    );
    const sortedCards = useMemo(
        () => sortBySort(draftCards).filter((card) => card.id !== "pluginPanels" && card.id !== "welcome"),
        [draftCards]
    );
    const enabledCards = sortedCards.filter((card) => card.enabled !== false);
    const disabledCards = sortedCards.filter((card) => card.enabled === false);
    const sortedPanels = useMemo(() => sortBySort(draftPanels), [draftPanels]);
    const enabledPanels = sortedPanels.filter(panelEnabled);
    const disabledPanels = sortedPanels.filter((panel) => !panelEnabled(panel));
    const layoutItems = useMemo<LayoutItem[]>(
        () =>
            [
                ...enabledCards.map((card) => ({ type: "card" as const, card })),
                ...enabledPanels.map((panel) => ({ type: "plugin" as const, panel })),
            ].sort(
                (a, b) =>
                    sortValue(a.type === "card" ? a.card : a.panel, 0) -
                    sortValue(b.type === "card" ? b.card : b.panel, 0)
            ),
        [enabledCards, enabledPanels]
    );
    const disabledItems = useMemo<LayoutItem[]>(
        () =>
            [
                ...disabledCards.map((card) => ({ type: "card" as const, card })),
                ...disabledPanels.map((panel) => ({ type: "plugin" as const, panel })),
            ].sort(
                (a, b) =>
                    sortValue(a.type === "card" ? a.card : a.panel, 0) -
                    sortValue(b.type === "card" ? b.card : b.panel, 0)
            ),
        [disabledCards, disabledPanels]
    );

    const persistConfig = async (configToSave: AdminDashboardConfig) => {
        const { panels } = splitItems(configToSave.cards);
        if (panels.some((panel) => panelEnabled(panel) && !panel.pluginName?.trim())) {
            return;
        }
        const values = normalizeConfig(configToSave);
        await axiosInstance.post<ApiResponse<AdminDashboardConfig>>("/api/admin/index/config", values);
        const { data } = await axiosInstance.get<ApiResponse<IndexData>>("/api/admin/index");
        onSaved(data.data);
        await messageApi.success(res.saveSuccess);
    };

    useEffect(() => {
        if (!open) {
            return;
        }
        if (skipAutoSaveRef.current) {
            skipAutoSaveRef.current = false;
            return;
        }
        if (!dirtyRef.current) {
            return;
        }
        if (autoSaveTimerRef.current) {
            window.clearTimeout(autoSaveTimerRef.current);
        }
        autoSaveTimerRef.current = window.setTimeout(() => {
            dirtyRef.current = false;
            persistConfig(draftConfig);
        }, 600);
        return () => {
            if (autoSaveTimerRef.current) {
                window.clearTimeout(autoSaveTimerRef.current);
            }
        };
    }, [draftConfig, open]);

    const cardName = (id: string) => {
        const names: Record<string, string> = res.cardName;
        return names[id] || id;
    };

    const cardDescription = (id: string) => {
        const descriptions: Record<string, string> = res.cardDescription;
        return descriptions[id] || "";
    };

    const panelName = (panel: AdminDashboardPluginPanelConfig) =>
        panel.title ||
        (panel.data as { title?: string } | undefined)?.title ||
        panel.pluginName ||
        panel.id ||
        res.pluginPanels;

    const panelDescription = (panel: AdminDashboardPluginPanelConfig) =>
        (panel.data as { description?: string } | undefined)?.description ||
        panel.title ||
        panel.pluginName ||
        panel.id ||
        "";

    const layoutItemKey = (item: LayoutItem) =>
        item.type === "card" ? `card:${item.card.id}` : `plugin:${item.panel.id}`;

    const parseDragPayload = (value: string): DragPayload | undefined => {
        try {
            const payload = JSON.parse(value);
            if ((payload.source === "enabled" || payload.source === "disabled") && typeof payload.index === "number") {
                return payload;
            }
        } catch (e) {
            return undefined;
        }
        return undefined;
    };

    const applyDashboardItems = (nextEnabledItems: LayoutItem[], nextDisabledItems: LayoutItem[] = disabledItems) => {
        dirtyRef.current = true;
        setDraftConfig((prev) => ({
            ...prev,
            cards: (() => {
                const { cards, panels } = splitItems(prev.cards);
                const enabledIndexes = new Map(nextEnabledItems.map((item, index) => [layoutItemKey(item), index]));
                const disabledIndexes = new Map(nextDisabledItems.map((item, index) => [layoutItemKey(item), index]));
                const nextCards: AdminDashboardCardConfig[] = [
                    { ...(cards.find((card) => card.id === "welcome") || { id: "welcome" }), enabled: true, sort: 0 },
                    ...cards
                        .filter((card) => card.id !== "pluginPanels" && card.id !== "welcome")
                        .map((card) => {
                            const enabledIndex = enabledIndexes.get(`card:${card.id}`);
                            const disabledIndex = disabledIndexes.get(`card:${card.id}`);
                            if (enabledIndex !== undefined) {
                                return { ...card, enabled: true, sort: (enabledIndex + 1) * 10 };
                            }
                            if (disabledIndex !== undefined) {
                                return { ...card, enabled: false, sort: (disabledIndex + 1) * 10 };
                            }
                            return card;
                        }),
                ];
                const nextPanels = panels.map((panel) => {
                    const enabledIndex = enabledIndexes.get(`plugin:${panel.id}`);
                    const disabledIndex = disabledIndexes.get(`plugin:${panel.id}`);
                    if (enabledIndex !== undefined) {
                        return { ...panel, enabled: true, sort: (enabledIndex + 1) * 10 };
                    }
                    if (disabledIndex !== undefined) {
                        return { ...panel, enabled: false, sort: (disabledIndex + 1) * 10 };
                    }
                    return panel;
                });
                return buildItems(nextCards, nextPanels);
            })(),
        }));
    };

    const updateRefreshConfig = (
        patch: Partial<Pick<AdminDashboardConfig, "autoRefreshEnabled" | "autoRefreshIntervalSeconds">>
    ) => {
        dirtyRef.current = true;
        setDraftConfig((prev) => ({
            ...prev,
            ...patch,
        }));
    };

    const applyEnabledDrop = (payload: DragPayload | undefined, targetIndex: number) => {
        if (!payload) {
            return;
        }
        if (payload.source === "enabled") {
            applyDashboardItems(moveItem(layoutItems, payload.index, targetIndex));
            return;
        }
        const item = disabledItems[payload.index];
        if (!item) {
            return;
        }
        applyDashboardItems(
            insertItem(layoutItems, item, targetIndex),
            disabledItems.filter((_, index) => index !== payload.index)
        );
    };

    const applyDisabledDrop = (payload: DragPayload | undefined, targetIndex = disabledItems.length) => {
        if (!payload) {
            return;
        }
        if (payload.source === "disabled") {
            applyDashboardItems(layoutItems, moveItem(disabledItems, payload.index, targetIndex));
            return;
        }
        const item = layoutItems[payload.index];
        if (!item) {
            return;
        }
        applyDashboardItems(
            layoutItems.filter((_, index) => index !== payload.index),
            insertItem(disabledItems, item, targetIndex)
        );
    };

    const updateSelectedPanel = (patch: Partial<AdminDashboardPluginPanelConfig>) => {
        setSelectedPanel((current) => (current ? { ...current, ...patch } : current));
    };

    const removePanel = (panel: AdminDashboardPluginPanelConfig) => {
        dirtyRef.current = true;
        setDraftConfig((prev) => ({
            ...prev,
            cards: buildItems(
                splitItems(prev.cards).cards,
                splitItems(prev.cards).panels.filter((item) => item.id !== panel.id)
            ),
        }));
        setPanelSettingsOpen(false);
        setSelectedPanel(undefined);
        setEditingPanelId(undefined);
        setAddingPanel(false);
    };

    const addPanel = () => {
        const maxSort = layoutItems.reduce((max, item) => {
            const sort = item.type === "card" ? item.card.sort : item.panel.sort;
            return Math.max(max, sort || 0);
        }, 0);
        const panel = defaultPanel(maxSort + 10);
        setSelectedPanel(panel);
        setEditingPanelId(undefined);
        setAddingPanel(true);
        setPanelSettingsOpen(true);
    };

    const openPanelSettings = (panel: AdminDashboardPluginPanelConfig) => {
        setSelectedPanel({ ...panel });
        setEditingPanelId(panel.id);
        setAddingPanel(false);
        setPanelSettingsOpen(true);
    };

    const closePanelSettings = () => {
        setPanelSettingsOpen(false);
        setSelectedPanel(undefined);
        setEditingPanelId(undefined);
        setAddingPanel(false);
    };

    const applyPanelSettings = () => {
        if (!selectedPanel) {
            return;
        }
        const panel = toPanelConfig(selectedPanel);
        dirtyRef.current = true;
        if (addingPanel) {
            setDraftConfig((prev) => ({
                ...prev,
                cards: buildItems(splitItems(prev.cards).cards, [...splitItems(prev.cards).panels, panel]),
            }));
        } else {
            setDraftConfig((prev) => ({
                ...prev,
                cards: buildItems(
                    splitItems(prev.cards).cards,
                    splitItems(prev.cards).panels.map((item) => (item.id === editingPanelId ? panel : item))
                ),
            }));
        }
        closePanelSettings();
    };

    const removeSelectedPanel = () => {
        if (!selectedPanel) {
            return;
        }
        if (addingPanel) {
            closePanelSettings();
            return;
        }
        removePanel(selectedPanel);
    };

    const itemStyle = {
        border: `${token.lineWidth}px ${token.lineType} ${token.colorBorderSecondary}`,
        borderRadius: token.borderRadius,
        padding: token.paddingSM,
        background: token.colorBgContainer,
    };
    const dropZoneStyle = {
        border: `${token.lineWidth}px dashed ${token.colorBorderSecondary}`,
        borderRadius: token.borderRadius,
        padding: token.paddingSM,
        minHeight: 56,
    };

    const dragHandle = (
        <MenuOutlined
            style={{
                color: token.colorTextTertiary,
                cursor: "grab",
                lineHeight: 1,
            }}
        />
    );

    const renderLayoutItem = (item: LayoutItem, index: number) => {
        const isCard = item.type === "card";
        const title = isCard ? cardName(item.card.id) : panelName(item.panel);
        return (
            <div
                key={
                    isCard
                        ? `card-${item.card.id}`
                        : `plugin-${item.panel.id || item.panel.pluginName || item.panel.sort || index}`
                }
                draggable
                onDragStart={(event) => {
                    event.dataTransfer.setData("dashboardConfigItem", JSON.stringify({ source: "enabled", index }));
                }}
                onDragOver={(event) => event.preventDefault()}
                onDrop={(event) => {
                    event.preventDefault();
                    const payload = parseDragPayload(event.dataTransfer.getData("dashboardConfigItem"));
                    applyEnabledDrop(payload, index);
                }}
                style={{
                    ...itemStyle,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    gap: token.marginSM,
                }}
            >
                <Space>
                    {dragHandle}
                    <Typography.Text>{title}</Typography.Text>
                </Space>
                <Space>
                    <Tooltip title={isCard ? cardDescription(item.card.id) : panelDescription(item.panel)}>
                        <Button type="text" icon={<QuestionCircleOutlined />} />
                    </Tooltip>
                    {!isCard && (
                        <Tooltip title={res.pluginPanels}>
                            <Button
                                type="text"
                                icon={<SettingOutlined />}
                                onClick={() => openPanelSettings(item.panel)}
                            />
                        </Tooltip>
                    )}
                </Space>
            </div>
        );
    };

    const renderWelcomeItem = () => (
        <div
            style={{
                ...itemStyle,
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                gap: token.marginSM,
            }}
        >
            <Typography.Text>{cardName(welcomeCard.id)}</Typography.Text>
            <Space>
                <Tooltip title={cardDescription(welcomeCard.id)}>
                    <Button type="text" icon={<QuestionCircleOutlined />} />
                </Tooltip>
            </Space>
        </div>
    );

    const renderAddPanelItem = () => (
        <Button
            icon={<PlusOutlined />}
            onClick={addPanel}
            style={{
                width: "100%",
                height: "100%",
                minHeight: 48,
            }}
        >
            {res.addPluginPanel}
        </Button>
    );

    const renderDisabledItem = (item: LayoutItem, index: number) => {
        const isCard = item.type === "card";
        const title = isCard ? cardName(item.card.id) : panelName(item.panel);
        return (
            <div
                key={
                    isCard
                        ? `disabled-card-${item.card.id}`
                        : `disabled-plugin-${item.panel.id || item.panel.pluginName || item.panel.sort || index}`
                }
                draggable
                onDragStart={(event) => {
                    event.dataTransfer.setData("dashboardConfigItem", JSON.stringify({ source: "disabled", index }));
                }}
                onDragOver={(event) => event.preventDefault()}
                onDrop={(event) => {
                    event.preventDefault();
                    const payload = parseDragPayload(event.dataTransfer.getData("dashboardConfigItem"));
                    applyDisabledDrop(payload, index);
                }}
                style={{
                    ...itemStyle,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    gap: token.marginSM,
                }}
            >
                <Space>
                    {dragHandle}
                    <Typography.Text>{title}</Typography.Text>
                </Space>
                <Space>
                    <Tooltip title={isCard ? cardDescription(item.card.id) : panelDescription(item.panel)}>
                        <Button type="text" icon={<QuestionCircleOutlined />} />
                    </Tooltip>
                    {!isCard && (
                        <Button type="text" icon={<SettingOutlined />} onClick={() => openPanelSettings(item.panel)} />
                    )}
                </Space>
            </div>
        );
    };

    const renderPanelSettings = () => {
        if (!selectedPanel) {
            return null;
        }
        return (
            <div style={{ display: "grid", gap: token.marginSM }}>
                <Space wrap align="center" style={{ width: "100%" }}>
                    <Select
                        value={selectedPanel.type || "surface"}
                        style={{ width: compactLayout ? "100%" : 140 }}
                        options={[
                            { value: "surface", label: res.typeOption.surface },
                            { value: "view", label: res.typeOption.view },
                        ]}
                        onChange={(type) => updateSelectedPanel({ type })}
                    />
                </Space>
                <div
                    style={{
                        display: "grid",
                        gridTemplateColumns: settingsGridColumns,
                        gap: token.marginSM,
                    }}
                >
                    <Input
                        value={selectedPanel.pluginName}
                        placeholder={res.pluginName}
                        onChange={(event) => {
                            const pluginName = event.target.value.trim();
                            updateSelectedPanel({ pluginName });
                        }}
                    />
                    {selectedPanel.type === "view" ? (
                        <>
                            <Input
                                value={selectedPanel.viewUrl}
                                placeholder={res.viewUrl}
                                onChange={(event) => updateSelectedPanel({ viewUrl: event.target.value })}
                            />
                            <InputNumber
                                min={180}
                                value={selectedPanel.height}
                                placeholder={res.height}
                                style={{ width: "100%" }}
                                onChange={(height) => updateSelectedPanel({ height: height ?? undefined })}
                            />
                        </>
                    ) : (
                        <InputNumber
                            min={1}
                            value={selectedPanel.maxItems}
                            placeholder={res.maxItems}
                            style={{ width: "100%" }}
                            onChange={(maxItems) => updateSelectedPanel({ maxItems: maxItems ?? undefined })}
                        />
                    )}
                </div>
                <Typography.Text type="secondary">{res.pluginNameTip}</Typography.Text>
                <Collapse
                    size="small"
                    ghost
                    items={[
                        {
                            key: "advanced",
                            label: res.advanced,
                            children: (
                                <div
                                    style={{
                                        display: "grid",
                                        gridTemplateColumns: settingsGridColumns,
                                        gap: token.marginSM,
                                    }}
                                >
                                    <Input
                                        value={selectedPanel.surfaceUrl}
                                        placeholder={res.surfaceUrl}
                                        onChange={(event) => updateSelectedPanel({ surfaceUrl: event.target.value })}
                                    />
                                    <Input
                                        value={selectedPanel.actionUrl}
                                        placeholder={res.actionUrl}
                                        onChange={(event) => updateSelectedPanel({ actionUrl: event.target.value })}
                                    />
                                </div>
                            ),
                        },
                    ]}
                />
            </div>
        );
    };

    return (
        <>
            {contextHolder}
            <Tooltip title={res.entry}>
                <Button
                    icon={<SettingOutlined />}
                    type={subtle ? "text" : "default"}
                    style={subtle ? { color: "inherit" } : undefined}
                    onClick={() => setOpen(true)}
                />
            </Tooltip>
            <Drawer
                title={res.title}
                open={open}
                width={drawerWidth}
                onClose={() => {
                    setOpen(false);
                    closePanelSettings();
                }}
            >
                <Typography.Title level={5}>{res.autoRefresh}</Typography.Title>
                <div
                    style={{
                        ...itemStyle,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "space-between",
                        gap: token.marginSM,
                        flexWrap: "wrap",
                        marginBottom: token.marginLG,
                    }}
                >
                    <Space>
                        <Switch
                            checked={draftConfig.autoRefreshEnabled === true}
                            onChange={(autoRefreshEnabled) => updateRefreshConfig({ autoRefreshEnabled })}
                        />
                        <Typography.Text>{res.autoRefreshState}</Typography.Text>
                    </Space>
                    <Space>
                        <Typography.Text type="secondary">{res.autoRefreshInterval}</Typography.Text>
                        <InputNumber
                            min={10}
                            value={draftConfig.autoRefreshIntervalSeconds || 60}
                            disabled={draftConfig.autoRefreshEnabled !== true}
                            onChange={(autoRefreshIntervalSeconds) =>
                                updateRefreshConfig({ autoRefreshIntervalSeconds: autoRefreshIntervalSeconds ?? 60 })
                            }
                        />
                        <Typography.Text type="secondary">{res.autoRefreshIntervalUnit}</Typography.Text>
                    </Space>
                </div>
                <Typography.Title level={5}>{res.cards}</Typography.Title>
                <div
                    onDragOver={(event) => event.preventDefault()}
                    onDrop={(event) => {
                        event.preventDefault();
                        const payload = parseDragPayload(event.dataTransfer.getData("dashboardConfigItem"));
                        applyEnabledDrop(payload, layoutItems.length);
                    }}
                    style={{
                        ...dropZoneStyle,
                        display: "grid",
                        gridTemplateColumns: configGridColumns,
                        gap: token.marginSM,
                    }}
                >
                    {renderWelcomeItem()}
                    {layoutItems.map(renderLayoutItem)}
                    {renderAddPanelItem()}
                </div>
                <Typography.Title level={5} style={{ marginTop: token.marginLG }}>
                    {res.disabledPanels}
                </Typography.Title>
                <div
                    onDragOver={(event) => event.preventDefault()}
                    onDrop={(event) => {
                        event.preventDefault();
                        const payload = parseDragPayload(event.dataTransfer.getData("dashboardConfigItem"));
                        applyDisabledDrop(payload);
                    }}
                    style={{
                        ...dropZoneStyle,
                        display: "grid",
                        gridTemplateColumns: configGridColumns,
                        gap: token.marginSM,
                    }}
                >
                    {disabledItems.length > 0 ? (
                        disabledItems.map(renderDisabledItem)
                    ) : (
                        <Typography.Text type="secondary">{res.disabledEmpty}</Typography.Text>
                    )}
                </div>
                <Modal
                    title={selectedPanel ? panelName(selectedPanel) : res.pluginPanels}
                    open={panelSettingsOpen}
                    width={panelSettingsWidth}
                    onCancel={closePanelSettings}
                    footer={
                        <div
                            style={{
                                display: "flex",
                                flexDirection: compactLayout ? "column-reverse" : "row",
                                justifyContent: "space-between",
                                gap: token.marginSM,
                            }}
                        >
                            <Button
                                danger
                                icon={<DeleteOutlined />}
                                block={compactLayout}
                                onClick={removeSelectedPanel}
                            />
                            <Space
                                direction={compactLayout ? "vertical" : "horizontal"}
                                style={{ width: compactLayout ? "100%" : undefined }}
                            >
                                <Button block={compactLayout} onClick={closePanelSettings}>
                                    {getRes().pluginSurface.cancel}
                                </Button>
                                <Button
                                    type="primary"
                                    block={compactLayout}
                                    disabled={panelEnabled(selectedPanel || {}) && !selectedPanel?.pluginName?.trim()}
                                    onClick={applyPanelSettings}
                                >
                                    {getRes().yes}
                                </Button>
                            </Space>
                        </div>
                    }
                >
                    {renderPanelSettings()}
                </Modal>
            </Drawer>
        </>
    );
};

export default DashboardConfigDrawer;
