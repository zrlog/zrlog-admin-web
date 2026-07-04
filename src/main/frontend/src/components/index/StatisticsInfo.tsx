import { Typography } from "antd";
import { getRealRouteUrl, getRes } from "../../utils/constants";
import Row from "antd/es/grid/row";
import Col from "antd/es/grid/col";
import {
    CloudServerOutlined,
    CommentOutlined,
    ContainerOutlined,
    FileTextOutlined,
    FundOutlined,
    HddOutlined,
} from "@ant-design/icons";
import { Link } from "react-router-dom";
import { StatisticsInfoState } from "../../type";
import { ReactElement } from "react";
import { useTheme } from "antd-style";
import Card from "antd/es/card";

const StatisticsInfo = ({ data }: { data: StatisticsInfoState }) => {
    const theme = useTheme();
    const cardBackground = theme.colorFillQuaternary;
    const cardHoverBackground = theme.colorFillTertiary;
    const accentColor = theme.colorText;
    const tileStyle = {
        borderRadius: theme.borderRadiusLG,
        background: cardBackground,
        height: "100%",
        minHeight: 112,
        boxSizing: "border-box" as const,
    };
    const totalArticles = data.articleCount;

    const formatBytes = (bytes?: number | string) => {
        if (typeof bytes === "string") {
            return bytes.trim().length > 0 ? bytes : "0 B";
        }
        if (!bytes || bytes <= 0) {
            return "0 B";
        }
        const units = ["B", "KB", "MB", "GB", "TB"];
        let value = bytes;
        let unitIndex = 0;
        while (value >= 1024 && unitIndex < units.length - 1) {
            value /= 1024;
            unitIndex++;
        }
        return `${value >= 100 || unitIndex === 0 ? value.toFixed(0) : value.toFixed(1)} ${units[unitIndex]}`;
    };

    const statusItems = [
        {
            key: "published",
            label: getRes().article.status.published,
            value: data.publishedCount,
            color: theme.colorSuccess,
        },
        {
            key: "private",
            label: getRes().article.status.private,
            value: data.privateCount,
            color: theme.colorWarning,
        },
        {
            key: "draft",
            label: getRes().article.status.draft,
            value: data.draftCount,
            color: theme.colorTextSecondary,
        },
    ].filter((item) => item.value > 0 || totalArticles === 0);

    const summaryCard = ({
        icon,
        title,
        value,
        subtitle,
    }: {
        icon: ReactElement;
        title: string;
        value: string | number;
        subtitle?: string;
    }) => (
        <div
            style={{
                ...tileStyle,
                padding: 16,
            }}
        >
            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 10 }}>
                <span style={{ color: accentColor }}>{icon}</span>
                <Typography.Text type="secondary">{title}</Typography.Text>
            </div>
            <Typography.Text style={{ display: "block", fontSize: 24, fontWeight: 700, lineHeight: 1.2 }}>
                {value}
            </Typography.Text>
            {subtitle && (
                <Typography.Text type="secondary" style={{ display: "block", marginTop: 6, fontSize: 12 }}>
                    {subtitle}
                </Typography.Text>
            )}
        </div>
    );

    const value = (icon: ReactElement, text: string | number) => {
        return (
            <div
                style={{
                    display: "flex",
                    gap: 6,
                    flexFlow: "row",
                    alignItems: "center",
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                }}
            >
                <span style={{ color: accentColor, fontSize: 22, lineHeight: 1 }}>{icon}</span>
                <Typography.Text
                    style={{ color: theme.colorTextHeading, fontSize: 24, fontWeight: 600, lineHeight: 1.5 }}
                    ellipsis={true}
                >
                    {text}
                </Typography.Text>
            </div>
        );
    };

    const StatCard = ({
        title,
        valueRender,
        link,
    }: {
        title: string;
        valueRender: () => JSX.Element;
        link?: string;
    }) => {
        const content = (
            <div
                style={{
                    ...tileStyle,
                    padding: 18,
                    display: "flex",
                    flexDirection: "column",
                    justifyContent: "space-between",
                    transition: "all 0.3s ease",
                    cursor: link ? "pointer" : "default",
                }}
                onMouseEnter={(e) => {
                    if (link) {
                        e.currentTarget.style.transform = "translateY(-2px)";
                        e.currentTarget.style.background = cardHoverBackground;
                    }
                }}
                onMouseLeave={(e) => {
                    if (link) {
                        e.currentTarget.style.transform = "translateY(0)";
                        e.currentTarget.style.background = cardBackground;
                    }
                }}
            >
                <Typography.Text
                    type="secondary"
                    style={{ fontSize: 14, fontWeight: 500, marginBottom: 16, display: "block" }}
                >
                    {title}
                </Typography.Text>
                <div>{valueRender()}</div>
            </div>
        );

        if (link) {
            return (
                <Link to={getRealRouteUrl(link)} style={{ display: "block", height: "100%" }}>
                    {content}
                </Link>
            );
        }
        return content;
    };

    return (
        <Card
            bordered={false}
            className="dashboard-card"
            styles={{ body: { padding: theme.paddingLG } }}
            title={
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <FundOutlined />
                    <span>{getRes().index.statistics}</span>
                </div>
            }
        >
            <Row gutter={[16, 16]}>
                <Col xs={12} sm={12} md={6}>
                    <StatCard
                        title={getRes().index.statisticsCard.todayComment}
                        valueRender={() => value(<CommentOutlined />, data.toDayCommCount)}
                    />
                </Col>
                <Col xs={12} sm={12} md={6}>
                    <StatCard
                        title={getRes().index.statisticsCard.totalComment}
                        link="/comment"
                        valueRender={() => value(<CommentOutlined />, data.commCount)}
                    />
                </Col>
                <Col xs={12} sm={12} md={6}>
                    <StatCard
                        title={getRes().index.statisticsCard.totalArticle}
                        link="/article"
                        valueRender={() => value(<ContainerOutlined />, data.articleCount)}
                    />
                </Col>
                <Col xs={12} sm={12} md={6}>
                    <StatCard
                        title={getRes().index.statisticsCard.totalArticleView}
                        valueRender={() => value(<ContainerOutlined />, data.clickCount)}
                    />
                </Col>
            </Row>
            <div style={{ height: theme.marginLG }} />
            <Row gutter={[16, 16]}>
                <Col xs={24} md={12}>
                    <div
                        style={{
                            ...tileStyle,
                            padding: 16,
                        }}
                    >
                        <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 10 }}>
                            <span style={{ color: accentColor }}>
                                <FileTextOutlined />
                            </span>
                            <Typography.Text type="secondary">{getRes().index.status}</Typography.Text>
                        </div>
                        <Typography.Text style={{ display: "block", fontSize: 24, fontWeight: 700, lineHeight: 1.2 }}>
                            {`${data.publishedCount}/${totalArticles}`}
                        </Typography.Text>
                        <div
                            style={{
                                display: "flex",
                                height: 10,
                                borderRadius: theme.borderRadiusLG,
                                overflow: "hidden",
                                marginTop: 16,
                                background: theme.colorFillSecondary,
                            }}
                        >
                            {statusItems.map((item) => (
                                <div
                                    key={item.key}
                                    style={{
                                        width: totalArticles > 0 ? `${(item.value / totalArticles) * 100}%` : "33.33%",
                                        background: item.color,
                                        minWidth: item.value > 0 ? 8 : 0,
                                        transition: "all 0.3s ease",
                                    }}
                                />
                            ))}
                        </div>
                        <div style={{ display: "flex", flexWrap: "wrap", gap: 12, marginTop: 12 }}>
                            {statusItems.map((item) => (
                                <div key={item.key} style={{ display: "flex", alignItems: "center", gap: 6 }}>
                                    <span
                                        style={{
                                            width: 8,
                                            height: 8,
                                            borderRadius: "50%",
                                            background: item.color,
                                            display: "inline-block",
                                        }}
                                    />
                                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                                        {`${item.label} ${item.value}`}
                                    </Typography.Text>
                                </div>
                            ))}
                        </div>
                    </div>
                </Col>
                <Col xs={24} md={6}>
                    {summaryCard({
                        icon: <HddOutlined />,
                        title: getRes().index.storage.disk,
                        value: formatBytes(data.usedDiskSpace),
                    })}
                </Col>
                <Col xs={24} md={6}>
                    {summaryCard({
                        icon: <CloudServerOutlined />,
                        title: getRes().index.storage.cache,
                        value: formatBytes(data.usedCacheSpace),
                    })}
                </Col>
            </Row>
        </Card>
    );
};

export default StatisticsInfo;
