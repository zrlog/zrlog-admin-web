import HistoryIcon from "@zrlog/ui/icons/history";
import { Card } from "antd";
import { getRealRouteUrl, getRes } from "../../utils/constants";
import { AdminAuditLogEntry } from "../../type";
import DashboardCardAction from "./DashboardCardAction";
import AuditTimeline from "../system/AuditTimeline";

const AuditTrail = ({ data }: { data?: { auditLogs?: AdminAuditLogEntry[]; loading?: boolean } }) => (
    <Card
        title={
            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <HistoryIcon />
                <span>{getRes().index.audit.label}</span>
            </div>
        }
        extra={
            <DashboardCardAction to={getRealRouteUrl("/system/audit")}>{getRes().index.audit.more}</DashboardCardAction>
        }
        bordered={false}
        className="dashboard-card"
        styles={{ body: { padding: 20 } }}
    >
        <AuditTimeline logs={(data?.auditLogs || []).slice(0, 3)} loading={data?.loading} />
    </Card>
);

export default AuditTrail;
