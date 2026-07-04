import { useEffect, useState } from "react";
import { ColorPicker, Form, Input, message, Row } from "antd";
import Divider from "antd/es/divider";
import Button from "antd/es/button";
import TextArea from "antd/es/input/TextArea";
import Col from "antd/es/grid/col";
import { getPreset, getRes } from "../../utils/constants";
import Switch from "antd/es/switch";
import { colorPickerBgColors } from "../../utils/helpers";
import { useAxiosBaseInstance } from "../../base/AppBase";
import ResourceDragger, { DraggerUploadResponse } from "../../common/ResourceDragger";
import { CameraOutlined } from "@ant-design/icons";
import PreviewConfig from "./preview-config";
import Editor from "@editor/dist/editor";
import { getLangByRes } from "../../base/AppInit";
import { getAppState } from "../../base/ConfigProviderApp";
import Card from "antd/es/card";
import { EditorMode } from "@editor/dist/editor/editor.types";
import { postRefreshCacheSse } from "../../utils/sse-utils";
import BackendImage from "../../common/BackendImage";
import { useResponsiveFormLayout } from "../../utils/responsive-form";
import { useTheme } from "antd-style";

const layout = {
    labelCol: { span: 8 },
    wrapperCol: { span: 16 },
};

type TemplateConfigState = {
    dataMap: Record<string, any>;
    config: ConfigParam[];
    loading: boolean;
};

export type ConfigParam = {
    label: string;
    type: string;
    value: string;
    previewValue: string;
    htmlElementType: string;
    contentType: string;
    placeholder: string;
};

const convertToDataMap = (data: TemplateConfigState) => {
    const dataMap: Record<string, string> = {};
    for (const [key, value] of Object.entries(data.config)) {
        dataMap[key] = value.value;
    }
    return dataMap;
};

