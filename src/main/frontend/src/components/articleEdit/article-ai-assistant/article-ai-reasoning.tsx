import { Collapse, Typography, theme } from "antd";
import { getRes } from "../../../utils/constants";

const ArticleAiReasoning = ({ content, thinking = false }: { content?: string; thinking?: boolean }) => {
    const { token } = theme.useToken();
    if (!content) return null;
    return (
        <div style={{ marginBottom: token.marginXS }}>
            <Collapse
                size="small"
                ghost
                defaultActiveKey={thinking ? ["reasoning"] : []}
                items={[
                    {
                        key: "reasoning",
                        label: getRes().articleEdit.assistant.reasoningProcess,
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
