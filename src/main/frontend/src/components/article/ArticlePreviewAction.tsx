import { EditOutlined, EyeOutlined } from "@ant-design/icons";
import { Button, Drawer, Grid, Tooltip } from "antd";
import { useState } from "react";
import { Link } from "react-router-dom";
import { getRealRouteUrl, getRes } from "../../utils/constants";
import { getAppState } from "../../base/ConfigProviderApp";
import ArticlePreviewSnapshot from "./article-preview-snapshot";
import { markdownToHtmlSyncWithCallback } from "@editor/dist/editor/utils/marked-utils";

export type ArticleListEntry = {
    id: number;
    title: string;
    markdown?: string;
    digest?: string;
    keywords?: string;
};

export const ArticlePreviewAction = ({ article }: { article: ArticleListEntry }) => {
    const [visible, setVisible] = useState(false);
    const [previewHtml, setPreviewHtml] = useState("");
    const screens = Grid.useBreakpoint();
    const drawerWidth = screens.lg ? 800 : screens.md ? 640 : "100%";

    const handlePreview = () => {
        const html = markdownToHtmlSyncWithCallback(article.markdown || "", (nextHtml) => {
            setPreviewHtml(nextHtml);
        });
        setPreviewHtml(html);
        setVisible(true);
    };

    return (
        <>
            <Tooltip title={getRes().preview}>
                <Button
                    type="text"
                    size="small"
                    title={getRes().preview}
                    icon={<EyeOutlined style={{ color: getAppState().colorPrimary }} />}
                    onClick={handlePreview}
                />
            </Tooltip>

            <Drawer
                title={article.title || getRes().preview}
                extra={
                    <Link to={getRealRouteUrl("/article-edit?id=" + article.id)}>
                        <Button style={{ height: "auto" }} type="link" icon={<EditOutlined />}>
                            {getRes().edit}
                        </Button>
                    </Link>
                }
                width={drawerWidth}
                onClose={() => setVisible(false)}
                open={visible}
                styles={{ body: { height: "100%", marginBottom: 24 } }}
            >
                <ArticlePreviewSnapshot
                    htmlContent={previewHtml}
                    dark={getAppState().dark}
                    digest={article.digest}
                    digestLabel={getRes().article.previewSnapshot.digest}
                    keywords={article.keywords}
                    keywordsLabel={getRes().article.previewSnapshot.keywords}
                    emptyDescription={getRes().article.previewSnapshot.empty}
                />
            </Drawer>
        </>
    );
};
