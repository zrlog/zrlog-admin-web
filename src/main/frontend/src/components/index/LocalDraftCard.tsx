import { Button, Card, Popconfirm, Space, Tag, theme, Typography } from "antd";
import { ClearOutlined, EditOutlined, HistoryOutlined } from "@ant-design/icons";
import { Link } from "react-router-dom";
import { FunctionComponent } from "react";
import { getLabelValueSeparator, getRealRouteUrl, getRes } from "../../utils/constants";
import { LocalArticleCacheEntry, removeLocalArticleCacheByKey } from "../../utils/article-cache";
import TimeAgo from "@editor/dist/src/editor/TimeAgo";

type LocalDraftCardProps = {
    localDrafts: LocalArticleCacheEntry[];
    onClear: () => void;
};

const LocalDraftCard: FunctionComponent<LocalDraftCardProps> = ({ localDrafts, onClear }) => {
    const { token } = theme.useToken();
    const localDraft = localDrafts[0];
    const article = localDraft?.article;
    const localEdit = localDraft ? !localDraft.draft : false;
    const otherDrafts = localDrafts.slice(1, 4);

    const getUntitledText = (entry: LocalArticleCacheEntry) => {
        return entry.draft
            ? getRes().index.quickAction.localDraftUntitled
            : getRes().index.quickAction.localEditUntitled;
    };

    const getCacheTitle = (entry: LocalArticleCacheEntry) => {
        const title = entry.article.title?.trim();
        return title && title.length > 0 ? title : getUntitledText(entry);
    };

    const getEditPath = (entry: LocalArticleCacheEntry) => {
        return entry.article.logId && entry.article.logId > 0
            ? `/article-edit?id=${entry.article.logId}`
            : "/article-edit";
    };

    const getClearTitle = (entry: LocalArticleCacheEntry) => {
        return entry.draft ? getRes().index.quickAction.clearLocalDraft : getRes().index.quickAction.clearLocalEdit;
    };

    const getClearConfirm = (entry: LocalArticleCacheEntry) => {
        return entry.draft
            ? getRes().index.quickAction.clearLocalDraftConfirm
            : getRes().index.quickAction.clearLocalEditConfirm;
    };

    const clearLocalDraft = (entry: LocalArticleCacheEntry) => {
        removeLocalArticleCacheByKey(entry.key);
        onClear();
    };
    const localDraftTitle = localDraft ? getCacheTitle(localDraft) : "";

    return (
        <Card
            title={
                <div style={{ display: "flex", alignItems: "center", gap: token.marginXS }}>
                    <HistoryOutlined />
                    <span>
                        {localEdit ? getRes().index.quickAction.localEdit : getRes().index.quickAction.localDraft}
                    </span>
                </div>
            }
            extra={
                <Tag color={localEdit ? "warning" : "processing"}>
                    {localEdit ? getRes().index.quickAction.localEditTag : getRes().index.quickAction.localDraftTag}
                </Tag>
            }
            bordered={false}
            className="dashboard-card"
            styles={{ body: { padding: token.paddingLG } }}
        >
            {localDraft && article ? (
                <div style={{ display: "grid", gap: token.marginSM }}>
                    <Typography.Text strong ellipsis={{ tooltip: localDraftTitle }}>
                        {localDraftTitle}
                    </Typography.Text>
                    <Typography.Text type="secondary">
                        {localEdit ? getRes().index.quickAction.localEditTip : getRes().index.quickAction.localDraftTip}
                    </Typography.Text>
                    {localDraft.updatedAt > 0 && (
                        <Typography.Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
                            {getRes().index.quickAction.localCacheUpdatedAt}
                            {getLabelValueSeparator()}
                            <TimeAgo timestamp={localDraft.updatedAt} />
                        </Typography.Text>
                    )}
                    {localDrafts.length > 1 && (
                        <Typography.Text type="secondary">
                            {getRes().index.quickAction.localCacheCount.replace("{count}", `${localDrafts.length}`)}
                        </Typography.Text>
                    )}
                    <div style={{ display: "flex", gap: token.marginXS, flexWrap: "wrap" }}>
                        <Link to={getRealRouteUrl(getEditPath(localDraft))}>
                            <Button type="primary" size="small">
                                {getRes().index.quickAction.continueWriting}
                            </Button>
                        </Link>
                        <Popconfirm
                            title={getClearTitle(localDraft)}
                            description={getClearConfirm(localDraft)}
                            okText={getRes().yes}
                            cancelText={getRes().cancel}
                            onConfirm={() => clearLocalDraft(localDraft)}
                        >
                            <Button icon={<ClearOutlined />} size="small">
                                {getClearTitle(localDraft)}
                            </Button>
                        </Popconfirm>
                    </div>
                    {otherDrafts.length > 0 && (
                        <div style={{ display: "grid", gap: token.marginXS }}>
                            {otherDrafts.map((entry) => (
                                <div
                                    key={entry.key}
                                    style={{
                                        display: "flex",
                                        alignItems: "center",
                                        justifyContent: "space-between",
                                        gap: token.marginSM,
                                    }}
                                >
                                    <Space direction="vertical" size={0} style={{ minWidth: 0, flex: 1 }}>
                                        <Link to={getRealRouteUrl(getEditPath(entry))}>
                                            <Typography.Text ellipsis={{ tooltip: getCacheTitle(entry) }}>
                                                {getCacheTitle(entry)}
                                            </Typography.Text>
                                        </Link>
                                        {entry.updatedAt > 0 && (
                                            <Typography.Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
                                                <TimeAgo timestamp={entry.updatedAt} />
                                            </Typography.Text>
                                        )}
                                    </Space>
                                    <Popconfirm
                                        title={getClearTitle(entry)}
                                        description={getClearConfirm(entry)}
                                        okText={getRes().yes}
                                        cancelText={getRes().cancel}
                                        onConfirm={() => clearLocalDraft(entry)}
                                    >
                                        <Button icon={<ClearOutlined />} size="small" title={getClearTitle(entry)} />
                                    </Popconfirm>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            ) : (
                <div style={{ display: "grid", gap: token.marginSM }}>
                    <Typography.Text type="secondary">{getRes().index.quickAction.localDraftEmpty}</Typography.Text>
                    <Link to={getRealRouteUrl("/article-edit")}>
                        <Button icon={<EditOutlined />} size="small">
                            {getRes().index.quickAction.writeArticle}
                        </Button>
                    </Link>
                </div>
            )}
        </Card>
    );
};

export default LocalDraftCard;
