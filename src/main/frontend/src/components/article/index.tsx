import { AppstoreOutlined, EditOutlined, GlobalOutlined, LockOutlined, StarFilled } from "@ant-design/icons";

import { Button, Grid, Input, Segmented, Space, TableColumnsType, Tag, Tooltip, Typography, theme } from "antd";
import Divider from "antd/es/divider";
import { getLabelValueSeparator, getRealRouteUrl, getRes } from "../../utils/constants";
import type * as React from "react";
import { ReactElement, useEffect, useRef, useState } from "react";
import Tags from "../../common/Tags";
import BaseTable, { ArticlePageDataSource } from "../../common/BaseTable";
import { Link, useNavigate } from "react-router-dom";
import { useLocation } from "react-router";
import { getAppState } from "../../base/ConfigProviderApp";
import { ArticlePreviewAction } from "./ArticlePreviewAction";
import { removeCacheDataByKey } from "../../utils/cache";
import BackendImage from "../../common/BackendImage";
import HighlightText from "../../common/HighlightText";

const { Search } = Input;
const { Text } = Typography;
const { useBreakpoint } = Grid;

type ArticleTypeFilter = {
    text: string;
    value: string;
    selected: boolean;
};

const genTypes = (d: ArticlePageDataSource, search: string): ArticleTypeFilter[] => {
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
    const compactToolbar = screens.lg !== true;
    const compactArticleTable = screens.md !== true;
    const location = useLocation();
    const navigate = useNavigate();
    const ds = genTypes(data, location.search);
    const { token } = theme.useToken();

    const surface = {
        titleStateRow: {
            display: "flex",
            gap: token.marginXXS,
            whiteSpace: "normal" as const,
            flexFlow: "wrap" as const,
            alignItems: "center" as const,
        },
        titleStateDraft: {
            color: token.colorTextSecondary,
            fontSize: token.fontSizeSM,
        },
        titleStateIcon: {
            color: token.colorTextSecondary,
        },
        compactTitleMeta: {
            display: "flex",
            flexDirection: "column" as const,
            gap: token.marginXXS,
            minWidth: 0,
        },
        compactTitleMetaSpace: {
            h: token.marginXS,
            v: token.marginXXS,
        },
        titleText: {
            overflow: "hidden",
            textOverflow: "ellipsis",
            wordBreak: "break-word" as const,
        },
        compactTypeTag: {
            marginInlineEnd: 0,
        },
        toolbar: {
            justifyContent: "space-between",
            display: "flex",
            alignItems: compactToolbar ? ("stretch" as const) : ("center" as const),
            gap: token.margin,
            flexWrap: "wrap" as const,
            flexDirection: compactToolbar ? ("column" as const) : ("row" as const),
        },
        toolbarControlWrap: {
            display: "flex",
            alignItems: "center",
            gap: token.margin,
            flex: 1,
            minWidth: 0,
            width: "100%",
        },
        search: {
            width: compactToolbar ? "100%" : 260,
            maxWidth: "100%",
        },
        segmented: {
            maxWidth: "100%",
            width: compactToolbar ? "100%" : "auto",
        },
        thumbnailImage: {
            objectFit: "contain" as const,
            maxHeight: 108,
        },
        editActionIcon: {
            color: token.colorPrimary,
        },
    };

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

    const [filters, setFilters] = useState<ArticleTypeFilter[]>(ds);
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
            <span style={surface.titleStateRow}>
                {element}
                {record.rubbish && <span style={surface.titleStateDraft}>{getRes().article.status.draft}</span>}
                {record.privacy && <LockOutlined style={surface.titleStateIcon} />}
                {record.recommended && (
                    <Tag color="gold" bordered={false} icon={<StarFilled />} style={{ marginInlineEnd: 0 }}>
                        {getRes().article.recommended}
                    </Tag>
                )}
                {record.keywords && <Tags closeable={false} keywords={record.keywords} />}
            </span>
        );
    };

    const renderArticleTitle = (text: string, record: any, compact: boolean) => {
        const keyword = data.key || "";
        const title = (
            <Tooltip
                placement="top"
                title={
                    <div>
                        {getRes().article.previewOpenPrefix}
                        <HighlightText text={text} keyword={keyword} />
                        {getRes().article.previewOpenSuffix}
                    </div>
                }
            >
                <HighlightText text={text} keyword={keyword} style={surface.titleText} />
            </Tooltip>
        );
        const titleLink = record["url"].includes("previewMode") ? (
            <Link to={record["url"]}>{title}</Link>
        ) : (
            <a rel="noopener noreferrer" target={"_blank"} href={record.url}>
                {title}
            </a>
        );
        const titleWithState = wrapperArticleStateInfo(record, titleLink);
        if (!compact) {
            return titleWithState;
        }
        return (
            <div style={surface.compactTitleMeta}>
                {titleWithState}
                <Space size={[surface.compactTitleMetaSpace.h, surface.compactTitleMetaSpace.v]} wrap>
                    {record.typeName ? <Tag style={surface.compactTypeTag}>{record.typeName}</Tag> : null}
                    <Text type="secondary">
                        {getRes().article.viewCount}
                        {getLabelValueSeparator()}
                        {record.click}
                    </Text>
                    <Text type="secondary">
                        {getRes().article.commentSize}
                        {getLabelValueSeparator()}
                        {record.commentSize}
                    </Text>
                    <Text type="secondary">
                        {getRes().article.lastUpdateDate}
                        {getLabelValueSeparator()}
                        {record.lastUpdateDate}
                    </Text>
                </Space>
            </div>
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

        if (compactArticleTable) {
            return [
                {
                    title: getRes().title,
                    key: "title",
                    dataIndex: "title",
                    width: 280,
                    render: (text: string, record: any) => renderArticleTitle(text, record, true),
                },
            ];
        }

        const columns: TableColumnsType<any> = [
            {
                title: getRes().title,
                key: "title",
                dataIndex: "title",
                ellipsis: {
                    showTitle: false,
                },
                width: 400,
                render: (text: string, record: any) => renderArticleTitle(text, record, false),
            },
            {
                title: getRes().type,
                key: "typeName",
                dataIndex: "typeName",
                width: 100,
                filters: filters.map(({ text, value }) => ({ text, value })),
                filterMultiple: false,
                filteredValue: filters.filter((e) => e.selected).map((e) => e.value), // 动态绑定当前选中值
                onFilter: (value: React.Key | boolean) => {
                    const filterValue = String(value);
                    // 更新选中状态
                    if (
                        filters
                            .filter((e) => e.selected)
                            .map((e) => e.value)
                            .includes(filterValue)
                    ) {
                        return true;
                    }
                    handleFilterChange(filterValue, true);
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
        if (data.article_thumbnail_status) {
            columns.unshift({
                title: getRes().article.cover as string,
                dataIndex: "thumbnail",
                key: "thumbnail",
                width: 108,
                render: (url: string) => {
                    if (url && url.length > 0) {
                        return <BackendImage style={surface.thumbnailImage} src={url} />;
                    }
                    return <></>;
                },
            });
        }
        return columns;
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
            <div style={surface.toolbar}>
                <div style={surface.toolbarControlWrap}>
                    <Segmented
                        options={statusOptions}
                        value={currentStatus}
                        onChange={handleStatusChange}
                        block={compactToolbar}
                        style={surface.segmented}
                    />
                </div>
                <Search
                    allowClear
                    disabled={offline}
                    placeholder={`${getRes().article.listSearchTip}${leftBracket}${currentStatusLabel}${rightBracket}`}
                    onSearch={onSearch}
                    defaultValue={data.key}
                    enterButton={getRes().article.search}
                    style={surface.search}
                />
            </div>
            <Divider />
            <BaseTable
                defaultPageSize={data.defaultPageSize}
                offline={offline}
                datasource={data}
                columns={getColumns()}
                actionColumnWidth={compactArticleTable ? 88 : 116}
                hideId={compactArticleTable}
                editBtnRender={(id, record) => (
                    <>
                        <ArticlePreviewAction article={record} />
                        <Tooltip title={getRes().edit}>
                            <Link to={getRealRouteUrl("/article-edit?id=" + id)}>
                                <Button
                                    type="text"
                                    size="small"
                                    title={getRes().edit}
                                    icon={<EditOutlined style={surface.editActionIcon} />}
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
