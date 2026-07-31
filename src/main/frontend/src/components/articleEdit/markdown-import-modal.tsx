import { Alert, Checkbox, Descriptions, Divider, Grid, Modal, Radio, Select, Space, Typography } from "antd";
import { FunctionComponent, RefObject, useEffect, useMemo, useRef, useState } from "react";
import { getRes } from "../../utils/constants";
import { ArticleEntry } from "./index.types";
import {
    ArticleTypeOption,
    getMarkdownImportApplyFields,
    getDefaultMarkdownImportFields,
    getMarkdownImportTargetPolicy,
    MarkdownImportField,
    MarkdownImportPreview,
    MarkdownImportTarget,
    resolveMarkdownImportCategory,
} from "./markdown-import";

export type MarkdownImportApplyOptions = {
    preview: MarkdownImportPreview;
    selectedFields: Set<MarkdownImportField>;
    selectedTypeId?: number;
    target: MarkdownImportTarget;
    expectedCurrentMarkdown: string;
};

type MarkdownImportModalProps = {
    article: ArticleEntry;
    contentConflict: boolean;
    containerRef: RefObject<HTMLDivElement>;
    offline: boolean;
    open: boolean;
    preview?: MarkdownImportPreview;
    currentMarkdown: string;
    typeOptions: ArticleTypeOption[];
    onApply: (options: MarkdownImportApplyOptions) => Promise<boolean>;
    onCancel: () => void;
};

const formatMessage = (template: string, values: Record<string, string | number>) =>
    Object.entries(values).reduce((text, [key, value]) => text.replace(`{${key}}`, String(value)), template);

const formatBytes = (size: number) => {
    if (size < 1024) {
        return `${size} B`;
    }
    return `${(size / 1024).toFixed(size < 100 * 1024 ? 1 : 0)} KiB`;
};

