import { AppstoreOutlined, EditOutlined, GlobalOutlined, LockOutlined } from "@ant-design/icons";

import { Button, Grid, Image, Input, Segmented, TableColumnsType, Tooltip } from "antd";
import Divider from "antd/es/divider";
import { getRealRouteUrl, getRes } from "../../utils/constants";
import type * as React from "react";
import { ReactElement, useEffect, useRef, useState } from "react";
import Tags from "../../common/Tags";
import BaseTable, { ArticlePageDataSource } from "../../common/BaseTable";
import { Link, useNavigate } from "react-router-dom";
import { useLocation } from "react-router";
import { getAppState } from "../../base/ConfigProviderApp";
import { ArticlePreviewAction } from "./ArticlePreviewAction";
import { removeCacheDataByKey } from "../../utils/cache";

const { Search } = Input;
const { useBreakpoint } = Grid;

const genTypes = (d: ArticlePageDataSource, search: string) => {
    const typesStr = new URLSearchParams(search).get("types");

    return d.types
        ? d.types.map((e) => {
              return {
                  text: e.typeName,
                  value: e.alias,
                  selected: typesStr ? typesStr.split(",").includes(e.alias) : false,
              };
          })
        : [];
};

const Index = ({ data, offline }: { data: ArticlePageDataSource; offline: boolean }) => {
    const screens = useBreakpoint();
    const mobile = screens.xs === true;
    const location = useLocation();
    const navigate = useNavigate();
    const ds = genTypes(data, location.search);

    const statusOptions = [
        {
            label: `${getRes().article.tag.all} (${data.statusCounts?.total || 0})`,
            value: "",
            icon: <AppstoreOutlined />,
        },
        {
            label: `${getRes().article.status.published} (${data.statusCounts?.published || 0})`,
            value: "published",
            icon: <GlobalOutlined />,
        },
        {
            label: `${getRes().article.status.private} (${data.statusCounts?.privateCount || 0})`,
            value: "private",
            icon: <LockOutlined />,
        },
        {
            label: `${getRes().article.status.draft} (${data.statusCounts?.draft || 0})`,
            value: "draft",
            icon: <EditOutlined />,
        },
    ];

    const [filters, setFilters] = useState<Record<string, any>[]>(ds); // 用于存储选中的筛选项
    const jumped = useRef(false);

    // 从 URL 或后端数据中读取当前 status
    const currentStatus = data.status || new URLSearchParams(location.search).get("status") || "";
    const useCnBracket = getAppState().lang === "zh_CN";
    const leftBracket = useCnBracket ? "（" : "(";
    const rightBracket = useCnBracket ? "）" : ")";
    const currentStatusLabel =
        currentStatus === "published"
            ? getRes().article.status.published
            : currentStatus === "private"
            ? getRes().article.status.private
            : currentStatus === "draft"
            ? getRes().article.status.draft
            : getRes().article.tag.all;

    const handleStatusChange = (value: string | number) => {
        const params = new URLSearchParams(location.search);
        if (value) {
            params.set("status", value as string);
        } else {
            params.delete("status");
        }
        navigate(getRealRouteUrl(location.pathname + "?" + params.toString()));
    };

    const handleNavigation = () => {
        const params = new URLSearchParams(location.search);
        const selectedTypes = filters
            .filter((e) => e.selected)
            .map((e) => e.value)
            .join(",");
        if (selectedTypes) {
            params.set("types", selectedTypes);
        } else {
            params.delete("types");
        }
        navigate(getRealRouteUrl(location.pathname + "?" + params.toString()));
    };

    const handleFilterChange = (value: string, checked: boolean) => {
        setFilters((prevFilters) => prevFilters.map((f) => (f.value === value ? { ...f, selected: checked } : f)));
    };

    useEffect(() => {
        if (jumped.current) {
            handleNavigation();
        }
        jumped.current = true;
    }, [filters]);

    const wrapperArticleStateInfo = (record: any, element: ReactElement) => {
        return (
            <span style={{ display: "flex", gap: 4, whiteSpace: "normal", flexFlow: "wrap", alignItems: "center" }}>
                {element}
                {record.rubbish && (
                    <span style={{ color: "rgb(119, 119, 119)", fontSize: 12 }}>{getRes().article.status.draft}</span>
                )}
                {record.privacy && <LockOutlined style={{ color: "rgb(119, 119, 119)" }} />}
                {record.keywords && <Tags closeable={false} keywords={record.keywords} />}
            </span>
        );
    };

    const getColumns = (): TableColumnsType<any> => {
        const queryParams = new URLSearchParams(location.search);
        const sortParam = queryParams.get("sort");
        const sorterMap: Record<string, "descend" | "ascend" | undefined> = {};
        if (sortParam) {
            const [field, order] = sortParam.split(",");
            sorterMap[field] = order.toUpperCase() === "DESC" ? "descend" : "ascend";
        }

        return [
            {
                title: getRes().article.cover as string,
                dataIndex: "thumbnail",
                key: "thumbnail",
                width: data.article_thumbnail_status ? 108 : 0,
                render: (url: string) => {
                    if (url && url.length > 0 && data.article_thumbnail_status) {
                        return <Image style={{ objectFit: "contain", maxHeight: 108 }} src={url} />;
                    }
                    return <></>;
                },
            },
            {
                title: getRes().title,
                key: "title",
                dataIndex: "title",
                ellipsis: {
                    showTitle: false,
                },
                width: 400,
                render: (text: string, record: any) => {
                    const t = (
                        <Tooltip
                            placement="top"
                            title={
                                <div>
                                    {getRes().article.previewOpenPrefix}
                                    <span dangerouslySetInnerHTML={{ __html: text }}></span>
                                    {getRes().article.previewOpenSuffix}
                                </div>
                            }
                        >
                            <span
                                style={{ overflow: "hidden", textOverflow: "ellipsis", wordBreak: "break-word" }}
                                dangerouslySetInnerHTML={{ __html: text }}
                            />
                        </Tooltip>
                    );
                    if (record["url"].includes("previewMode")) {
                        return wrapperArticleStateInfo(record, <Link to={record["url"]}>{t}</Link>);
                    }
                    return wrapperArticleStateInfo(
                        record,
                        <a rel="noopener noreferrer" target={"_blank"} href={record.url}>
                            {t}
                        </a>
                    );
                },
            },
            {
                title: getRes().type,
                key: "typeName",
                dataIndex: "typeName",
                width: 100,
                //@ts-ignore
                filters: filters,
                filterMultiple: false,
                filteredValue: filters.filter((e) => e.selected).map((e) => e.value), // 动态绑定当前选中值
                onFilter: (value: React.Key | boolean) => {
                    // 更新选中状态
                    if (
                        filters
                            .filter((e) => e.selected)
                            .map((e) => e.value)
                            .includes(value)
                    ) {
                        return true;
                    }
                    //@ts-ignore
                    handleFilterChange(value, true);
                    handleNavigation();
                    return true; // 保留默认筛选功能
                },
            },
            {
                title: getRes().article.viewCount,
                key: "click",
                dataIndex: "click",
                width: 100,
                sorter: true,
                sortDirections: ["descend", "ascend"],
                sortOrder: sorterMap["click"],
            },
            {
                title: getRes().article.commentAble,
                key: "canComment",
                dataIndex: "canComment",
                render: (v: boolean) => (v ? getRes().yes : getRes().no),
                width: 80,
            },
            {
                title: getRes().article.commentSize,
                key: "commentSize",
                dataIndex: "commentSize",
                width: 100,
                sorter: true,
                sortDirections: ["descend", "ascend"],
                sortOrder: sorterMap["commentSize"],
            },
            {
                title: getRes().article.createTime,
                key: "releaseTime",
                dataIndex: "releaseTime",
                width: 120,
                sorter: true,
                sortDirections: ["ascend", "descend"],
                sortOrder: sorterMap["releaseTime"],
            },
            {
                title: getRes().article.lastUpdateDate,
                key: "lastUpdateDate",
                dataIndex: "lastUpdateDate",
                width: 120,
                sorter: true,
                sortDirections: ["ascend", "descend"],
                sortOrder: sorterMap["lastUpdateDate"],
            },
        ];
    };

    const onSearch = (key: string) => {
        setSearchKey(key);
    };

    const getDeleteApiUri = () => {
        return "/api/admin/article/delete";
    };

    const [searchKey, setSearchKey] = useState<string>(data.key ? data.key : "");

    return (
        <>
            <div
                style={{
                    justifyContent: "space-between",
                    display: "flex",
                    alignItems: mobile ? "stretch" : "center",
                    gap: 16,
                    flexWrap: "wrap",
                    flexDirection: mobile ? "column" : "row",
                }}
            >
                <div style={{ display: "flex", alignItems: "center", gap: 16, flex: 1, minWidth: 0, width: "100%" }}>
                    <Segmented
                        options={statusOptions}
                        value={currentStatus}
                        onChange={handleStatusChange}
                        block={mobile}
                        style={{ maxWidth: "100%", width: mobile ? "100%" : "auto" }}
                    />
                </div>
                <Search
                    allowClear
                    disabled={offline}
                    placeholder={`${getRes().article.listSearchTip}${leftBracket}${currentStatusLabel}${rightBracket}`}
                    onSearch={onSearch}
                    defaultValue={data.key}
                    enterButton={getRes().article.search}
                    style={{ width: mobile ? "100%" : 260, maxWidth: "100%" }}
                />
            </div>
            <Divider />
            <BaseTable
                defaultPageSize={data.defaultPageSize}
                offline={offline}
                datasource={data}
                columns={getColumns()}
                actionColumnWidth={116}
                editBtnRender={(id) => (
                    <>
                        <ArticlePreviewAction id={id} />
                        <Tooltip title={getRes().edit}>
                            <Link to={getRealRouteUrl("/article-edit?id=" + id)}>
                                <Button
                                    type="text"
                                    size="small"
                                    title={getRes().edit}
                                    icon={<EditOutlined style={{ color: getAppState().colorPrimary }} />}
                                />
                            </Link>
                        </Tooltip>
                    </>
                )}
                deleteSuccessCallback={(id) => {
                    removeCacheDataByKey(getRealRouteUrl("/article-edit?id=" + id));
                }}
                deleteApi={getDeleteApiUri()}
                searchKey={searchKey}
            />
        </>
    );
};

export default Index;
