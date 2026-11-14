import { Alert, Button, Drawer, Pagination, Space, Spin, Typography } from "antd";
import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import { useAxiosBaseInstance } from "../../../base/AppBase";
import { getRes } from "../../../utils/constants";
import { getErrorMessage } from "../../../utils/error-message";
import { formatDateTime } from "../../../utils/date-time";
import type { ApiResponse } from "../../../type";
import type { Run, Runs } from "./types";

/** Diagnostic records stay available on demand without becoming the conversation timeline. */
export default function ExecutionRecordsDrawer({
    taskId,
    revision,
    onClose,
    onDetail,
    renderStatus,
}: {
    taskId: string;
    revision: number;
    onClose: () => void;
    onDetail: (run: Run) => void;
    renderStatus: (status: string) => ReactNode;
}) {
    const api = useAxiosBaseInstance();
    const [page, setPage] = useState(1);
    const [data, setData] = useState<Runs>();
    const [error, setError] = useState<string>();
    const [retry, setRetry] = useState(0);
    useEffect(() => {
        const controller = new AbortController();
        setError(undefined);
        setData(undefined);
        const requestConfig = { params: { taskId, page, pageSize: 20 }, signal: controller.signal, showError: false };
        void api
            .get<ApiResponse<Runs>>("/api/admin/assistant/runs", requestConfig)
            .then(({ data }) => {
                if (controller.signal.aborted) return;
                if (data.error) throw new Error(data.message || getRes().error.unknown);
                if (data.data.items.length === 0 && page > 1) setPage(page - 1);
                else setData(data.data);
            })
            .catch((error) => {
                if (!controller.signal.aborted) setError(getErrorMessage(error, getRes().error.unknown));
            });
        return () => controller.abort();
    }, [api, taskId, page, retry, revision]);
    return (
        <Drawer
            title={getRes().assistant.executionRecords}
            open
            onClose={onClose}
            size={560}
            closable={{ placement: "end" }}
        >
            {error ? (
                <Alert
                    type="error"
                    title={error}
                    action={<Button onClick={() => setRetry((value) => value + 1)}>{getRes().common.retry}</Button>}
                />
            ) : !data ? (
                <Spin />
            ) : (
                <Space orientation="vertical" size="large" style={{ width: "100%" }}>
                    {data.items.length === 0 && (
                        <Typography.Text type="secondary">{getRes().assistant.noExecutionRecords}</Typography.Text>
                    )}
                    {data.items.map((run) => (
                        <div key={run.id}>
                            <Space wrap>
                                {renderStatus(run.status)}
                                <Typography.Text type="secondary">{formatDateTime(run.startedAt)}</Typography.Text>
                            </Space>
                            <Typography.Paragraph>{run.summary}</Typography.Paragraph>
                            <Button type="link" onClick={() => onDetail(run)}>
                                {getRes().assistant.executionDetails}
                            </Button>
                        </div>
                    ))}
                    {data.total > data.pageSize && (
                        <Pagination
                            current={data.page}
                            pageSize={data.pageSize}
                            total={data.total}
                            size="small"
                            showSizeChanger={false}
                            onChange={setPage}
                        />
                    )}
                </Space>
            )}
        </Drawer>
    );
}