const MarkdownImportModal: FunctionComponent<MarkdownImportModalProps> = ({
    article,
    contentConflict,
    containerRef,
    offline,
    open,
    preview,
    currentMarkdown,
    typeOptions,
    onApply,
    onCancel,
}) => {
    const res = getRes().articleEdit.markdownImport;
    const screens = Grid.useBreakpoint();
    const targetPolicy = useMemo(
        () => getMarkdownImportTargetPolicy(article, currentMarkdown, contentConflict),
        [article, contentConflict, currentMarkdown]
    );
    const categoryResolution = useMemo(
        () => resolveMarkdownImportCategory(preview?.metadata.categoryNames || [], typeOptions),
        [preview, typeOptions]
    );
    const [target, setTarget] = useState<MarkdownImportTarget>(targetPolicy.initialTarget);
    const [selectedFields, setSelectedFields] = useState<Set<MarkdownImportField>>(new Set());
    const [selectedTypeId, setSelectedTypeId] = useState<number | undefined>();
    const [applying, setApplying] = useState(false);
    const applyingRef = useRef(false);

    useEffect(() => {
        const nextTarget = targetPolicy.initialTarget;
        setTarget(nextTarget);
        setSelectedFields(preview ? getDefaultMarkdownImportFields(preview, article, nextTarget) : new Set());
        setSelectedTypeId(categoryResolution.typeId ?? (nextTarget === "current" ? article.typeId : undefined));
    }, [article, categoryResolution.typeId, preview, targetPolicy.initialTarget]);

    if (!preview) {
        return null;
    }

    const updateTarget = (nextTarget: MarkdownImportTarget) => {
        setTarget(nextTarget);
        setSelectedFields(getDefaultMarkdownImportFields(preview, article, nextTarget));
        setSelectedTypeId(categoryResolution.typeId ?? (nextTarget === "current" ? article.typeId : undefined));
    };

    const updateField = (field: MarkdownImportField, checked: boolean) => {
        setSelectedFields((current) => {
            const next = new Set(current);
            if (checked) {
                next.add(field);
            } else {
                next.delete(field);
            }
            return next;
        });
    };

    const renderMetadataField = (
        field: Exclude<MarkdownImportField, "category">,
        label: string,
        value: string | undefined
    ) => {
        if (!value) {
            return null;
        }
        const requiredForNewDraft = target === "newDraft" && field === "title";
        return (
            <div key={field} style={{ minWidth: 0 }}>
                <Checkbox
                    checked={requiredForNewDraft || selectedFields.has(field)}
                    disabled={requiredForNewDraft}
                    onChange={(event) => updateField(field, event.target.checked)}
                >
                    {label}
                </Checkbox>
                <Typography.Paragraph
                    type="secondary"
                    ellipsis={{ rows: 2, expandable: true }}
                    style={{ margin: "4px 0 0 24px", wordBreak: "break-word" }}
                >
                    {value}
                </Typography.Paragraph>
            </div>
        );
    };

    const targetUnavailable = target === "newDraft" && offline;
    const categoryRequired = target === "newDraft" || !article.typeId || article.typeId <= 0;
    const categorySelected = categoryRequired || selectedFields.has("category");
    const applyDisabled = targetUnavailable || (categorySelected && (!selectedTypeId || selectedTypeId <= 0));
    const ignoredFields = preview.ignoredFields.concat(preview.unknownFields);
    const remoteImageCount = preview.resources.remoteImages.length;
    const relativeImageCount = preview.resources.relativeImages.length;
    const siteAbsoluteImageCount = preview.resources.siteAbsoluteImages.length;
    const otherUnsupportedImageCount =
        preview.resources.embeddedImages.length + preview.resources.unsupportedImages.length;

    const handleApply = async () => {
        if (applyingRef.current) {
            return;
        }
        applyingRef.current = true;
        setApplying(true);
        try {
            const applied = await onApply({
                preview,
                selectedFields: getMarkdownImportApplyFields(selectedFields, target, categoryRequired, selectedTypeId),
                selectedTypeId,
                target,
                expectedCurrentMarkdown: currentMarkdown,
            });
            if (applied) {
                onCancel();
            }
        } finally {
            applyingRef.current = false;
            setApplying(false);
        }
    };

    const handleCancel = () => {
        if (!applyingRef.current) {
            onCancel();
        }
    };

    return (
        <Modal
            title={res.title}
            open={open}
            onCancel={handleCancel}
            onOk={handleApply}
            okText={target === "newDraft" ? res.createDraft : res.importCurrent}
            cancelText={getRes().cancel}
            okButtonProps={{
                danger: targetPolicy.dangerousCurrentReplace && target === "current",
                disabled: applying || applyDisabled,
            }}
            cancelButtonProps={{ disabled: applying }}
            confirmLoading={applying}
            closable={!applying}
            keyboard={!applying}
            maskClosable={!applying}
            width={screens.md ? 720 : "calc(100vw - 24px)"}
            style={screens.md ? undefined : { top: 8, paddingBottom: 8 }}
            getContainer={() => containerRef.current as HTMLElement}
            destroyOnClose
            styles={{
                body: {
                    maxHeight: screens.md ? "70vh" : "calc(100dvh - 156px)",
                    overflowY: "auto",
                },
            }}
        >
            <Descriptions size="small" column={screens.sm ? 2 : 1}>
                <Descriptions.Item label={res.fileName}>
                    <span style={{ overflowWrap: "anywhere" }}>{preview.fileName}</span>
                </Descriptions.Item>
                <Descriptions.Item label={res.fileSize}>{formatBytes(preview.fileSize)}</Descriptions.Item>
                <Descriptions.Item label={res.bodyLength}>{preview.markdown.length}</Descriptions.Item>
                <Descriptions.Item label={res.imageCount}>{preview.resources.imageReferences.length}</Descriptions.Item>
            </Descriptions>

            <Divider titlePlacement="start" plain>
                {res.target}
            </Divider>
            {targetPolicy.currentAllowed && targetPolicy.newDraftAllowed ? (
                <Radio.Group value={target} onChange={(event) => updateTarget(event.target.value)}>
                    <Space direction="vertical" size={8}>
                        <Radio value="newDraft" disabled={offline}>
                            {res.targetNewDraft}
                        </Radio>
                        <Radio value="current">
                            <Typography.Text type={targetPolicy.dangerousCurrentReplace ? "danger" : undefined}>
                                {targetPolicy.dangerousCurrentReplace ? res.targetReplaceCurrent : res.targetCurrent}
                            </Typography.Text>
                        </Radio>
                    </Space>
                </Radio.Group>
            ) : (
                <Alert
                    type={targetPolicy.forcedNewDraftReason ? "warning" : "info"}
                    showIcon
                    message={
                        targetPolicy.forcedNewDraftReason === "private"
                            ? res.privateCreatesDraft
                            : targetPolicy.forcedNewDraftReason === "contentConflict"
                            ? res.conflictCreatesDraft
                            : targetPolicy.forcedNewDraftReason === "published"
                            ? res.publishedCreatesDraft
                            : res.blankImportsCurrent
                    }
                />
            )}
            {targetPolicy.dangerousCurrentReplace && target === "current" ? (
                <Alert type="warning" showIcon message={res.replaceWarning} style={{ marginTop: 12 }} />
            ) : null}
            {targetUnavailable ? (
                <Alert type="error" showIcon message={res.offlineCreateUnavailable} style={{ marginTop: 12 }} />
            ) : null}

            <Divider titlePlacement="start" plain>
                {res.metadata}
            </Divider>
            <Space direction="vertical" size={12} style={{ width: "100%" }}>
                {renderMetadataField("title", res.fieldTitle, preview.metadata.title)}
                {renderMetadataField("alias", res.fieldAlias, preview.metadata.alias)}
                {renderMetadataField("digest", res.fieldDigest, preview.metadata.digest)}
                {renderMetadataField("keywords", res.fieldKeywords, preview.metadata.keywords)}
                <div>
                    <Checkbox
                        checked={categorySelected}
                        disabled={categoryRequired}
                        onChange={(event) => updateField("category", event.target.checked)}
                    >
                        {res.fieldCategory}
                    </Checkbox>
                    <Select
                        value={selectedTypeId}
                        options={typeOptions}
                        disabled={!categorySelected}
                        placeholder={getRes().pleaseChoose + getRes().articleType.title}
                        onChange={setSelectedTypeId}
                        style={{ display: "block", margin: "6px 0 0 24px", maxWidth: 360 }}
                        getPopupContainer={(triggerNode) => triggerNode.parentElement}
                    />
                </div>
            </Space>

            {categoryResolution.unmatchedNames.length > 0 ? (
                <Alert
                    type="warning"
                    showIcon
                    message={formatMessage(res.unknownCategories, {
                        categories: categoryResolution.unmatchedNames.join(", "),
                    })}
                    style={{ marginTop: 12 }}
                />
            ) : null}
            {categoryResolution.additionalMatchedNames.length > 0 ? (
                <Alert
                    type="info"
                    showIcon
                    message={formatMessage(res.additionalCategoriesIgnored, {
                        category: categoryResolution.matchedName || "",
                        categories: categoryResolution.additionalMatchedNames.join(", "),
                    })}
                    style={{ marginTop: 12 }}
                />
            ) : null}
            {preview.invalidFields.length > 0 ? (
                <Alert
                    type="warning"
                    showIcon
                    message={formatMessage(res.invalidFields, { fields: preview.invalidFields.join(", ") })}
                    style={{ marginTop: 12 }}
                />
            ) : null}
            {ignoredFields.length > 0 ? (
                <Alert
                    type="info"
                    showIcon
                    message={formatMessage(res.ignoredFields, { fields: ignoredFields.join(", ") })}
                    style={{ marginTop: 12 }}
                />
            ) : null}

            <Divider titlePlacement="start" plain>
                {res.resources}
            </Divider>
            <Space direction="vertical" size={8} style={{ width: "100%" }}>
                {remoteImageCount > 0 ? (
                    <Alert
                        type="info"
                        showIcon
                        message={formatMessage(res.remoteImagesPreserved, {
                            count: remoteImageCount,
                            hosts: preview.resources.remoteHosts.join(", "),
                        })}
                        style={{ overflowWrap: "anywhere" }}
                    />
                ) : null}
                {relativeImageCount > 0 ? (
                    <Alert
                        type="warning"
                        showIcon
                        message={formatMessage(res.relativeImagesUnresolved, { count: relativeImageCount })}
                    />
                ) : null}
                {siteAbsoluteImageCount > 0 ? (
                    <Alert
                        type="warning"
                        showIcon
                        message={formatMessage(res.siteAbsoluteImagesPreserved, { count: siteAbsoluteImageCount })}
                    />
                ) : null}
                {otherUnsupportedImageCount > 0 ? (
                    <Alert
                        type="warning"
                        showIcon
                        message={formatMessage(res.unsupportedImages, { count: otherUnsupportedImageCount })}
                    />
                ) : null}
                {preview.resources.imageReferences.length === 0 ? (
                    <Typography.Text type="secondary">{res.noImages}</Typography.Text>
                ) : null}
            </Space>
        </Modal>
    );
};

export default MarkdownImportModal;
