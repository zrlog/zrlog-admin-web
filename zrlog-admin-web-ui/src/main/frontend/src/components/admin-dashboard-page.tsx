import { applyUserPreferences, getUserPreferenceRevision } from "../utils/user-preferences";
import { FunctionComponent, lazy, useEffect, useState } from "react";
import { jumpToLoginPage, useAxiosBaseInstance } from "../base/AppBase";
import { BasicUserInfo } from "../type";
import { Spin } from "antd";
import { getCsrData } from "../api";
import { useNavigate } from "react-router-dom";
import { getSsDate } from "../base/SsData";

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

    useEffect(() => {
        if (offline) {
            return;
        }
        //有用户信息，不用主动请求
        if (userInfo !== undefined && userInfo !== null) {
            return;
        }
        getCsrData(`/user/info`, new Date().getTime(), axiosBaseInstance)
            .then((data) => {
                if (data && data.error === 0) {
                    const userData: BasicUserInfo = data.data;
                    if (userData.key) {
                        getSsDate().key = userData.key;
                    }
                    getSsDate().user = userData;
                    setUserInfo(userData);
                } else {
                    jumpToLoginPage(navigate);
                }
            })
            .catch(() => {
                jumpToLoginPage(navigate);
            });
    }, []);

    useEffect(() => {
        if (offline || !userInfo) return;
        const appearanceSession = getSsDate().appearanceSession;
        if (appearanceSession && appearanceSession.key === session && appearanceSession.userId === userInfo.userId)
            return;
        let active = true;
        const revision = getUserPreferenceRevision();
        const userId = userInfo.userId;
        axiosBaseInstance
            .get("/api/admin/user/preferences")
            .then(({ data }) => {
                if (
                    active &&
                    data.error === 0 &&
                    revision === getUserPreferenceRevision() &&
                    session === getSsDate().key &&
                    userId === getSsDate().user?.userId
                ) {
                    applyUserPreferences(data.data.effective);
                    getSsDate().appearanceSession = { key: session, userId };
                }
            })
            .catch(() => undefined);
        return () => {
            active = false;
        };
    }, [offline, userInfo?.userId, session, axiosBaseInstance]);

    if (userInfo === undefined || userInfo === null) {
        return <Spin fullscreen={true} delay={1000} />;
    }

    return <AsyncAdminDashboardRouter offline={offline} userInfo={userInfo} />;
};

export default AdminDashboardPage;
