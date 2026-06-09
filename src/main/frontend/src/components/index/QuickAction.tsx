import { Badge, Card, Col, Row, Typography } from "antd";

import { Link } from "react-router-dom";
import { getRealRouteUrl, getRes } from "utils/constants";
import {
    DatabaseOutlined,
    EditOutlined,
    FolderAddFilled,
    PlusCircleOutlined,
    ThunderboltOutlined,
} from "@ant-design/icons";
import { useTheme } from "antd-style";

const QuickActionCard = ({ draftCount, embedded = false }: { draftCount: number; embedded?: boolean }) => {
    const theme = useTheme();
    const iconBgColor = embedded ? theme.colorFillQuaternary : theme.colorPrimaryBg;
    const textColor = embedded ? theme.colorText : undefined;
    const mutedTextColor = embedded ? theme.colorTextSecondary : undefined;
    const embeddedBorder = `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`;
    const embeddedContainerBorder = `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`;
    const transparentBorder = `${theme.lineWidth}px ${theme.lineType} transparent`;

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
                    className={`dashboard-action-tile ${embedded ? "dashboard-action-tile-embedded" : ""}`}
                    style={{
                        display: "flex",
                        flexDirection: "column",
                        alignItems: "center",
                        justifyContent: "center",
                        padding: embedded ? "14px 10px" : "16px 8px",
                        borderRadius: theme.borderRadiusLG,
                        cursor: "pointer",
                        transition: "all 0.3s cubic-bezier(0.2, 0, 0, 1)",
                        backgroundColor: embedded ? theme.colorFillQuaternary : "transparent",
                        border: embedded ? embeddedBorder : transparentBorder,
                    }}
                >
                    <Badge count={count} offset={[-5, 5]} size="small" color={theme.colorPrimary}>
                        <div
                            className="dashboard-action-tile-icon"
                            style={{
                                width: 56,
                                height: 56,
                                borderRadius: theme.borderRadiusLG,
                                backgroundColor: iconBgColor,
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "center",
                                marginBottom: 10,
                                color: theme.colorPrimary,
                                fontSize: 22,
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
                    background: theme.colorBgContainer,
                    border: embeddedContainerBorder,
                }}
            >
                <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 10 }}>
                    <ThunderboltOutlined style={{ color: mutedTextColor, fontSize: 14 }} />
                    <Typography.Text
                        style={{
                            color: mutedTextColor,
                            fontSize: 13,
                            fontWeight: 600,
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
