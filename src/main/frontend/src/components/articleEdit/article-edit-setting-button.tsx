import { getRes } from "../../utils/constants";
import Col from "antd/es/grid/col";
import Row from "antd/es/grid/row";
import Card from "antd/es/card";
import ThumbnailUpload from "./thumbnail-upload";
import Form from "antd/es/form";
import Switch from "antd/es/switch";
import ArticleEditTag from "./article-edit-tag";
import { Drawer, InputRef } from "antd";
import { SettingFilled, SettingOutlined } from "@ant-design/icons";
import { RefObject, useEffect, useState } from "react";
import { ArticleChangeableValue, ArticleEntry } from "./index.types";
import Button from "antd/es/button";
import DigestEditorCard from "./digest-editor-card";
import { getAppState } from "../../base/ConfigProviderApp";
import { colorToRgba } from "../../layout/slider";
import { getShortcutTitle } from "./shortcut-utils";

const ArticleEditSettingButton = ({
    article,
    initDigest,
    saving,
    tags,
    containerRef,
    digestRef,
    handleValuesChange,
    open,
    onOpenChange,
}: {
    article: ArticleEntry;
    initDigest: string;
    saving: boolean;
    tags: any;
    containerRef: RefObject<HTMLDivElement>;
    digestRef: RefObject<InputRef>;
    handleValuesChange: (cv: ArticleChangeableValue) => void;
    open?: boolean;
    onOpenChange?: (open: boolean) => void;
}) => {
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

    return (
        <>
            <Button
                href={"#settings"}
                type={"text"}
                title={getShortcutTitle(getRes().articleEdit.settings, {
                    alt: true,
                    shift: true,
                    key: "S",
                })}
                style={{
                    border: 0,
                    display: "flex",
                    justifyContent: "center",
                    alignItems: "center",
                    cursor: "pointer",
                    color: "rgb(119, 119, 119)",
                }}
                icon={
                    settingsOpen ? (
                        <SettingFilled style={{ fontSize: getAppState().compactMode ? 18 : 24, display: "flex" }} />
                    ) : (
                        <SettingOutlined style={{ fontSize: getAppState().compactMode ? 18 : 24, display: "flex" }} />
                    )
                }
                onClick={(e) => {
                    e.stopPropagation();
                    e.preventDefault();
                    updateOpen(!settingsOpen);
                }}
            />
            <Drawer
                title={getRes().articleEdit.settings + (saving ? "[" + getRes().articleEdit.saving + "]" : "")}
                placement="right"
                autoFocus={false}
                keyboard={true}
                onClose={() => {
                    updateOpen(false);
                }}
                styles={{
                    header: {
                        padding: 12,
                        background: getAppState().dark ? "auto" : colorToRgba(getAppState().colorPrimary, 0.04),
                    },
                    body: {
                        padding: 12,
                        overflowX: "hidden",
                        background: getAppState().dark ? "auto" : colorToRgba(getAppState().colorPrimary, 0.04),
                    },
                }}
                open={settingsOpen}
                //@ts-ignore
                getContainer={() => {
                    return containerRef.current;
                }}
            >
                <Col md={24} sm={24} xs={24} style={{ overflow: "hidden" }}>
                    <Row gutter={[8, 8]}>
                        <Col span={24}>
                            <Card
                                title={
                                    <span style={{ textAlign: "start", display: "flex" }}>
                                        {getRes().articleEdit.cover}
                                    </span>
                                }
                                style={{ textAlign: "center", marginTop: 6 }}
                            >
                                <ThumbnailUpload
                                    //@ts-ignore
                                    getContainer={() => {
                                        return containerRef.current;
                                    }}
                                    thumbnail={article.thumbnail}
                                    onChange={(e) => {
                                        handleValuesChange({ thumbnail: e });
                                    }}
                                />
                            </Card>
                        </Col>
                        <Col span={24}>
                            <Card title={getRes().articleEdit.settings}>
                                <Row>
                                    <Col xs={24} md={12}>
                                        <Form.Item
                                            style={{ marginBottom: 0 }}
                                            valuePropName="checked"
                                            label={getRes().articleEdit.commentAble}
                                        >
                                            <Switch
                                                value={article.canComment}
                                                onChange={(checked) => {
                                                    handleValuesChange({ canComment: checked });
                                                }}
                                            />
                                        </Form.Item>
                                    </Col>
                                    <Col xs={24} md={12}>
                                        <Form.Item
                                            style={{ marginBottom: 0 }}
                                            valuePropName="checked"
                                            label={getRes().articleEdit.status.private}
                                        >
                                            <Switch
                                                value={article.privacy}
                                                onChange={(checked) => {
                                                    handleValuesChange({ privacy: checked });
                                                }}
                                            />
                                        </Form.Item>
                                    </Col>
                                </Row>
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
                                />
                            </Card>
                        </Col>
                        <Col span={24}>
                            <DigestEditorCard
                                digestRef={digestRef}
                                initDigest={initDigest}
                                handleValuesChange={handleValuesChange}
                            />
                        </Col>
                    </Row>
                </Col>
            </Drawer>
        </>
    );
};
export default ArticleEditSettingButton;
