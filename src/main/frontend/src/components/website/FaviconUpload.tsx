import { CameraOutlined, DeleteFilled } from "@ant-design/icons";
import { message } from "antd";
import Image from "antd/es/image";
import { FunctionComponent, useState } from "react";
import { RcFile } from "antd/es/upload";
import ResourceDragger from "../../common/ResourceDragger";
import { useTheme } from "antd-style";
import ImageCropper from "../../common/ImageCropper";
import { resolveBackendCropImageUrl } from "../../utils/crop-image-url";
import { colorToRgba } from "../../layout/slider";

type ThumbnailUploadProps = {
    onChange?: (e: string | null) => void;
    url?: string;
};

const FaviconUpload: FunctionComponent<ThumbnailUploadProps> = ({ onChange, url }) => {
    const [cropOpen, setCropOpen] = useState(false);
    const [cropImageUrl, setCropImageUrl] = useState("");
    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });

    const changeToDataUrl = (dataUrl: string | null) => {
        onChange?.(dataUrl);
    };

    const theme = useTheme();

    const openCropper = (imageUrl: string) => {
        setCropImageUrl(imageUrl);
        setCropOpen(true);
    };

    const handleBeforeUpload = async (file: RcFile): Promise<boolean> => {
        return new Promise((resolve) => {
            const reader = new FileReader();
            reader.readAsDataURL(file);
            reader.onload = () => {
                openCropper(reader.result as string);
                resolve(false);
            };
            reader.onerror = () => {
                resolve(false);
            };
        });
    };

    const border = `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorder}`;

    return (
        <>
            {contextHolder}
            <ResourceDragger
                accept={"image/*"}
                beforeUpload={(file) => handleBeforeUpload(file)}
                bodyAspectRatio={1}
                cardStyle={{
                    border: border,
                    maxWidth: 192,
                }}
                resourcePicker={{
                    onlyImage: true,
                    onSelectFile: openCropper,
                }}
                style={{ overflow: "hidden" }}
            >
                {(url === undefined || url === null || url === "") && (
                    <p
                        className="ant-upload-drag-icon"
                        style={{
                            height: "100%",
                            padding: 0,
                            margin: 0,
                            display: "flex",
                            justifyContent: "center",
                            alignItems: "center",
                        }}
                    >
                        <CameraOutlined style={{ color: theme.colorTextSecondary, fontSize: theme.fontSizeHeading3 }} />
                    </p>
                )}
                {url != null && url !== "" && (
                    <div style={{ position: "relative", height: "100%" }}>
                        <Image
                            style={{ position: "relative" }}
                            preview={false}
                            src={url}
                            wrapperStyle={{ position: "relative" }}
                        />
                        <div
                            style={{
                                position: "absolute",
                                right: 0,
                                top: 0,
                                padding: 4,
                                background: colorToRgba(theme.colorText, 0.72),
                                color: theme.colorWhite,
                                fontSize: theme.fontSizeLG,
                            }}
                            onClick={(e) => {
                                changeToDataUrl(null);
                                e.stopPropagation();
                            }}
                        >
                            <DeleteFilled />
                        </div>
                    </div>
                )}
            </ResourceDragger>
            <ImageCropper
                open={cropOpen}
                imageUrl={cropImageUrl}
                aspectRatio={1}
                resolveImageUrl={resolveBackendCropImageUrl}
                onCancel={() => setCropOpen(false)}
                onError={(errorMessage) => messageApi.error(errorMessage)}
                onOk={(dataUrl) => {
                    changeToDataUrl(dataUrl);
                    setCropOpen(false);
                }}
            />
        </>
    );
};
export default FaviconUpload;
