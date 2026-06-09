import { Alert, App, Button, Empty, Form, Input, Modal, Select, Space, Switch, Tag, theme, Tooltip } from "antd";
import { ReloadOutlined } from "@ant-design/icons";
import { Column, Line, Pie } from "@ant-design/plots";
import { AxiosInstance } from "axios";
import { CSSProperties, FunctionComponent, ReactNode, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getRes } from "../../utils/constants";
import { buildBackgroundTaskResult, getBackgroundTaskNoticeType } from "../../utils/background-task-result";
import { navigateToPluginAdminRoute } from "../plugin-admin-route";
import {
    InteractiveSurface,
    StandardSurfaceResponse,
    SurfaceAction,
    SurfaceActionResponse,
    SurfaceChart,
    SurfaceField,
    SurfaceStatus,
    SurfaceViewLink,
} from "./types";

export type InteractiveSurfaceViewProps = {
    surfaceUrl: string;
    actionUrl: string;
    axiosInstance: AxiosInstance;
    className?: string;
    style?: CSSProperties;
    title?: string;
    maxItems?: number;
    initialSurface?: InteractiveSurface;
    initialLoadStatus?: "success" | "error";
    initialError?: string;
    loadOnMount?: boolean;
    notifyLoadError?: boolean;
    reloadToken?: number | string;
    showRefresh?: boolean;
    onOpenView?: (view: string, url?: string) => void;
    onRefresh?: (surface: InteractiveSurface) => void;
};

const statusColor = (status?: SurfaceStatus) => {
    if (status === "warning") {
        return "gold";
    }
    if (status === "error") {
        return "red";
    }
    if (status === "processing") {
        return "blue";
    }
    return "default";
};

const statusText = (status?: SurfaceStatus) => {
    const res = getRes().pluginSurface.status;
    if (status === "warning") {
        return res.warning;
    }
    if (status === "error") {
        return res.error;
    }
    if (status === "processing") {
        return res.processing;
    }
    return res.normal;
};

const buttonType = (action: SurfaceAction) => (action.style === "primary" ? "primary" : "default");

const fieldNode = (field: SurfaceField): ReactNode => {
    if (field.type === "textarea") {
        return <Input.TextArea rows={3} placeholder={field.placeholder} maxLength={500} />;
    }
    if (field.type === "datetime") {
        return <Input type="datetime-local" />;
    }
    if (field.type === "switch") {
        return <Switch />;
    }
    if (field.type === "select") {
        return <Select options={field.options || []} />;
    }
    return <Input placeholder={field.placeholder} maxLength={120} />;
};

const errorMessage = (e: unknown, fallback: string) => {
    if (e instanceof Error && e.message) {
        return e.message;
    }
    return fallback;
};

const numberValue = (value: string | number | null | undefined) => {
    if (typeof value === "number") {
        return Number.isFinite(value) ? value : 0;
    }
    if (typeof value === "string") {
        const parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : 0;
    }
    return 0;
};

const stringValue = (value: string | number | null | undefined) => {
    if (value === null || value === undefined) {
        return "";
    }
    return String(value);
};

const chartLegend = (token: ReturnType<typeof theme.useToken>["token"]) => ({
    color: {
        position: "bottom" as const,
        itemLabelFill: token.colorTextSecondary,
        itemValueFill: token.colorTextSecondary,
        titleFill: token.colorTextSecondary,
    },
});

