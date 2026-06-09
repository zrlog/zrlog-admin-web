import { FunctionComponent } from "react";
import { Empty, List, Space, Typography } from "antd";
import { AssistantTool, AssistantToolButton, AssistantToolGroup } from "./article-ai-assistant.types";

type SkillPanelProps = {
    emptyDescription: string;
    groups: AssistantToolGroup[];
    tools: AssistantToolButton[];
    selectedTool?: AssistantTool;
    theme: any;
    onSelect: (tool: AssistantToolButton) => void;
};

const ArticleAiAssistantSkillPanel: FunctionComponent<SkillPanelProps> = ({
    emptyDescription,
    groups,
    tools,
    selectedTool,
    theme,
    onSelect,
}) => {
    if (tools.length === 0) {
        return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyDescription} />;
    }
    return (
        <div
            style={{
                maxHeight: 260,
                overflowY: "auto",
                paddingInline: 2,
            }}
        >
            {groups.map((group) => {
                const groupTools = tools.filter((tool) => tool.group === group.key);
                if (groupTools.length === 0) {
                    return null;
                }
                return (
                    <div key={group.key} style={{ marginBottom: 6 }}>
                        <Typography.Text
                            type="secondary"
                            style={{
                                display: "block",
                                fontSize: 12,
                                lineHeight: "20px",
                                paddingInline: 6,
                            }}
                        >
                            {group.label}
                        </Typography.Text>
                        <List
                            size="small"
                            dataSource={groupTools}
                            split={false}
                            renderItem={(tool) => {
                                const active = selectedTool === tool.key;
                                return (
                                    <List.Item
                                        key={tool.key}
                                        title={tool.disabledReason || `/${tool.command}`}
                                        style={{
                                            cursor: tool.disabled ? "not-allowed" : "pointer",
                                            minHeight: 36,
                                            padding: "4px 6px",
                                            borderRadius: theme.borderRadius,
                                            display: "flex",
                                            alignItems: "center",
                                            gap: 8,
                                            opacity: tool.disabled ? 0.58 : 1,
                                            background: active ? theme.colorFillSecondary : "transparent",
                                        }}
                                        onClick={() => {
                                            if (!tool.disabled) {
                                                onSelect(tool);
                                            }
                                        }}
                                    >
                                        <span
                                            style={{
                                                display: "inline-flex",
                                                color: theme.colorTextSecondary,
                                            }}
                                        >
                                            {tool.icon}
                                        </span>
                                        <Space
                                            size={4}
                                            style={{
                                                flex: 1,
                                                minWidth: 0,
                                            }}
                                        >
                                            <Typography.Text ellipsis style={{ fontSize: 13 }}>
                                                {tool.label}
                                            </Typography.Text>
                                            <Typography.Text
                                                type="secondary"
                                                style={{
                                                    fontSize: 12,
                                                    whiteSpace: "nowrap",
                                                }}
                                            >
                                                /{tool.command}
                                            </Typography.Text>
                                        </Space>
                                    </List.Item>
                                );
                            }}
                        />
                    </div>
                );
            })}
        </div>
    );
};

export default ArticleAiAssistantSkillPanel;
