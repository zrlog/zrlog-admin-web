import { CameraOutlined, DeleteFilled, LoadingOutlined } from "@ant-design/icons";
import { FunctionComponent, ReactNode, useState } from "react";
import { getRes } from "../../utils/constants";
import { message, Typography } from "antd";
import { getAppState } from "../../base/ConfigProviderApp";
import ResourceDragger from "../../common/ResourceDragger";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { useTheme } from "antd-style";
import BackendImage from "../../common/BackendImage";

type ThumbnailUploadProps = {
    onChange?: (e: string) => void;
    thumbnail?: string;
    getContainer?: () => HTMLElement;
    aspectRatio?: number;
    title?: ReactNode;
};

const ThumbnailUpload: FunctionComponent<ThumbnailUploadProps> = ({
    onChange,
    thumbnail,
    getContainer,
    aspectRatio,
    title,
}) => {
    const [uploading, setUploading] = useState<boolean>(false);

    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });

    const axiosInstance = useAxiosBaseInstance(getContainer);

    const theme = useTheme();

    return (
        <ResourceDragger
            axiosInstance={axiosInstance}
            onSuccess={({ data }) => {
                setUploading(false);
                if (onChange) {
                    onChange(data.url);
                }
            }}
            disabled={uploading}
            onProgress={() => {
                setUploading(true);
            }}
            onError={(e) => {
                messageApi.error(e.message);
                setUploading(false);
            }}
            getContainer={getContainer}
            accept={"image/*"}
            style={{ overflow: "hidden", height: "100%" }}
            bodyAspectRatio={aspectRatio}
            cardSize="default"
            cardStyle={{ width: "100%" }}
            buildUploadUrl={(type) => `/api/admin/upload/thumbnail?dir=${type}`}
            type={"thumbnail"}
            title={title}
            resourcePicker={{
                disabled: uploading,
                onlyImage: true,
                onSelectFile: (path) => {
                    onChange?.(path);
                },
            }}
        >
            {contextHolder}
            {(thumbnail === undefined || thumbnail === null || thumbnail === "") && (
                <>
                    <p
                        className="ant-upload-drag-icon"
                        style={{
                            padding: `16px 0`,
                            margin: 0,
                            display: "flex",
                            justifyContent: "center",
                            alignItems: "center",
                        }}
                    >
                        <CameraOutlined style={{ fontSize: 28, color: getAppState().colorPrimary }} />
                    </p>
                    <Typography
                        style={{
                            margin: 0,
                            fontSize: "var(--ant-font-size-lg)",
                        }}
                    >
                        {uploading && <LoadingOutlined />} {getRes().articleEdit.upload.tips}
                    </Typography>
                </>
            )}
            {thumbnail != null && thumbnail !== "" && (
                <div style={{ position: "relative" }}>
                    <BackendImage
                        style={{ borderRadius: 0, position: "relative", aspectRatio, objectFit: "cover" }}
                        width="100%"
                        preview={false}
                        id="thumbnail"
                        src={thumbnail}
                    />
                    <div
                        style={{
                            position: "absolute",
                            right: 0,
                            top: 0,
                            borderRadius: `0 ${theme.borderRadiusLG}px`,
                            padding: 12,
                            background: getAppState().colorPrimary + "5e",
                            color: "white",
                            fontSize: 20,
                        }}
                        onClick={(e) => {
                            if (onChange) {
                                onChange("");
                            }
                            e.stopPropagation();
                        }}
                    >
                        <DeleteFilled />
                    </div>
                </div>
            )}
        </ResourceDragger>
    );
};
export default ThumbnailUpload;
