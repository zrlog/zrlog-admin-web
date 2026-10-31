import Tag from "@zrlog/ui/antd/Tag";
import EditIcon from "@zrlog/ui/icons/edit";
import HistoryIcon from "@zrlog/ui/icons/history";
import LoadingIcon from "@zrlog/ui/icons/loading";
import LoginIcon from "@zrlog/ui/icons/login";
import SettingsIcon from "@zrlog/ui/icons/settings";
import TimeAgo from "@zrlog/editor/dist/editor/TimeAgo";
import { Timeline, Typography } from "antd";
import { useTheme } from "antd-style";
import { Link } from "react-router-dom";
import { AdminAuditLogEntry } from "../../type";
import { getRealRouteUrl, getRes } from "../../utils/constants";
import { formatDateTime } from "../../utils/date-time";

export const auditSource = (log: AdminAuditLogEntry) =>
    log.source === "assistant"
        ? log.sourceLabel
        : [
              log.ip,
              log.os,
              log.source === "mcp" ? log.sourceLabel : log.browser,
              log.agentName && `${log.agentName}${log.agentVersion ? ` ${log.agentVersion}` : ""}`,
              log.source === "unknown" ? log.sourceLabel : undefined,
          ]
              .filter(Boolean)
              .join(" · ");

export default function AuditTimeline({
    logs,
    loading,
    fullDate = false,
    emptyText,
}: {
    logs: AdminAuditLogEntry[];
    loading?: boolean;
    fullDate?: boolean;
    emptyText?: string;
}) {
    const theme = useTheme();
    const text = getRes().system.audit;
    const icon = (type: string) => {
        switch (type?.toLowerCase()) {
            case "login":
                return <LoginIcon style={{ color: theme.colorInfo }} />;
            case "article":
                return <EditIcon style={{ color: theme.colorSuccess }} />;
            case "setting":
                return <SettingsIcon style={{ color: theme.colorPrimary }} />;
            default:
                return <HistoryIcon />;
        }
    };
    return (
        <Timeline pending={loading} pendingDot={<LoadingIcon />}>
            {logs.map((log, index) => (
                <Timeline.Item key={index} dot={icon(log.type)}>
                    <div style={{ display: "flex", flexWrap: "wrap", justifyContent: "space-between", gap: 8 }}>
                        <div style={{ flex: "1 1 200px", minWidth: 0, overflowWrap: "anywhere" }}>
                            <Typography.Text strong style={{ display: "block" }}>
                                {log.action}
                            </Typography.Text>
                            {log.content && (
                                <Typography.Text type="secondary" style={{ display: "block", fontSize: 12 }}>
                                    {log.content}
                                </Typography.Text>
                            )}
                            <div
                                style={{
                                    display: "flex",
                                    flexWrap: "wrap",
                                    alignItems: "baseline",
                                    gap: 8,
                                    fontSize: 12,
                                }}
                            >
                                <Typography.Text type="secondary" style={{ fontSize: "inherit" }}>
                                    {auditSource(log)}
                                </Typography.Text>
                                {log.assistantRunId && (
                                    <Link
                                        to={getRealRouteUrl(
                                            `/assistant?runId=${encodeURIComponent(log.assistantRunId)}`
                                        )}
                                    >
                                        {text.viewExecution}
                                    </Link>
                                )}
                                {log.crawler && <Tag color="gold">{text.crawler}</Tag>}
                            </div>
                        </div>
                        <Typography.Text type="secondary" style={{ fontSize: 12, whiteSpace: "nowrap" }}>
                            {fullDate ? formatDateTime(log.timestamp) : <TimeAgo timestamp={log.timestamp} />}
                        </Typography.Text>
                    </div>
                </Timeline.Item>
            ))}
            {logs.length === 0 && !loading && (
                <div style={{ textAlign: "center", padding: "40px 0" }}>
                    <Typography.Text type="secondary">{emptyText || text.empty}</Typography.Text>
                </div>
            )}
        </Timeline>
    );
}
