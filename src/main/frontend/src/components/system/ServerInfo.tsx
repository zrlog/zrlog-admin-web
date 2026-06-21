import {
    AppleOutlined,
    ArrowRightOutlined,
    ClockCircleOutlined,
    CloudServerOutlined,
    CodeOutlined,
    DashboardOutlined,
    DatabaseOutlined,
    DockerOutlined,
    GlobalOutlined,
    HddOutlined,
    PartitionOutlined,
    SettingOutlined,
    WindowsOutlined,
} from "@ant-design/icons";
import LinuxOutlined from "@ant-design/icons/lib/icons/LinuxOutlined";
import { Card, Col, Row, Space, Typography } from "antd";
import { useTheme } from "antd-style";
import GraalVmOutlined from "icons/GraalVMOutlined";
import ZrLogOutlined from "icons/ZrLogOutlined";
import * as React from "react";
import { Link } from "react-router-dom";
import CPUIcon from "../../icons/CPUIcon";
import MemoryIcon from "../../icons/MemoryIcon";
import { RiJavaLine } from "../../icons/ri/RiJavaLine";
import { ServerInfoEntry, SystemData } from "../../type";
import { getRes } from "../../utils/constants";

type ServerInfoProps = {
    data: SystemData;
};

const APPLICATION_KEYS = ["programInfo", "runtime", "webServer", "system", "runPath"];
const CONFIGURATION_KEYS = ["dbInfo", "timezone", "locale", "encoding"];
const RESOURCE_KEYS = ["cpuInfo", "usedDiskSpace", "usedCacheSpace"];

const clampPercent = (value: number) => Math.min(Math.max(value, 0), 100);

const parseCpuPercent = (value?: string) => {
    const text = value?.trim();
    const match = text?.match(/-?\d+(?:\.\d+)?/);
    if (!text || !match) {
        return undefined;
    }
    const numberValue = Number(match[0]);
    if (!Number.isFinite(numberValue) || numberValue < 0) {
        return undefined;
    }
    if (text.includes("%")) {
        return clampPercent(numberValue);
    }
    if (numberValue <= 1) {
        return clampPercent(numberValue * 100);
    }
    return clampPercent(numberValue);
};

const parseSize = (value?: string) => {
    const match = value?.trim().match(/^(\d+(?:\.\d+)?)\s*([KMGT]?B?)$/i);
    if (!match) {
        return undefined;
    }
    const unit = match[2].toUpperCase();
    const unitScale: Record<string, number> = {
        B: 1,
        K: 1024,
        KB: 1024,
        M: 1024 ** 2,
        MB: 1024 ** 2,
        G: 1024 ** 3,
        GB: 1024 ** 3,
        T: 1024 ** 4,
        TB: 1024 ** 4,
    };
    return Number(match[1]) * (unitScale[unit] || 1);
};

const parseConnectionUsage = (value?: string) => {
    const match = value?.match(/(\d+)\s*\/\s*(\d+)/);
    if (!match) {
        return undefined;
    }
    const active = Number(match[1]);
    const total = Number(match[2]);
    if (!Number.isFinite(active) || !Number.isFinite(total) || total <= 0) {
        return undefined;
    }
    return clampPercent((active / total) * 100);
};

const parseLoadValues = (value?: string) => {
    return (value?.match(/\d+(?:\.\d+)?/g) || []).slice(0, 3).map((item) => Number(item));
};

const formatPercent = (percent: number) => {
    const normalized = percent >= 10 ? Math.round(percent) : Math.round(percent * 10) / 10;
    return `${normalized}%`;
};

const getItemsByKeys = (itemMap: Map<string, ServerInfoEntry>, keys: string[]) => {
    return keys.map((key) => itemMap.get(key)).filter((item): item is ServerInfoEntry => Boolean(item));
};

type GridSpan = {
    xs: number;
    sm?: number;
    md?: number;
    lg?: number;
    xl?: number;
};

