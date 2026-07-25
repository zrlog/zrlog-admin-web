import { FunctionComponent } from "react";
import { Alert, Button, Space, Tag } from "antd";
import TimeAgo from "@editor/dist/editor/TimeAgo";
import { getLabelValueSeparator, getRes } from "../../utils/constants";
import { ArticleEditState, ArticleEntry } from "./index.types";

type ArticleContentConflictAlertProps = {
    conflict: NonNullable<ArticleEditState["contentConflict"]>;
    serverArticle: ArticleEntry;
    onKeepServer: () => void;
    onUseLocal: () => void;
};

const normalizeConflictValue = (value: unknown) => {
    if (value === undefined || value === null) {
        return "";
    }
    if (typeof value === "string") {
        return value.trim();
    }
    return JSON.stringify(value);
};

const getArticleBody = (article: ArticleEntry) => article.markdown || article.content || "";

const ArticleContentConflictAlert: FunctionComponent<ArticleContentConflictAlertProps> = ({
    conflict,
    serverArticle,
    onKeepServer,
    onUseLocal,
}) => {
    const detail = getRes()
        .articleEdit.contentConflict.serverUpdatedWithLocalEditDetail.replace(
            "{serverVersion}",
            `${conflict.serverVersion}`
        )
        .replace("{localVersion}", `${conflict.localVersion}`);
    const fieldLabels = getRes().articleEdit.version.fields;
    const conflictFields = [
        {
            key: "title",
            label: fieldLabels.title,
            serverValue: serverArticle.title,
            localValue: conflict.localArticle.title,
        },
        {
            key: "digest",
            label: fieldLabels.digest,
            serverValue: serverArticle.digest,
            localValue: conflict.localArticle.digest,
        },
        {
            key: "keywords",
            label: fieldLabels.keywords,
            serverValue: serverArticle.keywords,
            localValue: conflict.localArticle.keywords,
        },
        {
            key: "alias",
            label: fieldLabels.alias,
            serverValue: serverArticle.alias,
            localValue: conflict.localArticle.alias,
        },
        {
            key: "thumbnail",
            label: fieldLabels.thumbnail,
            serverValue: serverArticle.thumbnail,
            localValue: conflict.localArticle.thumbnail,
        },
        {
            key: "typeId",
            label: fieldLabels.typeId,
            serverValue: serverArticle.typeId,
            localValue: conflict.localArticle.typeId,
        },
        {
            key: "canComment",
            label: fieldLabels.canComment,
            serverValue: serverArticle.canComment,
            localValue: conflict.localArticle.canComment,
        },
        {
            key: "recommended",
            label: fieldLabels.recommended,
            serverValue: serverArticle.recommended,
            localValue: conflict.localArticle.recommended,
        },
        {
            key: "privacy",
            label: fieldLabels.privacy,
            serverValue: serverArticle.privacy,
            localValue: conflict.localArticle.privacy,
        },
        {
            key: "rubbish",
            label: fieldLabels.rubbish,
            serverValue: serverArticle.rubbish,
            localValue: conflict.localArticle.rubbish,
        },
        {
            key: "markdown",
            label: fieldLabels.markdown,
            serverValue: getArticleBody(serverArticle),
            localValue: getArticleBody(conflict.localArticle),
        },
    ].filter((field) => normalizeConflictValue(field.serverValue) !== normalizeConflictValue(field.localValue));
    const localBodyLength = getArticleBody(conflict.localArticle).trim().length;
    const serverBodyLength = getArticleBody(serverArticle).trim().length;
    const bodyLengthDetail = getRes()
        .articleEdit.contentConflict.bodyLengthDetail.replace("{localLength}", `${localBodyLength}`)
        .replace("{serverLength}", `${serverBodyLength}`);

    return (
        <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message={getRes().articleEdit.contentConflict.serverUpdatedWithLocalEditTitle}
            description={
                <Space direction="vertical" size={4}>
                    <span>{detail}</span>
                    {conflict.localUpdatedAt ? (
                        <span>
                            {getRes().articleEdit.contentConflict.localSavedAt}
                            {getLabelValueSeparator()}
                            <TimeAgo timestamp={conflict.localUpdatedAt} />
                        </span>
                    ) : null}
                    {conflictFields.length > 0 ? (
                        <Space size={[4, 4]} wrap>
                            <span>{getRes().articleEdit.contentConflict.changedFields}</span>
                            {conflictFields.map((field) => (
                                <Tag key={field.key}>{field.label}</Tag>
                            ))}
                        </Space>
                    ) : null}
                    {localBodyLength !== serverBodyLength ? <span>{bodyLengthDetail}</span> : null}
                </Space>
            }
            action={
                <Space size={8}>
                    <Button size="small" onClick={onKeepServer}>
                        {getRes().articleEdit.contentConflict.keepServer}
                    </Button>
                    <Button size="small" type="primary" onClick={onUseLocal}>
                        {getRes().articleEdit.contentConflict.useLocalEdit}
                    </Button>
                </Space>
            }
        />
    );
};

export default ArticleContentConflictAlert;
