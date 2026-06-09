import { useAxiosBaseInstance } from "../base/AppBase";
import { useEffect } from "react";
import { getLabelValueSeparator, getRealRouteUrl, getRes } from "../utils/constants";
import BaseTable, { PageDataSource } from "../common/BaseTable";
import TextArea from "antd/es/input/TextArea";
import { removeBackgroundTaskByKey } from "../utils/background-task-store";
import { Grid, Space, Typography } from "antd";
import { Link } from "react-router-dom";

const { Text } = Typography;

const Comment = ({ data, offline }: { data: PageDataSource; offline: boolean }) => {
    const axiosInstance = useAxiosBaseInstance();
    const screens = Grid.useBreakpoint();
    const compactCommentTable = screens.md !== true;

    useEffect(() => {
        if (offline) {
            return;
        }
        axiosInstance
            .post("/api/admin/comment/readAll")
            .then(() => {
                removeBackgroundTaskByKey("server.comment.unread");
            })
            .catch(() => undefined);
    }, [axiosInstance, offline]);

    const renderArticleLink = (logId?: number) => {
        if (!logId || logId <= 0) {
            return null;
        }
        return <Link to={getRealRouteUrl(`/article-edit?id=${logId}`)}>{`${getRes().comment.article} #${logId}`}</Link>;
    };

    const renderCommentContent = (text: string, record: any, compact: boolean) => {
        const commentContent = (
            <TextArea
                autoSize={{ minRows: 1, maxRows: 6 }}
                variant="borderless"
                style={{ minWidth: 0 }}
                readOnly={true}
                value={text}
            />
        );
        if (!compact) {
            return commentContent;
        }
        return (
            <div style={{ display: "flex", flexDirection: "column", gap: 8, minWidth: 0 }}>
                {commentContent}
                <Space size={[8, 4]} wrap>
                    {record.userName ? <Text strong>{record.userName}</Text> : null}
                    {renderArticleLink(record.logId)}
                    {record.userMail ? <Text type="secondary">{record.userMail}</Text> : null}
                    {record.userHome ? (
                        <Text type="secondary" style={{ wordBreak: "break-all" }}>
                            {getRes().comment.userHome}
                            {getLabelValueSeparator()}
                            {record.userHome}
                        </Text>
                    ) : null}
                    {record.userIp ? (
                        <Text type="secondary">
                            {getRes().comment.ip}
                            {getLabelValueSeparator()}
                            {record.userIp}
                        </Text>
                    ) : null}
                    {record.commTime ? (
                        <Text type="secondary">
                            {getRes().comment.date}
                            {getLabelValueSeparator()}
                            {record.commTime}
                        </Text>
                    ) : null}
                </Space>
            </div>
        );
    };

    const getColumns = () => {
        if (compactCommentTable) {
            return [
                {
                    title: getRes().comment.content,
                    dataIndex: "userComment",
                    key: "userComment",
                    width: 280,
                    render: (text: string, record: any) => renderCommentContent(text, record, true),
                },
            ];
        }

        return [
            {
                title: getRes().comment.content,
                dataIndex: "userComment",
                key: "userComment",
                width: 600,
                render: (text: string, record: any) => renderCommentContent(text, record, false),
            },
            {
                title: getRes().comment.article,
                dataIndex: "logId",
                key: "logId",
                width: 120,
                render: (logId: number) => renderArticleLink(logId),
            },
            {
                title: getRes().comment.nickName,
                dataIndex: "userName",
                key: "userName",
            },
            {
                title: getRes().comment.userHome,
                key: "userHome",
                dataIndex: "userHome",
            },
            {
                title: getRes().comment.ip,
                key: "userIp",
                dataIndex: "userIp",
            },
            {
                title: getRes().user.email,
                key: "userMail",
                dataIndex: "userMail",
            },
            {
                title: getRes().comment.date,
                key: "commTime",
                dataIndex: "commTime",
            },
        ];
    };

    const getDeleteApiUri = () => {
        return "/api/admin/comment/delete";
    };

    return (
        <>
            <BaseTable
                defaultPageSize={10}
                offline={offline}
                datasource={data}
                columns={getColumns()}
                actionColumnWidth={compactCommentTable ? 72 : undefined}
                hideId={compactCommentTable}
                deleteApi={getDeleteApiUri()}
            />
        </>
    );
};

export default Comment;
