import { Fragment, lazy, Suspense, useEffect } from "react";
import { App, Card, ColorPicker, Empty, Form, Input, Spin, Switch } from "antd";
import { CameraOutlined } from "@ant-design/icons";
import { useTheme } from "antd-style";
import { getPreset, getRes } from "../../utils/constants";
import { colorPickerBgColors } from "../../utils/helpers";
import { useAxiosBaseInstance } from "../../base/AppBase";
import ResourceDragger from "../../common/ResourceDragger";
import BackendImage from "../../common/BackendImage";
import { useResponsiveFormLayout } from "../../utils/responsive-form";
import PreviewConfig from "./preview-config";
import { ConfigParam, ConfigValues, hasConfigFields, TemplateConfigData } from "./template-config-model";

const TemplateConfigYaml = lazy(() => import("./template-config-yaml"));

type TemplateConfigFormProps = {
    data: TemplateConfigData;
    values: ConfigValues;
    onChange: (changes: ConfigValues) => void;
    onSubmit: () => void;
    formId: string;
    disabled?: boolean;
};

const TemplateConfigForm = ({ data, values, onChange, onSubmit, formId, disabled }: TemplateConfigFormProps) => {
    const [form] = Form.useForm();
    const { message } = App.useApp();
    const theme = useTheme();
    const imagePreviewSize = 128 - theme.padding * 2;
    const axiosInstance = useAxiosBaseInstance();
    const { formLayout } = useResponsiveFormLayout();
    useEffect(() => form.setFieldsValue(values), [form, values]);

    const getInput = (key: string, param: ConfigParam) => {
        if (param.type === "file") {
            const src = typeof values[key] === "string" ? String(values[key]) : "";
            return (
                <ResourceDragger
                    axiosInstance={axiosInstance}
                    disabled={disabled}
                    style={{ width: 128, height: 128 }}
                    onSuccess={(response) => onChange({ [key]: response.data.url })}
                    onError={(response) => {
                        void message.error(response.message);
                    }}
                    type="image"
                    bodyAspectRatio={1}
                    resourcePicker={{ onlyImage: true, onSelectFile: (path) => onChange({ [key]: path }) }}
                >
                    {src ? (
                        <BackendImage
                            preview={false}
                            height={imagePreviewSize}
                            width={imagePreviewSize}
                            src={src}
                            style={{ objectFit: "contain" }}
                        />
                    ) : (
                        <div
                            style={{
                                width: imagePreviewSize,
                                height: imagePreviewSize,
                                display: "grid",
                                placeItems: "center",
                                margin: "0 auto",
                            }}
                        >
                            <CameraOutlined
                                style={{ color: theme.colorTextSecondary, fontSize: theme.fontSizeHeading2 }}
                            />
                        </div>
                    )}
                </ResourceDragger>
            );
        }
        if (param.htmlElementType === "switch") return <Switch />;
        if (param.htmlElementType === "textarea" || param.htmlElementType === "large-textarea") {
            return (
                <Input.TextArea
                    rows={param.htmlElementType === "large-textarea" ? 20 : 5}
                    placeholder={param.placeholder}
                />
            );
        }
        if (param.type === "hidden") return <Input hidden />;
        if (param.htmlElementType === "colorPicker") {
            return (
                <ColorPicker
                    showText
                    disabledAlpha
                    presets={[{ defaultOpen: true, label: getPreset(), colors: colorPickerBgColors }]}
                />
            );
        }
        return <Input type={param.type} placeholder={param.placeholder} />;
    };

    return (
        <Form
            id={formId}
            form={form}
            disabled={disabled}
            initialValues={values}
            onFinish={onSubmit}
            onValuesChange={onChange}
            {...formLayout}
        >
            {!hasConfigFields(data) && <Empty description={getRes().templateConfig.empty} />}
            {Object.entries(data.config).map(([key, param]) =>
                param.type === "yml" ? (
                    <Card
                        key={key}
                        title={param.label}
                        styles={{ body: { padding: 0 } }}
                        style={{ overflow: "hidden" }}
                    >
                        <Suspense fallback={<Spin style={{ display: "block", padding: theme.paddingLG }} />}>
                            <TemplateConfigYaml
                                value={String(values[key] ?? "")}
                                onChange={(value) => onChange({ [key]: value })}
                                disabled={disabled}
                            />
                        </Suspense>
                    </Card>
                ) : (
                    <Fragment key={key}>
                        <Form.Item
                            label={param.label}
                            name={key}
                            hidden={param.type === "hidden"}
                            valuePropName={param.htmlElementType === "switch" ? "checked" : "value"}
                            getValueFromEvent={
                                param.htmlElementType === "colorPicker" ? (color) => color.toHexString() : undefined
                            }
                        >
                            {getInput(key, param)}
                        </Form.Item>
                        <PreviewConfig
                            contentType={param.contentType}
                            value={String(values[key] ?? "")}
                            initPreviewValue={param.previewValue}
                        />
                    </Fragment>
                )
            )}
        </Form>
    );
};

export default TemplateConfigForm;
