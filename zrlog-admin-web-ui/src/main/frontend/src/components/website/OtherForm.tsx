import Form from "antd/es/form";
import TextArea from "antd/es/input/TextArea";
import Button from "antd/es/button";
import Alert from "antd/es/alert";
import Space from "antd/es/space";
import Typography from "antd/es/typography";
import Tag from "antd/es/tag";
import { PlusOutlined } from "@ant-design/icons";
import { getRes } from "../../utils/constants";
import { useEffect, useState } from "react";

import { Other } from "./index";
import { getAppState } from "../../base/ConfigProviderApp";
import PreviewConfig from "../template/preview-config";
import { useTheme } from "antd-style";
import { useResponsiveFormLayout } from "../../utils/responsive-form";
import WebsiteSubmitBar from "./WebsiteSubmitBar";

const layout = {
    labelCol: { span: 8 },
    wrapperCol: { span: 16 },
};

type RobotsGroup = {
    agents: string[];
    disallows: string[];
    allows: string[];
};

const parseRobotsGroups = (value?: string): RobotsGroup[] => {
    const groups: RobotsGroup[] = [];
    let currentGroup: RobotsGroup | undefined;

    (value || "").split(/\r?\n/).forEach((rawLine) => {
        const line = rawLine.replace(/#.*/, "").trim();
        if (!line) {
            return;
        }

        const separatorIndex = line.indexOf(":");
        if (separatorIndex < 0) {
            return;
        }

        const key = line.slice(0, separatorIndex).trim().toLowerCase();
        const directiveValue = line.slice(separatorIndex + 1).trim();

        if (key === "user-agent") {
            if (!currentGroup || currentGroup.disallows.length > 0 || currentGroup.allows.length > 0) {
                currentGroup = {
                    agents: [],
                    disallows: [],
                    allows: [],
                };
                groups.push(currentGroup);
            }
            currentGroup.agents.push(directiveValue.toLowerCase());
            return;
        }

        if (!currentGroup) {
            return;
        }

        if (key === "disallow") {
            currentGroup.disallows.push(directiveValue);
        }
        if (key === "allow") {
            currentGroup.allows.push(directiveValue);
        }
    });

    return groups;
};

const normalizeRobotsPath = (value: string) => {
    const trimmedValue = value.trim();
    if (trimmedValue === "/") {
        return trimmedValue;
    }
    return trimmedValue.replace(/\/+$/, "");
};
const isAdminDisallowPath = (value: string) => {
    const normalizedPath = normalizeRobotsPath(value);
    return normalizedPath === "/admin" || normalizedPath.startsWith("/admin/");
};
const isRootDisallowPath = (value: string) => {
    const normalizedPath = normalizeRobotsPath(value);
    return normalizedPath === "/" || normalizedPath === "/*";
};

const OtherForm = ({
    data,
    offline,
    offlineData,
    onSubmit,
    loading,
}: {
    data: Other;
    offline: boolean;
    offlineData: boolean;
    onSubmit: (data: Other) => void;
    loading?: boolean;
}) => {
    const [state, setState] = useState<Other>(data);
    const [form] = Form.useForm();
    const theme = useTheme();
    const { formLayout } = useResponsiveFormLayout(layout);

    const border = `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorder}`;
    const aiCrawlerPolicySnippet = getRes().websiteOther.aiCrawlerTrainingPolicySnippet;
    const hasAiCrawlerTrainingPolicy = (value?: string) => /(^|\n)\s*User-agent:\s*GPTBot\s*(\n|$)/i.test(value || "");
    const aiCrawlerPolicyAlreadyAdded = hasAiCrawlerTrainingPolicy(state.robotRuleContent);
    const robotsGroups = parseRobotsGroups(state.robotRuleContent);
    const robotsContent = state.robotRuleContent || "";
    const hasRobotsContent = robotsContent.trim().length > 0;
    const hasSitemapDirective = /^sitemap\s*:/im.test(robotsContent);
    const defaultAgentGroups = robotsGroups.filter((group) => group.agents.includes("*"));
    const hasDefaultAgent = defaultAgentGroups.length > 0;
    const defaultAgentDisallowsAdmin = defaultAgentGroups.some((group) =>
        group.disallows.some((disallowPath) => isAdminDisallowPath(disallowPath))
    );
    const defaultAgentDisallowsRoot = defaultAgentGroups.some((group) =>
        group.disallows.some((disallowPath) => isRootDisallowPath(disallowPath))
    );
    const hasExplicitOpenAiSearchPolicy = robotsGroups.some((group) => group.agents.includes("oai-searchbot"));
    const hasExplicitChatGptUserPolicy = robotsGroups.some((group) => group.agents.includes("chatgpt-user"));
    const robotsPreviewItems = hasRobotsContent
        ? [
              {
                  key: "defaultAgent",
                  color: hasDefaultAgent ? "success" : "warning",
                  label: hasDefaultAgent
                      ? getRes().websiteOther.robotsPreview.defaultAgentPresent
                      : getRes().websiteOther.robotsPreview.defaultAgentMissing,
              },
              {
                  key: "adminPath",
                  color: defaultAgentDisallowsAdmin ? "success" : "warning",
                  label: defaultAgentDisallowsAdmin
                      ? getRes().websiteOther.robotsPreview.adminPathProtected
                      : getRes().websiteOther.robotsPreview.adminPathMissing,
              },
              {
                  key: "siteWideBlock",
                  color: defaultAgentDisallowsRoot ? "error" : "success",
                  label: defaultAgentDisallowsRoot
                      ? getRes().websiteOther.robotsPreview.siteWideBlockDetected
                      : getRes().websiteOther.robotsPreview.siteWideBlockClear,
              },
              {
                  key: "sitemap",
                  color: hasSitemapDirective ? "success" : "default",
                  label: hasSitemapDirective
                      ? getRes().websiteOther.robotsPreview.sitemapPresent
                      : getRes().websiteOther.robotsPreview.sitemapMissing,
              },
              {
                  key: "gptbot",
                  color: aiCrawlerPolicyAlreadyAdded ? "processing" : "default",
                  label: aiCrawlerPolicyAlreadyAdded
                      ? getRes().websiteOther.robotsPreview.gptBotPolicyPresent
                      : getRes().websiteOther.robotsPreview.gptBotPolicyMissing,
              },
              {
                  key: "openAiSearch",
                  color: hasExplicitOpenAiSearchPolicy ? "warning" : "default",
                  label: hasExplicitOpenAiSearchPolicy
                      ? getRes().websiteOther.robotsPreview.openAiSearchExplicit
                      : getRes().websiteOther.robotsPreview.openAiSearchNeutral,
              },
              {
                  key: "chatGptUser",
                  color: hasExplicitChatGptUserPolicy ? "warning" : "default",
                  label: hasExplicitChatGptUserPolicy
                      ? getRes().websiteOther.robotsPreview.chatGptUserExplicit
                      : getRes().websiteOther.robotsPreview.chatGptUserNeutral,
              },
          ]
        : [
              {
                  key: "empty",
                  color: "warning",
                  label: getRes().websiteOther.robotsPreview.empty,
              },
          ];

    const appendAiCrawlerPolicySnippet = () => {
        const currentValue = form.getFieldValue("robotRuleContent") || state.robotRuleContent || "";
        if (hasAiCrawlerTrainingPolicy(currentValue)) {
            return;
        }
        const nextValue = [currentValue.trimEnd(), aiCrawlerPolicySnippet].filter(Boolean).join("\n\n");
        form.setFieldsValue({ robotRuleContent: nextValue });
        setState((prevState) => ({ ...prevState, robotRuleContent: nextValue }));
    };

    useEffect(() => {
        setState(data);
        form.setFieldsValue(data);
    }, [data]);

    return (
        <Form
            form={form}
            {...formLayout}
            disabled={offline || offlineData}
            initialValues={data}
            onValuesChange={(_k, v) => setState((prevState) => ({ ...prevState, ...v }))}
            onFinish={(nv) => onSubmit({ ...state, ...nv })}
        >
            {getAppState().lang == "zh_CN" && (
                <Form.Item name="icp" label={getRes().websiteOther.icp} tooltip={getRes().websiteOther.icpTip}>
                    <TextArea />
                </Form.Item>
            )}
            <Form.Item
                name="webCm"
                label={getRes().websiteOther.statistics}
                tooltip={getRes().websiteOther.statisticsTip}
            >
                <TextArea rows={7} />
            </Form.Item>
            <PreviewConfig contentType={"html"} value={state.webCm} style={{ border: border }} />
            <Form.Item
                name="robotRuleContent"
                label={getRes().websiteOther.robots}
                tooltip={getRes().websiteOther.robotsTip}
            >
                <TextArea rows={7} placeholder={getRes().websiteOther.robotsPlaceholder} />
            </Form.Item>
            <Form.Item label={getRes().websiteOther.robotsPreview.title}>
                <Space direction="vertical" size={theme.marginXS} style={{ width: "100%" }}>
                    <Typography.Text type="secondary">{getRes().websiteOther.robotsPreview.desc}</Typography.Text>
                    <Space wrap size={[theme.marginXS, theme.marginXS]}>
                        {robotsPreviewItems.map((item) => (
                            <Tag key={item.key} color={item.color}>
                                {item.label}
                            </Tag>
                        ))}
                    </Space>
                </Space>
            </Form.Item>
            <Form.Item label={getRes().websiteOther.aiCrawlerPolicy}>
                <Space direction="vertical" size={8} style={{ width: "100%" }}>
                    <Alert
                        type="info"
                        showIcon
                        message={getRes().websiteOther.aiCrawlerPolicyTitle}
                        description={getRes().websiteOther.aiCrawlerPolicyDesc}
                    />
                    <Typography.Text type="secondary">
                        {getRes().websiteOther.aiCrawlerPolicySnippetLabel}
                    </Typography.Text>
                    <Typography.Text
                        code
                        style={{
                            background: theme.colorFillQuaternary,
                            border,
                            borderRadius: theme.borderRadius,
                            display: "block",
                            padding: 12,
                            whiteSpace: "pre-wrap",
                        }}
                    >
                        {aiCrawlerPolicySnippet}
                    </Typography.Text>
                    <Button
                        size="small"
                        icon={<PlusOutlined />}
                        disabled={offline || offlineData || aiCrawlerPolicyAlreadyAdded}
                        onClick={appendAiCrawlerPolicySnippet}
                    >
                        {aiCrawlerPolicyAlreadyAdded
                            ? getRes().websiteOther.aiCrawlerPolicyAlreadyAdded
                            : getRes().websiteOther.aiCrawlerPolicyAppend}
                    </Button>
                </Space>
            </Form.Item>

            <WebsiteSubmitBar loading={loading} disabled={offline || offlineData} />
        </Form>
    );
};

export default OtherForm;
