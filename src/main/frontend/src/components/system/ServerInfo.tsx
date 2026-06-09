import {
    AppleOutlined,
    ArrowRightOutlined,
    ClockCircleOutlined,
    CloudServerOutlined,
    CodeOutlined,
    DatabaseOutlined,
    DockerOutlined,
    GlobalOutlined,
    HddOutlined,
    LaptopOutlined,
    PartitionOutlined,
    WindowsOutlined,
} from "@ant-design/icons";
import LinuxOutlined from "@ant-design/icons/lib/icons/LinuxOutlined";
import { Card, Col, Grid, Row, Space, Typography } from "antd";
import { useTheme } from "antd-style";
import GraalVmOutlined from "icons/GraalVMOutlined";
import ZrLogOutlined from "icons/ZrLogOutlined";
import * as React from "react";
import { Link } from "react-router-dom";
import CPUIcon from "../../icons/CPUIcon";
import MemoryIcon from "../../icons/MemoryIcon";
import { ServerInfoEntry } from "../../type";
import { RiJavaLine } from "../../icons/ri/RiJavaLine";

const { useBreakpoint } = Grid;

type ServerInfoProps = {
    data: ServerInfoEntry[];
    dockerMode: boolean;
    nativeImageMode: boolean;
    title: React.ReactNode;
};

const FEATURED_KEYS = [
    "programInfo",
    "runtime",
    "system",
    "dbInfo",
    "usedMemorySpace",
    "usedDiskSpace",
    "cpuLoad",
    "uptime",
];

const FEATURED_LIMIT = 2;

const keyRank = (key: string) => {
    const index = FEATURED_KEYS.indexOf(key);
    return index === -1 ? FEATURED_KEYS.length + 1 : index;
};

