import { FolderOpenOutlined } from "@ant-design/icons";
import EditorBaseDragger, { DraggerUploadResponse } from "@editor/dist/editor/common/BaseDragger";
import type { UploadConfig } from "@editor/dist/editor/editor.types";
import type { AxiosInstance } from "axios";
import Button from "antd/es/button";
import Card from "antd/es/card";
import Modal from "antd/es/modal";
import Dragger from "antd/es/upload/Dragger";
import type { UploadProps } from "antd/es/upload/interface";
import { ComponentProps, CSSProperties, FunctionComponent, ReactNode, useState } from "react";
import { Grid } from "antd";
import { getRes, tryAppendBackendServerUrl } from "../utils/constants";
import FileManagerPicker from "../components/file-manager/picker";

export type { DraggerUploadResponse };

type ResourceDraggerProps = Omit<ComponentProps<typeof EditorBaseDragger>, "type" | "uploadConfig"> & {
    axiosInstance?: AxiosInstance;
    beforeUpload?: UploadProps["beforeUpload"];
    bodyAspectRatio?: CSSProperties["aspectRatio"];
    buildUploadUrl?: UploadConfig["buildUploadUrl"];
    cardBodyStyle?: CSSProperties;
    cardSize?: ComponentProps<typeof Card>["size"];
    cardStyle?: CSSProperties;
    resourcePicker?: {
        buttonText?: string;
        disabled?: boolean;
        onlyImage?: boolean;
        onSelectFile: (path: string) => void;
        title?: string;
    };
    title?: ReactNode;
    type?: string;
    getContainer?: () => HTMLElement;
};

const ResourceDragger: FunctionComponent<ResourceDraggerProps> = ({
    axiosInstance,
    bodyAspectRatio,
    buildUploadUrl,
    cardBodyStyle,
    cardSize = "small",
    cardStyle,
    resourcePicker,
    beforeUpload,
    children,
    disabled,
    title,
    type = "image",
    getContainer,
    ...props
}) => {
    const [pickerOpen, setPickerOpen] = useState(false);
    const pickerEnabled = Boolean(resourcePicker);
    const pickerDisabled = disabled || resourcePicker?.disabled;
    const pickerTitle = resourcePicker?.title ?? getRes().common.chooseFromAssets;
    const screens = Grid.useBreakpoint();
    const narrow = screens.md !== true;
    const pickerWidth = narrow ? "100vw" : screens.lg ? 860 : 720;
    const uploadConfig: UploadConfig | undefined = axiosInstance
        ? {
              axiosInstance,
              buildUploadUrl: buildUploadUrl ?? ((type) => `/api/admin/upload?dir=${type}`),
              formName: "imgFile",
              tryAppendBackendServerUrl,
          }
        : undefined;
    const draggerSizeStyle: CSSProperties = {
        ...(props.style ?? {}),
        background: props.style?.background ?? "transparent",
        border: props.style?.border ?? 0,
        width: props.style?.width ?? "100%",
        height: props.style?.height ?? props.height ?? "100%",
    };

    return (
        <>
            <Card
                extra={
                    pickerEnabled ? (
                        <Button
                            disabled={pickerDisabled}
                            htmlType="button"
                            icon={<FolderOpenOutlined />}
                            onClick={() => setPickerOpen(true)}
                            size="small"
                            style={{ height: "auto", padding: 0 }}
                            type="link"
                        >
                            {resourcePicker?.buttonText ?? getRes().common.chooseFromAssets}
                        </Button>
                    ) : null
                }
                size={cardSize}
                style={{ display: "inline-block", ...cardStyle }}
                styles={{
                    body: { aspectRatio: bodyAspectRatio, minHeight: 128, minWidth: 128, padding: 8, ...cardBodyStyle },
                    title: { minWidth: 24 },
                }}
                title={title}
            >
                {uploadConfig ? (
                    <EditorBaseDragger
                        {...props}
                        disabled={disabled}
                        style={draggerSizeStyle}
                        type={type}
                        uploadConfig={uploadConfig}
                    >
                        {children}
                    </EditorBaseDragger>
                ) : (
                    <Dragger
                        accept={props.accept}
                        beforeUpload={beforeUpload}
                        disabled={disabled}
                        height={props.height}
                        multiple={false}
                        showUploadList={false}
                        style={draggerSizeStyle}
                    >
                        {children}
                    </Dragger>
                )}
            </Card>
            {resourcePicker && (
                <Modal
                    title={pickerTitle}
                    open={pickerOpen}
                    onCancel={() => setPickerOpen(false)}
                    footer={null}
                    width={pickerWidth}
                    destroyOnClose
                    getContainer={getContainer}
                    style={narrow ? { top: 0, maxWidth: "100vw", paddingBottom: 0 } : undefined}
                    styles={{
                        body: {
                            height: narrow ? "calc(100dvh - 112px)" : "70vh",
                            minHeight: narrow ? 360 : 420,
                            padding: 0,
                        },
                    }}
                >
                    <FileManagerPicker
                        style={{ height: "100%" }}
                        onlyImage={resourcePicker.onlyImage}
                        onSelectFile={(path) => {
                            resourcePicker.onSelectFile(path);
                            setPickerOpen(false);
                        }}
                    />
                </Modal>
            )}
        </>
    );
};

export default ResourceDragger;
