import { Button, Empty, Flex, Grid, List, Typography } from "antd";
import Table from "@zrlog/ui/antd/Table";
import { useTheme } from "antd-style";
import type { ReactNode } from "react";
import { getRes } from "../../utils/constants";
import { formatDateTime } from "../../utils/date-time";
import type { Run } from "./types";

export default function RunHistory({
    runs,
    onDetail,
    renderStatus,
}: {
    runs: Run[];
    onDetail: (run: Run) => void;
    renderStatus: (status: string) => ReactNode;
}) {
    const res = getRes().automations;
    const screens = Grid.useBreakpoint();
    const theme = useTheme();
    const detail = (run: Run) => (
        <Button type="link" onClick={() => onDetail(run)} style={{ paddingInline: 0 }}>
            {res.detail}
        </Button>
    );
    const emptyText = <Empty description={res.emptyRuns} />;

    if (screens.lg) {
        return (
            <Table<Run>
                rowKey="id"
                dataSource={runs}
                pagination={false}
                tableLayout="fixed"
                locale={{ emptyText }}
                columns={[
                    {
                        title: res.name,
                        dataIndex: "taskName",
                        width: "24%",
                        render: (name: string) => (
                            <Typography.Paragraph
                                strong
                                ellipsis={{ rows: 2, tooltip: name }}
                                style={{ margin: 0, whiteSpace: "normal", overflowWrap: "anywhere" }}
                            >
                                {name}
                            </Typography.Paragraph>
                        ),
                    },
                    { title: res.status, dataIndex: "status", width: 140, render: renderStatus },
                    {
                        title: res.started,
                        dataIndex: "startedAt",
                        width: 180,
                        render: (value: number) => (
                            <Typography.Text type="secondary">{formatDateTime(value)}</Typography.Text>
                        ),
                    },
                    {
                        title: res.summary,
                        dataIndex: "summary",
                        render: (summary?: string) => (
                            <Typography.Paragraph
                                ellipsis={{ rows: 2, tooltip: summary }}
                                style={{ margin: 0, whiteSpace: "normal", overflowWrap: "anywhere" }}
                            >
                                {summary || "—"}
                            </Typography.Paragraph>
                        ),
                    },
                    { key: "detail", width: 112, align: "end", render: (_, run) => detail(run) },
                ]}
            />
        );
    }

    return (
        <List
            rowKey="id"
            dataSource={runs}
            locale={{ emptyText }}
            renderItem={(run) => (
                <List.Item>
                    <Flex vertical gap={theme.marginXXS} style={{ width: "100%", minWidth: 0 }}>
                        <Flex justify="space-between" align="start" gap={theme.marginXS}>
                            <Typography.Paragraph
                                strong
                                ellipsis={{ rows: 2 }}
                                style={{ margin: 0, minWidth: 0, overflowWrap: "anywhere" }}
                            >
                                {run.taskName}
                            </Typography.Paragraph>
                            <span style={{ flexShrink: 0 }}>{renderStatus(run.status)}</span>
                        </Flex>
                        {run.summary && (
                            <Typography.Paragraph
                                ellipsis={{ rows: 2 }}
                                style={{ margin: 0, overflowWrap: "anywhere" }}
                            >
                                {run.summary}
                            </Typography.Paragraph>
                        )}
                        <Flex justify="space-between" align="center" gap={theme.marginXS} wrap>
                            <Typography.Text type="secondary">{formatDateTime(run.startedAt)}</Typography.Text>
                            {detail(run)}
                        </Flex>
                    </Flex>
                </List.Item>
            )}
        />
    );
}
