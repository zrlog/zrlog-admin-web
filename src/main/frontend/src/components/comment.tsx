import { useAxiosBaseInstance } from "../base/AppBase";
import { useEffect } from "react";
import { getRes } from "../utils/constants";
import BaseTable, { PageDataSource } from "../common/BaseTable";
import TextArea from "antd/es/input/TextArea";
import { removeBackgroundTaskByKey } from "../utils/background-task-store";

const Comment = ({ data, offline }: { data: PageDataSource; offline: boolean }) => {
    const axiosInstance = useAxiosBaseInstance();

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

    const getColumns = () => {
        return [
            {
                title: getRes().comment.content,
                dataIndex: "userComment",
                key: "userComment",
                width: 600,
                render: (text: string) => (
                    <TextArea
                        autoSize={{ minRows: 1, maxRows: 6 }}
                        style={{ border: "none", minWidth: 300 }}
                        readOnly={true}
                        value={text}
                    />
                ),
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
                title: "IP",
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
                deleteApi={getDeleteApiUri()}
            />
        </>
    );
};

export default Comment;
