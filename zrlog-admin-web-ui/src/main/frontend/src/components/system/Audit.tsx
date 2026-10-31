import { Button, Flex, Pagination } from "antd";
import Input from "@zrlog/ui/antd/Input";
import RefreshIcon from "@zrlog/ui/icons/refresh";
import { useUiApp } from "@zrlog/ui/feedback";
import { useEffect, useState } from "react";
import { AdminAuditLogEntry } from "../../type";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getCsrData } from "../../api";
import { getRes } from "../../utils/constants";
import AdminSettingsLayout from "../common/AdminSettingsLayout";
import AuditTimeline, { auditSource } from "./AuditTimeline";

export default function Audit({
    data,
    offline = false,
    offlineData = false,
}: {
    data: AdminAuditLogEntry[];
    offline?: boolean;
    offlineData?: boolean;
}) {
    const [logs, setLogs] = useState(data);
    const [query, setQuery] = useState("");
    const [page, setPage] = useState(1);
    const [loading, setLoading] = useState(false);
    const axios = useAxiosBaseInstance();
    const { message } = useUiApp();
    const text = getRes().system.audit;
    useEffect(() => {
        setLogs(data);
        setPage(1);
    }, [data]);
    const refresh = async () => {
        setLoading(true);
        try {
            const result = await getCsrData("/system/audit", Date.now(), axios);
            if (result.error) throw new Error("Audit query failed");
            setLogs(result.data);
            setPage(1);
        } catch {
            message.error(text.refreshFailed);
        } finally {
            setLoading(false);
        }
    };
    const term = query.trim().toLocaleLowerCase();
    const filtered = (logs || []).filter((log) =>
        [log.action, log.content, auditSource(log)].some((value) => value?.toLocaleLowerCase().includes(term))
    );
    return (
        <AdminSettingsLayout
            activeKey="audit"
            extra={
                <Button icon={<RefreshIcon />} loading={loading} disabled={offline || offlineData} onClick={refresh}>
                    {text.refresh}
                </Button>
            }
        >
            <Flex vertical gap={24}>
                <Input
                    aria-label={text.search}
                    placeholder={text.search}
                    allowClear
                    value={query}
                    onChange={(event) => {
                        setQuery(event.target.value);
                        setPage(1);
                    }}
                />
                <AuditTimeline
                    logs={filtered.slice((page - 1) * 20, page * 20)}
                    fullDate
                    emptyText={term ? text.noResults : text.empty}
                />
                <Pagination
                    current={page}
                    pageSize={20}
                    total={filtered.length}
                    showSizeChanger={false}
                    hideOnSinglePage
                    onChange={setPage}
                />
            </Flex>
        </AdminSettingsLayout>
    );
}