const ServerInfo = ({ data }: ServerInfoProps) => {
    const theme = useTheme();
    const borderSecondary = `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`;
    const infoEntries = [...(data.serverInfos || []), ...(data.serverInfos2 || [])];
    const itemMap = new Map(infoEntries.map((item) => [item.key, item]));
    const applicationItems = getItemsByKeys(itemMap, APPLICATION_KEYS);
    const configurationItems = getItemsByKeys(itemMap, CONFIGURATION_KEYS);
    const resourceItems = getItemsByKeys(itemMap, RESOURCE_KEYS);
    const loadLabels = [
        getRes().system.loadAverage.oneMinute,
        getRes().system.loadAverage.fiveMinutes,
        getRes().system.loadAverage.fifteenMinutes,
    ];

    const metricColor = (percent: number) => {
        if (percent >= 85) {
            return theme.colorError;
        }
        if (percent >= 65) {
            return theme.colorWarning;
        }
        return theme.colorSuccess;
    };

    const externalUrl = (key: string, value: string) => {
        if (key === "programInfo") {
            return "https://www.zrlog.com/changelog/?ref=systemInfo";
        }
        if (key === "webServer") {
            if (value.toLowerCase().startsWith("simplewebserver/")) {
                return "https://github.com/94fzb/simplewebserver";
            }
            if (value.startsWith("Apache Tomcat/")) {
                return "https://tomcat.apache.org/";
            }
            if (value.startsWith("Lambda")) {
                return "https://aws.amazon.com/lambda/";
            }
        }
        if (key === "runtime") {
            if (data.nativeImageMode) {
                return "https://www.graalvm.org";
            }
            return "https://www.oracle.com/java";
        }
        return "";
    };

    const wrapByLink = (item: ServerInfoEntry, node: React.ReactNode) => {
        const url = externalUrl(item.key, item.value);
        if (!url) {
            return node;
        }
        return (
            <Link
                target="_blank"
                rel="noopener noreferrer"
                to={url}
                style={{ display: "block", color: "inherit", height: "100%" }}
            >
                {node}
            </Link>
        );
    };

    const renderRuntimeIcons = (iconSize: number) => {
        const items: React.ReactNode[] = [];
        if (data.dockerMode) {
            items.push(<DockerOutlined key="docker" style={{ fontSize: iconSize }} />);
        }
        if (data.nativeImageMode) {
            items.push(<GraalVmOutlined key="graalvm" />);
        } else {
            items.push(<RiJavaLine key="java" style={{ width: iconSize, height: iconSize }} />);
        }
        return (
            <span
                style={{ display: "inline-flex", alignItems: "center", gap: theme.marginXS, color: theme.colorPrimary }}
            >
                {items}
            </span>
        );
    };

    const renderItemIcon = (item: ServerInfoEntry, iconSize = 24) => {
        if (item.key === "runtime") {
            return renderRuntimeIcons(iconSize);
        }
        if (item.key === "programInfo") {
            return <ZrLogOutlined size={iconSize} />;
        }
        if (item.key === "system") {
            if (item.value.startsWith("Linux")) {
                return <LinuxOutlined />;
            }
            if (item.value.startsWith("Darwin") || item.value.startsWith("Mac")) {
                return <AppleOutlined />;
            }
            return <WindowsOutlined />;
        }
        if (item.key === "usedCacheSpace" || item.key === "usedDiskSpace") {
            return <HddOutlined />;
        }
        if (item.key === "usedMemorySpace" || item.key === "totalMemorySpace") {
            return <MemoryIcon />;
        }
        if (item.key === "dbInfo" || item.key === "dbConnectSize") {
            return <DatabaseOutlined />;
        }
        if (item.key === "cpuInfo" || item.key === "cpuLoad" || item.key === "systemLoad") {
            return <CPUIcon />;
        }
        if (item.key === "webServer") {
            return <CloudServerOutlined />;
        }
        if (item.key === "runPath") {
            return <PartitionOutlined />;
        }
        if (item.key === "timezone" || item.key === "uptime") {
            return <ClockCircleOutlined />;
        }
        if (item.key === "locale") {
            return <GlobalOutlined />;
        }
        if (item.key === "encoding") {
            return <CodeOutlined />;
        }
        return <GlobalOutlined />;
    };

    const renderIconBox = (item: ServerInfoEntry) => {
        return (
            <span
                style={{
                    width: 36,
                    height: 36,
                    borderRadius: theme.borderRadius,
                    background: theme.colorPrimaryBg,
                    color: theme.colorPrimary,
                    display: "inline-flex",
                    alignItems: "center",
                    justifyContent: "center",
                    flexShrink: 0,
                    fontSize: theme.fontSizeLG,
                }}
            >
                {renderItemIcon(item)}
            </span>
        );
    };

    const renderProgressBar = (percent: number) => {
        const color = metricColor(percent);
        return (
            <div
                style={{
                    height: 8,
                    borderRadius: theme.borderRadiusLG,
                    background: theme.colorFillSecondary,
                    overflow: "hidden",
                }}
            >
                <div
                    style={{
                        width: `${percent}%`,
                        height: "100%",
                        borderRadius: theme.borderRadiusLG,
                        background: color,
                        transition: "width 0.3s ease",
                    }}
                />
            </div>
        );
    };

    const renderMetricTile = ({
        item,
        value,
        percent,
        extra,
    }: {
        item: ServerInfoEntry;
        value?: string;
        percent?: number;
        extra?: React.ReactNode;
    }) => {
        return (
            <div
                style={{
                    height: "100%",
                    minHeight: 132,
                    padding: theme.padding,
                    borderRadius: theme.borderRadiusLG,
                    border: borderSecondary,
                    background: theme.colorBgContainer,
                    display: "flex",
                    flexDirection: "column",
                    gap: theme.marginSM,
                    boxSizing: "border-box",
                }}
            >
                <div style={{ display: "flex", alignItems: "center", gap: theme.marginSM, minWidth: 0 }}>
                    {renderIconBox(item)}
                    <Typography.Text type="secondary" ellipsis={true} style={{ minWidth: 0 }}>
                        {item.name}
                    </Typography.Text>
                </div>
                <Typography.Text
                    style={{
                        display: "block",
                        color: theme.colorTextHeading,
                        fontSize: theme.fontSizeHeading4,
                        fontWeight: 700,
                        lineHeight: 1.2,
                        wordBreak: "break-word",
                    }}
                >
                    {value || item.value}
                </Typography.Text>
                {typeof percent === "number" ? renderProgressBar(percent) : null}
                {extra}
            </div>
        );
    };

    const renderLoadBars = (item: ServerInfoEntry) => {
        const values = parseLoadValues(item.value);
        if (values.length === 0) {
            return undefined;
        }
        const maxValue = Math.max(...values, 1);
        return (
            <div style={{ display: "grid", gap: theme.marginXS }}>
                {values.map((value, index) => (
                    <div key={`${item.key}-${index}`} style={{ display: "grid", gap: theme.marginXXS }}>
                        <div
                            style={{
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "space-between",
                                gap: theme.marginXS,
                            }}
                        >
                            <Typography.Text type="secondary" style={{ fontSize: theme.fontSizeSM }}>
                                {loadLabels[index]}
                            </Typography.Text>
                            <Typography.Text style={{ fontSize: theme.fontSizeSM }}>{value.toFixed(2)}</Typography.Text>
                        </div>
                        <div
                            style={{
                                height: 6,
                                borderRadius: theme.borderRadiusLG,
                                background: theme.colorFillSecondary,
                                overflow: "hidden",
                            }}
                        >
                            <div
                                style={{
                                    width: `${clampPercent((value / maxValue) * 100)}%`,
                                    height: "100%",
                                    borderRadius: theme.borderRadiusLG,
                                    background: theme.colorPrimary,
                                }}
                            />
                        </div>
                    </div>
                ))}
            </div>
        );
    };

    const renderInfoItem = (item: ServerInfoEntry) => {
        const node = (
            <div
                style={{
                    height: "100%",
                    padding: theme.padding,
                    borderRadius: theme.borderRadiusLG,
                    border: borderSecondary,
                    background: theme.colorBgContainer,
                    display: "flex",
                    gap: theme.marginSM,
                    alignItems: "flex-start",
                    minWidth: 0,
                    boxSizing: "border-box",
                }}
            >
                {renderIconBox(item)}
                <div style={{ minWidth: 0, flex: 1 }}>
                    <Typography.Text type="secondary" style={{ fontSize: theme.fontSizeSM }}>
                        {item.name}
                    </Typography.Text>
                    <Typography.Text
                        style={{
                            display: "block",
                            marginTop: theme.marginXXS,
                            color: theme.colorText,
                            fontWeight: 500,
                            lineHeight: 1.55,
                            wordBreak: "break-word",
                        }}
                    >
                        {item.value}
                    </Typography.Text>
                </div>
                {externalUrl(item.key, item.value) ? (
                    <span
                        style={{
                            width: 28,
                            height: 28,
                            borderRadius: theme.borderRadius,
                            background: theme.colorFillQuaternary,
                            color: theme.colorPrimary,
                            display: "inline-flex",
                            alignItems: "center",
                            justifyContent: "center",
                            flexShrink: 0,
                        }}
                    >
                        <ArrowRightOutlined />
                    </span>
                ) : null}
            </div>
        );
        return wrapByLink(item, node);
    };

    const renderSectionTitle = (icon: React.ReactNode, title: string) => (
        <Space size={theme.marginXS}>
            {icon}
            <span>{title}</span>
        </Space>
    );

    const renderInfoGrid = (items: ServerInfoEntry[], getSpan: (item: ServerInfoEntry) => GridSpan) => {
        if (items.length === 0) {
            return null;
        }
        return (
            <Row gutter={[theme.marginSM, theme.marginSM]}>
                {items.map((item) => (
                    <Col {...getSpan(item)} key={item.key}>
                        {renderInfoItem(item)}
                    </Col>
                ))}
            </Row>
        );
    };

    const getApplicationSpan = (item: ServerInfoEntry) => {
        if (item.key === "runPath") {
            return { xs: 24, md: 24, xl: 16 };
        }
        return { xs: 24, md: 12, xl: 8 };
    };

    const getConfigurationSpan = () => ({ xs: 24, md: 12, xl: 6 });

    const getResourceSpan = () => ({ xs: 24, md: 12 });

    const cpuLoad = itemMap.get("cpuLoad");
    const usedMemory = itemMap.get("usedMemorySpace");
    const totalMemory = itemMap.get("totalMemorySpace");
    const dbConnections = itemMap.get("dbConnectSize");
    const uptime = itemMap.get("uptime");
    const systemLoad = itemMap.get("systemLoad");
    const cpuPercent = parseCpuPercent(cpuLoad?.value);
    const dbConnectionPercent = parseConnectionUsage(dbConnections?.value);
    const memoryPercent =
        usedMemory && totalMemory
            ? (() => {
                  const usedSize = parseSize(usedMemory.value);
                  const totalSize = parseSize(totalMemory.value);
                  return usedSize !== undefined && totalSize ? clampPercent((usedSize / totalSize) * 100) : undefined;
              })()
            : undefined;
    const overviewItems: { key: string; node: React.ReactNode }[] = [];
    if (cpuLoad) {
        overviewItems.push({
            key: cpuLoad.key,
            node: renderMetricTile({
                item: cpuLoad,
                value: cpuPercent !== undefined ? formatPercent(cpuPercent) : cpuLoad.value,
                percent: cpuPercent,
            }),
        });
    }
    if (usedMemory) {
        overviewItems.push({
            key: usedMemory.key,
            node: renderMetricTile({
                item: usedMemory,
                value: totalMemory ? `${usedMemory.value} / ${totalMemory.value}` : usedMemory.value,
                percent: memoryPercent,
            }),
        });
    }
    if (dbConnections) {
        overviewItems.push({
            key: dbConnections.key,
            node: renderMetricTile({
                item: dbConnections,
                percent: dbConnectionPercent,
            }),
        });
    }
    if (uptime) {
        overviewItems.push({
            key: uptime.key,
            node: renderMetricTile({
                item: uptime,
            }),
        });
    }

    return (
        <Space direction="vertical" size={theme.marginMD} style={{ width: "100%" }}>
            {overviewItems.length > 0 ? (
                <Card
                    title={renderSectionTitle(<DashboardOutlined />, getRes().system.overview)}
                    styles={{ body: { padding: theme.padding } }}
                >
                    <Row gutter={[theme.marginSM, theme.marginSM]}>
                        {overviewItems.map((item) => (
                            <Col xs={24} sm={12} xl={6} key={item.key}>
                                {item.node}
                            </Col>
                        ))}
                    </Row>
                </Card>
            ) : null}

            <Card
                title={renderSectionTitle(<CloudServerOutlined />, getRes().system.applicationRuntime)}
                styles={{ body: { padding: theme.padding } }}
            >
                {renderInfoGrid(applicationItems, getApplicationSpan)}
            </Card>

            <Card
                title={renderSectionTitle(<SettingOutlined />, getRes().system.systemConfiguration)}
                styles={{ body: { padding: theme.padding } }}
            >
                {renderInfoGrid(configurationItems, getConfigurationSpan)}
            </Card>

            <Card
                title={renderSectionTitle(<HddOutlined />, getRes().system.resourceStatus)}
                styles={{ body: { padding: theme.padding } }}
            >
                <Row gutter={[theme.marginSM, theme.marginSM]}>
                    {systemLoad ? (
                        <Col xs={24} md={12}>
                            {renderMetricTile({
                                item: systemLoad,
                                extra: renderLoadBars(systemLoad),
                            })}
                        </Col>
                    ) : null}
                    {resourceItems.map((item) => (
                        <Col {...getResourceSpan()} key={item.key}>
                            {renderInfoItem(item)}
                        </Col>
                    ))}
                </Row>
            </Card>
        </Space>
    );
};

export default ServerInfo;
