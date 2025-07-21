import Divider from "antd/es/divider";
import Form from "antd/es/form";
import TextArea from "antd/es/input/TextArea";
import Button from "antd/es/button";
import { getRes } from "../../utils/constants";
import { useEffect, useState } from "react";

import { AI } from "./index";
import Select, { DefaultOptionType } from "antd/es/select";
import { Input } from "antd";
import AIIcon from "@editor/dist/src/ai/AIIcon";

const layout = {
    labelCol: { span: 8 },
    wrapperCol: { span: 16 },
};

const AIForm = ({
    data,
    offline,
    offlineData,
    onSubmit,
    loading,
}: {
    data: AI;
    offline: boolean;
    offlineData: boolean;
    onSubmit: (data: AI) => void;
    loading?: boolean;
}) => {
    const [state, setState] = useState<AI>(data);
    const [form] = Form.useForm();

    const getModelOptions = (): DefaultOptionType[] => {
        return data.allProviders
            .filter((e) => {
                return state.ai_provider === e.name;
            })
            .map((e) => {
                return e.models.map((e) => {
                    return {
                        label: e,
                        value: e,
                    } as DefaultOptionType;
                });
            })[0];
    };

    const getAiProviderOptions = (): DefaultOptionType[] => {
        return data.allProviders.map((e) => {
            return {
                label: (
                    <div style={{ display: "flex", alignItems: "center", gap: 4 }}>
                        <AIIcon name={e.name} />
                        {e.name.toLowerCase().replace("_", "")}
                    </div>
                ),
                value: e.name,
            } as DefaultOptionType;
        });
    };

    useEffect(() => {
        setState(data);
        form.setFieldsValue(data);
    }, [data]);

    useEffect(() => {
        setState((prevState) => {
            return {
                ...prevState,
                ai_model: "",
            };
        });
    }, [state.ai_provider]);

    return (
        <Form
            form={form}
            {...layout}
            disabled={offline || offlineData}
            initialValues={data}
            onValuesChange={(_k, v) => setState({ ...state, ...v })}
            onFinish={(nv) => onSubmit({ ...state, ...nv })}
        >
            <Form.Item name="ai_provider" label={getRes().websiteAi.aiProvider} required={true}>
                <Select style={{ maxWidth: 200 }} options={getAiProviderOptions()} />
            </Form.Item>
            <Form.Item name={"ai_model"} label={getRes().websiteAi.aiModel} required={true}>
                <Select style={{ maxWidth: 200 }} options={getModelOptions()} />
            </Form.Item>
            <Form.Item
                name={"ai_api_key"}
                label={getRes().websiteAi.aiApiKey}
                tooltip={getRes().websiteAi.aiApiKeyTip}
                required={true}
            >
                <Input />
            </Form.Item>
            <Form.Item name={"ai_prompt"} label={getRes().websiteAi.aiPrompt} tooltip={getRes().websiteAi.aiPromptTip}>
                <TextArea rows={7} />
            </Form.Item>
            <Divider />
            <Button
                enterKeyHint={"enter"}
                disabled={offline || offlineData}
                loading={loading}
                type="primary"
                htmlType="submit"
            >
                {getRes().submit}
            </Button>
        </Form>
    );
};

export default AIForm;
