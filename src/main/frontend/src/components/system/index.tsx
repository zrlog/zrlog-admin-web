import ServerInfo from "./ServerInfo";
import { SystemData } from "../../type";
import { FunctionComponent, useEffect, useRef, useState } from "react";
import { getRes } from "../../utils/constants";
import { CloudServerOutlined, DatabaseOutlined } from "@ant-design/icons";
import Row from "antd/es/grid/row";
import { Col, Space } from "antd";
import { getCsrData, getTimeInfoBySearchStr } from "../../api";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { useLocation } from "react-router";

type SystemProps = {
    data: SystemData;
    offline: boolean;
};

const System: FunctionComponent<SystemProps> = ({ data }) => {
    const [state, setState] = useState<SystemData>(data);

    const axiosInstance = useAxiosBaseInstance();

    const cycleDuration = 5000;

    const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

    const location = useLocation();

    const fetchSystemInfo = () => {
        if (document.visibilityState === "visible") {
            getCsrData("/system", getTimeInfoBySearchStr(location.search), axiosInstance).then(({ data }) => {
                setState(data);
                timer.current = setTimeout(fetchSystemInfo, cycleDuration);
            });
        } else {
            timer.current = setTimeout(fetchSystemInfo, cycleDuration);
        }
    };

    useEffect(() => {
        timer.current = setTimeout(fetchSystemInfo, cycleDuration);
        return () => {
            if (timer && timer.current) {
                clearTimeout(timer.current);
            }
        };
    }, []);

    useEffect(() => {
        setState(data);
    }, [data]);

    return (
        <>
            <Row gutter={[8, 8]}>
                <Col xs={24} md={12}>
                    <ServerInfo
                        title={
                            <Space size={8}>
                                <CloudServerOutlined />
                                <span>{getRes().system.runtimeEnvironment}</span>
                            </Space>
                        }
                        data={state.serverInfos}
                        dockerMode={state.dockerMode}
                        nativeImageMode={state.nativeImageMode}
                    />
                </Col>
                <Col xs={24} md={12}>
                    <ServerInfo
                        title={
                            <Space size={8}>
                                <DatabaseOutlined />
                                <span>{getRes().system.resourceOverview}</span>
                            </Space>
                        }
                        data={state.serverInfos2}
                        dockerMode={state.dockerMode}
                        nativeImageMode={state.nativeImageMode}
                    />
                </Col>
            </Row>
        </>
    );
};

export default System;