const SurfaceChartView: FunctionComponent<{
    chart: SurfaceChart;
    emptyText: string;
    styles: {
        chart: CSSProperties;
        chartTitle: CSSProperties;
        chartEmpty: CSSProperties;
    };
    token: ReturnType<typeof theme.useToken>["token"];
}> = ({ chart, emptyText, styles, token }) => {
    const height = Math.max(120, Math.min(chart.height || 160, 320));

    if (!chart.data || chart.data.length === 0) {
        return (
            <div style={styles.chart}>
                {chart.title && <div style={styles.chartTitle}>{chart.title}</div>}
                <div style={{ ...styles.chartEmpty, minHeight: height }}>{emptyText}</div>
            </div>
        );
    }

    if (chart.type === "donut" || chart.type === "pie") {
        const nameField = chart.nameField || "name";
        const valueField = chart.valueField || "value";
        const rows = chart.data.map((row, index) => ({
            id: `${stringValue(row[nameField])}-${index}`,
            name: stringValue(row[nameField]),
            value: numberValue(row[valueField]),
        }));
        const total = rows.reduce((sum, row) => sum + row.value, 0);

        return (
            <div style={styles.chart}>
                {chart.title && <div style={styles.chartTitle}>{chart.title}</div>}
                {total <= 0 ? (
                    <div style={{ ...styles.chartEmpty, minHeight: height }}>{emptyText}</div>
                ) : (
                    <Pie
                        data={rows}
                        angleField="value"
                        colorField="name"
                        height={height}
                        innerRadius={chart.type === "donut" ? 0.62 : 0}
                        legend={chartLegend(token)}
                        tooltip={{
                            title: "name",
                            items: [
                                {
                                    field: "value",
                                    name: chart.unit || valueField,
                                },
                            ],
                        }}
                    />
                )}
            </div>
        );
    }

    const xField = chart.xField || "label";
    const yField = chart.yField || "value";
    const seriesField = chart.seriesField || chart.nameField;
    const rows = chart.data.map((row) => ({
        label: stringValue(row[xField]),
        value: numberValue(row[yField]),
        ...(seriesField ? { series: stringValue(row[seriesField]) } : {}),
    }));
    const axis = {
        labelFill: token.colorTextSecondary,
        titleFill: token.colorTextSecondary,
        lineStroke: token.colorBorderSecondary,
        tickStroke: token.colorBorderSecondary,
    };
    const seriesConfig = seriesField
        ? {
              seriesField: "series",
              colorField: "series",
              legend: chartLegend(token),
          }
        : {
              colorField: token.colorPrimary,
          };
    const commonConfig = {
        data: rows,
        xField: "label",
        yField: "value",
        height,
        ...seriesConfig,
        axis: {
            x: axis,
            y: axis,
        },
        tooltip: {
            title: "label",
            items: [
                {
                    field: "value",
                    name: chart.unit || yField,
                },
            ],
        },
    };
    const columnConfig = seriesField ? { ...commonConfig, group: true } : commonConfig;

    return (
        <div style={styles.chart}>
            {chart.title && <div style={styles.chartTitle}>{chart.title}</div>}
            {chart.type === "bar" ? <Column {...columnConfig} /> : <Line {...commonConfig} />}
        </div>
    );
};

