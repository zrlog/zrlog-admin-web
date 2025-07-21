import React, { RefObject, useEffect, useState } from "react";
import { App, Button, Drawer, Empty, List, Select, Space, Tag, Typography } from "antd";
import { HistoryOutlined, RollbackOutlined } from "@ant-design/icons";
import TimeAgo from "@editor/dist/src/editor/TimeAgo";
import { getRes } from "../../utils/constants";
import { getAppState } from "../../base/ConfigProviderApp";
import { useTheme } from "antd-style";
import { getShortcutTitle } from "./shortcut-utils";

const { Paragraph, Text } = Typography;

type DiffLineType = "context" | "add" | "remove";

type DiffLine = {
    type: DiffLineType;
    content: string;
};

type VersionItem = {
    version: number;
    createdAt?: number;
    userId?: number;
    title?: string;
    current?: boolean;
};

type CompareResponse = {
    fromVersion: number;
    toVersion: number;
    changedFields: string[];
    fromArticle: Record<string, any>;
    toArticle: Record<string, any>;
};

type ArticleVersionDrawerProps = {
    logId?: number;
    currentVersion: number;
    axiosInstance: any;
    onRollback: (targetVersion: number) => Promise<void>;
    containerRef: RefObject<HTMLDivElement>;
    open?: boolean;
    onOpenChange?: (open: boolean) => void;
};

