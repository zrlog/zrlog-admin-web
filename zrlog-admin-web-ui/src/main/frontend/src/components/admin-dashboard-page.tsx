import { FunctionComponent, lazy, useEffect, useState } from "react";
import { jumpToLoginPage, useAxiosBaseInstance } from "../base/AppBase";
import { BasicUserInfo } from "../type";
import { Spin } from "antd";
import { useNavigate } from "react-router-dom";
import { getSsDate } from "../base/SsData";
import { hasUserAppearance } from "../utils/user-appearance";
import { refreshUserInfo, subscribeUserInfo } from "../utils/user-info";

type AdminDashBroadPageProps = {
    offline: boolean;
};

const AsyncAdminDashboardRouter = lazy(() => import("components/admin-dashboard-router"));

const AdminDashboardPage: FunctionComponent<AdminDashBroadPageProps> = ({ offline }) => {
    const axiosBaseInstance = useAxiosBaseInstance();

    const navigate = useNavigate();

    const initUserInfo = getSsDate().user;

    const [userInfo, setUserInfo] = useState<BasicUserInfo | null>(initUserInfo);
    const session = getSsDate().key;

    useEffect(() => subscribeUserInfo(setUserInfo), []);

    useEffect(() => {
        const currentUser = getSsDate().user;
        if (offline || (currentUser && hasUserAppearance(currentUser) && !getSsDate().userFromCache)) return;
        let active = true;
        const userId = currentUser?.userId;
        const isCurrent = () => active && session === getSsDate().key && userId === getSsDate().user?.userId;
        refreshUserInfo(axiosBaseInstance, () => active)
            .then((response) => {
                if (response && response.error !== 0) jumpToLoginPage(navigate);
            })
            .catch(() => {
                // An existing cached account remains available during a transient outage.
                if (isCurrent() && !currentUser) jumpToLoginPage(navigate);
            });
        return () => {
            active = false;
        };
    }, [offline, session, axiosBaseInstance, navigate]);

    if (userInfo === undefined || userInfo === null) {
        return <Spin fullscreen={true} delay={1000} />;
    }

    return <AsyncAdminDashboardRouter offline={offline} userInfo={userInfo} />;
};

export default AdminDashboardPage;
