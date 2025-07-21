import { CameraOutlined, DeleteFilled, LoadingOutlined } from "@ant-design/icons";
import Image from "antd/es/image";
import { FunctionComponent, useState } from "react";
import { getRes, tryAppendBackendServerUrl } from "../../utils/constants";
import { message, Typography } from "antd";
import { getAppState } from "../../base/ConfigProviderApp";
import BaseDragger from "@editor/dist/src/editor/common/BaseDragger";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { useTheme } from "antd-style";

type ThumbnailUploadProps = {
    onChange?: (e: string) => void;
    thumbnail?: string;
    getContainer?: () => HTMLElement;
};

const ThumbnailUpload: FunctionComponent<ThumbnailUploadProps> = ({ onChange, thumbnail, getContainer }) => {
    const [uploading, setUploading] = useState<boolean>(false);

    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });

    const axiosInstance = useAxiosBaseInstance(getContainer);

    const theme = useTheme();

    return (
        <BaseDragger
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
            accept={"image/*"}
            style={{ overflow: "hidden", minHeight: 102, maxHeight: 256 }}
            uploadConfig={{
                buildUploadUrl: function (type): string {
                    return `/api/admin/upload/thumbnail?dir=${type}`;
                },
                formName: "imgFile",
                axiosInstance: axiosInstance,
                tryAppendBackendServerUrl: tryAppendBackendServerUrl,
            }}
            type={"thumbnail"}
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
                    <Image
                        style={{ borderRadius: 0, position: "relative" }}
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
        </BaseDragger>
    );
};
export default ThumbnailUpload;
