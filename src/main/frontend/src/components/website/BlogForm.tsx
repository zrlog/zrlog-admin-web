import Form from "antd/es/form";
import Input from "antd/es/input";
import Switch from "antd/es/switch";
import { getRes } from "../../utils/constants";
import { useEffect, useState } from "react";
import { Blog } from "./index";
import TextArea from "antd/es/input/TextArea";
import { useResponsiveFormLayout } from "../../utils/responsive-form";
import WebsiteSubmitBar from "./WebsiteSubmitBar";

const layout = {
    labelCol: { span: 8 },
    wrapperCol: { span: 16 },
};

const BlogForm = ({
    data,
    offlineData,
    offline,
    onSubmit,
    loading,
}: {
    data: Blog;
    offlineData: boolean;
    offline?: boolean;
    loading?: boolean;
    onSubmit: (data: Blog) => void;
}) => {
    const [state, setState] = useState<Blog>(data);
    const [form] = Form.useForm();
    const { formLayout } = useResponsiveFormLayout(layout);

    useEffect(() => {
        setState(data);
        form.setFieldsValue(data);
    }, [data]);

    return (
        <Form
            form={form}
            {...formLayout}
            initialValues={data}
            disabled={offline || offlineData}
            onValuesChange={(_k, v) => setState({ ...state, ...v })}
            onFinish={(nv) => onSubmit({ ...state, ...nv })}
        >
            <Form.Item name="host" label={getRes().websiteBlog.host} tooltip={getRes().websiteBlog.hostTip}>
                <Input style={{ maxWidth: 300 }} placeholder="" />
            </Form.Item>
            <Form.Item
                valuePropName="checked"
                name="generator_html_status"
                label={getRes().websiteBlog.staticSite}
                tooltip={getRes().websiteBlog.staticSiteTip}
            >
                <Switch />
            </Form.Item>
            <Form.Item
                valuePropName="checked"
                name="disable_comment_status"
                label={getRes().websiteBlog.disableComment}
                tooltip={getRes().websiteBlog.disableCommentTip}
            >
                <Switch />
            </Form.Item>
            <Form.Item valuePropName="checked" name="article_thumbnail_status" label={getRes().websiteBlog.cover}>
                <Switch />
            </Form.Item>
            <Form.Item
                name="system_notification"
                label={getRes().websiteBlog.systemNotify}
                tooltip={getRes().websiteBlog.systemNotifyTip}
            >
                <TextArea />
            </Form.Item>

            <WebsiteSubmitBar loading={loading} disabled={offline || offlineData} />
        </Form>
    );
};

export default BlogForm;
