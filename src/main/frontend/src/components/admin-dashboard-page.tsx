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
                    if (data.key) {
                        getSsDate().key = data.key;
                    }
                    const userData = data.data;
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

    if (userInfo === undefined || userInfo === null) {
        return <Spin fullscreen={true} delay={1000} />;
    }

    return <AsyncAdminDashboardRouter offline={offline} userInfo={userInfo} />;
};

export default AdminDashboardPage;
