import { Collapse, Descriptions, Space, Typography } from "antd";
import Tag from "@zrlog/ui/antd/Tag";
import { Link } from "react-router-dom";
import { getRealRouteUrl, getRes } from "../../utils/constants";
import { formatDateTime } from "../../utils/date-time";
import type { ChildResult } from "./types";

/** Children belong to this execution; they never appear as independent assistant plans. */
export default function RunChildren({ children, nested = false }: { children?: ChildResult[]; nested?: boolean }) {
    const res = getRes().assistant;
    if (!children?.length) return null;
    const date = (value?: number) => (value ? formatDateTime(value) : "—");
    return (
        <section aria-label={res.childTasks} style={{ width: "100%" }}>
            {!nested && (
                <>
                    <Typography.Title level={5}>
                        {res.childTasks} ({children.length})
                    </Typography.Title>
                    <Typography.Paragraph type="secondary">{res.childTasksHelp}</Typography.Paragraph>
                </>
            )}
            <Collapse
                items={children.map((child, index) => ({
                    key: `${child.id}-${child.externalRunId ?? index}`,
                    label: (
                        <Space wrap style={{ minWidth: 0, overflowWrap: "anywhere" }}>
                            <Typography.Text>{child.name}</Typography.Text>
                            <Tag
                                color={
                                    child.status === "success"
                                        ? "success"
                                        : child.status === "failed"
                                        ? "error"
                                        : ["uncertain", "needs_attention"].includes(child.status)
                                        ? "warning"
                                        : undefined
                                }
                            >
                                {(res.statuses as Record<string, string>)[child.status] ?? child.status}
                            </Tag>
                        </Space>
                    ),
                    children: (
                        <Space orientation="vertical" style={{ width: "100%" }}>
                            {child.summary && (
                                <Typography.Paragraph style={{ margin: 0 }}>{child.summary}</Typography.Paragraph>
                            )}
                            {child.text && (
                                <Typography.Paragraph
                                    style={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere", margin: 0 }}
                                >
                                    {child.text}
                                </Typography.Paragraph>
                            )}
                            <Descriptions
                                column={1}
                                size="small"
                                items={[
                                    { key: "started", label: res.started, children: date(child.startedAt) },
                                    { key: "finished", label: res.finished, children: date(child.finishedAt) },
                                    ...(child.externalRunId
                                        ? [
                                              {
                                                  key: "reference",
                                                  label: res.runReference,
                                                  children: (
                                                      <Typography.Text copyable style={{ overflowWrap: "anywhere" }}>
                                                          {child.externalRunId}
                                                      </Typography.Text>
                                                  ),
                                              },
                                          ]
                                        : []),
                                ]}
                            />
                            {child.actionPath?.startsWith("/") && !child.actionPath.startsWith("//") && (
                                <Link to={getRealRouteUrl(child.actionPath)}>{child.actionLabel || res.detail}</Link>
                            )}
                            <RunChildren children={child.children} nested />
                        </Space>
                    ),
                }))}
            />
        </section>
    );
}
