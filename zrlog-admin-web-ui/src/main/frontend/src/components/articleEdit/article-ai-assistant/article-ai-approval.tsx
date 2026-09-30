import { Alert, Button, Collapse, Space, Typography } from "antd";
import { useTheme } from "antd-style";
import { getRes } from "../../../utils/constants";
import type { ChatRun } from "./use-article-chat";

type Props = {
    run: ChatRun;
    disabled: boolean;
    onDecide: (decision: "approve" | "reject") => void;
    onRefresh: () => void;
};

export default function ArticleAiApproval({ run, disabled, onDecide, onRefresh }: Props) {
    const res = getRes().articleEdit.approval;
    const theme = useTheme();
    const approval = run.approval;
    const expired =
        run.status === "expired" || (run.status === "awaiting_approval" && (approval?.expiresAt || 0) <= Date.now());
    const waiting = run.status === "awaiting_approval" && !expired;
    const running = run.status === "running" || run.status === "executing";
    const title = waiting
        ? res.waiting
        : expired
        ? res.expired
        : running
        ? res.running
        : run.status === "uncertain"
        ? res.uncertain
        : res.failed;
    const tools = new Map(
        Object.entries({
            create_article: res.create,
            update_article: res.update,
            publish_article: res.publish,
            upload_attachment: res.upload,
        })
    );
    const fields = new Map(Object.entries(res.fields));
    const errorMessages = new Map(Object.entries(getRes().articleEdit.knowledge));
    const format = (field: string, value: string) => {
        if (field === "status") {
            const statuses = getRes().article.status;
            if (value === "draft") return statuses.draft;
            if (value === "private") return statuses.private;
            if (value === "published") return statuses.published;
        }
        if (field === "canComment" || field === "recommended") {
            if (value === "true" || value === "1") return res.yes;
            if (value === "false" || value === "0") return res.no;
        }
        return value || res.empty;
    };
    return (
        <Alert
            type={run.status === "failed" || run.status === "uncertain" ? "error" : "info"}
            title={title}
            description={
                <Space orientation="vertical" style={{ width: "100%", minWidth: 0 }}>
                    {run.status === "failed" && run.error && (
                        <Typography.Text>
                            {errorMessages.get(run.error) || getRes().articleEdit.knowledge.requestFailed}
                        </Typography.Text>
                    )}
                    {approval && (
                        <>
                            <Typography.Text strong style={{ overflowWrap: "anywhere" }}>
                                {tools.get(approval.tool)}
                                {approval.title ? ` · ${approval.title}` : ""}
                            </Typography.Text>
                            {approval.publicImpact && (
                                <Typography.Text type="warning">{res.publicImpact}</Typography.Text>
                            )}
                            {!!approval.changes.length && (
                                <Collapse
                                    size="small"
                                    items={[
                                        {
                                            key: "changes",
                                            label: res.preview,
                                            children: (
                                                <Space orientation="vertical" style={{ width: "100%" }}>
                                                    {approval.changes.map((change) => (
                                                        <div key={change.field}>
                                                            <Typography.Text strong>
                                                                {fields.get(change.field) || res.field}
                                                            </Typography.Text>
                                                            {[
                                                                { label: res.before, value: change.before },
                                                                { label: res.after, value: change.after },
                                                            ].map((item) => (
                                                                <div key={item.label}>
                                                                    <Typography.Text type="secondary">
                                                                        {item.label}
                                                                    </Typography.Text>
                                                                    <div
                                                                        style={{
                                                                            whiteSpace: "pre-wrap",
                                                                            overflowWrap: "anywhere",
                                                                            maxHeight: 200,
                                                                            overflow: "auto",
                                                                            padding: theme.paddingXS,
                                                                            background: theme.colorFillQuaternary,
                                                                            borderRadius: theme.borderRadiusSM,
                                                                        }}
                                                                    >
                                                                        {format(change.field, item.value)}
                                                                    </div>
                                                                </div>
                                                            ))}
                                                            {change.truncated && (
                                                                <Typography.Text type="secondary">
                                                                    {res.truncated}
                                                                </Typography.Text>
                                                            )}
                                                        </div>
                                                    ))}
                                                </Space>
                                            ),
                                        },
                                    ]}
                                />
                            )}
                        </>
                    )}
                    {waiting && (
                        <Space wrap>
                            <Button type="primary" disabled={disabled} onClick={() => onDecide("approve")}>
                                {res.approve}
                            </Button>
                            <Button disabled={disabled} onClick={() => onDecide("reject")}>
                                {res.reject}
                            </Button>
                        </Space>
                    )}
                    {running && (
                        <Button disabled={disabled} onClick={onRefresh}>
                            {res.refresh}
                        </Button>
                    )}
                </Space>
            }
        />
    );
}
