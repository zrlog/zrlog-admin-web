import { Alert, App, Button, Card, Empty, Grid, List, Space, Statistic, Tag, Typography } from "antd";
import { HealthCheckIssue, SystemHealthData } from "../../type";
import { HeartOutlined, LinkOutlined, ReloadOutlined, SearchOutlined, ToolOutlined } from "@ant-design/icons";
import Row from "antd/es/grid/row";
import { Col } from "antd";
import { getRealRouteUrl, getRes } from "../../utils/constants";
import { Link } from "react-router-dom";
import { AxiosInstance } from "axios";
import { useEffect, useState } from "react";

const { useBreakpoint } = Grid;

const severityColor = (severity: HealthCheckIssue["severity"]) => {
    if (severity === "error") {
        return "error";
    }
    if (severity === "warning") {
        return "warning";
    }
    return "processing";
};

const scoreStatus = (score: number) => {
    if (score >= 85) {
        return "success";
    }
    if (score >= 60) {
        return "warning";
    }
    return "error";
};

const formatTime = (timestamp: number) => {
    return new Intl.DateTimeFormat(undefined, {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
    }).format(timestamp);
};

const HealthCheckPanel = ({ axiosInstance }: { axiosInstance: AxiosInstance }) => {
    const res = getRes().system.health;
    const { message } = App.useApp();
    const screens = useBreakpoint();
    const [loading, setLoading] = useState(false);
    const [optimizing, setOptimizing] = useState(false);
    const [state, setState] = useState<SystemHealthData | null>(null);

    const fetchHealthCheck = async () => {
        setLoading(true);
        try {
            const { data } = await axiosInstance.get("/api/admin/system/health-check");
            setState(data.data);
        } finally {
            setLoading(false);
        }
    };

    const optimizeDatabase = async () => {
        setOptimizing(true);
        try {
            const { data } = await axiosInstance.post("/api/admin/system/optimize");
            setState(data.data);
            message.success(res.optimizeSuccess);
        } finally {
            setOptimizing(false);
        }
    };

    useEffect(() => {
        fetchHealthCheck();
    }, []);

    const issueMeta = (key: string) => {
        return res.issueMeta[key as keyof typeof res.issueMeta];
    };

    const suggestionMeta = (key: string) => {
        return res.suggestionMeta[key as keyof typeof res.suggestionMeta];
    };

    const issueCountLabel = (item: HealthCheckIssue) => {
        if (item.key === "databaseFragment") {
            return state?.databaseFragmentLabel || item.count;
        }
        return item.count;
    };

    const issueDetail = (item: HealthCheckIssue) => {
        if (item.key === "databaseFragment" && state) {
            return `${issueMeta(item.key)?.detail || item.key} ${state.databaseEngine} · ${
                state.databaseFragmentLabel
            }`;
        }
        return issueMeta(item.key)?.detail || item.key;
    };

    const showDatabaseFragment = state
        ? !state.databaseEngine.toLowerCase().includes("d1") && !state.databaseEngine.toLowerCase().includes("webapi")
        : true;
    const showDatabaseInfo = state ? !state.databaseEngine.toLowerCase().includes("webapi") : true;

    return (
        <Card
            title={
                <Space size={8}>
                    <HeartOutlined />
                    <span>{res.title}</span>
                </Space>
            }
            extra={
                <Space size={8}>
                    <Button size="small" icon={<ReloadOutlined />} onClick={fetchHealthCheck} loading={loading}>
                        {res.refresh}
                    </Button>
                    {state?.canOptimizeDatabase && (
                        <Button
                            size="small"
                            type="primary"
                            icon={<ToolOutlined />}
                            onClick={optimizeDatabase}
                            loading={optimizing}
                        >
                            {optimizing ? res.optimizing : res.optimize}
                        </Button>
                    )}
                </Space>
            }
            loading={loading && !state}
        >
            {state ? (
                <Space direction="vertical" size={16} style={{ width: "100%" }}>
                    <Row gutter={[12, 12]}>
                        <Col xs={24} sm={12} lg={6}>
                            <Card size="small" styles={{ body: { padding: 16 } }}>
                                <Statistic title={res.score} value={state.score} suffix="/ 100" />
                            </Card>
                        </Col>
                        <Col xs={24} sm={12} lg={6}>
                            <Card size="small" styles={{ body: { padding: 16 } }}>
                                <Statistic
                                    title={res.brokenLinks}
                                    value={state.brokenLinkCount}
                                    prefix={<LinkOutlined />}
                                />
                            </Card>
                        </Col>
                        <Col xs={24} sm={12} lg={6}>
                            <Card size="small" styles={{ body: { padding: 16 } }}>
                                <Statistic
                                    title={res.seoMissing}
                                    value={state.seoIssueCount}
                                    prefix={<SearchOutlined />}
                                />
                            </Card>
                        </Col>
                        {showDatabaseFragment && (
                            <Col xs={24} sm={12} lg={6}>
                                <Card size="small" styles={{ body: { padding: 16 } }}>
                                    <Statistic
                                        title={res.databaseFragment}
                                        value={state.databaseFragmentLabel}
                                        prefix={<ToolOutlined />}
                                    />
                                </Card>
                            </Col>
                        )}
                    </Row>

                    {showDatabaseInfo && (
                        <Alert
                            type={scoreStatus(state.score)}
                            showIcon
                            message={`${res.database}: ${state.databaseEngine}`}
                            description={`${res.lastChecked}: ${formatTime(state.checkedAt)}`}
                        />
                    )}

                    <Row gutter={[12, 12]}>
                        <Col xs={24} lg={12}>
                            <Card size="small" title={res.issues}>
                                {state.issues.length > 0 ? (
                                    <List
                                        dataSource={state.issues}
                                        renderItem={(item) => (
                                            <List.Item>
                                                <div
                                                    style={{
                                                        width: "100%",
                                                        display: "flex",
                                                        flexDirection: screens.sm ? "row" : "column",
                                                        alignItems: screens.sm ? "center" : "stretch",
                                                        justifyContent: "space-between",
                                                        gap: 12,
                                                    }}
                                                >
                                                    <div style={{ flex: 1, minWidth: 0 }}>
                                                        <List.Item.Meta
                                                            title={
                                                                <Space size={8} wrap>
                                                                    <span>
                                                                        {issueMeta(item.key)?.title || item.key}
                                                                    </span>
                                                                    <Tag color={severityColor(item.severity)}>
                                                                        {issueCountLabel(item)}
                                                                    </Tag>
                                                                </Space>
                                                            }
                                                            description={
                                                                <Space
                                                                    direction="vertical"
                                                                    size={4}
                                                                    style={{ width: "100%" }}
                                                                >
                                                                    <Typography.Text type="secondary">
                                                                        {issueDetail(item)}
                                                                    </Typography.Text>
                                                                    {item.samples.length > 0 && (
                                                                        <Space
                                                                            direction="vertical"
                                                                            size={0}
                                                                            style={{ width: "100%" }}
                                                                        >
                                                                            {item.samples.map((sample) => (
                                                                                <Typography.Text
                                                                                    key={sample}
                                                                                    ellipsis={true}
                                                                                >
                                                                                    {sample}
                                                                                </Typography.Text>
                                                                            ))}
                                                                        </Space>
                                                                    )}
                                                                </Space>
                                                            }
                                                        />
                                                    </div>
                                                    {item.actionUri ? (
                                                        <div
                                                            style={{
                                                                flexShrink: 0,
                                                                alignSelf: screens.sm ? "center" : "stretch",
                                                            }}
                                                        >
                                                            <Button
                                                                type="link"
                                                                style={{ paddingInline: 0 }}
                                                                block={!screens.sm}
                                                            >
                                                                <Link to={getRealRouteUrl(item.actionUri)}>
                                                                    {res.issueAction}
                                                                </Link>
                                                            </Button>
                                                        </div>
                                                    ) : null}
                                                </div>
                                            </List.Item>
                                        )}
                                    />
                                ) : (
                                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={res.empty} />
                                )}
                            </Card>
                        </Col>
                        <Col xs={24} lg={12}>
                            <Card size="small" title={res.suggestions}>
                                <List
                                    dataSource={state.suggestions}
                                    renderItem={(item) => (
                                        <List.Item
                                            actions={
                                                item.actionUri
                                                    ? [
                                                          <Link key={item.key} to={getRealRouteUrl(item.actionUri)}>
                                                              {res.suggestionAction}
                                                          </Link>,
                                                      ]
                                                    : undefined
                                            }
                                        >
                                            <List.Item.Meta
                                                title={suggestionMeta(item.key)?.title || item.key}
                                                description={
                                                    <Typography.Text type="secondary">
                                                        {suggestionMeta(item.key)?.detail || item.key}
                                                    </Typography.Text>
                                                }
                                            />
                                        </List.Item>
                                    )}
                                />
                            </Card>
                        </Col>
                    </Row>
                </Space>
            ) : (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={res.empty} />
            )}
        </Card>
    );
};

export default HealthCheckPanel;