const ServerInfo = ({ data, dockerMode, nativeImageMode, title }: ServerInfoProps) => {
    const screens = useBreakpoint();
    const theme = useTheme();
    const accentBg = theme.colorPrimaryBg;
    const accentBorder = theme.colorPrimaryBorder;
    const accentColor = theme.colorPrimary;
    const neutralBg = theme.colorFillQuaternary;
    const accentBorderStyle = `${theme.lineWidth}px ${theme.lineType} ${accentBorder}`;
    const borderSecondary = `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`;
    const systemSurface = {
        link: {
            display: "block",
            color: "inherit",
        },
        spacing: {
            label: theme.marginSM,
            section: theme.marginMD,
            row: theme.padding,
            rowInline: theme.marginXS,
            listPadY: theme.padding,
            listPadX: theme.padding,
            sectionBody: theme.paddingLG,
        },
        icons: {
            featuredBox: 42,
            detailBox: 32,
            iconFont: theme.fontSize,
            textFont: theme.fontSize,
            secondaryFont: theme.fontSizeSM,
        },
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
            if (nativeImageMode) {
                return "https://www.graalvm.org";
            }
            return "https://www.oracle.com/java";
        }
        return "";
    };

    const getItemMeta = (item: ServerInfoEntry) => {
        if (item.key === "runtime") {
            return {
                icon: (
                    <Space size={theme.marginXXS / 2}>
                        {dockerMode ? <DockerOutlined /> : null}
                        {nativeImageMode && screens.sm ? <GraalVmOutlined /> : <LaptopOutlined />}
                    </Space>
                ),
            };
        }
        if (item.key === "system") {
            if (item.value.startsWith("Linux")) {
                return { icon: <LinuxOutlined /> };
            }
            if (item.value.startsWith("Darwin") || item.value.startsWith("Mac")) {
                return { icon: <AppleOutlined /> };
            }
            return { icon: <WindowsOutlined /> };
        }
        if (item.key === "usedCacheSpace" || item.key === "usedDiskSpace") {
            return { icon: <HddOutlined /> };
        }
        if (item.key === "usedMemorySpace" || item.key === "totalMemorySpace") {
            return { icon: <MemoryIcon /> };
        }
        if (item.key === "dbInfo" || item.key === "dbConnectSize") {
            return { icon: <DatabaseOutlined /> };
        }
        if (item.key === "cpuInfo" || item.key === "cpuLoad" || item.key === "systemLoad") {
            return { icon: <CPUIcon /> };
        }
        if (item.key === "programInfo") {
            return { icon: <ZrLogOutlined /> };
        }
        if (item.key === "webServer") {
            return { icon: <CloudServerOutlined /> };
        }
        if (item.key === "runPath") {
            return { icon: <PartitionOutlined /> };
        }
        if (item.key === "timezone" || item.key === "uptime") {
            return { icon: <ClockCircleOutlined /> };
        }
        if (item.key === "locale") {
            return { icon: <GlobalOutlined /> };
        }
        if (item.key === "encoding") {
            return { icon: <CodeOutlined /> };
        }
        return { icon: <GlobalOutlined /> };
    };

    const wrapByLink = (item: ServerInfoEntry, node: React.ReactNode) => {
        const url = externalUrl(item.key, item.value);
        if (!url) {
            return node;
        }
        return (
            <Link target="_blank" to={url} style={systemSurface.link}>
                {node}
            </Link>
        );
    };

    const sortedItems = [...data].sort((a, b) => keyRank(a.key) - keyRank(b.key));
    const featuredItems = sortedItems.slice(0, Math.min(FEATURED_LIMIT, sortedItems.length));
    const detailItems = sortedItems.slice(featuredItems.length);
    const usesBareLogo = (key: string) => key === "runtime" || key === "programInfo";

    const renderRuntimeIcons = (variant: "featured" | "detail") => {
        const gap = variant === "featured" ? systemSurface.spacing.label : systemSurface.spacing.rowInline;
        const iconSize = variant === "featured" ? 34 : 28;

        const items = [];
        if (dockerMode) {
            items.push(<DockerOutlined style={{ fontSize: iconSize }} />);
        }
        if (nativeImageMode) {
            items.push(<GraalVmOutlined />);
        } else {
            items.push(<RiJavaLine style={{ width: iconSize, height: iconSize }} />);
        }
        return (
            <div
                style={{
                    display: "inline-flex",
                    alignItems: "center",
                    gap,
                    color: accentColor,
                }}
            >
                {items}
            </div>
        );
    };

    const renderProgramInfoIcon = (variant: "featured" | "detail") => {
        return <ZrLogOutlined size={variant === "featured" ? 34 : 26} />;
    };

    const renderExternalIndicator = (variant: "featured" | "detail") => {
        const size = variant === "featured" ? 32 : 28;
        return (
            <div
                style={{
                    width: size,
                    height: size,
                    borderRadius: theme.borderRadius,
                    display: "inline-flex",
                    alignItems: "center",
                    justifyContent: "center",
                    background: variant === "featured" ? accentBg : neutralBg,
                    color: accentColor,
                    fontSize: theme.fontSizeSM,
                    flexShrink: 0,
                }}
            >
                <ArrowRightOutlined />
            </div>
        );
    };

    const renderFeaturedCard = (item: ServerInfoEntry) => {
        const meta = getItemMeta(item);
        const iconNode =
            item.key === "runtime" ? (
                renderRuntimeIcons("featured")
            ) : item.key === "programInfo" ? (
                renderProgramInfoIcon("featured")
            ) : (
                <div
                    style={{
                        minWidth: systemSurface.icons.featuredBox,
                        height: systemSurface.icons.featuredBox,
                        borderRadius: theme.borderRadius,
                        display: "inline-flex",
                        alignItems: "center",
                        justifyContent: "center",
                        background: accentBg,
                        color: accentColor,
                        fontSize: systemSurface.icons.iconFont,
                    }}
                >
                    {meta.icon}
                </div>
            );

        return wrapByLink(
            item,
            <div
                style={{
                    height: "100%",
                    padding: systemSurface.spacing.sectionBody,
                    borderRadius: theme.borderRadiusLG,
                    border: accentBorderStyle,
                    background: theme.colorBgContainer,
                }}
            >
                <Space direction="vertical" size={systemSurface.spacing.section} style={{ width: "100%" }}>
                    <div
                        style={{
                            display: "flex",
                            alignItems: "flex-start",
                            justifyContent: "space-between",
                            gap: systemSurface.spacing.label,
                        }}
                    >
                        <div
                            style={{
                                minWidth: 0,
                                flex: 1,
                            }}
                        >
                            {iconNode}
                        </div>
                        {externalUrl(item.key, item.value) ? renderExternalIndicator("featured") : null}
                    </div>
                    <div>
                        <Typography.Text type="secondary">{item.name}</Typography.Text>
                        <div
                            style={{
                                marginTop: theme.marginXXS,
                                color: theme.colorText,
                                fontSize: screens.xs ? 18 : 20,
                                lineHeight: 1.45,
                                fontWeight: 600,
                                wordBreak: "break-word",
                            }}
                        >
                            {item.value}
                        </div>
                    </div>
                </Space>
            </div>
        );
    };

    const renderDetailItem = (item: ServerInfoEntry) => {
        const meta = getItemMeta(item);
        return wrapByLink(
            item,
            <div
                style={{
                    display: "flex",
                    alignItems: "flex-start",
                    gap: systemSurface.spacing.label,
                    padding: `${systemSurface.spacing.listPadY}px ${systemSurface.spacing.listPadX}px`,
                    borderRadius: theme.borderRadiusLG,
                    border: borderSecondary,
                    background: theme.colorBgContainer,
                }}
            >
                <div
                    style={{
                        flexShrink: 0,
                        marginTop: theme.marginXXS / 2,
                        minWidth: usesBareLogo(item.key) ? undefined : systemSurface.icons.detailBox,
                    }}
                >
                    {item.key === "runtime" ? (
                        renderRuntimeIcons("detail")
                    ) : item.key === "programInfo" ? (
                        renderProgramInfoIcon("detail")
                    ) : (
                        <div
                            style={{
                                width: systemSurface.icons.detailBox,
                                height: systemSurface.icons.detailBox,
                                borderRadius: theme.borderRadius,
                                display: "inline-flex",
                                alignItems: "center",
                                justifyContent: "center",
                                background: neutralBg,
                                color: accentColor,
                                fontSize: systemSurface.icons.textFont,
                            }}
                        >
                            {meta.icon}
                        </div>
                    )}
                </div>
                <div style={{ minWidth: 0, flex: 1 }}>
                    <Typography.Text type="secondary" style={{ fontSize: systemSurface.icons.secondaryFont }}>
                        {item.name}
                    </Typography.Text>
                    <div
                        style={{
                            marginTop: theme.marginXS,
                            color: theme.colorText,
                            fontSize: systemSurface.icons.textFont,
                            lineHeight: 1.55,
                            fontWeight: 500,
                            wordBreak: "break-word",
                        }}
                    >
                        {item.value}
                    </div>
                </div>
                {externalUrl(item.key, item.value) ? (
                    <div
                        style={{
                            alignSelf: "stretch",
                            display: "flex",
                            alignItems: "center",
                        }}
                    >
                        {renderExternalIndicator("detail")}
                    </div>
                ) : null}
            </div>
        );
    };

    if (!data || data.length === 0) {
        return <></>;
    }

    return (
        <Card title={title} styles={{ body: { overflow: "hidden", padding: systemSurface.spacing.row } }}>
            <Space direction="vertical" size={systemSurface.spacing.section} style={{ width: "100%" }}>
                <Row gutter={[systemSurface.spacing.section, systemSurface.spacing.section]}>
                    {featuredItems.map((item) => (
                        <Col xs={24} md={featuredItems.length === 1 ? 24 : 12} key={item.key}>
                            {renderFeaturedCard(item)}
                        </Col>
                    ))}
                </Row>
                {detailItems.length > 0 ? (
                    <Row gutter={[systemSurface.spacing.listPadY, systemSurface.spacing.listPadY]}>
                        {detailItems.map((item) => (
                            <Col xs={24} md={12} key={item.key}>
                                {renderDetailItem(item)}
                            </Col>
                        ))}
                    </Row>
                ) : null}
            </Space>
        </Card>
    );
};

export default ServerInfo;
