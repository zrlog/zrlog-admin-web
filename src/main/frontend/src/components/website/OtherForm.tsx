import Divider from "antd/es/divider";
import Form from "antd/es/form";
import TextArea from "antd/es/input/TextArea";
import Button from "antd/es/button";
import { getRes } from "../../utils/constants";
import { useEffect, useState } from "react";

import { Other } from "./index";
import { getAppState } from "../../base/ConfigProviderApp";
import PreviewConfig from "../template/preview-config";

const layout = {
    labelCol: { span: 8 },
    wrapperCol: { span: 16 },
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

    useEffect(() => {
        setState(data);
        form.setFieldsValue(data);
    }, [data]);

    return (
        <Form
            form={form}
            {...layout}
            disabled={offline || offlineData}
            initialValues={data}
            onValuesChange={(_k, v) => setState({ ...state, ...v })}
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
            <PreviewConfig contentType={"html"} value={state.webCm} />
            <Form.Item
                name="robotRuleContent"
                label={getRes().websiteOther.robots}
                tooltip={getRes().websiteOther.robotsTip}
            >
                <TextArea rows={7} placeholder={"User-agent: *\n" + "Disallow: /admin/"} />
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

export default OtherForm;
