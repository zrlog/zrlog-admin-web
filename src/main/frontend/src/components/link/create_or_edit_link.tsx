import { FunctionComponent, PropsWithChildren, useEffect, useState } from "react";
import { Col, Form, Input, InputNumber, message, Modal } from "antd";
import Row from "antd/es/grid/row";
import TextArea from "antd/es/input/TextArea";
import { Link } from "react-router-dom";
import { getRes } from "../../utils/constants";
import { postRefreshCacheSse } from "../../utils/sse-utils";
import { useResponsiveFormLayout } from "../../utils/responsive-form";

const layout = {
    labelCol: { span: 4 },
    wrapperCol: { span: 20 },
};

export type EditLinkProps = PropsWithChildren & {
    record: LinkEntry;
    editSuccessCall: () => void;
    offline: boolean;
};

type LinkEntry = {
    id: number;
    linkName: string;
    url: string;
    sort?: number;
    icon?: string;
    introduction?: string;
};

const CreateOrEditLink: FunctionComponent<EditLinkProps> = ({ record, editSuccessCall, offline, children }) => {
    const [showModel, setShowModel] = useState<boolean>(false);
    const [updateForm, setUpdateForm] = useState<LinkEntry>(record);
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
        postRefreshCacheSse<any>(isUpdate() ? "/api/admin/link/update" : "/api/admin/link/add", {
            body: updateForm,
            messageApi,
            messageKey: "linkRefreshCache",
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

    useEffect(() => {
        setUpdateForm(record);
    }, [record]);

    const setValue = (changedValues: any) => {
        setUpdateForm(changedValues);
    };

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
                                label={getRes().link.title}
                                style={{ marginBottom: 8 }}
                                name="url"
                                rules={[{ required: true, message: "" }]}
                                extra={getRes().link.urlHelp}
                            >
                                <Input placeholder={getRes().link.urlPlaceholder} />
                            </Form.Item>
                        </Col>
                    </Row>
                    <Row>
                        <Col span={24}>
                            <Form.Item
                                label={getRes().link.name}
                                style={{ marginBottom: 8 }}
                                name="linkName"
                                rules={[{ required: true, message: "" }]}
                                extra={getRes().link.nameHelp}
                            >
                                <Input placeholder={getRes().link.namePlaceholder} />
                            </Form.Item>
                        </Col>
                    </Row>
                    <Row>
                        <Col span={24}>
                            <Form.Item
                                label={getRes().introduction}
                                style={{ marginBottom: 8 }}
                                name="alt"
                                rules={[{ required: true, message: "" }]}
                                extra={getRes().link.introductionHelp}
                            >
                                <TextArea placeholder={getRes().link.introductionPlaceholder} />
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
                                extra={getRes().link.iconHelp}
                            >
                                <Input placeholder={getRes().link.iconPlaceholder} />
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
                                extra={getRes().link.sortHelp}
                            >
                                <InputNumber style={{ width: "100%" }} placeholder={getRes().link.sortPlaceholder} />
                            </Form.Item>
                        </Col>
                    </Row>
                </Form>
            </Modal>
        </>
    );
};
export default CreateOrEditLink;
