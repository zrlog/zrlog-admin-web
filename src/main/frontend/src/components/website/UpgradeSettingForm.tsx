import Row from "antd/es/grid/row";
import Col from "antd/es/grid/col";
import Button from "antd/es/button";
import { getRealRouteUrl, getRes } from "../../utils/constants";
import Form from "antd/es/form";
import Select from "antd/es/select";
import Switch from "antd/es/switch";

import { App, message, theme } from "antd";
import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Upgrade } from "./index";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { ApiResponse, UpgradeData } from "../../type";
import axios, { AxiosResponse, CancelTokenSource } from "axios";
import UpgradeContent from "../upgrade-content";
import { useResponsiveFormLayout } from "../../utils/responsive-form";
import WebsiteSubmitBar from "./WebsiteSubmitBar";

const layout = {
    labelCol: { span: 8 },
    wrapperCol: { span: 16 },
};

const UpgradeSettingForm = ({
    data,
    offlineData,
    offline,
    onSubmit,
    loading,
}: {
    data: Upgrade;
    offlineData: boolean;
    offline: boolean;
    loading?: boolean;
    onSubmit: (data: Upgrade) => void;
}) => {
    const [checking, setChecking] = useState<boolean>(false);
    const { modal } = App.useApp();
    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });
    const { token } = theme.useToken();

    const [state, setState] = useState<Upgrade>(data);
    const [form] = Form.useForm();

    const navigate = useNavigate();
    const axiosInstance = useAxiosBaseInstance();
    const cancelTokenSource = useRef<CancelTokenSource | null>(null);
    const { formLayout, narrow, screens } = useResponsiveFormLayout(layout);
    const formItemStyle = narrow ? { marginBottom: token.marginMD } : undefined;
    const cycleSelectWidth = narrow ? "100%" : 120;
    const checkRowStyle = {
        marginBottom: narrow ? token.marginMD : token.margin,
    };

    const checkNewVersion = async () => {
        if (checking) {
            return;
        }
        setChecking(true);
        try {
            const source = axios.CancelToken.source();
            cancelTokenSource.current = source;
            const { data }: AxiosResponse<ApiResponse<UpgradeData>> = await axiosInstance.get("/api/admin/upgrade", {
                cancelToken: source.token,
            });
            if (data.data.upgrade) {
                modal.info({
                    width: narrow ? "calc(100vw - 32px)" : 682,
                    title: `${getRes().upgrade.detectedPrefix}${data.data.version.type}`,
                    content: (
                        <div
                            style={{
                                maxHeight: narrow ? "60vh" : "62vh",
                                overflowY: "auto",
                                paddingRight: narrow ? 0 : 4,
                            }}
                        >
                            <UpgradeContent data={data.data} />
                        </div>
                    ),
                    closable: true,
                    okText: getRes().upgrade.doUpgrade,
                    onOk: () => {
                        navigate(getRealRouteUrl("/upgrade"), { replace: true });
                    },
                });
            } else {
                if (data.error === 0) {
                    setChecking(false);
                    await messageApi.info(data.message);
                }
            }
        } catch (e) {
            if (!axios.isCancel(e)) {
                throw e;
            }
        } finally {
            setChecking(false);
        }
    };

    useEffect(() => {
        setState(data);
        form.setFieldsValue(data);
    }, [data]);

    useEffect(() => {
        return () => {
            if (cancelTokenSource.current) {
                cancelTokenSource.current.cancel();
            }
        };
    }, []);

    if (screens.md === undefined) {
        return <></>;
    }

    return (
        <Form
            form={form}
            {...formLayout}
            disabled={offline || offlineData}
            initialValues={data}
            onValuesChange={(_k, v) => setState({ ...state, ...v })}
            onFinish={(nv) => onSubmit({ ...state, ...nv })}
        >
            {contextHolder}
            <Row style={checkRowStyle}>
                <Col xs={24}>
                    <Button
                        type="dashed"
                        disabled={offline || offlineData}
                        loading={checking}
                        onClick={checkNewVersion}
                        block={narrow}
                        style={{ float: narrow ? undefined : "right" }}
                    >
                        {getRes().upgrade.check}
                    </Button>
                </Col>
            </Row>
            <Form.Item
                name="autoUpgradeVersion"
                label={getRes().websiteUpgrade.autoCheckCycle}
                tooltip={getRes().websiteUpgrade.autoCheckCycleTip}
                style={formItemStyle}
            >
                <Select
                    style={{ width: cycleSelectWidth }}
                    options={[
                        {
                            label: getRes().websiteUpgrade.cycle.oneDay,
                            value: 86400,
                        },
                        {
                            label: getRes().websiteUpgrade.cycle.oneWeek,
                            value: 604800,
                        },
                        {
                            label: getRes().websiteUpgrade.cycle.halfMonth,
                            value: 1296000,
                        },
                        {
                            label: getRes().websiteUpgrade.cycle.never,
                            value: -1,
                        },
                    ]}
                ></Select>
            </Form.Item>
            <Form.Item
                valuePropName="checked"
                name="upgradePreview"
                label={getRes().websiteUpgrade.previewVersionCheck}
                tooltip={getRes().websiteUpgrade.previewVersionCheckTip}
                style={formItemStyle}
            >
                <Switch />
            </Form.Item>

            <WebsiteSubmitBar loading={loading} disabled={offline || offlineData} />
        </Form>
    );
};

export default UpgradeSettingForm;
