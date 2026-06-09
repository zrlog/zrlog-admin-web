import { useState } from "react";
import Divider from "antd/es/divider";
import Form from "antd/es/form";
import { Button, Card, Input, message, theme } from "antd";
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

const User = ({ data, offline }: { data: BasicUserInfo; offline: boolean }) => {
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
            maxWidth: 640,
            width: "100%",
        },
        formColumn: {
            maxWidth: 600,
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
        divider: {
            margin: `${token.marginLG}px 0 ${token.margin}px`,
        },
        submitItem: {
            marginBottom: 0,
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
            <Card title={getRes().user.title} style={surface.card}>
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
                            <Divider style={surface.divider} />
                            <Form.Item style={surface.submitItem}>
                                <Button disabled={offline} loading={submitting} type="primary" htmlType="submit">
                                    {getRes().submit}
                                </Button>
                            </Form.Item>
                        </Form>
                    </Col>
                </Row>
            </Card>
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

export default User;
