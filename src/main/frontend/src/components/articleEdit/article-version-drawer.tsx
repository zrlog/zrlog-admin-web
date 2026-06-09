import React, { RefObject, useEffect, useState } from "react";
import { App, Button, Drawer, Empty, Grid, List, Select, Space, Tag, Typography } from "antd";
import { HistoryOutlined, RollbackOutlined } from "@ant-design/icons";
import TimeAgo from "@editor/dist/src/editor/TimeAgo";
import { getRes } from "../../utils/constants";
import { getAppState } from "../../base/ConfigProviderApp";
import { useTheme } from "antd-style";
import { getShortcutTitle } from "./shortcut-utils";
import { collectMarkdownReferenceSummary } from "./markdown-reference-utils";

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
    showTrigger?: boolean;
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

const getDiffLineStyle = (type: DiffLineType, theme: ReturnType<typeof useTheme>) => {
    switch (type) {
        case "add":
            return {
                background: theme.colorSuccessBg,
                color: theme.colorSuccessText,
            };
        case "remove":
            return {
                background: theme.colorErrorBg,
                color: theme.colorErrorText,
            };
        default:
            return {
                background: "transparent",
                color: theme.colorText,
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

const PRIMARY_DIFF_FIELDS = new Set(["title", "markdown", "content"]);

const toReferenceSourceText = (value: unknown) => (typeof value === "string" ? value : "");

const collectArticleReferenceTargets = (article?: Record<string, any>) => {
    if (!article) {
        return [];
    }
    const referenceSummary = collectMarkdownReferenceSummary(
        [toReferenceSourceText(article.markdown), toReferenceSourceText(article.content)].join("\n")
    );
    const references = new Set<string>(referenceSummary.imageReferences.concat(referenceSummary.linkReferences));
    const thumbnail = toReferenceSourceText(article.thumbnail).trim();
    if (thumbnail) {
        references.add(thumbnail);
    }
    return Array.from(references).sort((a, b) => a.localeCompare(b));
};

const getArticleReferenceChanges = (fromArticle?: Record<string, any>, toArticle?: Record<string, any>) => {
    const fromReferences = new Set(collectArticleReferenceTargets(fromArticle));
    const toReferences = new Set(collectArticleReferenceTargets(toArticle));
    return {
        added: Array.from(toReferences).filter((target) => !fromReferences.has(target)),
        removed: Array.from(fromReferences).filter((target) => !toReferences.has(target)),
    };
};

const ArticleVersionDrawer: React.FC<ArticleVersionDrawerProps> = ({
    logId,
    currentVersion,
    axiosInstance,
    onRollback,
    containerRef,
    open,
    onOpenChange,
    showTrigger = true,
}) => {
    const theme = useTheme();
    const screens = Grid.useBreakpoint();
    const diffBorder = `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`;
    const [innerOpen, setInnerOpen] = useState(false);
    const [versions, setVersions] = useState<VersionItem[]>([]);
    const [loading, setLoading] = useState(false);
    const [compareLoading, setCompareLoading] = useState(false);
    const [selectedVersion, setSelectedVersion] = useState<number | undefined>(undefined);
    const [compareData, setCompareData] = useState<CompareResponse | null>(null);
    const { modal } = App.useApp();
    const drawerOpen = open ?? innerOpen;
    const mobileMode = screens.sm !== true;
    const drawerWidth = screens.lg ? 960 : screens.md ? 720 : "100%";

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

    const getChangedFieldLabel = (field: string) => {
        const fields = getRes().articleEdit.version.fields;
        switch (field) {
            case "title":
                return fields.title;
            case "content":
                return fields.content;
            case "markdown":
                return fields.markdown;
            case "digest":
                return fields.digest;
            case "keywords":
                return fields.keywords;
            case "alias":
                return fields.alias;
            case "thumbnail":
                return fields.thumbnail;
            case "typeId":
                return fields.typeId;
            case "canComment":
                return fields.canComment;
            case "recommended":
                return fields.recommended;
            case "privacy":
                return fields.privacy;
            case "rubbish":
                return fields.rubbish;
            case "editorType":
                return fields.editorType;
            default:
                return getRes().articleEdit.version.unknownField.replace("{field}", field);
        }
    };

    const formatChangedFieldValue = (value: unknown) => {
        if (value === undefined || value === null || value === "") {
            return getRes().articleEdit.version.emptyValue;
        }
        if (typeof value === "boolean") {
            return value ? getRes().yes : getRes().no;
        }
        if (typeof value === "object") {
            return JSON.stringify(value);
        }
        return `${value}`;
    };

    const fieldChangeDetails = compareData?.changedFields.filter((field) => !PRIMARY_DIFF_FIELDS.has(field)) || [];
    const referenceChanges = compareData
        ? getArticleReferenceChanges(compareData.fromArticle, compareData.toArticle)
        : { added: [], removed: [] };
    const hasReferenceChanges = referenceChanges.added.length > 0 || referenceChanges.removed.length > 0;
    const referenceChangeGroups = [
        {
            key: "removed",
            label: getRes().articleEdit.version.resourceRemoved,
            color: "red",
            values: referenceChanges.removed,
        },
        {
            key: "added",
            label: getRes().articleEdit.version.resourceAdded,
            color: "green",
            values: referenceChanges.added,
        },
    ].filter((item) => item.values.length > 0);

    return (
        <>
            {showTrigger && (
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
                    icon={
                        <HistoryOutlined style={{ fontSize: getAppState().compactMode ? 18 : 24, display: "flex" }} />
                    }
                    onClick={(e) => {
                        e.stopPropagation();
                        e.preventDefault();
                        updateOpen(true);
                    }}
                />
            )}
            <Drawer
                title={getRes().articleEdit.version.label}
                width={drawerWidth}
                open={drawerOpen}
                onClose={() => updateOpen(false)}
                getContainer={() => containerRef.current ?? document.body}
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
                            <List.Item>
                                <div
                                    style={{
                                        width: "100%",
                                        display: "flex",
                                        flexDirection: mobileMode ? "column" : "row",
                                        alignItems: mobileMode ? "stretch" : "center",
                                        justifyContent: "space-between",
                                        gap: 8,
                                    }}
                                >
                                    <Space style={{ minWidth: 0 }} wrap>
                                        <Tag color={item.current ? "blue" : "default"}>
                                            {item.current ? getRes().articleEdit.version.current : `v${item.version}`}
                                        </Tag>
                                        <Text>{item.title || "-"}</Text>
                                    </Space>
                                    <Space
                                        style={{
                                            justifyContent: mobileMode ? "space-between" : "flex-end",
                                        }}
                                        wrap
                                    >
                                        {item.createdAt ? <TimeAgo timestamp={item.createdAt} /> : null}
                                        {!item.current ? (
                                            <Button
                                                type="link"
                                                style={{ paddingInline: 0 }}
                                                block={mobileMode}
                                                onClick={() => setSelectedVersion(item.version)}
                                            >
                                                {getRes().articleEdit.version.compareAction}
                                            </Button>
                                        ) : null}
                                    </Space>
                                </div>
                            </List.Item>
                        )}
                    />
                    {compareData ? (
                        <Space direction="vertical" size={12} style={{ width: "100%" }}>
                            <Space wrap>
                                <Tag color="gold">v{compareData.fromVersion}</Tag>
                                <Tag color="blue">v{compareData.toVersion}</Tag>
                                {compareData.changedFields.map((field) => (
                                    <Tag key={field}>{getChangedFieldLabel(field)}</Tag>
                                ))}
                            </Space>
                            {fieldChangeDetails.length > 0 ? (
                                <div
                                    style={{
                                        border: diffBorder,
                                        borderRadius: theme.borderRadius,
                                        overflow: "hidden",
                                    }}
                                >
                                    <div
                                        style={{
                                            padding: "10px 14px",
                                            borderBottom: diffBorder,
                                            background: theme.colorFillQuaternary,
                                        }}
                                    >
                                        <Text strong>{getRes().articleEdit.version.fieldChanges}</Text>
                                    </div>
                                    <List
                                        size="small"
                                        dataSource={fieldChangeDetails}
                                        renderItem={(field) => (
                                            <List.Item>
                                                <Space direction="vertical" size={8} style={{ width: "100%" }}>
                                                    <Text strong>{getChangedFieldLabel(field)}</Text>
                                                    <div
                                                        style={{
                                                            display: "grid",
                                                            gridTemplateColumns: mobileMode ? "1fr" : "1fr 1fr",
                                                            gap: 8,
                                                        }}
                                                    >
                                                        <Space direction="vertical" size={4}>
                                                            <Text type="secondary">
                                                                {getRes().articleEdit.version.from}
                                                            </Text>
                                                            <Paragraph style={{ marginBottom: 0 }} copyable>
                                                                {formatChangedFieldValue(
                                                                    compareData.fromArticle?.[field]
                                                                )}
                                                            </Paragraph>
                                                        </Space>
                                                        <Space direction="vertical" size={4}>
                                                            <Text type="secondary">
                                                                {getRes().articleEdit.version.to}
                                                            </Text>
                                                            <Paragraph style={{ marginBottom: 0 }} copyable>
                                                                {formatChangedFieldValue(
                                                                    compareData.toArticle?.[field]
                                                                )}
                                                            </Paragraph>
                                                        </Space>
                                                    </div>
                                                </Space>
                                            </List.Item>
                                        )}
                                    />
                                </div>
                            ) : null}
                            {hasReferenceChanges ? (
                                <div
                                    style={{
                                        border: diffBorder,
                                        borderRadius: theme.borderRadius,
                                        overflow: "hidden",
                                    }}
                                >
                                    <div
                                        style={{
                                            padding: "10px 14px",
                                            borderBottom: diffBorder,
                                            background: theme.colorFillQuaternary,
                                        }}
                                    >
                                        <Text strong>{getRes().articleEdit.version.resourceChanges}</Text>
                                    </div>
                                    <List
                                        size="small"
                                        dataSource={referenceChangeGroups}
                                        renderItem={(group) => (
                                            <List.Item>
                                                <Space direction="vertical" size={8} style={{ width: "100%" }}>
                                                    <Tag color={group.color}>{group.label}</Tag>
                                                    <div
                                                        style={{
                                                            maxHeight: 180,
                                                            overflow: "auto",
                                                        }}
                                                    >
                                                        {group.values.map((target) => (
                                                            <Paragraph
                                                                key={`${group.key}-${target}`}
                                                                copyable
                                                                style={{
                                                                    marginBottom: 6,
                                                                    wordBreak: "break-all",
                                                                }}
                                                            >
                                                                {target}
                                                            </Paragraph>
                                                        ))}
                                                    </div>
                                                </Space>
                                            </List.Item>
                                        )}
                                    />
                                </div>
                            ) : null}
                            <div
                                style={{
                                    border: diffBorder,
                                    borderRadius: theme.borderRadius,
                                    overflow: "hidden",
                                }}
                            >
                                <div
                                    style={{
                                        padding: "10px 14px",
                                        borderBottom: diffBorder,
                                        fontFamily: "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace",
                                        fontSize: 13,
                                        background: theme.colorFillQuaternary,
                                    }}
                                >
                                    <div
                                        style={{ color: theme.colorErrorText }}
                                    >{`--- v${compareData.fromVersion}`}</div>
                                    <div
                                        style={{ color: theme.colorSuccessText }}
                                    >{`+++ v${compareData.toVersion}`}</div>
                                </div>
                                <div style={{ padding: "10px 14px" }}>
                                    <Paragraph style={{ marginBottom: 8 }} type="secondary">
                                        {getRes().articleEdit.version.fields.title}
                                    </Paragraph>
                                    <div
                                        style={{
                                            borderRadius: theme.borderRadiusSM,
                                            overflow: "hidden",
                                            border: diffBorder,
                                            marginBottom: 14,
                                        }}
                                    >
                                        {titleDiffLines.map((line, index) => (
                                            <div
                                                key={`title-${index}`}
                                                style={{
                                                    ...getDiffLineStyle(line.type, theme),
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
                                            border: diffBorder,
                                        }}
                                    >
                                        {markdownDiffLines.map((line, index) => (
                                            <div
                                                key={`md-${index}`}
                                                style={{
                                                    ...getDiffLineStyle(line.type, theme),
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
