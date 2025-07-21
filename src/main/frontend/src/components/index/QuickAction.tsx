import { Badge, Button, Card, Col, Popconfirm, Row, Tag, Typography } from "antd";

import { Link } from "react-router-dom";
import { getRealRouteUrl, getRes } from "utils/constants";
import {
    ClearOutlined,
    DatabaseOutlined,
    HistoryOutlined,
    FolderAddFilled,
    PlusCircleOutlined,
    EditOutlined,
    ThunderboltOutlined,
} from "@ant-design/icons";
import { getAppState } from "../../base/ConfigProviderApp";
import { useTheme } from "antd-style";
import { getLocalArticleCache, removeLocalArticleCache } from "../../utils/article-cache";
import { ArticleEntry } from "../articleEdit/index.types";
import { useState } from "react";

const QuickActionCard = ({ draftCount, embedded = false }: { draftCount: number; embedded?: boolean }) => {
    const isDark = getAppState().dark;
    const theme = useTheme();
    const [localDraft, setLocalDraft] = useState<ArticleEntry | undefined>(() => getLocalArticleCache());
    const iconBgColor = embedded
        ? "rgba(255,255,255,0.12)"
        : isDark
        ? "rgba(255,255,255,0.08)"
        : `${getAppState().colorPrimary}15`;
    const textColor = embedded ? "rgba(255,255,255,0.92)" : undefined;
    const mutedTextColor = embedded ? "rgba(255,255,255,0.68)" : undefined;
    const localDraftTitle =
        localDraft?.title && localDraft.title.trim().length > 0
            ? localDraft.title.trim()
            : getRes().index.quickAction.localDraftUntitled;

    const clearLocalDraft = () => {
        removeLocalArticleCache();
        setLocalDraft(undefined);
    };

    const ActionItem = ({
        to,
        icon,
        label,
        count,
    }: {
        to: string;
        icon: React.ReactNode;
        label: string;
        count?: number;
    }) => (
        <Col xs={12} md={8} lg={6}>
            <Link to={getRealRouteUrl(to)}>
                <div
                    style={{
                        display: "flex",
                        flexDirection: "column",
                        alignItems: "center",
                        justifyContent: "center",
                        padding: embedded ? "14px 10px" : "16px 8px",
                        borderRadius: theme.borderRadiusLG,
                        cursor: "pointer",
                        transition: "all 0.3s",
                        backgroundColor: embedded ? "rgba(255,255,255,0.04)" : "transparent",
                        border: embedded ? "1px solid rgba(255,255,255,0.08)" : "1px solid transparent",
                        backdropFilter: embedded ? "blur(8px)" : undefined,
                    }}
                    onMouseEnter={(e) => {
                        e.currentTarget.style.backgroundColor = embedded
                            ? "rgba(255,255,255,0.10)"
                            : isDark
                            ? "rgba(255,255,255,0.04)"
                            : "rgba(0,0,0,0.02)";
                        e.currentTarget.style.transform = "translateY(-2px)";
                    }}
                    onMouseLeave={(e) => {
                        e.currentTarget.style.backgroundColor = "transparent";
                        e.currentTarget.style.transform = "translateY(0)";
                    }}
                >
                    <Badge count={count} offset={[-5, 5]} size="small" color={getAppState().colorPrimary}>
                        <div
                            style={{
                                width: 56,
                                height: 56,
                                borderRadius: theme.borderRadiusLG,
                                backgroundColor: iconBgColor,
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "center",
                                marginBottom: 10,
                                color: embedded ? "white" : getAppState().colorPrimary,
                                fontSize: 22,
                                boxShadow: embedded ? "inset 0 1px 0 rgba(255,255,255,0.08)" : undefined,
                            }}
                        >
                            {icon}
                        </div>
                    </Badge>
                    <Typography.Text
                        style={{ fontWeight: 500, fontSize: 13, whiteSpace: "nowrap", color: textColor }}
                        type={embedded ? undefined : "secondary"}
                    >
                        {label}
                    </Typography.Text>
                </div>
            </Link>
        </Col>
    );

    const content = (
        <>
            {localDraft && (
                <div
                    style={{
                        marginBottom: 16,
                        padding: embedded ? 14 : 16,
                        borderRadius: theme.borderRadiusLG,
                        background: embedded
                            ? "rgba(255,255,255,0.10)"
                            : isDark
                            ? "rgba(255,255,255,0.04)"
                            : `${getAppState().colorPrimary}12`,
                        border: embedded
                            ? "1px solid rgba(255,255,255,0.16)"
                            : `1px solid ${getAppState().colorPrimary}26`,
                    }}
                >
                    <div
                        style={{
                            display: "flex",
                            justifyContent: "space-between",
                            gap: 12,
                            alignItems: "flex-start",
                        }}
                    >
                        <div style={{ minWidth: 0, flex: 1 }}>
                            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 8 }}>
                                <HistoryOutlined
                                    style={{
                                        color: embedded ? "rgba(255,255,255,0.92)" : getAppState().colorPrimary,
                                    }}
                                />
                                <Typography.Text
                                    style={{ color: textColor, fontWeight: 600 }}
                                    type={embedded ? undefined : "secondary"}
                                >
                                    {getRes().index.quickAction.localDraft}
                                </Typography.Text>
                                <Tag color={getAppState().colorPrimary}>{getRes().index.quickAction.localDraftTag}</Tag>
                            </div>
                            <Typography.Text
                                style={{
                                    display: "block",
                                    color: textColor,
                                    fontSize: 14,
                                    marginBottom: 6,
                                }}
                                ellipsis={{ tooltip: localDraftTitle }}
                            >
                                {localDraftTitle}
                            </Typography.Text>
                            <Typography.Text
                                type={embedded ? undefined : "secondary"}
                                style={{
                                    color: mutedTextColor,
                                    fontSize: 12,
                                }}
                            >
                                {getRes().index.quickAction.localDraftTip}
                            </Typography.Text>
                        </div>
                        <Popconfirm
                            title={getRes().index.quickAction.clearLocalDraft}
                            description={getRes().index.quickAction.clearLocalDraftConfirm}
                            okText={getRes().yes}
                            cancelText={getRes().cancel}
                            onConfirm={clearLocalDraft}
                        >
                            <Button
                                type="text"
                                size="small"
                                icon={<ClearOutlined />}
                                style={{ color: embedded ? "rgba(255,255,255,0.72)" : undefined }}
                            />
                        </Popconfirm>
                    </div>
                    <div style={{ display: "flex", gap: 8, marginTop: 12, flexWrap: "wrap" }}>
                        <Link to={getRealRouteUrl("/article-edit")}>
                            <Button type={embedded ? "default" : "primary"} size="small">
                                {getRes().index.quickAction.continueWriting}
                            </Button>
                        </Link>
                    </div>
                </div>
            )}
            <Row gutter={[8, 8]}>
                <ActionItem
                    to="/article-edit"
                    icon={<PlusCircleOutlined />}
                    label={getRes().index.quickAction.writeArticle}
                />
                <ActionItem
                    to="/article?status=draft"
                    icon={<EditOutlined />}
                    label={getRes().article.status.draft}
                    count={draftCount}
                />
                <ActionItem to="/article-type" icon={<FolderAddFilled />} label={getRes().articleType.title} />
                <ActionItem
                    to="/plugin?page=backup-sql-file/files"
                    icon={<DatabaseOutlined />}
                    label={getRes().index.quickAction.backupFiles}
                />
            </Row>
        </>
    );

    if (embedded) {
        return (
            <div
                style={{
                    marginTop: 20,
                    padding: 16,
                    borderRadius: theme.borderRadiusLG,
                    background: "linear-gradient(180deg, rgba(255,255,255,0.10) 0%, rgba(255,255,255,0.06) 100%)",
                    border: "1px solid rgba(255,255,255,0.14)",
                    backdropFilter: "blur(14px)",
                    boxShadow: "inset 0 1px 0 rgba(255,255,255,0.12)",
                }}
            >
                <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 10 }}>
                    <ThunderboltOutlined style={{ color: "rgba(255,255,255,0.92)", fontSize: 14 }} />
                    <Typography.Text
                        style={{
                            color: mutedTextColor,
                            fontSize: 13,
                            fontWeight: 600,
                            letterSpacing: 0.2,
                        }}
                    >
                        {getRes().index.quickAction.label}
                    </Typography.Text>
                </div>
                {content}
            </div>
        );
    }

    return (
        <Card
            title={
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <ThunderboltOutlined />
                    <span>{getRes().index.quickAction.label}</span>
                </div>
            }
            bordered={false}
            className="dashboard-card"
            styles={{ body: { padding: 20 } }}
        >
            {content}
        </Card>
    );
};

export default QuickActionCard;