const TemplateConfig = ({
    data,
    offline,
    offlineData,
}: {
    data: TemplateConfigState;
    offline: boolean;
    offlineData: boolean;
}) => {
    const theme = useTheme();
    const dataMap = convertToDataMap(data);
    const [state, setState] = useState<TemplateConfigState>({
        config: data.config,
        dataMap: dataMap,
        loading: false,
    });

    const [form] = Form.useForm();

    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });
    const { formLayout, narrow, screens } = useResponsiveFormLayout(layout);
    const editorHeight = narrow ? 420 : screens.xl ? 520 : 480;
    const borderSecondary = `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`;

    const setValue = (changedValues: any) => {
        setState((prevState) => {
            return {
                ...prevState,
                dataMap: { ...prevState.dataMap, ...changedValues },
            };
        });
    };

    const onUploadChange = (data: DraggerUploadResponse, key: string) => {
        state.dataMap[key] = data.data.url;
        setState({
            ...state,
            dataMap: state.dataMap,
        });
    };

    const onAssetSelect = (path: string, key: string) => {
        setState((prevState) => ({
            ...prevState,
            dataMap: {
                ...prevState.dataMap,
                [key]: path,
            },
        }));
    };

    const getInput = (key: string, value: ConfigParam) => {
        if (value.type === "file") {
            return (
                <ResourceDragger
                    axiosInstance={axiosInstance}
                    style={{ width: 128, height: 128 }}
                    onSuccess={(e) => onUploadChange(e, key)}
                    onError={(e) => {
                        messageApi.error(e.message);
                    }}
                    type={"image"}
                    bodyAspectRatio={1}
                    resourcePicker={{
                        onlyImage: true,
                        onSelectFile: (path) => onAssetSelect(path, key),
                    }}
                >
                    {state.dataMap[key] && state.dataMap[key].length > 0 ? (
                        <BackendImage preview={false} height={128} width={128} src={state.dataMap[key]} />
                    ) : (
                        <p
                            className="ant-upload-drag-icon"
                            style={{
                                margin: 0,
                                width: 128,
                                height: 128,
                                display: "flex",
                                justifyContent: "center",
                                alignItems: "center",
                            }}
                        >
                            <CameraOutlined
                                style={{ color: theme.colorTextSecondary, fontSize: theme.fontSizeHeading2 }}
                            />
                        </p>
                    )}
                </ResourceDragger>
            );
        } else if (value.htmlElementType === "switch") {
            return <Switch />;
        } else if (value.htmlElementType === "textarea" || value.htmlElementType === "large-textarea") {
            return (
                <TextArea rows={value.htmlElementType === "large-textarea" ? 20 : 5} placeholder={value.placeholder} />
            );
        } else if (value.type === "hidden") {
            return <Input hidden={true} />;
        } else if (value.htmlElementType === "colorPicker") {
            return (
                <ColorPicker
                    value={state.dataMap[key]}
                    onChange={(color) => {
                        state.dataMap[key] = color.toHexString();
                        setState({
                            ...state,
                            dataMap: state.dataMap,
                        });
                    }}
                    showText={(color) => color.toHexString()}
                    disabledAlpha={true}
                    presets={[
                        {
                            defaultOpen: true,
                            label: getPreset(),
                            colors: colorPickerBgColors,
                        },
                    ]}
                />
            );
        }
        return <Input type={value.type} placeholder={value.placeholder} />;
    };

    const isLarge = () => {
        for (const value of Object.values(state.config)) {
            if (value.type === "yml") {
                return true;
            }
        }
        return false;
    };

    const getFormItems = () => {
        const formInputs = [];
        for (const [key, value] of Object.entries(state.config)) {
            if (value.type === "yml") {
                return (
                    <Card
                        title={value.label}
                        styles={{
                            body: {
                                padding: 0,
                                borderTop: borderSecondary,
                                boxSizing: "border-box",
                            },
                        }}
                        style={{ overflow: "hidden" }}
                    >
                        <Editor
                            height={editorHeight}
                            onChange={(e) => {
                                setValue({
                                    [key]: e.value,
                                });
                            }}
                            fullscreen={false}
                            previewContent={""}
                            value={value.value}
                            config={{
                                mode: EditorMode.YML,
                                axiosInstance: axiosInstance,
                                disableStatistics: true,
                                disableToolbar: true,
                                dark: getAppState().dark,
                                lang: getLangByRes(),
                                preview: false,
                                uploadConfig: {
                                    buildUploadUrl: () => {
                                        return "";
                                    },
                                    formName: "",
                                    axiosInstance: axiosInstance,
                                },
                            }}
                        />
                    </Card>
                );
            }
            const input = (
                <>
                    <Form.Item
                        label={value.label}
                        name={key}
                        key={key}
                        style={{ display: value.type === "hidden" ? "none" : "" }}
                    >
                        {getInput(key, value)}
                    </Form.Item>
                    <PreviewConfig
                        contentType={value.contentType}
                        value={state.dataMap[key]}
                        initPreviewValue={value.previewValue ? value.previewValue : ""}
                    />
                </>
            );
            formInputs.push(input);
        }
        return formInputs;
    };

    const axiosInstance = useAxiosBaseInstance();
    const onFinish = async () => {
        setState((prevState) => {
            return {
                ...prevState,
                loading: true,
            };
        });
        try {
            const data = await postRefreshCacheSse<any>("/api/admin/template/config", {
                body: state.dataMap,
                messageApi,
                messageKey: "templateConfigRefreshCache",
                backgroundTaskTitle: getRes().backgroundTask.title + " · " + getRes().websiteTemplate.title,
            });
            if (data.error) {
                messageApi.error(data.message);
            } else if (data.error === 0) {
                messageApi.success(data.message);
            }
        } finally {
            setState((prevState) => {
                return {
                    ...prevState,
                    loading: false,
                };
            });
        }
    };

    useEffect(() => {
        const newDataMap = convertToDataMap(data);
        form.setFieldsValue(newDataMap);
        setState({
            config: data.config,
            dataMap: newDataMap,
            loading: false,
        });
    }, [data]);

    return (
        <>
            {contextHolder}
            <Row>
                <Col xs={24} style={{ maxWidth: isLarge() ? 900 : 600 }}>
                    <Form
                        form={form}
                        disabled={offline || offlineData}
                        onFinish={() => onFinish()}
                        initialValues={state.dataMap}
                        onValuesChange={(_k, v) => setValue(v)}
                        {...formLayout}
                    >
                        {getFormItems()}
                        <Divider />
                        <Button
                            loading={state.loading}
                            disabled={offline || offlineData}
                            type="primary"
                            htmlType="submit"
                        >
                            {getRes().submit}
                        </Button>
                    </Form>
                </Col>
            </Row>
        </>
    );
};

export default TemplateConfig;
