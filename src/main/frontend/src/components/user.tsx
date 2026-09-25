import UserSettingsLayout from "./common/UserSettingsLayout";
import SettingsSubmitBar from "./common/SettingsSubmitBar";
import { UserApplications } from "./oauth";
import UserPreferencesForm from "./user-preferences";
import { useState } from "react";
import Form from "antd/es/form";
import { Input, message, theme } from "antd";
import Row from "antd/es/grid/row";
import Col from "antd/es/grid/col";
import Constants, { getRes } from "../utils/constants";
import { useAxiosBaseInstance } from "../base/AppBase";
import ResourceDragger, { DraggerUploadResponse } from "../common/ResourceDragger";
import { postRefreshCacheSse } from "../utils/sse-utils";
import ImageCropper from "../common/ImageCropper";
import { resolveBackendCropImageUrl } from "../utils/crop-image-url";
import BackendImage from "../common/BackendImage";
import { useResponsiveFormLayout } from "../utils/responsive-form";

const layout = {
    labelCol: { span: 8 },
    wrapperCol: { span: 16 },
};

type BasicUserInfo = {
    userName: string;
    header: string;
    email: string;
};

const UserProfile = ({ data, offline }: { data: BasicUserInfo; offline: boolean }) => {
    const [userInfo, setUserInfo] = useState<BasicUserInfo>(data);
    const [cropOpen, setCropOpen] = useState(false);
    const [cropImageUrl, setCropImageUrl] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });
    const { formLayout } = useResponsiveFormLayout(layout);
    const { token } = theme.useToken();

    const avatarSide = 128;
    const surface = {
        card: {
            maxWidth: 800,
            width: "100%",
        },
        formColumn: {
            maxWidth: 800,
        },
        uploader: {
            overflow: "hidden",
        },
        avatarImage: {
            objectFit: "cover" as const,
            width: avatarSide,
            height: avatarSide,
            borderRadius: token.borderRadius,
        },
    };

    const setValue = (changedValues: BasicUserInfo) => {
        setUserInfo({ ...userInfo, ...changedValues });
    };

    const openCropper = (url: string) => {
        setCropImageUrl(url);
        setCropOpen(true);
    };

    const onUploadChange = (info: DraggerUploadResponse) => {
        openCropper(info.data.url);
    };

    const axiosInstance = useAxiosBaseInstance();

    const onFinish = () => {
        if (submitting) {
            return;
        }
        setSubmitting(true);
        postRefreshCacheSse<any>("/api/admin/user/update", {
            body: userInfo,
            messageApi,
            messageKey: "userRefreshCache",
        })
            .then(async (data) => {
                if (data.error) {
                    await messageApi.error(data.message);
                } else if (data.error === 0) {
                    await messageApi.success(data.message);
                }
            })
            .finally(() => setSubmitting(false));
    };

    return (
        <>
            {contextHolder}
            <div style={surface.card}>
                <Row>
                    <Col style={surface.formColumn} xs={24}>
                        <Form
                            onFinish={() => onFinish()}
                            initialValues={userInfo}
                            onValuesChange={(_k, v) => setValue(v)}
                            {...formLayout}
                        >
                            <Form.Item label={getRes().user.userName} name="userName" rules={[{ required: true }]}>
                                <Input />
                            </Form.Item>

                            <Form.Item name="email" label={getRes().user.email}>
                                <Input type={"email"} />
                            </Form.Item>

                            <Form.Item label={getRes().user.headPortrait} rules={[{ required: true }]}>
                                <ResourceDragger
                                    axiosInstance={axiosInstance}
                                    style={surface.uploader}
                                    onSuccess={(e) => onUploadChange(e)}
                                    onError={(e) => {
                                        messageApi.error(e.message);
                                    }}
                                    type={"image"}
                                    bodyAspectRatio={1}
                                    resourcePicker={{
                                        onlyImage: true,
                                        onSelectFile: openCropper,
                                    }}
                                >
                                    <BackendImage
                                        fallback={Constants.getFillBackImg()}
                                        preview={false}
                                        height={avatarSide}
                                        width={avatarSide}
                                        style={surface.avatarImage}
                                        src={userInfo.header}
                                    />
                                </ResourceDragger>
                            </Form.Item>
                            <SettingsSubmitBar disabled={offline} loading={submitting} />
                        </Form>
                    </Col>
                </Row>
            </div>
            <ImageCropper
                open={cropOpen}
                imageUrl={cropImageUrl}
                aspectRatio={1}
                resolveImageUrl={resolveBackendCropImageUrl}
                onCancel={() => setCropOpen(false)}
                onError={(errorMessage) => messageApi.error(errorMessage)}
                onOk={(dataUrl) => {
                    setValue({ ...userInfo, header: dataUrl });
                    setCropOpen(false);
                }}
            />
        </>
    );
};

const User = (props: {
    data: BasicUserInfo;
    offline: boolean;
    activeKey?: "profile" | "preferences" | "applications";
}) => {
    const activeKey = props.activeKey || "profile";
    return (
        <UserSettingsLayout activeKey={activeKey}>
            {activeKey === "profile" ? (
                <UserProfile {...props} />
            ) : activeKey === "preferences" ? (
                <UserPreferencesForm offline={props.offline} />
            ) : (
                <UserApplications offline={props.offline} />
            )}
        </UserSettingsLayout>
    );
};

export default User;
