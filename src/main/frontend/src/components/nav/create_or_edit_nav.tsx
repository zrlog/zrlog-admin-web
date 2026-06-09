import { FunctionComponent, PropsWithChildren, useEffect, useState } from "react";
import { Col, Form, Input, InputNumber, message, Modal } from "antd";
import Row from "antd/es/grid/row";
import { Link } from "react-router-dom";
import { getRes } from "../../utils/constants";
import { postRefreshCacheSse } from "../../utils/sse-utils";
import { useResponsiveFormLayout } from "../../utils/responsive-form";

const layout = {
    labelCol: { span: 4 },
    wrapperCol: { span: 20 },
};

export type EditNavProps = PropsWithChildren & {
    record: LogNav;
    offline: boolean;
    editSuccessCall: () => void;
};

type LogNav = {
    id: number;
    sort?: number | null;
    navName: string;
    url: string;
    icon: string;
};

const CreateOrEditNav: FunctionComponent<EditNavProps> = ({ record, editSuccessCall, offline, children }) => {
    const [showModel, setShowModel] = useState<boolean>(false);
    const [updateForm, setUpdateForm] = useState<LogNav>(record);
    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });
    const [loading, setLoading] = useState<boolean>(false);
    const { formLayout, narrow } = useResponsiveFormLayout(layout);
    const isUpdate = () => {
        return record.id && record.id > 0;
    };
    const handleOk = () => {
        if (loading) {
            return;
        }
        setLoading(true);
        postRefreshCacheSse<any>(isUpdate() ? "/api/admin/nav/update" : "/api/admin/nav/add", {
            body: updateForm,
            messageApi,
            messageKey: "navRefreshCache",
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
                width={narrow ? "calc(100vw - 32px)" : undefined}
            >
                <Form initialValues={updateForm} onValuesChange={(_k, v) => setValue(v)} {...formLayout}>
                    <Form.Item name="id" style={{ display: "none" }}>
                        <Input hidden={true} />
                    </Form.Item>
                    <Row>
                        <Col span={24}>
                            <Form.Item
                                label={getRes().nav.link}
                                style={{ marginBottom: 8 }}
                                name="url"
                                rules={[{ required: true, message: "" }]}
                                extra={getRes().nav.linkHelp}
                            >
                                <Input placeholder={getRes().nav.linkPlaceholder} />
                            </Form.Item>
                        </Col>
                    </Row>
                    <Row>
                        <Col span={24}>
                            <Form.Item
                                label={getRes().nav.name}
                                style={{ marginBottom: 8 }}
                                name="navName"
                                rules={[{ required: true, message: "" }]}
                            >
                                <Input placeholder={getRes().nav.namePlaceholder} />
                            </Form.Item>
                        </Col>
                    </Row>
                    <Row>
                        <Col span={24}>
                            <Form.Item
                                label={getRes().icon}
                                style={{ marginBottom: 8 }}
                                name="icon"
                                rules={[{ message: "" }]}
                                extra={getRes().nav.iconHelp}
                            >
                                <Input placeholder={getRes().nav.iconPlaceholder} />
                            </Form.Item>
                        </Col>
                    </Row>
                    <Row>
                        <Col span={24}>
                            <Form.Item
                                label={getRes().order}
                                style={{ marginBottom: 8 }}
                                name="sort"
                                rules={[{ required: true, message: "" }]}
                                extra={getRes().nav.sortHelp}
                            >
                                <InputNumber style={{ width: "100%" }} placeholder={getRes().nav.sortPlaceholder} />
                            </Form.Item>
                        </Col>
                    </Row>
                </Form>
            </Modal>
        </>
    );
};
export default CreateOrEditNav;
