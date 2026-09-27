import { getLabelValueSeparator, getRes } from "../../utils/constants";
import BaseTable, { PageDataSource } from "../../common/BaseTable";
import { EditOutlined } from "@ant-design/icons";
import { Button, Grid, Space, Tag, Typography, theme } from "antd";
import CreateOrEditType from "./create_or_edit_type";

const { Text } = Typography;

const Type = ({ data, offline }: { data: PageDataSource; offline: boolean }) => {
    const screens = Grid.useBreakpoint();
    const compactTypeTable = screens.md !== true;
    const { token } = theme.useToken();
    const surface = {
        compactWrap: {
            display: "flex",
            flexDirection: "column" as const,
            gap: token.marginXXS,
            minWidth: 0,
        },
        compactMetaSpace: {
            h: token.marginSM,
            v: token.marginXXS,
        },
        compactTypeTag: {
            marginInlineEnd: 0,
        },
        addButton: {
            marginBottom: token.marginSM,
        },
        editIcon: {
            color: token.colorPrimary,
        },
    };

    const renderTypeName = (name: string, record: Record<string, any>, compact: boolean) => {
        const title = (
            <a rel="noopener noreferrer" target={"_blank"} href={record.url}>
                {name}
            </a>
        );
        if (!compact) {
            return title;
        }
        return (
            <div style={surface.compactWrap}>
                {title}
                <Space size={[surface.compactMetaSpace.h, surface.compactMetaSpace.v]} wrap>
                    {record.alias ? <Tag style={surface.compactTypeTag}>{record.alias}</Tag> : null}
                    <Text type="secondary">
                        {getRes().articleType.size}
                        {getLabelValueSeparator()}
                        {record.amount}
                    </Text>
                    {record.remark ? (
                        <Text type="secondary">
                            {getRes().introduction}
                            {getLabelValueSeparator()}
                            <span dangerouslySetInnerHTML={{ __html: record.remark }} />
                        </Text>
                    ) : null}
                </Space>
            </div>
        );
    };

    const getArticleCount = (record: Record<string, any>) => {
        const amount = Number(record.amount ?? record.typeamount ?? 0);
        return Number.isFinite(amount) ? amount : 0;
    };

    const getDeleteDisabledTip = (record: Record<string, any>) => {
        const amount = getArticleCount(record);
        if (amount <= 0) {
            return undefined;
        }
        return getRes().articleType.deleteBlocked.replace("{count}", `${amount}`);
    };

    const getColumns = () => {
        if (compactTypeTable) {
            return [
                {
                    title: getRes().articleType.title,
                    dataIndex: "typeName",
                    key: "typeName",
                    width: 280,
                    render: (e: string, r: Record<string, any>) => renderTypeName(e, r, true),
                },
            ];
        }

        return [
            {
                title: getRes().articleType.title,
                dataIndex: "typeName",
                key: "typeName",
                width: 240,
                render: (e: string, r: Record<string, any>) => renderTypeName(e, r, false),
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
                actionColumnWidth={compactTypeTable ? 88 : undefined}
                addBtnRender={(addSuccessCall) => {
                    return (
                        <CreateOrEditType
                            record={{ id: 0, typeName: "", alias: "" }}
                            offline={offline}
                            editSuccessCall={addSuccessCall}
                        >
                            <Button type="primary" disabled={offline} style={surface.addButton}>
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
                            icon={<EditOutlined style={surface.editIcon} />}
                        />
                    </CreateOrEditType>
                )}
                deleteDisabledTip={getDeleteDisabledTip}
                datasource={data}
                deleteApi={"/api/admin/type/delete"}
            />
        </>
    );
};

export default Type;
