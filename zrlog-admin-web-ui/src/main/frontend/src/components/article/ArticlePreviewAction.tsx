import EditIcon from "@zrlog/ui/icons/edit";
import EyeIcon from "@zrlog/ui/icons/eye";
import {Button, Drawer, Grid, Space, theme, Tooltip} from "antd";
import {useState} from "react";
import {Link} from "react-router-dom";
import {getRealRouteUrl, getRes} from "../../utils/constants";
import {getAppState} from "../../base/ConfigProviderApp";
import ArticlePreviewSnapshot from "./article-preview-snapshot";
import {markdownToHtmlSyncWithCallback} from "@zrlog/editor/dist/editor/utils/marked-utils";
import {ArticlePdfAction} from "./ArticlePdfAction";

export type ArticlePrintableEntry = {
    title: string;
    markdown?: string;
    digest?: string;
    keywords?: string;
};

export type ArticleListEntry = ArticlePrintableEntry & {
    id: number;
};

export const ArticlePreviewAction = ({article}: { article: ArticleListEntry }) => {
    const [visible, setVisible] = useState(false);
    const [previewHtml, setPreviewHtml] = useState("");
    const screens = Grid.useBreakpoint();
    const {token} = theme.useToken();
    const isMobile = !screens.md;
    const drawerWidth = screens.lg ? 800 : screens.md ? 640 : "100%";
    const previewTitle = article.title || getRes().preview;

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
                    icon={<EyeIcon style={{color: getAppState().colorPrimary}}/>}
                    onClick={handlePreview}
                />
            </Tooltip>

            <Drawer
                title={<span title={previewTitle}>{previewTitle}</span>}
                extra={
                    <Space size={4} wrap>
                        <ArticlePdfAction article={article} buttonSize="middle" buttonType="link" showText/>
                        <Link to={getRealRouteUrl("/article-edit?id=" + article.id)}>
                            <Button style={{height: "auto"}} type="link" icon={<EditIcon/>}>
                                {getRes().edit}
                            </Button>
                        </Link>
                    </Space>
                }
                width={drawerWidth}
                onClose={() => setVisible(false)}
                open={visible}
                styles={{
                    header: isMobile
                        ? {
                            display: "grid",
                            gridTemplateColumns: "minmax(0, 1fr) auto",
                            rowGap: token.marginXS,
                        }
                        : undefined,
                    title: {
                        minWidth: 0,
                        display: "-webkit-box",
                        WebkitBoxOrient: "vertical",
                        WebkitLineClamp: 2,
                        overflow: "hidden",
                        overflowWrap: "anywhere",
                    },
                    extra: isMobile ? {gridColumn: "1 / -1", gridRow: 2, justifySelf: "end", minWidth: 0} : undefined,
                    close: isMobile ? {gridColumn: 2, gridRow: 1, alignSelf: "start"} : undefined,
                    body: {height: "100%", marginBottom: 24},
                }}
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
