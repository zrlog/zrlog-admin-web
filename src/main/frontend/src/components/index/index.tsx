import { Card, Col, Row } from "antd";
import { getRealRouteUrl, getRes } from "../../utils/constants";

import { FunctionComponent } from "react";
import { IndexData } from "../../type";
import ActivityGraph, { generateCompleteData } from "./ActivityGraph";
import IndexTipBg from "./IndexTipBg";
import StatisticsInfo from "./StatisticsInfo";
import QuickActionCard from "./QuickAction";
import { getAppState } from "../../base/ConfigProviderApp";
import DataInsights from "./DataInsights";
import AuditTrail from "./AuditTrail";
import { BarChartOutlined, InfoCircleOutlined, RightOutlined, SmileOutlined } from "@ant-design/icons";
import { Link } from "react-router-dom";

type IndexProps = {
    data: IndexData;
};

const Index: FunctionComponent<IndexProps> = ({ data }) => {
    const sectionGap = 20;

    if (data.statisticsInfo === null) {
        return <></>;
    }
    return (
        <>
            <Row gutter={[20, 20]}>
                {/* Left Column */}
                <Col xs={24} lg={12}>
                    <div style={{ display: "flex", flexDirection: "column", gap: sectionGap }}>
                        <Card
                            style={{
                                overflow: "hidden",
                                boxShadow: getAppState().dark
                                    ? "0 4px 16px rgba(0,0,0,0.4)"
                                    : `0 8px 24px ${getAppState().colorPrimary}40`,
                            }}
                            title={
                                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                                    <SmileOutlined style={{ fontSize: 18 }} />
                                    <span>{data.welcomeTip}</span>
                                </div>
                            }
                            extra={
                                <div
                                    style={{
                                        display: "flex",
                                        alignItems: "center",
                                        color: "rgba(255,255,255,0.92)",
                                    }}
                                >
                                    <Link
                                        to={getRealRouteUrl("/system")}
                                        style={{
                                            display: "inline-flex",
                                            alignItems: "center",
                                            gap: 6,
                                            color: "rgba(255,255,255,0.92)",
                                            whiteSpace: "nowrap",
                                        }}
                                    >
                                        <span>{getRes().index.welcome.viewSystem}</span>
                                        <RightOutlined />
                                    </Link>
                                </div>
                            }
                            styles={{
                                header: {
                                    background: getAppState().dark
                                        ? "linear-gradient(135deg, #2b2930 0%, #1c1b1f 100%)"
                                        : `linear-gradient(135deg, ${getAppState().colorPrimary} 0%, ${
                                              getAppState().colorPrimary
                                          }dd 100%)`,
                                    color: "white",
                                },
                                body: {
                                    position: "relative",
                                    padding: "16px 20px 20px",
                                    overflow: "hidden",
                                    color: "white",
                                },
                            }}
                        >
                            <IndexTipBg
                                style={{
                                    position: "absolute",
                                    height: "100%",
                                    width: "100%",
                                    background: getAppState().dark
                                        ? "linear-gradient(180deg, #1f1e22 0%, #19181c 100%)"
                                        : `linear-gradient(180deg, ${getAppState().colorPrimary}f2 0%, ${
                                              getAppState().colorPrimary
                                          }d6 100%)`,
                                    top: 0,
                                    left: 0,
                                }}
                            />
                            <div
                                style={{
                                    position: "relative",
                                    zIndex: 1,
                                    maxWidth: 720,
                                }}
                            >
                                <div
                                    style={{
                                        fontSize: 15,
                                        opacity: 0.92,
                                        lineHeight: 1.7,
                                        display: "grid",
                                        gap: 6,
                                    }}
                                >
                                    {data.tips.map((e, idx) => (
                                        <span key={idx} style={{ display: "block" }}>
                                            {e}
                                        </span>
                                    ))}
                                </div>
                                <div
                                    style={{
                                        marginTop: 18,
                                        display: "flex",
                                        alignItems: "center",
                                        gap: 8,
                                        flexWrap: "wrap",
                                        color: "rgba(255,255,255,0.92)",
                                    }}
                                >
                                    <InfoCircleOutlined style={{ fontSize: 14, opacity: 0.82 }} />
                                    <span style={{ fontSize: 12, opacity: 0.78 }}>
                                        {getRes().index.welcome.currentVersion}
                                    </span>
                                    <span
                                        style={{
                                            fontSize: 14,
                                            lineHeight: 1.25,
                                            wordBreak: "break-word",
                                            opacity: 0.98,
                                        }}
                                    >
                                        {data.versionInfo}
                                    </span>
                                </div>
                            </div>
                        </Card>
                        <QuickActionCard draftCount={data.statisticsInfo.draftCount} />
                        <StatisticsInfo data={data.statisticsInfo} />
                    </div>
                </Col>

                {/* Right Column */}
                <Col xs={24} lg={12}>
                    <div style={{ display: "flex", flexDirection: "column", gap: sectionGap }}>
                        <Card
                            title={
                                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                                    <BarChartOutlined />
                                    <span>{getRes().index.activity}</span>
                                </div>
                            }
                            bordered={false}
                            className="dashboard-card"
                            styles={{ body: { padding: 20 } }}
                        >
                            <div style={{ overflow: "auto" }}>
                                <ActivityGraph data={generateCompleteData(data.activityData)} />
                            </div>
                        </Card>
                        <AuditTrail logs={data.statisticsInfo.auditLogs} />
                        <DataInsights typeData={data.statisticsInfo.typeData} tagData={data.statisticsInfo.tagData} />
                    </div>
                </Col>
            </Row>
        </>
    );
};

export default Index;
