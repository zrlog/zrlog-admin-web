import { useState } from "react";
import Divider from "antd/es/divider";
import Form from "antd/es/form";
import { Button, Input, message } from "antd";
import Row from "antd/es/grid/row";
import Col from "antd/es/grid/col";
import Image from "antd/es/image";
import Constants, { getRes, tryAppendBackendServerUrl } from "../utils/constants";
import { useAxiosBaseInstance } from "../base/AppBase";
import BaseDragger, { DraggerUploadResponse } from "@editor/dist/src/editor/common/BaseDragger";
import { postRefreshCacheSse } from "../utils/sse-utils";

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
    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });

    const setValue = (changedValues: BasicUserInfo) => {
        setUserInfo({ ...userInfo, ...changedValues });
    };

    const onUploadChange = (info: DraggerUploadResponse) => {
        setValue({ ...userInfo, header: info.data.url });
    };

    const axiosInstance = useAxiosBaseInstance();
    const onFinish = () => {
        postRefreshCacheSse<any>("/api/admin/user/update", {
            body: userInfo,
            messageApi,
            messageKey: "userRefreshCache",
        }).then(async (data) => {
            if (data.error) {
                await messageApi.error(data.message);
            } else if (data.error === 0) {
                await messageApi.success(data.message);
            }
        });
    };

    return (
        <>
            {contextHolder}
            <Row>
                <Col style={{ maxWidth: 600 }} xs={24}>
                    <Form
                        onFinish={() => onFinish()}
                        initialValues={userInfo}
                        onValuesChange={(_k, v) => setValue(v)}
                        {...layout}
                    >
                        <Form.Item label={getRes().user.userName} name="userName" rules={[{ required: true }]}>
                            <Input />
                        </Form.Item>

                        <Form.Item name="email" label={getRes().user.email}>
                            <Input type={"email"} />
                        </Form.Item>

                        <Form.Item label={getRes().user.headPortrait} rules={[{ required: true }]}>
                            <BaseDragger
                                uploadConfig={{
                                    axiosInstance: axiosInstance,
                                    formName: "imgFile",
                                    buildUploadUrl: (type) => {
                                        return `/api/admin/upload?dir=${type}`;
                                    },
                                    tryAppendBackendServerUrl: tryAppendBackendServerUrl,
                                }}
                                style={{ width: 128, height: 128, overflow: "hidden" }}
                                onSuccess={(e) => onUploadChange(e)}
                                onError={(e) => {
                                    messageApi.error(e.message);
                                }}
                                type={"image"}
                            >
                                <Image
                                    fallback={Constants.getFillBackImg()}
                                    preview={false}
                                    height={128}
                                    width={128}
                                    style={{ objectFit: "cover" }}
                                    src={tryAppendBackendServerUrl(userInfo.header)}
                                />
                            </BaseDragger>
                        </Form.Item>
                        <Divider />
                        <Form.Item>
                            <Button disabled={offline} type="primary" htmlType="submit">
                                {getRes().submit}
                            </Button>
                        </Form.Item>
                    </Form>
                </Col>
            </Row>
        </>
    );
};

export default User;
