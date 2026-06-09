import { DeleteOutlined, EditOutlined, SwapOutlined } from "@ant-design/icons";
import { Button, Descriptions, Empty, Grid, Input, Modal, Space, Table, Tag, Typography, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { useAxiosBaseInstance } from "../../base/AppBase";
import type { PageDataSource } from "../../common/BaseTable";
import type { ApiResponse } from "../../type";
import { cacheIgnoreReloadTime, getLabelValueSeparator, getRealRouteUrl, getRes } from "../../utils/constants";
import { mapToQueryString } from "../../utils/helpers";
import { postRefreshCacheSse } from "../../utils/sse-utils";

type TagEntry = {
    id: number;
    text: string;
    count: number;
    url: string;
};

type TagAction = "rename" | "merge" | "delete";

type TagImpact = {
    id: number;
    title: string;
    beforeKeywords: string;
    afterKeywords: string;
};

type TagPreview = {
    sourceTag: string;
    targetTag: string;
    operation: TagAction;
    affectedArticleCount: number;
    updatedArticleCount: number;
    hasMore: boolean;
    executed: boolean;
    articles: TagImpact[];
};

type TagActionState = {
    open: boolean;
    action: TagAction;
    record?: TagEntry;
    targetTag: string;
    preview?: TagPreview;
    previewLoading: boolean;
    submitting: boolean;
};

const previewLimit = 10;

const initialActionState: TagActionState = {
    open: false,
    action: "rename",
    targetTag: "",
    previewLoading: false,
    submitting: false,
};

const normalizeTag = (tag: string) => tag.trim();

const formatMessage = (template: string, values: Record<string, string | number>) =>
    Object.entries(values).reduce((text, [key, value]) => text.replace(`{${key}}`, String(value)), template);

const TagManagement = ({ data, offline }: { data: PageDataSource; offline: boolean }) => {
    const rows = (data?.rows || []) as TagEntry[];
    const screens = Grid.useBreakpoint();
    const compactTable = screens.md !== true;
    const navigate = useNavigate();
    const axiosInstance = useAxiosBaseInstance();
    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });
    const [searchKey, setSearchKey] = useState(data?.key || "");
    const [actionState, setActionState] = useState<TagActionState>(initialActionState);
    const tagRes = getRes().tagManage;

    useEffect(() => {
        setSearchKey(data?.key || "");
    }, [data?.key]);

    const buildUrl = (page: number, size: number, key?: string, reload = false) => {
        const query: Record<string, number | string> = {};
        if (page > 1) {
            query.page = page;
        }
        if (size !== (data?.defaultPageSize || 10)) {
            query.size = size;
        }
        const trimmedKey = normalizeTag(key || "");
        if (trimmedKey) {
            query.key = trimmedKey;
        }
        if (reload) {
            query[cacheIgnoreReloadTime] = Date.now();
        }
        const queryString = mapToQueryString(query);
        return getRealRouteUrl(queryString ? `/tag?${queryString}` : "/tag");
    };

    const refreshCurrentPage = () => {
        navigate(buildUrl(data?.page || 1, data?.size || data?.defaultPageSize || 10, searchKey, true));
    };

    const openAction = (record: TagEntry, action: TagAction) => {
        setActionState({
            ...initialActionState,
            open: true,
            action,
            record,
        });
    };

    const closeAction = () => {
        setActionState(initialActionState);
    };

    const buildRequestBody = () => ({
        sourceTag: actionState.record?.text || "",
        targetTag: actionState.action === "delete" ? "" : normalizeTag(actionState.targetTag),
    });

    const validateAction = () => {
        if (!actionState.record) {
            return false;
        }
        if (actionState.action === "delete") {
            return true;
        }
        const targetTag = normalizeTag(actionState.targetTag);
        if (!targetTag) {
            messageApi.warning(tagRes.targetTagRequired);
            return false;
        }
        if (targetTag === actionState.record.text) {
            messageApi.warning(tagRes.sameTarget);
            return false;
        }
        return true;
    };

    const loadPreview = async () => {
        if (!validateAction()) {
            return;
        }
        setActionState((prev) => ({ ...prev, previewLoading: true }));
        try {
            const { data: response } = await axiosInstance.post<ApiResponse<TagPreview>>(
                `/api/admin/tag/preview?operation=${actionState.action}`,
                buildRequestBody()
            );
            if (response.error) {
                messageApi.error(response.message);
                return;
            }
            setActionState((prev) => ({ ...prev, preview: response.data }));
        } finally {
            setActionState((prev) => ({ ...prev, previewLoading: false }));
        }
    };

    const executeAction = async () => {
        if (!validateAction()) {
            return;
        }
        setActionState((prev) => ({ ...prev, submitting: true }));
        try {
            const response = await postRefreshCacheSse<ApiResponse<TagPreview>>(
                `/api/admin/tag/${actionState.action}`,
                {
                    body: buildRequestBody(),
                    messageApi,
                    messageKey: "tagManageRefreshCache",
                    waitForComplete: true,
                }
            );
            if (response.error) {
                messageApi.error(response.message);
                return;
            }
            messageApi.success(formatMessage(tagRes.success, { count: response.data?.updatedArticleCount || 0 }));
            closeAction();
            refreshCurrentPage();
        } finally {
            setActionState((prev) => ({ ...prev, submitting: false }));
        }
    };

    const renderTagName = (text: string, record: TagEntry) => (
        <Space direction={compactTable ? "vertical" : "horizontal"} size={compactTable ? 4 : 8}>
            <a href={record.url} target="_blank" rel="noopener noreferrer" title={tagRes.url}>
                {text}
            </a>
            {compactTable ? (
                <Typography.Text type="secondary">
                    {tagRes.count}
                    {getLabelValueSeparator()}
                    {record.count}
                </Typography.Text>
            ) : null}
        </Space>
    );

    const columns: ColumnsType<TagEntry> = useMemo(() => {
        const commonColumns: ColumnsType<TagEntry> = [
            {
                title: tagRes.name,
                dataIndex: "text",
                key: "text",
                width: compactTable ? 220 : 260,
                render: renderTagName,
            },
        ];
        if (!compactTable) {
            commonColumns.push({
                title: tagRes.count,
                dataIndex: "count",
                key: "count",
                width: 120,
            });
        }
        commonColumns.push({
            title: getRes().actions,
            key: "actions",
            width: compactTable ? 132 : 188,
            align: "center",
            render: (_value, record) => (
                <Space size={0} wrap={false}>
                    <Button
                        type="text"
                        size="small"
                        disabled={offline}
                        title={tagRes.rename}
                        icon={<EditOutlined />}
                        onClick={() => openAction(record, "rename")}
                    />
                    <Button
                        type="text"
                        size="small"
                        disabled={offline}
                        title={tagRes.merge}
                        icon={<SwapOutlined />}
                        onClick={() => openAction(record, "merge")}
                    />
                    <Button
                        type="text"
                        size="small"
                        danger
                        disabled={offline}
                        title={tagRes.delete}
                        icon={<DeleteOutlined />}
                        onClick={() => openAction(record, "delete")}
                    />
                </Space>
            ),
        });
        return commonColumns;
    }, [compactTable, offline, tagRes]);

    const previewColumns: ColumnsType<TagImpact> = [
        {
            title: getRes().article.title,
            dataIndex: "title",
            key: "title",
            width: 220,
            render: (title: string, record) => (
                <a href={getRealRouteUrl(`/article-edit?id=${record.id}`)} target="_blank" rel="noopener noreferrer">
                    {title}
                </a>
            ),
        },
        {
            title: tagRes.before,
            dataIndex: "beforeKeywords",
            key: "beforeKeywords",
            width: 220,
            render: (keywords: string) => <Tag>{keywords}</Tag>,
        },
        {
            title: tagRes.after,
            dataIndex: "afterKeywords",
            key: "afterKeywords",
            width: 220,
            render: (keywords: string) =>
                keywords ? <Tag>{keywords}</Tag> : <Typography.Text type="secondary">-</Typography.Text>,
        },
    ];

    const getActionTitle = () => {
        if (actionState.action === "merge") {
            return tagRes.mergeTitle;
        }
        if (actionState.action === "delete") {
            return tagRes.deleteTitle;
        }
        return tagRes.renameTitle;
    };

    return (
        <>
            {contextHolder}
            <Input.Search
                allowClear
                enterButton={tagRes.search}
                value={searchKey}
                placeholder={tagRes.searchTip}
                style={{ maxWidth: 360, marginBottom: 12 }}
                onChange={(event) => setSearchKey(event.target.value)}
                onSearch={(value) => navigate(buildUrl(1, data?.size || data?.defaultPageSize || 10, value))}
            />
            <Table<TagEntry>
                rowKey="id"
                columns={columns}
                dataSource={rows}
                size={compactTable ? "small" : undefined}
                scroll={{ x: compactTable ? 420 : 568 }}
                pagination={{
                    hideOnSinglePage: true,
                    simple: compactTable,
                    current: data?.page || 1,
                    pageSize: data?.size || data?.defaultPageSize || 10,
                    total: data?.totalElements || 0,
                    onChange: (page, size) => navigate(buildUrl(page, size, searchKey)),
                }}
            />
            <Modal
                open={actionState.open}
                title={getActionTitle()}
                onCancel={closeAction}
                onOk={executeAction}
                okText={
                    actionState.action === "merge"
                        ? tagRes.confirmMerge
                        : actionState.action === "delete"
                        ? tagRes.confirmDelete
                        : tagRes.confirmRename
                }
                okButtonProps={{
                    danger: actionState.action === "delete",
                    loading: actionState.submitting,
                    disabled: offline,
                }}
                cancelText={getRes().cancel}
                destroyOnHidden
                width={compactTable ? undefined : 760}
            >
                <Space direction="vertical" size="middle" style={{ width: "100%" }}>
                    <Descriptions column={1} size="small">
                        <Descriptions.Item label={tagRes.name}>{actionState.record?.text}</Descriptions.Item>
                    </Descriptions>
                    {actionState.action !== "delete" ? (
                        <Input
                            addonBefore={tagRes.targetTag}
                            value={actionState.targetTag}
                            placeholder={tagRes.targetTagPlaceholder}
                            disabled={offline}
                            onChange={(event) =>
                                setActionState((prev) => ({
                                    ...prev,
                                    targetTag: event.target.value,
                                    preview: undefined,
                                }))
                            }
                        />
                    ) : (
                        <Typography.Text type="secondary">{tagRes.deleteWarning}</Typography.Text>
                    )}
                    <Space>
                        <Button loading={actionState.previewLoading} disabled={offline} onClick={loadPreview}>
                            {tagRes.preview}
                        </Button>
                        {actionState.preview ? (
                            <Typography.Text type="secondary">
                                {formatMessage(tagRes.affectedArticles, {
                                    count: actionState.preview.affectedArticleCount,
                                })}
                            </Typography.Text>
                        ) : null}
                    </Space>
                    {actionState.preview ? (
                        actionState.preview.articles.length > 0 ? (
                            <>
                                <Table<TagImpact>
                                    rowKey="id"
                                    size="small"
                                    columns={previewColumns}
                                    dataSource={actionState.preview.articles}
                                    pagination={false}
                                    scroll={{ x: 660 }}
                                />
                                {actionState.preview.hasMore ? (
                                    <Typography.Text type="secondary">
                                        {formatMessage(tagRes.previewMore, { count: previewLimit })}
                                    </Typography.Text>
                                ) : null}
                            </>
                        ) : (
                            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={tagRes.previewEmpty} />
                        )
                    ) : null}
                </Space>
            </Modal>
        </>
    );
};

export default TagManagement;
