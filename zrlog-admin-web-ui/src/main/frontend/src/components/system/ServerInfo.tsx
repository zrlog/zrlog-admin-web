import AppleOutlined from "@ant-design/icons/lib/icons/AppleOutlined";
import DockerOutlined from "@ant-design/icons/lib/icons/DockerOutlined";
import LinuxOutlined from "@ant-design/icons/lib/icons/LinuxOutlined";
import WindowsOutlined from "@ant-design/icons/lib/icons/WindowsOutlined";
import DatabaseIcon from "@zrlog/ui/icons/database";
import TimeIcon from "@zrlog/ui/icons/time";
import { Descriptions, Flex, Typography, theme } from "antd";
import { ReactNode } from "react";
import GraalVmOutlined from "icons/GraalVMOutlined";
import ZrLogOutlined from "icons/ZrLogOutlined";
import CPUIcon from "../../icons/CPUIcon";
import MemoryIcon from "../../icons/MemoryIcon";
import { RiJavaLine } from "../../icons/ri/RiJavaLine";
import { ServerInfoEntry, SystemData } from "../../type";
import { getRes } from "../../utils/constants";
import SettingsSection from "../common/SettingsSection";

const APPLICATION_KEYS = ["programInfo", "runtime", "webServer", "system", "runPath"];
const CONFIGURATION_KEYS = ["dbInfo", "timezone", "locale", "encoding"];
const RESOURCE_KEYS = ["cpuInfo", "totalMemorySpace", "usedDiskSpace", "usedCacheSpace"];

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

