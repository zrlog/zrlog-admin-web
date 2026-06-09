import { Button, Grid, message, PaginationProps, Space, Table, TableColumnsType } from "antd";
import { FunctionComponent, useEffect, useState } from "react";
import { mapToQueryString } from "../utils/helpers";
import Popconfirm from "antd/es/popconfirm";
import { cacheIgnoreReloadTime, getRealRouteUrl, getRes } from "../utils/constants";
import { DeleteOutlined } from "@ant-design/icons";
import { Link, useNavigate } from "react-router-dom";
import { useLocation } from "react-router";
import { SorterResult } from "antd/es/table/interface";
import { postRefreshCacheSse } from "../utils/sse-utils";

type BaseTableProps = {
    deleteApi: string;
    deleteSuccessCallback?: (id: number) => void;
    columns: TableColumnsType<any>;
    actionColumnWidth?: number;
    datasource?: PageDataSource;
    searchKey?: string;
    hideId?: boolean;
    offline: boolean;
    defaultPageSize: number;
    addBtnRender?: (addSuccessCall: () => void) => any;
    editBtnRender?: (id: number, record: any, editSuccessCall: () => void) => any;
    deleteDisabledTip?: (record: any) => string | undefined;
};

export type PageDataSource = {
    rows: [];
    page: number;
    key: string;
    sort: string[];
    size: number;
    defaultPageSize: number;
    totalElements: number;
};
export type ArticlePageDataSource = PageDataSource & {
    types: Record<string, any>[];
    article_thumbnail_status: boolean;
    status?: string;
    statusCounts?: {
        total: number;
        draft: number;
        privateCount: number;
        published: number;
    };
};

export type TableData = {
    tableLoaded: boolean;
    pagination: MyPagination;
    tablePagination?: PaginationProps;
    rows: [];
    query: string | undefined;
};

export type MyPagination = {
    page: number;
    size: number;
    key?: string;
    sort: string[];
};

