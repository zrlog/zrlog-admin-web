import Form from "antd/es/form";
import Switch from "antd/es/switch";
import { Input, Select } from "antd";
import { useEffect } from "react";
import { getRes } from "../../utils/constants";
import { ContentProtector } from "./index";
import { useResponsiveFormLayout } from "../../utils/responsive-form";
import WebsiteSubmitBar from "./WebsiteSubmitBar";

const layout = {
    labelCol: { span: 8 },
    wrapperCol: { span: 16 },
};

const LICENSE_TYPES = [
    "ALL_RIGHTS_RESERVED",
    "CC_BY_4_0",
    "CC_BY_SA_4_0",
    "CC_BY_ND_4_0",
    "CC_BY_NC_4_0",
    "CC_BY_NC_SA_4_0",
    "CC_BY_NC_ND_4_0",
] as const;

const ContentProtectorForm = ({
    data,
    offlineData,
    offline,
    onSubmit,
    loading,
}: {
    data: ContentProtector;
    offlineData: boolean;
    offline: boolean;
    onSubmit: (data: ContentProtector) => void;
    loading?: boolean;
}) => {
    const [form] = Form.useForm();
    const { formLayout } = useResponsiveFormLayout(layout);
    const licenseOptions = LICENSE_TYPES.map((licenseType) => ({
        label: getRes().websiteContentProtector.licenseTypes[licenseType],
        value: licenseType,
    }));

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
            <Form.Item
                valuePropName="checked"
                name="content_protector_enabled"
                label={getRes().websiteContentProtector.enabled}
                tooltip={getRes().websiteContentProtector.enabledTip}
            >
                <Switch />
            </Form.Item>
            <Form.Item
                name="content_protector_license_type"
                label={getRes().websiteContentProtector.licenseType}
                tooltip={getRes().websiteContentProtector.licenseTypeTip}
            >
                <Select options={licenseOptions} />
            </Form.Item>
            <Form.Item
                name="content_protector_template"
                label={getRes().websiteContentProtector.template}
                tooltip={getRes().websiteContentProtector.templateTip}
            >
                <Input.TextArea rows={8} />
            </Form.Item>

            <WebsiteSubmitBar loading={loading} disabled={offline || offlineData} />
        </Form>
    );
};

export default ContentProtectorForm;