const ServerInfo = ({ data }: { data: SystemData }) => {
    const { token } = theme.useToken();
    const text = getRes().system;
    const border = `${token.lineWidth}px ${token.lineType} ${token.colorBorderSecondary}`;
    const entries = [...(data.serverInfos || []), ...(data.serverInfos2 || [])];
    const itemMap = new Map(entries.map((item) => [item.key, item]));
    const cpu = itemMap.get("cpuLoad");
    const memory = itemMap.get("usedMemorySpace");
    const connections = itemMap.get("dbConnectSize");
    const uptime = itemMap.get("uptime");
    const load = itemMap.get("systemLoad");
    const cpuPercent = parseCpuPercent(cpu?.value);
    const metrics: Array<{ item?: ServerInfoEntry; value?: string; percent?: number; icon: ReactNode }> = [
        {
            item: cpu,
            value: cpuPercent === undefined ? cpu?.value : formatPercent(cpuPercent),
            percent: cpuPercent,
            icon: <CPUIcon />,
        },
        { item: memory, icon: <MemoryIcon /> },
        { item: connections, percent: parseConnectionUsage(connections?.value), icon: <DatabaseIcon /> },
        { item: uptime, icon: <TimeIcon /> },
    ];

    const externalUrl = (item: ServerInfoEntry) => {
        if (item.key === "programInfo") return "https://www.zrlog.com/changelog/?ref=systemInfo";
        if (item.key === "runtime")
            return data.nativeImageMode ? "https://www.graalvm.org" : "https://www.oracle.com/java";
        if (item.key === "webServer") {
            if (item.value.toLowerCase().startsWith("simplewebserver/"))
                return "https://github.com/94fzb/simplewebserver";
            if (item.value.startsWith("Apache Tomcat/")) return "https://tomcat.apache.org/";
            if (item.value.startsWith("Lambda")) return "https://aws.amazon.com/lambda/";
        }
        return undefined;
    };

    const environmentIcon = (item: ServerInfoEntry) => {
        if (item.key === "programInfo") return <ZrLogOutlined size={18} />;
        if (item.key === "runtime") {
            return (
                <>
                    {data.dockerMode && <DockerOutlined />}
                    {data.nativeImageMode ? (
                        <GraalVmOutlined size={18} />
                    ) : (
                        <RiJavaLine style={{ width: 18, height: 18 }} />
                    )}
                </>
            );
        }
        if (item.key === "system") {
            if (item.value.startsWith("Linux")) return <LinuxOutlined />;
            if (item.value.startsWith("Darwin") || item.value.startsWith("Mac")) return <AppleOutlined />;
            return <WindowsOutlined />;
        }
        return null;
    };

    const details = (items: ServerInfoEntry[]) => (
        <Descriptions
            size="small"
            column={{ xs: 1, sm: 1, md: 1, xl: 2 }}
            styles={{ label: { whiteSpace: "normal" }, content: { minWidth: 0, overflowWrap: "anywhere" } }}
            items={items.map((item) => {
                const href = externalUrl(item);
                const icon = environmentIcon(item);
                return {
                    key: item.key,
                    label: item.name,
                    span: item.key === "runPath" || item.key === "cpuInfo" ? 2 : 1,
                    children: (
                        <div style={{ display: "flex", alignItems: "baseline", gap: token.marginXS, minWidth: 0 }}>
                            {icon && (
                                <span
                                    aria-hidden="true"
                                    style={{ display: "inline-flex", gap: token.marginXXS, flexShrink: 0 }}
                                >
                                    {icon}
                                </span>
                            )}
                            {href ? (
                                <Typography.Link href={href} target="_blank" rel="noopener noreferrer">
                                    {item.value}
                                </Typography.Link>
                            ) : (
                                <Typography.Text style={{ overflowWrap: "anywhere" }}>{item.value}</Typography.Text>
                            )}
                        </div>
                    ),
                };
            })}
        />
    );

    const loadValues = parseLoadValues(load?.value);
    const loadLabels = [text.loadAverage.oneMinute, text.loadAverage.fiveMinutes, text.loadAverage.fifteenMinutes];

    return (
        <Flex vertical gap={token.marginLG} style={{ paddingBottom: token.paddingLG }}>
            {metrics.some(({ item }) => item) && (
                <SettingsSection title={text.overview}>
                    <div
                        style={{
                            display: "grid",
                            gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 180px), 1fr))",
                            gap: token.marginSM,
                        }}
                    >
                        {metrics.map(
                            ({ item, value, percent, icon }) =>
                                item && (
                                    <dl
                                        key={item.key}
                                        style={{
                                            margin: 0,
                                            padding: token.padding,
                                            border,
                                            borderRadius: token.borderRadiusLG,
                                            minWidth: 0,
                                            background: token.colorFillQuaternary,
                                        }}
                                    >
                                        <dt
                                            style={{
                                                display: "flex",
                                                alignItems: "center",
                                                gap: token.marginXS,
                                                color: token.colorTextSecondary,
                                            }}
                                        >
                                            <span
                                                aria-hidden="true"
                                                style={{ display: "inline-flex", color: token.colorPrimary }}
                                            >
                                                {icon}
                                            </span>
                                            {item.name}
                                        </dt>
                                        <dd
                                            style={{
                                                margin: `${token.marginSM}px 0 0`,
                                                fontSize: token.fontSizeHeading4,
                                                fontWeight: token.fontWeightStrong,
                                                lineHeight: token.lineHeightHeading4,
                                                color: token.colorTextHeading,
                                                overflowWrap: "anywhere",
                                                fontVariantNumeric: "tabular-nums",
                                            }}
                                        >
                                            {value || item.value}
                                            {percent !== undefined && (
                                                <div
                                                    role="meter"
                                                    aria-label={item.name}
                                                    aria-valuemin={0}
                                                    aria-valuemax={100}
                                                    aria-valuenow={percent}
                                                    aria-valuetext={formatPercent(percent)}
                                                    style={{
                                                        marginTop: token.marginSM,
                                                        height: token.sizeXXS,
                                                        borderRadius: token.borderRadiusSM,
                                                        background: token.colorFillSecondary,
                                                        overflow: "hidden",
                                                    }}
                                                >
                                                    <div
                                                        style={{
                                                            width: `${percent}%`,
                                                            height: "100%",
                                                            background:
                                                                percent >= 85
                                                                    ? token.colorError
                                                                    : percent >= 65
                                                                    ? token.colorWarning
                                                                    : token.colorPrimary,
                                                        }}
                                                    />
                                                </div>
                                            )}
                                        </dd>
                                    </dl>
                                )
                        )}
                    </div>
                </SettingsSection>
            )}
            <SettingsSection title={text.applicationRuntime} divided>
                {details(getItemsByKeys(itemMap, APPLICATION_KEYS))}
            </SettingsSection>
            <SettingsSection title={text.systemConfiguration} divided>
                {details(getItemsByKeys(itemMap, CONFIGURATION_KEYS))}
            </SettingsSection>
            <SettingsSection title={text.resourceStatus} divided>
                <Flex vertical gap={token.margin}>
                    {load && (
                        <div>
                            <Typography.Text type="secondary">{load.name}</Typography.Text>
                            {loadValues.length ? (
                                <div
                                    style={{
                                        display: "grid",
                                        gridTemplateColumns: "repeat(3, minmax(0, 1fr))",
                                        gap: token.marginSM,
                                        marginTop: token.marginXS,
                                    }}
                                >
                                    {loadValues.map((value, index) => (
                                        <dl key={loadLabels[index]} style={{ margin: 0 }}>
                                            <dt
                                                style={{
                                                    color: token.colorTextSecondary,
                                                    fontSize: token.fontSizeSM,
                                                }}
                                            >
                                                {loadLabels[index]}
                                            </dt>
                                            <dd
                                                style={{
                                                    margin: `${token.marginXXS}px 0 0`,
                                                    fontSize: token.fontSizeLG,
                                                    fontVariantNumeric: "tabular-nums",
                                                }}
                                            >
                                                {value.toFixed(2)}
                                            </dd>
                                        </dl>
                                    ))}
                                </div>
                            ) : (
                                <Typography.Paragraph style={{ marginTop: token.marginXS }}>
                                    {load.value}
                                </Typography.Paragraph>
                            )}
                        </div>
                    )}
                    {details(getItemsByKeys(itemMap, RESOURCE_KEYS))}
                </Flex>
            </SettingsSection>
        </Flex>
    );
};

export default ServerInfo;
