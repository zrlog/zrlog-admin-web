import ServerInfo from "./ServerInfo";
import { SystemData } from "../../type";
import { FunctionComponent, useEffect, useRef, useState } from "react";
import { getCsrData, getTimeInfoBySearchStr } from "../../api";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { useLocation, useNavigate } from "react-router";
import { Button, Flex } from "antd";
import { getRealRouteUrl, getRes } from "../../utils/constants";
import { hasAction } from "../../utils/account-access";

type SystemProps = {
    data: SystemData;
    offline: boolean;
};

const SYSTEM_REFRESH_INTERVAL = 5000;

const System: FunctionComponent<SystemProps> = ({ data }) => {
    const [state, setState] = useState<SystemData>(data);

    const axiosInstance = useAxiosBaseInstance();

    const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

    const location = useLocation();
    const navigate = useNavigate();

    useEffect(() => {
        let active = true;

        const scheduleNext = () => {
            if (active) {
                timer.current = setTimeout(fetchSystemInfo, SYSTEM_REFRESH_INTERVAL);
            }
        };

        const fetchSystemInfo = () => {
            if (document.visibilityState !== "visible") {
                scheduleNext();
                return;
            }
            getCsrData("/system", getTimeInfoBySearchStr(location.search), axiosInstance)
                .then(({ data }) => {
                    if (active) {
                        setState(data);
                    }
                })
                .catch(() => undefined)
                .finally(scheduleNext);
        };

        timer.current = setTimeout(fetchSystemInfo, SYSTEM_REFRESH_INTERVAL);
        return () => {
            active = false;
            if (timer && timer.current) {
                clearTimeout(timer.current);
            }
        };
    }, [axiosInstance, location.search]);

    useEffect(() => {
        setState(data);
    }, [data]);

    return (
        <Flex vertical gap="middle">
            {hasAction("system.manage") && (
                <div>
                    <Button onClick={() => navigate(getRealRouteUrl("/system/proxy"))}>
                        {getRes().system.proxy.title}
                    </Button>
                </div>
            )}
            <ServerInfo data={state} />
        </Flex>
    );
};

export default System;
