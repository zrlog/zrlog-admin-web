import { Collapse, Space, Typography, theme } from "antd";
import { LoadingOutlined } from "@ant-design/icons";
import { getRes } from "../../../utils/constants";

const ArticleAiReasoning = ({
    content,
    thinking = false,
    status,
}: {
    content?: string;
    thinking?: boolean;
    status?: string;
}) => {
    const { token } = theme.useToken();
    if (!content && !status) return null;
    const progress = status ? (
        <Space role="status">
            <LoadingOutlined />
            <Typography.Text type="secondary">{status}</Typography.Text>
        </Space>
    ) : null;
    if (!content) return <div style={{ marginBottom: token.marginXS }}>{progress}</div>;
    return (
        <div style={{ marginBottom: token.marginXS }}>
            <Collapse
                size="small"
                ghost
                defaultActiveKey={thinking || status ? ["reasoning"] : []}
                items={[
                    {
                        key: "reasoning",
                        label: (
                            <Space>
                                {getRes().articleEdit.assistant.reasoningProcess}
                                {progress}
                            </Space>
                        ),
                        children: (
                            <Typography.Paragraph type="secondary" style={{ whiteSpace: "pre-wrap", marginBottom: 0 }}>
                                {content}
                            </Typography.Paragraph>
                        ),
                    },
                ]}
            />
        </div>
    );
};

export default ArticleAiReasoning;
