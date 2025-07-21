import { FunctionComponent } from "react";
import { AdminCommonProps } from "../type";
import { Button, message, Table } from "antd";
import { useAxiosBaseInstance } from "../base/AppBase";
import Divider from "antd/es/divider";
import { getRes } from "../utils/constants";

type Lock = {
    name: string;
    remark: string;
};

type DevResponse = {
    locks: Lock[];
};

const Dev: FunctionComponent<AdminCommonProps<DevResponse>> = ({ data }) => {
    const axiosInstance = useAxiosBaseInstance();

    const [messageApi, messageContextHolder] = message.useMessage({
        maxCount: 3,
    });

    return (
        <>
            {messageContextHolder}
            <Button
                type={"primary"}
                onClick={async () => {
                    const { data } = await axiosInstance.get("/api/admin/dev/releaseLocks");
                    if (data.error) {
                        messageApi.error(data.message);
                    } else {
                        messageApi.success(data.message);
                    }
                }}
            >
                Release All Lock
            </Button>
            <Divider />
            <Table
                columns={[
                    {
                        title: getRes().key,
                        dataIndex: "name",
                        key: "name",
                    },
                    {
                        title: getRes().value,
                        dataIndex: "remark",
                        key: "remark",
                    },
                ]}
                dataSource={data.locks}
            />
        </>
    );
};
export default Dev;