const BaseTable: FunctionComponent<BaseTableProps> = ({
    deleteApi,
    editBtnRender,
    addBtnRender,
    columns,
    datasource,
    defaultPageSize,
    actionColumnWidth,
    searchKey,
    deleteSuccessCallback,
    hideId,
    offline,
    deleteDisabledTip,
}) => {
    const navigate = useNavigate();
    const location = useLocation();
    const screens = Grid.useBreakpoint();
    const pinnedColumns = screens.xl === true;
    const compactTable = screens.md !== true;

    const buildJumpUrl = (page: number, size: number, searchKey: string | undefined, sorter: string[]) => {
        return buildJumpUrlFull(page, size, searchKey, -1, sorter);
    };

    const buildJumpUrlFull = (page: number, size: number, searchKey: string | undefined, t: number, sort: string[]) => {
        const queryParam: Record<string, number | string | string[]> = {};
        if (page > 1) {
            queryParam.page = page;
        }
        //默认分页的， 1000000，为不分页时候返回的值
        if (size != defaultPageSize && size != 1000000) {
            queryParam.size = size;
        }
        if (searchKey && searchKey.trim().length > 0) {
            queryParam.key = searchKey.trim();
        }
        if (t > 0) {
            queryParam[cacheIgnoreReloadTime] = t;
        }
        if (sort.length > 0) {
            //暂时支持一个属性进行排序
            queryParam["sort"] = sort[0];
        }
        // 保留当前 URL 中的 status 参数
        const currentStatus = new URLSearchParams(location.search).get("status");
        if (currentStatus) {
            queryParam["status"] = currentStatus;
        }
        const queryStr = mapToQueryString(queryParam);
        if (queryStr.length === 0) {
            return getRealRouteUrl(location.pathname);
        }
        return getRealRouteUrl(location.pathname + "?" + queryStr);
    };

    const fetchData = (page: number, size: number, searchKey: string | undefined, sorter: string[]) => {
        navigate(buildJumpUrl(page, size, searchKey, sorter));
    };

    const fetchDataWithReload = (page: number, size: number, searchKey: string | undefined, sorter: string[]) => {
        navigate(buildJumpUrlFull(page, size, searchKey, new Date().getTime(), sorter));
    };

    const [tableDataState, setTableDataState] = useState<TableData>({
        pagination: {
            page: datasource?.page ? datasource.page : 1,
            key: datasource?.key,
            sort: datasource?.sort ? datasource?.sort : [],
            size: datasource?.size ? datasource?.size : defaultPageSize,
        },
        query: datasource?.key,
        tableLoaded: true,
        rows: datasource ? datasource.rows : [],
        tablePagination: {
            total: datasource?.totalElements,
            current: datasource?.page,
            pageSize: datasource?.size,
            onChange: (page: number, size: number) => {
                fetchData(page, size, tableDataState.query, []);
            },
        },
    });

    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });
    const handleDelete = async (pagination: MyPagination, deleteApiUri: string, key: string): Promise<boolean> => {
        const data = await postRefreshCacheSse<any>(deleteApiUri + "?id=" + key, {
            messageApi,
            messageKey: "tableDeleteRefreshCache",
        });
        if (data.error) {
            messageApi.error(data.message);
            return false;
        }
        if (data.error === 0) {
            messageApi.success(data.message);
            fetchDataWithReload(pagination.page, pagination.size, tableDataState.query, tableDataState.pagination.sort);
            return true;
        }
        return false;
    };

    useEffect(() => {
        if (searchKey === tableDataState.query) {
            return;
        }
        setTableDataState({ ...tableDataState, query: searchKey });
        fetchData(1, tableDataState.pagination.size, searchKey, tableDataState.pagination.sort);
    }, [searchKey]);

    useEffect(() => {
        setTableDataState((prevState) => {
            return {
                ...prevState,
                rows: datasource ? datasource.rows : [],
                pagination: {
                    page: datasource?.page ? datasource.page : 1,
                    size: datasource?.size ? datasource.size : 10,
                    sort: datasource?.sort ? datasource.sort : [],
                },
                tablePagination: {
                    current: datasource?.page,
                    pageSize: datasource?.size,
                    total: datasource?.totalElements,
                },
            };
        });
    }, [datasource]);

    const getActionedColumns = () => {
        const c: TableColumnsType<any> = [];
        if (hideId === null || hideId === undefined || !hideId) {
            c.push({
                title: getRes().id,
                dataIndex: "id",
                key: "id",
                fixed: pinnedColumns ? "left" : undefined,
                width: 64,
                render: (text: string) => {
                    return <span style={{ maxWidth: 64 }}>{text}</span>;
                },
            });
        }
        columns.forEach((e) => {
            c.push(e);
        });
        c.push({
            title: getRes().actions,
            dataIndex: "id",
            key: "action",
            fixed: pinnedColumns ? "right" : undefined,
            width: actionColumnWidth || 96,
            align: "center",
            render: (text: any, record: any) => {
                const disabledDeleteTip = deleteDisabledTip?.(record);
                const deleteDisabled = offline || Boolean(disabledDeleteTip);
                return text ? (
                    <Space size={0} wrap={false} style={{ whiteSpace: "nowrap" }}>
                        {editBtnRender
                            ? editBtnRender(text, record, () => {
                                  fetchDataWithReload(
                                      tableDataState.pagination.page,
                                      tableDataState.pagination.size,
                                      tableDataState.query,
                                      tableDataState.pagination.sort
                                  );
                              })
                            : null}
                        <Popconfirm
                            disabled={deleteDisabled}
                            title={getRes().deleteTips}
                            onConfirm={async () => {
                                const success = await handleDelete(tableDataState.pagination, deleteApi, record.id);
                                if (success) {
                                    if (deleteSuccessCallback) {
                                        deleteSuccessCallback(record.id);
                                    }
                                }
                            }}
                        >
                            <Button
                                disabled={deleteDisabled}
                                danger
                                type="text"
                                size="small"
                                title={disabledDeleteTip || getRes().deleteTips}
                                icon={<DeleteOutlined />}
                            />
                        </Popconfirm>
                    </Space>
                ) : null;
            },
        });
        return c;
    };

    const getColumnWidth = (width: unknown) => {
        if (typeof width === "number") {
            return width;
        }
        if (typeof width === "string" && width.endsWith("px")) {
            const parsedWidth = Number.parseInt(width, 10);
            return Number.isNaN(parsedWidth) ? 0 : parsedWidth;
        }
        return 160;
    };

    const tableColumns = getActionedColumns();
    const scrollX = tableColumns.reduce((total, column) => total + getColumnWidth(column.width), 0);

    return (
        <>
            {contextHolder}
            {addBtnRender
                ? addBtnRender(() => {
                      fetchDataWithReload(
                          tableDataState.pagination.page,
                          tableDataState.pagination.size,
                          tableDataState.query,
                          tableDataState.pagination.sort
                      );
                  })
                : undefined}
            <Table
                onChange={(pagination, _filter, sorter) => {
                    const sort =
                        (sorter as SorterResult) && (sorter as SorterResult).field
                            ? [
                                  (sorter as SorterResult).field +
                                      "," +
                                      ((sorter as SorterResult).order === "descend" ? "DESC" : "ASC"),
                              ]
                            : [];
                    if (sort.length > 0) {
                        setTableDataState({
                            ...tableDataState,
                            pagination: {
                                ...tableDataState.pagination,
                                sort: sort,
                            },
                        });
                    }
                    fetchData(
                        pagination.current ? pagination.current : 1,
                        pagination.pageSize ? pagination.pageSize : 10,
                        tableDataState.query,
                        sort
                    );
                }}
                style={{ minHeight: compactTable ? 360 : 512 }}
                columns={tableColumns}
                pagination={{
                    hideOnSinglePage: true,
                    simple: compactTable,
                    ...tableDataState.tablePagination,
                    itemRender: (page, _type, e) => {
                        return (
                            <Link
                                key={page}
                                to={buildJumpUrl(
                                    page,
                                    datasource?.size ? datasource.size : 10,
                                    tableDataState.query,
                                    datasource?.sort ? datasource.sort : []
                                )}
                            >
                                {e}
                            </Link>
                        );
                    },
                }}
                dataSource={tableDataState.rows}
                scroll={{ x: scrollX }}
                size={compactTable ? "small" : undefined}
            ></Table>
        </>
    );
};

export default BaseTable;
