import { Button, Card, Drawer, Tag, Timeline, Typography } from "antd";
import { EditOutlined, HistoryOutlined, LoginOutlined, RightOutlined, SettingOutlined } from "@ant-design/icons";
import TimeAgo from "@editor/dist/src/editor/TimeAgo";
import React, { useState } from "react";
import { getRes } from "../../utils/constants";
import { useTheme } from "antd-style";
import { StatisticsInfoState } from "../../type";

const { Text } = Typography;

type AuditLog = NonNullable<StatisticsInfoState["auditLogs"]>[number];

interface AuditTrailProps {
    data?: {
        auditLogs?: AuditLog[];
        loading?: boolean;
    };
}

const AuditTrail: React.FC<AuditTrailProps> = ({ data }) => {
    const [drawerVisible, setDrawerVisible] = useState(false);
    const theme = useTheme();
    const logs = data?.auditLogs || [];
    const loading = data?.loading;

    const getIcon = (type: string) => {
        switch (type) {
            case "LOGIN":
            case "login":
                return <LoginOutlined style={{ color: theme.colorInfo }} />;
            case "ARTICLE":
            case "article":
                return <EditOutlined style={{ color: theme.colorSuccess }} />;
            case "SETTING":
            case "setting":
                return <SettingOutlined style={{ color: theme.colorPrimary }} />;
            default:
                return <HistoryOutlined />;
        }
    };

    const renderTimeline = (data: AuditLog[]) => (
        <Timeline pending={loading} reverse={false}>
            {data.map((log, index) => (
                <Timeline.Item key={index} dot={getIcon(log.type)}>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                        <div style={{ flex: 1 }}>
                            <Text strong style={{ display: "block" }}>
                                {log.action}
                            </Text>
                            {log.content && (
                                <Text type="secondary" style={{ display: "block", fontSize: 12 }}>
                                    {log.content}
                                </Text>
                            )}
                            <Text type="secondary" style={{ fontSize: 12 }}>
                                {log.ip}
                                {(log.os || log.browser) && (
                                    <span style={{ opacity: 0.6, marginLeft: 8 }}>
                                        {log.os && ` · ${log.os}`}
                                        {log.browser && ` · ${log.browser}`}
                                    </span>
                                )}
                                {log.crawler && (
                                    <Tag color="gold" style={{ marginInlineStart: 8 }}>
                                        {getRes().index.audit.crawler}
                                    </Tag>
                                )}
                            </Text>
                        </div>
                        <Text type="secondary" style={{ fontSize: 12, whiteSpace: "nowrap", marginLeft: 12 }}>
                            <TimeAgo timestamp={log.timestamp} />
                        </Text>
                    </div>
                </Timeline.Item>
            ))}
            {data.length === 0 && !loading && (
                <div style={{ textAlign: "center", padding: "40px 0" }}>
                    <Text type="secondary">{getRes().index.audit.empty}</Text>
                </div>
            )}
        </Timeline>
    );

    return (
        <>
            <Card
                title={
                    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                        <HistoryOutlined />
                        <span>{getRes().index.audit.label}</span>
                    </div>
                }
                extra={
                    logs.length > 3 && (
                        <Button
                            type="link"
                            size="small"
                            style={{ color: theme.colorPrimary }}
                            onClick={() => setDrawerVisible(true)}
                        >
                            {getRes().index.audit.more} <RightOutlined />
                        </Button>
                    )
                }
                bordered={false}
                className="dashboard-card"
                styles={{ body: { padding: 20 } }}
            >
                {renderTimeline(logs.slice(0, 3))}
            </Card>

            <Drawer
                title={
                    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                        <HistoryOutlined />
                        <span>{getRes().index.audit.full}</span>
                    </div>
                }
                width={450}
                onClose={() => setDrawerVisible(false)}
                open={drawerVisible}
            >
                {renderTimeline(logs)}
            </Drawer>
        </>
    );
};

export default AuditTrail;
