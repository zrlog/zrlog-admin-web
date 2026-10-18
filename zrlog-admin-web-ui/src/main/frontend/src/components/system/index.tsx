import ServerInfo from "./ServerInfo";
import { SystemData } from "../../type";
import { FunctionComponent, useEffect, useRef, useState } from "react";
import { getCsrData, getTimeInfoBySearchStr } from "../../api";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { useLocation } from "react-router";
import SystemSettingsLayout, { SystemInfoPage } from "./SystemSettingsLayout";

type SystemProps = {
    data: SystemData;
    offline: boolean;
    activePage?: SystemInfoPage;
};

const SYSTEM_REFRESH_INTERVAL = 5000;

const System: FunctionComponent<SystemProps> = ({ data, offline, activePage = "overview" }) => {
    const [state, setState] = useState<SystemData>(data);

    const axiosInstance = useAxiosBaseInstance();

    const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

    const location = useLocation();

    useEffect(() => {
        if (offline || activePage === "runtime") return;
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
            getCsrData(
                activePage === "resources" ? "/system/resources" : "/system",
                getTimeInfoBySearchStr(location.search),
                axiosInstance
            )
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
    }, [axiosInstance, location.search, offline, activePage]);

    useEffect(() => {
        setState(data);
    }, [data]);

    return (
        <SystemSettingsLayout activeKey={activePage}>
            <ServerInfo data={state} page={activePage} />
        </SystemSettingsLayout>
    );
};

export default System;
