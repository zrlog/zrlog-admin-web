import { Button, Flex, Typography } from "antd";
import { useTheme } from "antd-style";
import type { ReactNode } from "react";
import { getRes } from "../../utils/constants";
import { formatDateTime } from "../../utils/date-time";
import TaskIcon from "./TaskIcon";
import type { Run } from "./types";

export default function TaskRunMessage({
    run,
    onDetail,
    renderStatus,
}: {
    run: Run;
    onDetail: (run: Run) => void;
    renderStatus: (status: string) => ReactNode;
}) {
    const res = getRes().assistant;
    const theme = useTheme();
    return (
        <article aria-label={`${run.taskName}: ${formatDateTime(run.startedAt)}`}>
            <Flex align="start" gap={theme.marginSM}>
                <TaskIcon type={run.taskType} />
                <Flex vertical gap={theme.marginXS} style={{ flex: 1, minWidth: 0 }}>
                    <Flex align="center" gap={theme.marginXS} wrap>
                        <Typography.Text strong>{run.taskName}</Typography.Text>
                        {renderStatus(run.status)}
                    </Flex>
                    <Typography.Text type="secondary">{formatDateTime(run.startedAt)}</Typography.Text>
                    {run.summary && (
                        <Typography.Paragraph style={{ margin: 0, whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>
                            {run.summary}
                        </Typography.Paragraph>
                    )}
                    <Button
                        type="link"
                        style={{ alignSelf: "flex-start", paddingInline: 0 }}
                        onClick={() => onDetail(run)}
                    >
                        {res.detail}
                    </Button>
                </Flex>
            </Flex>
        </article>
    );
}
