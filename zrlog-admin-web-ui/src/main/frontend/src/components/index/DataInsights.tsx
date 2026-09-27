import { Card, Empty, Tooltip, Typography } from "antd";
import { formatLabelValue, getRes } from "../../utils/constants";
import { PieChartOutlined } from "@ant-design/icons";
import { useTheme } from "antd-style";

type DataInsightsProps = {
    data?: {
        typeData?: { typeName: string; alias: string; typeamount: number }[];
        tagData?: { text: string; count: number }[];
    };
};

const DataInsights = ({ data }: DataInsightsProps) => {
    const theme = useTheme();
    const chartColors = [
        theme.colorPrimary,
        theme.colorSuccess,
        theme.colorInfo,
        theme.colorWarning,
        theme.colorError,
        theme.colorTextSecondary,
    ];

    // Filter out items with 0 count for the visualization
    const activeTypes =
        data?.typeData?.filter((t) => t.typeamount > 0).sort((a, b) => b.typeamount - a.typeamount) || [];
    const activeTags = data?.tagData?.filter((t) => t.count > 0).sort((a, b) => b.count - a.count) || [];

    const totalArticles = activeTypes.reduce((acc, curr) => acc + curr.typeamount, 0);

    return (
        <Card
            title={
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <PieChartOutlined />
                    <span>{getRes().index.insight.label}</span>
                </div>
            }
            bordered={false}
            className="dashboard-card"
            styles={{ body: { padding: 20 } }}
        >
            <div style={{ marginBottom: 32 }}>
                <Typography.Text type="secondary" style={{ display: "block", marginBottom: 12, fontSize: 13 }}>
                    {getRes().index.insight.categoryDistribution}
                </Typography.Text>
                {activeTypes.length > 0 ? (
                    <div
                        style={{
                            display: "flex",
                            height: 24,
                            borderRadius: theme.borderRadiusLG,
                            overflow: "hidden",
                            backgroundColor: theme.colorFillQuaternary,
                        }}
                    >
                        {activeTypes.map((type, index) => {
                            const percentage = (type.typeamount / totalArticles) * 100;
                            return (
                                <Tooltip
                                    key={type.alias}
                                    title={`${formatLabelValue(type.typeName, type.typeamount)} (${percentage.toFixed(
                                        1
                                    )}%)`}
                                >
                                    <div
                                        style={{
                                            width: `${percentage}%`,
                                            backgroundColor: chartColors[index % chartColors.length],
                                            transition: "all 0.3s",
                                        }}
                                    />
                                </Tooltip>
                            );
                        })}
                    </div>
                ) : (
                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={getRes().index.insight.empty.category} />
                )}
                <div style={{ display: "flex", flexWrap: "wrap", gap: 12, marginTop: 12 }}>
                    {activeTypes.slice(0, 6).map((type, index) => (
                        <div key={type.alias} style={{ display: "flex", alignItems: "center", gap: 4 }}>
                            <div
                                style={{
                                    width: 8,
                                    height: 8,
                                    borderRadius: "50%",
                                    backgroundColor: chartColors[index % chartColors.length],
                                }}
                            />
                            <Typography.Text style={{ fontSize: 12 }}>{type.typeName}</Typography.Text>
                        </div>
                    ))}
                </div>
            </div>

            <div>
                <Typography.Text type="secondary" style={{ display: "block", marginBottom: 12, fontSize: 13 }}>
                    {getRes().index.insight.hotTags}
                </Typography.Text>
                <div style={{ display: "flex", flexWrap: "wrap", gap: "6px 12px", alignItems: "baseline" }}>
                    {activeTags.length > 0 ? (
                        activeTags.slice(0, 15).map((tag) => {
                            const maxCount = activeTags[0].count;
                            const size = Math.max(13, Math.min(18, 13 + (tag.count / maxCount) * 5));
                            const opacity = Math.max(0.64, Math.min(0.92, 0.64 + (tag.count / maxCount) * 0.28));

                            return (
                                <span
                                    key={tag.text}
                                    style={{
                                        fontSize: size,
                                        opacity: opacity,
                                        fontWeight: tag.count === maxCount ? 600 : 500,
                                        lineHeight: 1.65,
                                        color: theme.colorText,
                                        cursor: "default",
                                        transition: "all 0.2s",
                                    }}
                                >
                                    {tag.text}
                                </span>
                            );
                        })
                    ) : (
                        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={getRes().index.insight.empty.tag} />
                    )}
                </div>
            </div>
        </Card>
    );
};

export default DataInsights;
