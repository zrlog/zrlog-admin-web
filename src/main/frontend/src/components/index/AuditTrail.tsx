import { Card, Typography, Timeline, Drawer, Button, Tag } from "antd";
import { HistoryOutlined, LoginOutlined, EditOutlined, SettingOutlined, RightOutlined } from "@ant-design/icons";
import TimeAgo from "@editor/dist/src/editor/TimeAgo";
import React, { useState } from "react";
import { getRes } from "../../utils/constants";
import { getAppState } from "../../base/ConfigProviderApp";

const { Text } = Typography;

interface AuditLog {
    timestamp: number;
    ip: string;
    action: string;
    type: string;
    os?: string;
    browser?: string;
    crawler?: boolean;
}

interface AuditTrailProps {
    logs?: AuditLog[];
    loading?: boolean;
}

const AuditTrail: React.FC<AuditTrailProps> = ({ logs = [], loading }) => {
    const [drawerVisible, setDrawerVisible] = useState(false);

    const getIcon = (type: string) => {
        switch (type) {
            case "login":
                return <LoginOutlined style={{ color: "#1890ff" }} />;
            case "article":
                return <EditOutlined style={{ color: "#52c41a" }} />;
            case "setting":
                return <SettingOutlined style={{ color: "#722ed1" }} />;
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
                <div style={{ textAlign: "center", padding: "40px 0", color: "#999" }}>
                    {getRes().index.audit.empty}
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
                            style={{ color: getAppState().colorPrimary }}
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
