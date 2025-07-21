import { getRes } from "../../utils/constants";
import BaseTable, { PageDataSource } from "../../common/BaseTable";
import { getAppState } from "../../base/ConfigProviderApp";
import { EditOutlined } from "@ant-design/icons";
import { Button } from "antd";
import CreateOrEditType from "./create_or_edit_type";

const Type = ({ data, offline }: { data: PageDataSource; offline: boolean }) => {
    const getColumns = () => {
        return [
            {
                title: getRes().articleType.title,
                dataIndex: "typeName",
                key: "typeName",
                width: 240,
                render: (e: string, r: Record<string, string>) => {
                    return (
                        <a rel="noopener noreferrer" target={"_blank"} href={r.url}>
                            {e}
                        </a>
                    );
                },
            },
            {
                title: getRes().alias,
                dataIndex: "alias",
                key: "alias",
                width: 120,
            },
            {
                title: getRes().introduction,
                key: "remark",
                dataIndex: "remark",
                width: 240,
                render: (e: string) => {
                    return <span dangerouslySetInnerHTML={{ __html: e }} />;
                },
            },
            {
                title: getRes().articleType.size,
                dataIndex: "amount",
                key: "amount",
                width: 80,
            },
        ];
    };

    return (
        <>
            <BaseTable
                defaultPageSize={10}
                offline={offline}
                hideId={true}
                columns={getColumns()}
                addBtnRender={(addSuccessCall) => {
                    return (
                        <CreateOrEditType
                            record={{ id: 0, typeName: "", alias: "" }}
                            offline={offline}
                            editSuccessCall={addSuccessCall}
                        >
                            <Button type="primary" disabled={offline} style={{ marginBottom: 8 }}>
                                {getRes().add}
                            </Button>
                        </CreateOrEditType>
                    );
                }}
                editBtnRender={(_id, record, editSuccessCall) => (
                    <CreateOrEditType offline={offline} record={record} editSuccessCall={editSuccessCall}>
                        <Button
                            type="text"
                            size="small"
                            title={getRes().edit}
                            icon={<EditOutlined style={{ color: getAppState().colorPrimary }} />}
                        />
                    </CreateOrEditType>
                )}
                datasource={data}
                deleteApi={"/api/admin/type/delete"}
            />
        </>
    );
};

export default Type;
