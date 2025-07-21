import { Typography, Tooltip, Empty, Card } from "antd";
import { getAppState } from "../../base/ConfigProviderApp";
import { getRes } from "../../utils/constants";
import { PieChartOutlined } from "@ant-design/icons";
import { useTheme } from "antd-style";

type DataInsightsProps = {
    typeData: { typeName: string; alias: string; typeamount: number }[];
    tagData: { text: string; count: number }[];
};

const DataInsights = ({ typeData, tagData }: DataInsightsProps) => {
    const isDark = getAppState().dark;
    const theme = useTheme();

    // Filter out items with 0 count for the visualization
    const activeTypes = typeData?.filter((t) => t.typeamount > 0).sort((a, b) => b.typeamount - a.typeamount) || [];
    const activeTags = tagData?.filter((t) => t.count > 0).sort((a, b) => b.count - a.count) || [];

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
                            backgroundColor: isDark ? "rgba(255,255,255,0.05)" : "#f5f5f5",
                        }}
                    >
                        {activeTypes.map((type, index) => {
                            const percentage = (type.typeamount / totalArticles) * 100;
                            const colors = [
                                getAppState().colorPrimary,
                                "#52c41a",
                                "#1890ff",
                                "#722ed1",
                                "#faad14",
                                "#eb2f96",
                            ];
                            return (
                                <Tooltip
                                    key={type.alias}
                                    title={`${type.typeName}: ${type.typeamount} (${percentage.toFixed(1)}%)`}
                                >
                                    <div
                                        style={{
                                            width: `${percentage}%`,
                                            backgroundColor: colors[index % colors.length],
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
                                    backgroundColor: [
                                        getAppState().colorPrimary,
                                        "#52c41a",
                                        "#1890ff",
                                        "#722ed1",
                                        "#faad14",
                                        "#eb2f96",
                                    ][index % 6],
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
                <div style={{ display: "flex", flexWrap: "wrap", gap: "8px 12px", alignItems: "baseline" }}>
                    {activeTags.length > 0 ? (
                        activeTags.slice(0, 15).map((tag) => {
                            // Scale font size based on count
                            const maxCount = activeTags[0].count;
                            const size = Math.max(12, Math.min(24, 12 + (tag.count / maxCount) * 12));
                            const opacity = Math.max(0.5, tag.count / maxCount);

                            return (
                                <span
                                    key={tag.text}
                                    style={{
                                        fontSize: size,
                                        opacity: opacity,
                                        fontWeight: size > 18 ? 600 : 400,
                                        color: isDark ? "rgba(255,255,255,0.85)" : "rgba(0,0,0,0.85)",
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
