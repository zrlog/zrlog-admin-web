import { CloseOutlined, EditOutlined, EyeOutlined, FileMarkdownOutlined, HomeOutlined } from "@ant-design/icons";
import { Button, Typography } from "antd";
import { useTheme } from "antd-style";
import { Link } from "react-router-dom";
import { getRealRouteUrl, getRes } from "../../utils/constants";

type FirstUseChecklistProps = {
    dismissing: boolean;
    onDismiss: () => void;
};

const FirstUseChecklist = ({ dismissing, onDismiss }: FirstUseChecklistProps) => {
    const theme = useTheme();
    const res = getRes().index.firstUse;
    const homeUrl = new URL(getRes().homeUrl || "/", window.location.href);
    homeUrl.searchParams.set("spm", "admin-first-use");
    if (getRes().buildId) {
        homeUrl.searchParams.set("buildId", getRes().buildId as string);
    }
    const itemStyle: React.CSSProperties = {
        display: "flex",
        alignItems: "flex-start",
        gap: 10,
        minWidth: 0,
    };
    const iconStyle: React.CSSProperties = {
        alignItems: "center",
        background: theme.colorFillSecondary,
        borderRadius: theme.borderRadius,
        color: theme.colorTextSecondary,
        display: "flex",
        flex: "0 0 32px",
        height: 32,
        justifyContent: "center",
        width: 32,
    };

    return (
        <section
            aria-label={res.title}
            style={{
                borderTop: `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`,
                marginTop: 18,
                paddingTop: 16,
            }}
        >
            <div
                style={{
                    alignItems: "center",
                    display: "flex",
                    flexWrap: "wrap",
                    gap: 8,
                    justifyContent: "space-between",
                    marginBottom: 14,
                }}
            >
                <Typography.Text strong>{res.title}</Typography.Text>
                <Button type="text" size="small" icon={<CloseOutlined />} loading={dismissing} onClick={onDismiss}>
                    {res.dismiss}
                </Button>
            </div>
            <div
                role="list"
                style={{
                    display: "grid",
                    gap: 12,
                    gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 190px), 1fr))",
                }}
            >
                <div role="listitem" style={itemStyle}>
                    <span style={iconStyle}>
                        <HomeOutlined />
                    </span>
                    <div style={{ minWidth: 0 }}>
                        <Typography.Text strong style={{ display: "block" }}>
                            {res.viewSite}
                        </Typography.Text>
                        <Button
                            type="link"
                            size="small"
                            href={homeUrl.toString()}
                            target="_blank"
                            rel="noopener noreferrer"
                            icon={<EyeOutlined />}
                            style={{ height: "auto", padding: "4px 0" }}
                        >
                            {res.openSite}
                        </Button>
                    </div>
                </div>
                <div role="listitem" style={itemStyle}>
                    <span style={iconStyle}>
                        <EditOutlined />
                    </span>
                    <div style={{ minWidth: 0 }}>
                        <Typography.Text strong style={{ display: "block" }}>
                            {res.createOrImport}
                        </Typography.Text>
                        <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
                            <Link to={getRealRouteUrl("/article-edit")}>{res.createArticle}</Link>
                            <Link to={getRealRouteUrl("/article-edit?intent=import-markdown")}>
                                <FileMarkdownOutlined /> {res.importMarkdown}
                            </Link>
                        </div>
                    </div>
                </div>
                <div role="listitem" style={itemStyle}>
                    <span style={iconStyle}>
                        <EyeOutlined />
                    </span>
                    <div style={{ minWidth: 0 }}>
                        <Typography.Text strong style={{ display: "block" }}>
                            {res.previewAndPublish}
                        </Typography.Text>
                        <Typography.Text type="secondary" style={{ display: "block", marginTop: 4 }}>
                            {res.previewAndPublishTip}
                        </Typography.Text>
                    </div>
                </div>
            </div>
        </section>
    );
};

export default FirstUseChecklist;
