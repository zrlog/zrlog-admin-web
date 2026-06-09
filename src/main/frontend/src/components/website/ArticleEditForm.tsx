import Form from "antd/es/form";
import Switch from "antd/es/switch";
import { InputNumber, Select } from "antd";
import { useEffect } from "react";
import { getRes } from "../../utils/constants";
import { ArticleEditSetting } from "./index";
import { editorLang } from "@editor/dist/src/editor/lang/editor-lang";
import { getAppState } from "../../base/ConfigProviderApp";
import { useResponsiveFormLayout } from "../../utils/responsive-form";
import WebsiteSubmitBar from "./WebsiteSubmitBar";

const layout = {
    labelCol: { span: 8 },
    wrapperCol: { span: 16 },
};

const ARTICLE_COVER_ASPECT_RATIO_OPTIONS = ["16:9", "4:3", "3:2", "1:1", "21:9"].map((ratio) => ({
    label: ratio,
    value: ratio,
}));

const ArticleEditForm = ({
    data,
    offlineData,
    offline,
    onSubmit,
    loading,
}: {
    data: ArticleEditSetting;
    offlineData: boolean;
    offline: boolean;
    onSubmit: (data: ArticleEditSetting) => void;
    loading?: boolean;
}) => {
    const [form] = Form.useForm();
    const { formLayout } = useResponsiveFormLayout(layout);
    const autoSaveIntervalOptions = [2, 5, 10].map((seconds) => ({
        label: getRes().websiteArticleEdit.autoSaveIntervalOption.replace("{seconds}", `${seconds}`),
        value: seconds,
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
                name="article_auto_digest_length"
                label={getRes().websiteArticleEdit.autoDigestLength}
                tooltip={getRes().websiteArticleEdit.autoDigestLengthTip}
            >
                <InputNumber
                    suffix={editorLang[getAppState().lang].wordsCount}
                    style={{ width: 120 }}
                    max={99999}
                    type={"number"}
                    min={-1}
                    placeholder=""
                />
            </Form.Item>
            <Form.Item
                name="article_edit_auto_save_interval"
                label={getRes().websiteArticleEdit.autoSaveInterval}
                tooltip={getRes().websiteArticleEdit.autoSaveIntervalTip}
            >
                <Select style={{ width: 120 }} options={autoSaveIntervalOptions} />
            </Form.Item>
            <Form.Item
                name="article_cover_aspect_ratio"
                label={getRes().websiteArticleEdit.coverAspectRatio}
                tooltip={getRes().websiteArticleEdit.coverAspectRatioTip}
            >
                <Select style={{ width: 120 }} options={ARTICLE_COVER_ASPECT_RATIO_OPTIONS} />
            </Form.Item>
            <Form.Item
                valuePropName="checked"
                name="article_editor_link_preview_enabled"
                label={getRes().websiteArticleEdit.linkPreview}
                tooltip={getRes().websiteArticleEdit.linkPreviewTip}
            >
                <Switch />
            </Form.Item>
            <Form.Item
                valuePropName="checked"
                name="article_publish_check_enabled"
                label={getRes().websiteArticleEdit.publishCheck}
                tooltip={getRes().websiteArticleEdit.publishCheckTip}
            >
                <Switch />
            </Form.Item>

            <WebsiteSubmitBar loading={loading} disabled={offline || offlineData} />
        </Form>
    );
};

export default ArticleEditForm;