const buildLineDiff = (beforeText: string, afterText: string): DiffLine[] => {
    const before = beforeText.split("\n");
    const after = afterText.split("\n");
    const dp: number[][] = Array.from({ length: before.length + 1 }, () => Array(after.length + 1).fill(0));

    for (let i = before.length - 1; i >= 0; i--) {
        for (let j = after.length - 1; j >= 0; j--) {
            if (before[i] === after[j]) {
                dp[i][j] = dp[i + 1][j + 1] + 1;
            } else {
                dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }
    }

    const lines: DiffLine[] = [];
    let i = 0;
    let j = 0;
    while (i < before.length && j < after.length) {
        if (before[i] === after[j]) {
            lines.push({ type: "context", content: before[i] });
            i++;
            j++;
        } else if (dp[i + 1][j] >= dp[i][j + 1]) {
            lines.push({ type: "remove", content: before[i] });
            i++;
        } else {
            lines.push({ type: "add", content: after[j] });
            j++;
        }
    }

    while (i < before.length) {
        lines.push({ type: "remove", content: before[i] });
        i++;
    }
    while (j < after.length) {
        lines.push({ type: "add", content: after[j] });
        j++;
    }

    return lines;
};

const getDiffLineStyle = (type: DiffLineType) => {
    switch (type) {
        case "add":
            return {
                background: "rgba(34, 197, 94, 0.12)",
                color: "#166534",
            };
        case "remove":
            return {
                background: "rgba(239, 68, 68, 0.12)",
                color: "#991b1b",
            };
        default:
            return {
                background: "transparent",
                color: getAppState().dark ? "rgba(255,255,255,0.8)" : "rgba(15,23,42,0.82)",
            };
    }
};

const getDiffPrefix = (type: DiffLineType) => {
    switch (type) {
        case "add":
            return "+";
        case "remove":
            return "-";
        default:
            return " ";
    }
};

const ArticleVersionDrawer: React.FC<ArticleVersionDrawerProps> = ({
    logId,
    currentVersion,
    axiosInstance,
    onRollback,
    containerRef,
    open,
    onOpenChange,
}) => {
    const theme = useTheme();
    const [innerOpen, setInnerOpen] = useState(false);
    const [versions, setVersions] = useState<VersionItem[]>([]);
    const [loading, setLoading] = useState(false);
    const [compareLoading, setCompareLoading] = useState(false);
    const [selectedVersion, setSelectedVersion] = useState<number | undefined>(undefined);
    const [compareData, setCompareData] = useState<CompareResponse | null>(null);
    const { modal } = App.useApp();
    const drawerOpen = open ?? innerOpen;

    useEffect(() => {
        if (open !== undefined) {
            setInnerOpen(open);
        }
    }, [open]);

    const updateOpen = (nextOpen: boolean) => {
        if (open === undefined) {
            setInnerOpen(nextOpen);
        }
        onOpenChange?.(nextOpen);
    };

    const loadVersions = async () => {
        if (!logId) {
            return;
        }
        setLoading(true);
        try {
            const { data } = await axiosInstance.get(`/api/admin/article-version?id=${logId}`);
            const items = data.data ? (data.data as VersionItem[]) : [];
            setVersions(items);
            if (!selectedVersion && items.length > 1) {
                setSelectedVersion(items[1].version);
            }
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (drawerOpen) {
            void loadVersions();
        }
    }, [drawerOpen, logId, currentVersion]);

    useEffect(() => {
        const loadCompare = async () => {
            if (!drawerOpen || !logId || selectedVersion === undefined) {
                return;
            }
            setCompareLoading(true);
            try {
                const { data } = await axiosInstance.get(
                    `/api/admin/article-version/compare?id=${logId}&fromVersion=${selectedVersion}&toVersion=${currentVersion}`
                );
                setCompareData(data.data as CompareResponse);
            } finally {
                setCompareLoading(false);
            }
        };
        void loadCompare();
    }, [drawerOpen, logId, selectedVersion, currentVersion]);

    const versionOptions = versions
        .filter((item) => item.version !== currentVersion)
        .map((item) => ({
            value: item.version,
            label: `v${item.version}`,
        }));

    const openRollbackConfirm = (version: number) => {
        modal.confirm({
            title: `${getRes().articleEdit.version.rollback.label} v${version}`,
            content: getRes().articleEdit.version.rollback.tip,
            okText: getRes().confirm,
            cancelText: getRes().cancel,
            getContainer: () => containerRef.current as HTMLElement,
            onOk: async () => {
                await onRollback(version);
                updateOpen(false);
            },
        });
    };

    const markdownDiffLines = compareData
        ? buildLineDiff(
              compareData.fromArticle?.markdown || compareData.fromArticle?.content || "",
              compareData.toArticle?.markdown || compareData.toArticle?.content || ""
          )
        : [];

    const titleDiffLines = compareData
        ? buildLineDiff(compareData.fromArticle?.title || "", compareData.toArticle?.title || "")
        : [];

    return (
        <>
            <Button
                href={"#articleVersionHistory"}
                type={"text"}
                title={getShortcutTitle(getRes().articleEdit.version.label, {
                    alt: true,
                    shift: true,
                    key: "V",
                })}
                disabled={!logId}
                style={{
                    border: 0,
                    display: "flex",
                    justifyContent: "center",
                    alignItems: "center",
                    cursor: !logId ? "not-allowed" : "pointer",
                    color: "rgb(119, 119, 119)",
                }}
                icon={<HistoryOutlined style={{ fontSize: getAppState().compactMode ? 18 : 24, display: "flex" }} />}
                onClick={(e) => {
                    e.stopPropagation();
                    e.preventDefault();
                    updateOpen(true);
                }}
            />
            <Drawer
                title={getRes().articleEdit.version.label}
                width={960}
                open={drawerOpen}
                onClose={() => updateOpen(false)}
                //@ts-ignore
                getContainer={() => {
                    return containerRef.current;
                }}
            >
                <Space direction="vertical" size={16} style={{ width: "100%" }}>
                    <Space style={{ width: "100%", justifyContent: "space-between" }} wrap>
                        <Space wrap>
                            <Text type="secondary">{getRes().articleEdit.version.current}</Text>
                            <Tag color="blue">v{currentVersion}</Tag>
                        </Space>
                        <Space wrap>
                            <Text type="secondary">{getRes().articleEdit.version.compare}</Text>
                            <Select
                                style={{ minWidth: 120 }}
                                loading={loading}
                                value={selectedVersion}
                                options={versionOptions}
                                onChange={setSelectedVersion}
                                placeholder="v0"
                            />
                            {selectedVersion !== undefined && (
                                <Button
                                    icon={<RollbackOutlined />}
                                    onClick={() => openRollbackConfirm(selectedVersion)}
                                >
                                    {getRes().articleEdit.version.rollback.label}
                                </Button>
                            )}
                        </Space>
                    </Space>
                    <List
                        bordered
                        loading={loading}
                        locale={{
                            emptyText: <Empty description={getRes().articleEdit.version.empty} />,
                        }}
                        dataSource={versions}
                        renderItem={(item) => (
                            <List.Item
                                actions={
                                    !item.current
                                        ? [
                                              <Button
                                                  type="link"
                                                  key="compare"
                                                  onClick={() => setSelectedVersion(item.version)}
                                              >
                                                  {getRes().articleEdit.version.compareAction}
                                              </Button>,
                                          ]
                                        : []
                                }
                            >
                                <Space style={{ width: "100%", justifyContent: "space-between" }} wrap>
                                    <Space>
                                        <Tag color={item.current ? "blue" : "default"}>
                                            {item.current ? getRes().articleEdit.version.current : `v${item.version}`}
                                        </Tag>
                                        <Text>{item.title || "-"}</Text>
                                    </Space>
                                    <Space>{item.createdAt ? <TimeAgo timestamp={item.createdAt} /> : null}</Space>
                                </Space>
                            </List.Item>
                        )}
                    />
                    {compareData ? (
                        <Space direction="vertical" size={12} style={{ width: "100%" }}>
                            <Space wrap>
                                <Tag color="gold">v{compareData.fromVersion}</Tag>
                                <Tag color="blue">v{compareData.toVersion}</Tag>
                                {compareData.changedFields.map((field) => (
                                    <Tag key={field}>{field}</Tag>
                                ))}
                            </Space>
                            <div
                                style={{
                                    border: "1px solid rgba(148, 163, 184, 0.25)",
                                    borderRadius: theme.borderRadius,
                                    overflow: "hidden",
                                }}
                            >
                                <div
                                    style={{
                                        padding: "10px 14px",
                                        borderBottom: "1px solid rgba(148, 163, 184, 0.18)",
                                        fontFamily: "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace",
                                        fontSize: 13,
                                        background: getAppState().dark
                                            ? "rgba(255,255,255,0.04)"
                                            : "rgba(15,23,42,0.03)",
                                    }}
                                >
                                    <div style={{ color: "#991b1b" }}>{`--- v${compareData.fromVersion}`}</div>
                                    <div style={{ color: "#166534" }}>{`+++ v${compareData.toVersion}`}</div>
                                </div>
                                <div style={{ padding: "10px 14px" }}>
                                    <Paragraph style={{ marginBottom: 8 }} type="secondary">
                                        {getRes().articleEdit.version.fields.title}
                                    </Paragraph>
                                    <div
                                        style={{
                                            borderRadius: theme.borderRadiusSM,
                                            overflow: "hidden",
                                            border: "1px solid rgba(148, 163, 184, 0.16)",
                                            marginBottom: 14,
                                        }}
                                    >
                                        {titleDiffLines.map((line, index) => (
                                            <div
                                                key={`title-${index}`}
                                                style={{
                                                    ...getDiffLineStyle(line.type),
                                                    fontFamily:
                                                        "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace",
                                                    fontSize: 13,
                                                    padding: "4px 10px",
                                                    whiteSpace: "pre-wrap",
                                                    wordBreak: "break-word",
                                                }}
                                            >
                                                {`${getDiffPrefix(line.type)} ${line.content}`}
                                            </div>
                                        ))}
                                    </div>
                                    <Paragraph style={{ marginBottom: 8 }} type="secondary">
                                        {getRes().articleEdit.version.fields.markdown}
                                    </Paragraph>
                                    <div
                                        style={{
                                            maxHeight: "52vh",
                                            overflow: "auto",
                                            borderRadius: theme.borderRadiusSM,
                                            border: "1px solid rgba(148, 163, 184, 0.16)",
                                        }}
                                    >
                                        {markdownDiffLines.map((line, index) => (
                                            <div
                                                key={`md-${index}`}
                                                style={{
                                                    ...getDiffLineStyle(line.type),
                                                    fontFamily:
                                                        "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace",
                                                    fontSize: 13,
                                                    lineHeight: 1.6,
                                                    padding: "4px 10px",
                                                    whiteSpace: "pre-wrap",
                                                    wordBreak: "break-word",
                                                }}
                                            >
                                                {`${getDiffPrefix(line.type)} ${line.content}`}
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            </div>
                        </Space>
                    ) : !compareLoading ? (
                        <Empty description={getRes().articleEdit.version.select} />
                    ) : null}
                </Space>
            </Drawer>
        </>
    );
};

export default ArticleVersionDrawer;
