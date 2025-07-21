import { EyeOutlined, EditOutlined } from "@ant-design/icons";
import { Button, Drawer, Spin, Tooltip, Typography } from "antd";
import { useState } from "react";
import { Link } from "react-router-dom";
import { getRealRouteUrl, getRes } from "../../utils/constants";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getAppState } from "../../base/ConfigProviderApp";
import HtmlPreviewPanel from "@editor/dist/src/editor/html-preview-panel";

export const ArticlePreviewAction = ({ id }: { id: number }) => {
    const [visible, setVisible] = useState(false);
    const [loading, setLoading] = useState(false);
    const [previewData, setPreviewData] = useState<any>(null);
    const axiosInstance = useAxiosBaseInstance();

    const handlePreview = async () => {
        setVisible(true);
        if (previewData && previewData.logId === id) {
            return;
        }
        setLoading(true);
        setPreviewData(null);
        try {
            const { data } = await axiosInstance.get("/api/admin/article/articleEdit?id=" + id);
            if (data.data) {
                setPreviewData(data.data.article);
            }
        } finally {
            setLoading(false);
        }
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
                title={
                    <div
                        style={{
                            display: "flex",
                            justifyContent: "space-between",
                            alignItems: "center",
                            width: "100%",
                            paddingRight: 24,
                        }}
                    >
                        <Typography.Title level={4} style={{ margin: 0 }}>
                            {previewData?.title || getRes().preview}
                        </Typography.Title>
                        {previewData && (
                            <Link to={getRealRouteUrl("/article-edit?id=" + previewData.logId)}>
                                <Button type="link" icon={<EditOutlined />}>
                                    {getRes().edit}
                                </Button>
                            </Link>
                        )}
                    </div>
                }
                width={800}
                onClose={() => setVisible(false)}
                open={visible}
                styles={{ body: { height: "100%" } }}
            >
                <style>
                    {`
                    .preview-spin, .preview-spin > .ant-spin-container {
                        height: 100%;
                    }
                `}
                </style>
                <Spin spinning={loading} wrapperClassName="preview-spin">
                    {previewData && (
                        <HtmlPreviewPanel
                            htmlContent={previewData.content || ""}
                            dark={getAppState().dark}
                            style={{ height: "100%", overflow: "auto" }}
                        />
                    )}
                </Spin>
            </Drawer>
        </>
    );
};
