import { FunctionComponent, PropsWithChildren, useEffect, useState } from "react";
import { Col, Form, Input, message, Modal } from "antd";
import Row from "antd/es/grid/row";
import TextArea from "antd/es/input/TextArea";
import { Link } from "react-router-dom";
import { getRes } from "../../utils/constants";
import { postRefreshCacheSse } from "../../utils/sse-utils";

const layout = {
    labelCol: { span: 4 },
    wrapperCol: { span: 20 },
};

type TypeEntry = {
    id: number;
    typeName: string;
    alias: string;
    remark?: string;
};

export type EditTypeProps = PropsWithChildren & {
    record: TypeEntry;
    editSuccessCall: () => void;
    offline: boolean;
};

const CreateOrEditType: FunctionComponent<EditTypeProps> = ({ record, editSuccessCall, offline, children }) => {
    const [showModel, setShowModel] = useState<boolean>(false);
    const [updateForm, setUpdateForm] = useState<TypeEntry>(record);
    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });
    const [loading, setLoading] = useState<boolean>(false);

    const isUpdate = () => {
        return record.id && record.id > 0;
    };
    const handleOk = () => {
        setLoading(true);
        postRefreshCacheSse<any>(isUpdate() ? "/api/admin/type/update" : "/api/admin/type/add", {
            body: updateForm,
            messageApi,
            messageKey: "typeRefreshCache",
        })
            .then(async (data) => {
                if (data.error) {
                    await messageApi.error(data.message);
                    return;
                }
                if (data.error === 0) {
                    setShowModel(false);
                    if (editSuccessCall) {
                        editSuccessCall();
                    }
                }
            })
            .finally(() => {
                setLoading(false);
            });
    };

    const setValue = (changedValues: any) => {
        setUpdateForm(changedValues);
    };

    useEffect(() => {
        setUpdateForm(record);
    }, [record]);

    return (
        <>
            {contextHolder}
            <Link
                to={isUpdate() ? "#edit-" + record.id : "#add"}
                onClick={(e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    if (offline) {
                        return;
                    }
                    setShowModel(true);
                }}
            >
                {children}
            </Link>
            <Modal
                title={isUpdate() ? getRes().edit : getRes().add}
                open={showModel}
                onOk={handleOk}
                okButtonProps={{
                    loading: loading,
                }}
                onCancel={() => setShowModel(false)}
            >
                <Form initialValues={updateForm} onValuesChange={(_k, v) => setValue(v)} {...layout}>
                    <Form.Item name="id" style={{ display: "none" }}>
                        <Input hidden={true} />
                    </Form.Item>
                    <Row>
                        <Col span={24}>
                            <Form.Item
                                label={getRes().articleType.title}
                                style={{ marginBottom: 8 }}
                                name="typeName"
                                rules={[{ required: true, message: "" }]}
                            >
                                <Input />
                            </Form.Item>
                        </Col>
                    </Row>
                    <Row>
                        <Col span={24}>
                            <Form.Item
                                label={getRes().alias}
                                style={{ marginBottom: 8 }}
                                name="alias"
                                rules={[{ required: true, message: "" }]}
                            >
                                <Input />
                            </Form.Item>
                        </Col>
                    </Row>
                    <Row>
                        <Col span={24}>
                            <Form.Item
                                label={getRes().introduction}
                                style={{ marginBottom: 8 }}
                                name="remark"
                                rules={[{ message: "" }]}
                            >
                                <TextArea />
                            </Form.Item>
                        </Col>
                    </Row>
                </Form>
            </Modal>
        </>
    );
};
export default CreateOrEditType;
