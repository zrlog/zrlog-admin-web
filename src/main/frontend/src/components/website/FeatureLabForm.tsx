import Form from "antd/es/form";
import Switch from "antd/es/switch";
import { Alert, theme } from "antd";
import { useEffect } from "react";
import { getRes } from "../../utils/constants";
import { FeatureLab } from "./index";
import { useResponsiveFormLayout } from "../../utils/responsive-form";
import WebsiteSubmitBar from "./WebsiteSubmitBar";

const layout = {
    labelCol: { span: 8 },
    wrapperCol: { span: 16 },
};

const FeatureLabForm = ({
    data,
    offlineData,
    offline,
    onSubmit,
    loading,
}: {
    data: FeatureLab;
    offlineData: boolean;
    offline: boolean;
    onSubmit: (data: FeatureLab) => void;
    loading?: boolean;
}) => {
    const [form] = Form.useForm();
    const { formLayout } = useResponsiveFormLayout(layout);
    const { token } = theme.useToken();

    useEffect(() => {
        form.setFieldsValue(data);
    }, [data, form]);

    return (
        <Form
            {...formLayout}
            form={form}
            disabled={offline || offlineData}
            initialValues={data}
            onFinish={(values) => onSubmit({ ...data, ...values })}
        >
            <Alert
                type="warning"
                showIcon
                message={getRes().websiteLab.notice}
                style={{ marginBottom: token.margin }}
            />
            <Form.Item
                valuePropName="checked"
                name="feature_resource_reference_enabled"
                label={getRes().websiteLab.resourceReference}
                tooltip={getRes().websiteLab.resourceReferenceTip}
            >
                <Switch />
            </Form.Item>
            <Form.Item
                valuePropName="checked"
                name="feature_article_extension_filter_enabled"
                label={getRes().websiteLab.articleExtensionFilter}
                tooltip={getRes().websiteLab.articleExtensionFilterTip}
            >
                <Switch />
            </Form.Item>
            <Form.Item
                valuePropName="checked"
                name="feature_webhook_enabled"
                label={getRes().websiteLab.webhook}
                tooltip={getRes().websiteLab.webhookTip}
            >
                <Switch />
            </Form.Item>
            <Form.Item
                valuePropName="checked"
                name="feature_personal_data_enabled"
                label={getRes().websiteLab.personalData}
                tooltip={getRes().websiteLab.personalDataTip}
            >
                <Switch />
            </Form.Item>

            <WebsiteSubmitBar loading={loading} disabled={offline || offlineData} />
        </Form>
    );
};

export default FeatureLabForm;
