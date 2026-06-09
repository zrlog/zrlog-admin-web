import Form from "antd/es/form";
import Input from "antd/es/input";
import TextArea from "antd/es/input/TextArea";
import Button from "antd/es/button";
import { getRes } from "../../utils/constants";
import { useEffect, useState } from "react";
import { Basic } from "./index";
import FaviconUpload from "./FaviconUpload";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { message } from "antd";
import { BulbOutlined } from "@ant-design/icons";
import { getAppState } from "../../base/ConfigProviderApp";
import { useResponsiveFormLayout } from "../../utils/responsive-form";
import WebsiteSubmitBar from "./WebsiteSubmitBar";

const layout = {
    labelCol: { span: 8 },
    wrapperCol: { span: 16 },
};

const BasicForm = ({
    data,
    offlineData,
    offline,
    onSubmit,
    loading,
}: {
    data: Basic;
    offlineData: boolean;
    offline: boolean;
    loading?: boolean;
    onSubmit: (data: Basic) => void;
}) => {
    const [state, setState] = useState<Basic>(data);
    const [optimizingDescription, setOptimizingDescription] = useState(false);
    const [form] = Form.useForm();
    const axiosInstance = useAxiosBaseInstance();
    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });
    const { formLayout } = useResponsiveFormLayout(layout);

    useEffect(() => {
        setState(data);
        form.setFieldsValue(data);
    }, [data]);

    const optimizeDescription = async () => {
        try {
            setOptimizingDescription(true);
            const values = {
                ...state,
                ...form.getFieldsValue(),
            };
            const { data } = await axiosInstance.post("/api/admin/website/description/optimize", values);
            if (data.error) {
                await messageApi.error(data.message);
                return;
            }
            const description = data.data.description;
            const nextState = {
                ...values,
                description,
            };
            setState(nextState);
            form.setFieldsValue({
                description,
            });
            await messageApi.success(getRes().website.descriptionOptimizeSuccess);
        } catch (e) {
            await messageApi.error(e instanceof Error ? e.message : getRes().error.unknown);
        } finally {
            setOptimizingDescription(false);
        }
    };

    return (
        <>
            {contextHolder}
            <Form
                form={form}
                {...formLayout}
                disabled={offline || offlineData}
                initialValues={data}
                onValuesChange={(_k, v) => setState((prevState) => ({ ...prevState, ...v }))}
                onFinish={(nv) => onSubmit({ ...state, ...nv })}
            >
                <Form.Item name="title" label={getRes().title} rules={[{ required: true }]}>
                    <Input placeholder="" showCount={true} maxLength={128} />
                </Form.Item>
                <Form.Item name="second_title" label={getRes().subTitle}>
                    <Input placeholder="" showCount={true} maxLength={128} />
                </Form.Item>
                <Form.Item name="keywords" label={getRes().keywords} tooltip={getRes().website.keywordsTip}>
                    <TextArea showCount={true} rows={2} maxLength={160} />
                </Form.Item>
                <Form.Item label={getRes().website.description} tooltip={getRes().website.descriptionTip}>
                    <Form.Item name="description" noStyle={true}>
                        <TextArea showCount={true} rows={5} maxLength={160} />
                    </Form.Item>
                    <Button
                        type="link"
                        size="small"
                        icon={<BulbOutlined />}
                        style={{ padding: 0, height: "auto", marginTop: 4, color: getAppState().colorPrimary }}
                        disabled={offline || offlineData}
                        loading={optimizingDescription}
                        onClick={optimizeDescription}
                    >
                        {getRes().website.optimizeDescription}
                    </Button>
                </Form.Item>
                <Form.Item name="author" label={getRes().author}>
                    <Input placeholder="" showCount={true} maxLength={128} />
                </Form.Item>
                <Form.Item
                    name="favicon_ico_base64"
                    label={`${getRes().favicon}`}
                    tooltip={getRes().website.faviconTip}
                >
                    <FaviconUpload
                        url={state.favicon_ico_base64}
                        onChange={(e) => {
                            setState({ ...state, favicon_ico_base64: e ? e : "" });
                        }}
                    />
                </Form.Item>

                <WebsiteSubmitBar loading={loading} disabled={offline || offlineData} />
            </Form>
        </>
    );
};

export default BasicForm;
