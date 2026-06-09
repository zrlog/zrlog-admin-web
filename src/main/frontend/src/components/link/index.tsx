import BaseTable, { PageDataSource } from "../../common/BaseTable";
import { getLabelValueSeparator, getRes } from "../../utils/constants";
import { Button, Grid, Space, Typography, theme } from "antd";
import { EditOutlined } from "@ant-design/icons";
import CreateOrEditLink from "./create_or_edit_link";

const { Text } = Typography;

const BLink = ({ data, offline }: { data: PageDataSource; offline: boolean }) => {
    const screens = Grid.useBreakpoint();
    const compactLinkTable = screens.md !== true;
    const { token } = theme.useToken();
    const surface = {
        linkText: {
            wordBreak: "break-all" as const,
        },
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
        iconText: {
            wordBreak: "break-all" as const,
        },
        addButton: {
            marginBottom: token.marginSM,
        },
        editIcon: {
            color: token.colorPrimary,
        },
    };

    const renderLink = (url: string, record: Record<string, any>, compact: boolean) => {
        const link = (
            <a style={surface.linkText} rel="noopener noreferrer" target={"_blank"} href={url}>
                {url}
            </a>
        );
        if (!compact) {
            return link;
        }
        return (
            <div style={surface.compactWrap}>
                {link}
                <Space size={[surface.compactMetaSpace.h, surface.compactMetaSpace.v]} wrap>
                    {record.linkName ? (
                        <Text>
                            {getRes().link.name}
                            {getLabelValueSeparator()}
                            <span dangerouslySetInnerHTML={{ __html: record.linkName }} />
                        </Text>
                    ) : null}
                    {record.alt ? (
                        <Text type="secondary">
                            {getRes().introduction}
                            {getLabelValueSeparator()}
                            {record.alt}
                        </Text>
                    ) : null}
                    {record.icon ? (
                        <Text type="secondary" style={surface.iconText}>
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
        if (compactLinkTable) {
            return [
                {
                    title: getRes().link.title,
                    dataIndex: "url",
                    key: "url",
                    width: 280,
                    render: (url: string, record: Record<string, any>) => renderLink(url, record, true),
                },
            ];
        }

        return [
            {
                title: getRes().link.title,
                dataIndex: "url",
                key: "url",
                width: 140,
                render: (url: string, record: Record<string, any>) => renderLink(url, record, false),
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
                actionColumnWidth={compactLinkTable ? 88 : undefined}
                addBtnRender={(addSuccessCall) => {
                    return (
                        <CreateOrEditLink
                            record={{ id: 0, url: "", linkName: "" }}
                            offline={offline}
                            editSuccessCall={addSuccessCall}
                        >
                            <Button type="primary" disabled={offline} style={surface.addButton}>
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
                            icon={<EditOutlined style={surface.editIcon} />}
                        />
                    </CreateOrEditLink>
                )}
                deleteApi={"/api/admin/link/delete"}
            />
        </>
    );
};

export default BLink;
