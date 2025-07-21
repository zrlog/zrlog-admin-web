import BaseTable, { PageDataSource } from "../../common/BaseTable";
import { getRes } from "../../utils/constants";
import { Button } from "antd";
import { EditOutlined } from "@ant-design/icons";
import { getAppState } from "../../base/ConfigProviderApp";
import CreateOrEditLink from "./create_or_edit_link";

const BLink = ({ data, offline }: { data: PageDataSource; offline: boolean }) => {
    const getColumns = () => {
        return [
            {
                title: getRes().link.title,
                dataIndex: "url",
                key: "url",
                width: 140,
                render: (url: string) => (
                    <a style={{ display: "inline" }} rel="noopener noreferrer" target={"_blank"} href={url}>
                        {url}
                    </a>
                ),
            },
            {
                title: getRes().link.name,
                key: "linkName",
                dataIndex: "linkName",
                width: 140,
                render: (e: string) => {
                    return <span dangerouslySetInnerHTML={{ __html: e }} />;
                },
            },
            {
                title: getRes().introduction,
                key: "alt",
                dataIndex: "alt",
                width: 140,
            },
            {
                title: getRes().icon,
                dataIndex: "icon",
                key: "icon",
                width: 240,
            },
            {
                title: getRes().order,
                key: "sort",
                dataIndex: "sort",
                width: 60,
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
                        <CreateOrEditLink
                            record={{ id: 0, url: "", linkName: "" }}
                            offline={offline}
                            editSuccessCall={addSuccessCall}
                        >
                            <Button type="primary" disabled={offline} style={{ marginBottom: 8 }}>
                                {getRes().add}
                            </Button>
                        </CreateOrEditLink>
                    );
                }}
                datasource={data}
                editBtnRender={(_id, record, editSuccessCall) => (
                    <CreateOrEditLink offline={offline} record={record} editSuccessCall={editSuccessCall}>
                        <Button
                            type="text"
                            size="small"
                            title={getRes().edit}
                            icon={<EditOutlined style={{ color: getAppState().colorPrimary }} />}
                        />
                    </CreateOrEditLink>
                )}
                deleteApi={"/api/admin/link/delete"}
            />
        </>
    );
};

export default BLink;
