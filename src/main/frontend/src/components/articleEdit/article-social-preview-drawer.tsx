import { Drawer, Empty, Grid, Space, Tag, theme, Typography } from "antd";
import { FunctionComponent, RefObject } from "react";
import { getRes } from "../../utils/constants";
import { SocialPreview } from "./index.types";
import BackendImage from "../../common/BackendImage";

const { Text, Title, Paragraph } = Typography;

type ArticleSocialPreviewDrawerProps = {
    preview?: SocialPreview;
    containerRef: RefObject<HTMLDivElement>;
    open: boolean;
    onOpenChange: (open: boolean) => void;
};

const getHostText = (url?: string) => {
    if (!url) {
        return window.location.host;
    }
    try {
        return new URL(url, window.location.origin).host;
    } catch (e) {
        return window.location.host;
    }
};

const ArticleSocialPreviewDrawer: FunctionComponent<ArticleSocialPreviewDrawerProps> = ({
    preview,
    containerRef,
    open,
    onOpenChange,
}) => {
    const { token } = theme.useToken();
    const screens = Grid.useBreakpoint();
    const res = getRes().articleEdit.socialPreview;
    const border = `${token.lineWidth}px ${token.lineType} ${token.colorBorderSecondary}`;
    const drawerWidth = screens.lg ? 520 : screens.md ? 480 : "100%";

    return (
        <Drawer
            title={res.title}
            width={drawerWidth}
            open={open}
            onClose={() => onOpenChange(false)}
            getContainer={() => containerRef.current ?? document.body}
        >
            {preview ? (
                <Space direction="vertical" size={16} style={{ width: "100%" }}>
                    <div
                        style={{
                            border,
                            borderRadius: token.borderRadius,
                            overflow: "hidden",
                            background: token.colorBgContainer,
                        }}
                    >
                        {preview.image ? (
                            <BackendImage
                                src={preview.image}
                                alt={res.imageAlt}
                                preview={false}
                                style={{ width: "100%", aspectRatio: "1.91 / 1", objectFit: "cover", display: "block" }}
                            />
                        ) : (
                            <div
                                style={{
                                    aspectRatio: "1.91 / 1",
                                    display: "flex",
                                    alignItems: "center",
                                    justifyContent: "center",
                                    padding: token.paddingLG,
                                    background: token.colorFillAlter,
                                    color: token.colorTextTertiary,
                                }}
                            >
                                {res.noImage}
                            </div>
                        )}
                        <div style={{ padding: token.paddingLG }}>
                            <Text
                                type="secondary"
                                style={{ display: "block", marginBottom: 8, textTransform: "uppercase" }}
                            >
                                {getHostText(preview.url)}
                            </Text>
                            <Title level={4} style={{ marginTop: 0, marginBottom: 8 }}>
                                {preview.title || res.untitled}
                            </Title>
                            <Paragraph type="secondary" ellipsis={{ rows: 3 }} style={{ marginBottom: 0 }}>
                                {preview.description || res.autoDescription}
                            </Paragraph>
                        </div>
                    </div>
                    <Space direction="vertical" size={8} style={{ width: "100%" }}>
                        <Space wrap>
                            <Tag color={preview.image ? "success" : "warning"}>
                                {preview.image ? res.coverReady : res.coverMissing}
                            </Tag>
                            <Tag>{`twitter:card ${preview.twitterCard}`}</Tag>
                        </Space>
                        <Text type="secondary">{res.rule}</Text>
                        <div
                            style={{
                                border,
                                borderRadius: token.borderRadiusSM,
                                padding: token.padding,
                                background: token.colorFillAlter,
                            }}
                        >
                            <Space direction="vertical" size={6} style={{ width: "100%" }}>
                                <Text>
                                    <Text type="secondary">og:title: </Text>
                                    {preview.title}
                                </Text>
                                <Text>
                                    <Text type="secondary">og:description: </Text>
                                    {preview.description}
                                </Text>
                                <Text>
                                    <Text type="secondary">og:site_name: </Text>
                                    {preview.siteName}
                                </Text>
                                <Text>
                                    <Text type="secondary">twitter:card: </Text>
                                    {preview.twitterCard}
                                </Text>
                            </Space>
                        </div>
                    </Space>
                </Space>
            ) : (
                <Empty description={res.empty} />
            )}
        </Drawer>
    );
};

export default ArticleSocialPreviewDrawer;
