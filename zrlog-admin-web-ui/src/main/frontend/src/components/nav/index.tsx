import BaseTable, { PageDataSource } from "../../common/BaseTable";
import { getLabelValueSeparator, getRes } from "../../utils/constants";
import CreateOrEditNav from "./create_or_edit_nav";
import { EditOutlined } from "@ant-design/icons";
import { getAppState } from "../../base/ConfigProviderApp";
import { Button, Grid, Space, Typography } from "antd";

const { Text } = Typography;

const Nav = ({ data, offline }: { data: PageDataSource; offline: boolean }) => {
    const screens = Grid.useBreakpoint();
    const compactNavTable = screens.md !== true;

    const renderNavUrl = (url: string, record: Record<string, any>, compact: boolean) => {
        const link = (
            <a
                style={{ display: "inline", wordBreak: "break-all" }}
                rel="noopener noreferrer"
                target={"_blank"}
                href={record["jumpUrl"]}
            >
                {url}
            </a>
        );
        if (!compact) {
            return link;
        }
        return (
            <div style={{ display: "flex", flexDirection: "column", gap: 6, minWidth: 0 }}>
                {link}
                <Space size={[8, 4]} wrap>
                    {record.navName ? (
                        <Text>
                            {getRes().nav.name}
                            {getLabelValueSeparator()}
                            {record.navName}
                        </Text>
                    ) : null}
                    {record.icon ? (
                        <Text type="secondary" style={{ wordBreak: "break-all" }}>
                            {getRes().icon}
                            {getLabelValueSeparator()}
                            {record.icon}
                        </Text>
                    ) : null}
                    <Text type="secondary">
                        {getRes().order}
                        {getLabelValueSeparator()}
                        {record.sort}
                    </Text>
                </Space>
            </div>
        );
    };

    const getColumns = () => {
        if (compactNavTable) {
            return [
                {
                    title: getRes().nav.title,
                    dataIndex: "url",
                    width: 280,
                    key: "url",
                    render: (url: string, record: Record<string, any>) => renderNavUrl(url, record, true),
                },
            ];
        }

        return [
            {
                title: getRes().nav.title,
                dataIndex: "url",
                width: 140,
                key: "url",
                render: (url: string, record: Record<string, any>) => renderNavUrl(url, record, false),
            },
            {
                title: getRes().nav.name,
                dataIndex: "navName",
                key: "navName",
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
                actionColumnWidth={compactNavTable ? 88 : undefined}
                addBtnRender={(addSuccessCall) => {
                    return (
                        <CreateOrEditNav
                            record={{ id: -1, navName: "", url: "", icon: "" }}
                            offline={offline}
                            editSuccessCall={addSuccessCall}
                        >
                            <Button type="primary" disabled={offline} style={{ marginBottom: 8 }}>
                                {getRes().add}
                            </Button>
                        </CreateOrEditNav>
                    );
                }}
                editBtnRender={(_id, record, editSuccessCall) => (
                    <CreateOrEditNav offline={offline} record={record} editSuccessCall={editSuccessCall}>
                        <Button
                            type="text"
                            size="small"
                            title={getRes().edit}
                            icon={<EditOutlined style={{ color: getAppState().colorPrimary }} />}
                        />
                    </CreateOrEditNav>
                )}
                datasource={data}
                deleteApi={"/api/admin/nav/delete"}
            />
        </>
    );
};

export default Nav;