const InteractiveSurfaceView: FunctionComponent<InteractiveSurfaceViewProps> = ({
    surfaceUrl,
    actionUrl,
    axiosInstance,
    className,
    style,
    title,
    maxItems,
    initialSurface,
    initialLoadStatus,
    initialError,
    loadOnMount = true,
    notifyLoadError = true,
    reloadToken,
    showRefresh = true,
    onOpenView,
    onRefresh,
}) => {
    const { message } = App.useApp();
    const navigate = useNavigate();
    const { token } = theme.useToken();
    const res = getRes().pluginSurface;
    const [surface, setSurface] = useState<InteractiveSurface | null>(initialSurface || null);
    const [loading, setLoading] = useState(loadOnMount && !initialSurface);
    const [loadError, setLoadError] = useState(
        initialLoadStatus === "error" ? initialError || res.loadFailed : undefined
    );
    const [submittingRef, setSubmittingRef] = useState<string>();
    const [activeAction, setActiveAction] = useState<SurfaceAction | null>(null);
    const [actionNotice, setActionNotice] = useState<{ type: "success" | "warning" | "error"; message: string }>();
    const [form] = Form.useForm();

    const styles = useMemo(() => {
        const borderSecondary = `${token.lineWidth}px ${token.lineType} ${token.colorBorderSecondary}`;
        return {
            shell: {
                width: "100%",
                boxSizing: "border-box" as const,
                ...style,
            },
            head: {
                display: "flex",
                alignItems: "flex-start",
                justifyContent: "space-between",
                gap: token.marginSM,
                marginBottom: token.marginSM,
            },
            titleWrap: {
                minWidth: 0,
            },
            titleRow: {
                display: "flex",
                alignItems: "center",
                gap: token.marginXS,
                minWidth: 0,
            },
            title: {
                margin: 0,
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap" as const,
                fontSize: token.fontSizeLG,
                lineHeight: `${token.lineHeightLG}em`,
                fontWeight: 650,
            },
            description: {
                marginTop: 4,
                color: token.colorTextSecondary,
                fontSize: token.fontSizeSM,
            },
            metrics: {
                display: "grid",
                gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 136px), 1fr))",
                gap: token.marginXS,
                marginBottom: token.marginSM,
            },
            metric: {
                minWidth: 0,
                padding: token.paddingSM,
                border: borderSecondary,
                borderRadius: token.borderRadius,
                background: token.colorBgContainer,
            },
            metricLabel: {
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap" as const,
                color: token.colorTextSecondary,
                fontSize: token.fontSizeSM,
            },
            metricValue: {
                marginTop: 2,
                fontSize: token.fontSizeHeading3,
                lineHeight: `${token.lineHeightHeading3}em`,
                fontWeight: 700,
                overflowWrap: "anywhere" as const,
                wordBreak: "break-word" as const,
            },
            list: {
                border: borderSecondary,
                borderRadius: token.borderRadius,
                background: token.colorBgContainer,
                overflow: "hidden",
            },
            charts: {
                display: "grid",
                gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 220px), 1fr))",
                gap: token.marginSM,
                marginBottom: token.marginSM,
            },
            chart: {
                minWidth: 0,
                overflow: "hidden",
                padding: token.paddingSM,
                border: borderSecondary,
                borderRadius: token.borderRadius,
                background: token.colorBgContainer,
            },
            chartTitle: {
                marginBottom: token.marginXS,
                color: token.colorText,
                fontSize: token.fontSize,
                fontWeight: 600,
            },
            chartEmpty: {
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                color: token.colorTextSecondary,
                fontSize: token.fontSizeSM,
            },
            item: {
                display: "grid",
                gridTemplateColumns: "minmax(0, 1fr) auto",
                gap: token.marginSM,
                alignItems: "center",
                padding: `${token.paddingXS}px ${token.paddingSM}px`,
                borderBottom: borderSecondary,
            },
            itemMain: {
                minWidth: 0,
            },
            itemTitle: {
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap" as const,
                fontSize: token.fontSize,
                fontWeight: 600,
            },
            itemDescription: {
                marginTop: 2,
                color: token.colorTextSecondary,
                fontSize: token.fontSizeSM,
            },
            empty: {
                padding: `${token.paddingLG}px ${token.paddingSM}px`,
                color: token.colorTextSecondary,
                textAlign: "center" as const,
                fontSize: token.fontSizeSM,
            },
            actions: {
                marginTop: token.marginSM,
            },
        };
    }, [style, token]);

    const loadSurface = async () => {
        setLoading(true);
        setActionNotice(undefined);
        try {
            const { data } = await axiosInstance.get<StandardSurfaceResponse<InteractiveSurface>>(surfaceUrl);
            if (!data.success) {
                throw new Error(data.message || res.loadFailed);
            }
            setSurface(data.data);
            setLoadError(undefined);
            onRefresh?.(data.data);
        } catch (e) {
            const error = errorMessage(e, res.loadFailed);
            setLoadError(error);
            if (notifyLoadError) {
                message.error(error);
            }
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (initialSurface && (reloadToken === undefined || reloadToken === 0)) {
            setSurface(initialSurface);
            setLoadError(undefined);
            setLoading(false);
            return;
        }
        setSurface(null);
        setLoadError(initialLoadStatus === "error" ? initialError || res.loadFailed : undefined);
        if (loadOnMount) {
            loadSurface();
            return;
        }
        setLoading(false);
    }, [initialError, initialLoadStatus, initialSurface, loadOnMount, reloadToken, res.loadFailed, surfaceUrl]);

    const openPluginView = (viewLink?: SurfaceViewLink) => {
        const targetView = viewLink || surface?.view;
        if (!targetView) {
            return;
        }
        onOpenView?.(targetView.view, targetView.url);
    };

    const resolveActionOpenView = (actionResponse: SurfaceActionResponse) => {
        if (actionResponse.openView && typeof actionResponse.openView !== "boolean") {
            return actionResponse.openView;
        }
        if (actionResponse.openView === true || actionResponse.refreshStrategy === "openView") {
            return actionResponse.surface?.view || surface?.view;
        }
        return undefined;
    };

    const openActionAdminRoute = (actionResponse: SurfaceActionResponse) => {
        return navigateToPluginAdminRoute(navigate, actionResponse.openAdminRoute || actionResponse.adminRoute);
    };

    const showActionResult = (status: "success" | "warning" | "error", description: string) => {
        setActionNotice({ type: status, message: description });
        if (status === "error") {
            message.error(description);
            return;
        }
        if (status === "warning") {
            message.warning(description);
            return;
        }
        message.success(description);
    };

    const runAction = async (action: SurfaceAction, values: Record<string, unknown> = {}) => {
        setSubmittingRef(action.actionRef);
        setActionNotice(undefined);
        try {
            const body = new URLSearchParams();
            body.set("actionRef", action.actionRef);
            body.set("values", JSON.stringify(values));
            const { data } = await axiosInstance.post<StandardSurfaceResponse<SurfaceActionResponse>>(actionUrl, body, {
                headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
            });
            if (!data.success) {
                throw new Error(data.message || res.actionFailed);
            }
            const actionResponse = data.data || {};
            if (actionResponse.surface) {
                setSurface(actionResponse.surface);
                onRefresh?.(actionResponse.surface);
            }
            if (actionResponse.refreshSurface || actionResponse.refreshStrategy === "reload") {
                await loadSurface();
            }
            const targetView = resolveActionOpenView(actionResponse);
            if (targetView) {
                openPluginView(targetView);
            }
            openActionAdminRoute(actionResponse);
            const actionResult = buildBackgroundTaskResult({
                success: true,
                message: actionResponse.message,
            });
            if (actionResult.description) {
                showActionResult(getBackgroundTaskNoticeType(actionResult.status), actionResult.description);
            }
        } catch (e) {
            const error = errorMessage(e, res.actionFailed);
            const actionResult = buildBackgroundTaskResult(
                {
                    success: false,
                    message: error,
                },
                {
                    fallbackErrorDescription: res.actionFailed,
                }
            );
            showActionResult(
                getBackgroundTaskNoticeType(actionResult.status),
                actionResult.description || res.actionFailed
            );
        } finally {
            setSubmittingRef(undefined);
        }
    };

    const handleAction = (action: SurfaceAction) => {
        if (navigateToPluginAdminRoute(navigate, action.openAdminRoute || action.adminRoute)) {
            return;
        }
        if (action.form && action.form.length > 0) {
            setActiveAction(action);
            form.resetFields();
            return;
        }
        runAction(action);
    };

    const submitModal = async () => {
        if (!activeAction) {
            return;
        }
        const values = await form.validateFields();
        await runAction(activeAction, values);
        setActiveAction(null);
    };

    const items = maxItems && surface?.items ? surface.items.slice(0, maxItems) : surface?.items || [];
    const showList =
        surface !== null &&
        (surface.items !== undefined || ((surface.metrics || []).length === 0 && (surface.charts || []).length === 0));

    return (
        <div className={className} style={styles.shell}>
            <div style={styles.head}>
                <div style={styles.titleWrap}>
                    <div style={styles.titleRow}>
                        <h1 style={styles.title}>{surface?.title || title || res.fallbackTitle}</h1>
                        {surface?.status && <Tag color={statusColor(surface.status)}>{statusText(surface.status)}</Tag>}
                    </div>
                    {surface?.description && <div style={styles.description}>{surface.description}</div>}
                </div>
                <Space size={4}>
                    {showRefresh && (
                        <Tooltip title={res.refresh}>
                            <Button
                                type="text"
                                size="small"
                                icon={<ReloadOutlined />}
                                loading={loading}
                                onClick={() => void loadSurface()}
                            />
                        </Tooltip>
                    )}
                    {surface?.view && <Button onClick={() => openPluginView()}>{surface.view.label}</Button>}
                </Space>
            </div>

            {actionNotice && (
                <Alert
                    type={actionNotice.type}
                    showIcon
                    closable
                    message={actionNotice.message}
                    style={{ marginBottom: token.marginSM }}
                    onClose={() => setActionNotice(undefined)}
                />
            )}

            {loading ? (
                <div style={styles.empty}>{res.loading}</div>
            ) : loadError ? (
                <Alert type="error" showIcon message={loadError} />
            ) : surface === null ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
                <>
                    <div style={styles.metrics}>
                        {(surface.metrics || []).map((metric) => (
                            <div style={styles.metric} key={metric.label}>
                                <div style={styles.metricLabel}>{metric.label}</div>
                                <div style={styles.metricValue}>{metric.value}</div>
                            </div>
                        ))}
                    </div>

                    {(surface.charts || []).length > 0 && (
                        <div style={styles.charts}>
                            {(surface.charts || []).map((chart, index) => (
                                <SurfaceChartView
                                    chart={chart}
                                    emptyText={res.chartEmpty}
                                    key={`${chart.title || chart.type}-${index}`}
                                    styles={styles}
                                    token={token}
                                />
                            ))}
                        </div>
                    )}

                    {showList && (
                        <div style={styles.list}>
                            {items.length === 0 ? (
                                <div style={styles.empty}>{res.empty}</div>
                            ) : (
                                items.map((item, index) => (
                                    <div
                                        style={{
                                            ...styles.item,
                                            borderBottom:
                                                index === items.length - 1 ? "none" : styles.item.borderBottom,
                                        }}
                                        key={item.id}
                                    >
                                        <div style={styles.itemMain}>
                                            <div style={styles.itemTitle}>{item.title}</div>
                                            {item.description && (
                                                <div style={styles.itemDescription}>{item.description}</div>
                                            )}
                                        </div>
                                        <Space wrap>
                                            {(item.actions || []).map((action) => (
                                                <Button
                                                    key={action.actionRef}
                                                    size="small"
                                                    danger={action.style === "danger"}
                                                    type={buttonType(action)}
                                                    loading={submittingRef === action.actionRef}
                                                    onClick={() => handleAction(action)}
                                                >
                                                    {action.label}
                                                </Button>
                                            ))}
                                        </Space>
                                    </div>
                                ))
                            )}
                        </div>
                    )}

                    <Space wrap style={styles.actions}>
                        {(surface.actions || []).map((action) => (
                            <Button
                                key={action.actionRef}
                                danger={action.style === "danger"}
                                type={buttonType(action)}
                                loading={submittingRef === action.actionRef}
                                onClick={() => handleAction(action)}
                            >
                                {action.label}
                            </Button>
                        ))}
                    </Space>
                </>
            )}

            <Modal
                title={activeAction?.label}
                open={activeAction !== null}
                okText={res.submit}
                cancelText={res.cancel}
                onOk={submitModal}
                onCancel={() => setActiveAction(null)}
            >
                <Form form={form} layout="vertical">
                    {(activeAction?.form || []).map((field) => (
                        <Form.Item
                            key={field.name}
                            name={field.name}
                            label={field.label}
                            valuePropName={field.type === "switch" ? "checked" : "value"}
                            rules={
                                field.required
                                    ? [{ required: true, message: res.requiredMessage.replace("{label}", field.label) }]
                                    : undefined
                            }
                        >
                            {fieldNode(field)}
                        </Form.Item>
                    ))}
                </Form>
            </Modal>
        </div>
    );
};

export default InteractiveSurfaceView;
