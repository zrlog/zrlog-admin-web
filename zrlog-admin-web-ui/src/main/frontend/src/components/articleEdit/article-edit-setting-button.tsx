import Switch from "@zrlog/ui/antd/Switch";
import Input from "@zrlog/ui/antd/Input";
import SettingsIcon from "@zrlog/ui/icons/settings";
import { getRes } from "../../utils/constants";
import Col from "antd/es/grid/col";
import Row from "antd/es/grid/row";
import Card from "antd/es/card";
import ThumbnailUpload from "./thumbnail-upload";
import Form from "antd/es/form";

import ArticleEditTag from "./article-edit-tag";
import { Drawer, InputRef } from "antd";

import { RefObject, useEffect, useId, useState } from "react";
import { ArticleChangeableValue, ArticleEntry } from "./index.types";
import Button from "antd/es/button";
import DigestEditorCard from "./digest-editor-card";
import { getShortcutTitle } from "./shortcut-utils";
import { parseCoverAspectRatio } from "./cover-aspect-ratio";
import { useTheme } from "antd-style";
import BaseInput from "../../common/BaseInput";
import useArticleEditorScreens from "./use-article-editor-screens";

const ArticleEditSettingButton = ({
    article,
    initDigest,
    saving,
    tags,
    containerRef,
    digestRef,
    aliasRef,
    aliasInputKey,
    handleValuesChange,
    open,
    onOpenChange,
    generatingDigest,
    onGenerateDigest,
    generatingTags,
    onGenerateTags,
    coverAspectRatio,
}: {
    article: ArticleEntry;
    initDigest: string;
    saving: boolean;
    tags: any;
    containerRef: RefObject<HTMLDivElement>;
    digestRef: RefObject<InputRef>;
    aliasRef: RefObject<InputRef>;
    aliasInputKey: string;
    handleValuesChange: (cv: ArticleChangeableValue) => void;
    open?: boolean;
    onOpenChange?: (open: boolean) => void;
    generatingDigest?: boolean;
    onGenerateDigest?: () => void;
    generatingTags?: boolean;
    onGenerateTags?: () => void;
    coverAspectRatio?: string;
}) => {
    const screens = useArticleEditorScreens();
    const theme = useTheme();
    const settingsId = useId();
    const [innerOpen, setInnerOpen] = useState(false);
    const settingsOpen = open ?? innerOpen;

    useEffect(() => {
        if (open !== undefined) {
            setInnerOpen(open);
        }
    }, [open]);

    const updateOpen = (nextOpen: boolean) => {
        if (open === undefined) {
            setInnerOpen(nextOpen);
        }
        onOpenChange?.(nextOpen);
    };
    const getContainer = () => containerRef.current ?? document.body;
    const arrangement = article.arrange_plugin ?? "";
    const arrangementInvalid = !!arrangement && !/^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$/.test(arrangement);

    return (
        <>
            <Button
                href={"#settings"}
                type="default"
                aria-label={getRes().articleEdit.settings}
                title={getShortcutTitle(getRes().articleEdit.settings, {
                    alt: true,
                    shift: true,
                    key: "S",
                })}
                icon={<SettingsIcon selected={settingsOpen} />}
                onClick={(e) => {
                    e.stopPropagation();
                    e.preventDefault();
                    updateOpen(!settingsOpen);
                }}
            />
            <Drawer
                title={saving ? getRes().articleEdit.saving : undefined}
                aria-label={getRes().articleEdit.settings}
                placement="right"
                autoFocus={false}
                keyboard={true}
                onClose={() => {
                    updateOpen(false);
                }}
                styles={{
                    header: {
                        padding: 12,
                        background: theme.colorBgContainer,
                    },
                    body: {
                        padding: 12,
                        overflowX: "hidden",
                        background: theme.colorBgLayout,
                    },
                }}
                open={settingsOpen}
                getContainer={getContainer}
                width={screens.sm ? undefined : "100%"}
            >
                <Col md={24} sm={24} xs={24} style={{ overflow: "hidden" }}>
                    <Row gutter={[8, 8]}>
                        <Col span={24}>
                            <ThumbnailUpload
                                getContainer={getContainer}
                                title={getRes().articleEdit.cover}
                                thumbnail={article.thumbnail}
                                aspectRatio={parseCoverAspectRatio(coverAspectRatio)}
                                onChange={(e) => {
                                    handleValuesChange({ thumbnail: e });
                                }}
                            />
                        </Col>
                        <Col span={24}>
                            <Card title={getRes().articleEdit.articleLink}>
                                <Form.Item label={getRes().alias} style={{ marginBottom: 0 }}>
                                    <BaseInput
                                        ref={aliasRef}
                                        key={aliasInputKey}
                                        defaultValue={article.alias}
                                        onChange={(value) => handleValuesChange({ alias: value })}
                                        maxLength={256}
                                        placeholder={getRes().articleEdit.inputAlias}
                                    />
                                </Form.Item>
                            </Card>
                        </Col>
                        {getRes().feature_article_arrangement_enabled === true && (
                            <Col span={24}>
                                <Card title={getRes().articleEdit.arrangement.title}>
                                    <Form.Item
                                        label={getRes().articleEdit.arrangement.plugin}
                                        layout="vertical"
                                        htmlFor="article-arrangement-plugin"
                                        style={{ marginBottom: 0 }}
                                        validateStatus={arrangementInvalid ? "error" : undefined}
                                        help={
                                            <span id="article-arrangement-help">
                                                {arrangementInvalid
                                                    ? getRes().articleEdit.arrangement.invalid
                                                    : getRes().articleEdit.arrangement.help}
                                            </span>
                                        }
                                    >
                                        <Input
                                            id="article-arrangement-plugin"
                                            value={arrangement}
                                            maxLength={64}
                                            allowClear
                                            aria-invalid={arrangementInvalid}
                                            aria-describedby="article-arrangement-help"
                                            placeholder={getRes().articleEdit.arrangement.placeholder}
                                            onChange={(event) =>
                                                handleValuesChange({ arrange_plugin: event.target.value })
                                            }
                                        />
                                    </Form.Item>
                                </Card>
                            </Col>
                        )}
                        <Col span={24}>
                            <Card title={getRes().articleEdit.settings}>
                                <Form component="div" layout="inline" style={{ gap: theme.marginSM }}>
                                    <Form.Item
                                        style={{ margin: 0 }}
                                        valuePropName="checked"
                                        label={getRes().articleEdit.comment}
                                        htmlFor={`${settingsId}-comment`}
                                    >
                                        <Switch
                                            id={`${settingsId}-comment`}
                                            value={article.canComment}
                                            onChange={(checked) => {
                                                handleValuesChange({ canComment: checked });
                                            }}
                                        />
                                    </Form.Item>
                                    <Form.Item
                                        style={{ margin: 0 }}
                                        valuePropName="checked"
                                        label={getRes().articleEdit.recommended}
                                        htmlFor={`${settingsId}-recommended`}
                                    >
                                        <Switch
                                            id={`${settingsId}-recommended`}
                                            value={article.recommended}
                                            onChange={(checked) => {
                                                handleValuesChange({ recommended: checked });
                                            }}
                                        />
                                    </Form.Item>
                                    <Form.Item
                                        style={{ margin: 0 }}
                                        valuePropName="checked"
                                        label={getRes().articleEdit.status.private}
                                        htmlFor={`${settingsId}-private`}
                                    >
                                        <Switch
                                            id={`${settingsId}-private`}
                                            value={article.privacy}
                                            onChange={(checked) => {
                                                handleValuesChange({ privacy: checked });
                                            }}
                                        />
                                    </Form.Item>
                                </Form>
                            </Card>
                        </Col>
                        <Col span={24}>
                            <Card title={getRes().articleEdit.tag.label}>
                                <ArticleEditTag
                                    onKeywordsChange={(text: string) => {
                                        handleValuesChange({ keywords: text });
                                    }}
                                    keywords={article!.keywords ? article.keywords : ""}
                                    allTags={tags.map((x: { text: any }) => x.text)}
                                    generatingTags={generatingTags}
                                    onGenerateTags={onGenerateTags}
                                />
                            </Card>
                        </Col>
                        <Col span={24}>
                            <DigestEditorCard
                                digestRef={digestRef}
                                initDigest={initDigest}
                                handleValuesChange={handleValuesChange}
                                generatingDigest={generatingDigest}
                                onGenerateDigest={onGenerateDigest}
                            />
                        </Col>
                    </Row>
                </Col>
            </Drawer>
        </>
    );
};
export default ArticleEditSettingButton;
