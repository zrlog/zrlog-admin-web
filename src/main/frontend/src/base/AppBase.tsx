import { Route, Routes, useNavigate } from "react-router-dom";
import { lazy, Suspense, useMemo } from "react";
import { App, Spin } from "antd";
import axios, { AxiosError, AxiosInstance } from "axios";
import { API_DO_UPGRADE_PATH, API_VERSION_PATH } from "../api";
import ErrorBoundary from "../common/ErrorBoundary";
import { getBackendServerUrl, getRealRouteUrl, getRes, isStaticPage } from "../utils/constants";
import AdminDashboardPage from "../components/admin-dashboard-page";
import { NavigateFunction } from "react-router";
import { syncMessageCenterStatus } from "../utils/message-center-status";
import { ADMIN_ERROR_CODE } from "../common/admin-error-code";

const AsyncLogin = lazy(() => import("components/login"));

const errorCountMap = new Map<number, number>();

export const jumpToLoginPage = (navigate: NavigateFunction): void => {
    if (!window.location.search.includes("redirectFrom")) {
        navigate(
            getRealRouteUrl(
                `/login?redirectFrom=${encodeURIComponent(
                    window.location.pathname.split(".html")[0]
                )}${encodeURIComponent(window.location.search)}`
            ),
            { replace: true }
        );
    }
};

export const useAxiosBaseInstance = (getContainer?: () => HTMLElement): AxiosInstance => {
    const { modal, message } = App.useApp();
    const navigate = useNavigate();

    return useMemo(() => {
        const axiosInstance = axios.create();
        if (isStaticPage()) {
            axiosInstance.defaults.withCredentials = true;
        }

        const commonAxiosErrorHandle = (error: any): Promise<any> => {
            // Upgrade flow handles these request failures in its own progress UI.
            if ((error as AxiosError) && error.config && error.config.url) {
                if ((error.config as any).showError === false) {
                    return Promise.reject(error);
                }
                if (error.config.url.includes(API_VERSION_PATH)) {
                    return Promise.reject(error.message);
                }
                if (error.config.url.includes(API_DO_UPGRADE_PATH)) {
                    return Promise.reject(error.message);
                }
            }
            if (error && error.response) {
                if (error.response.status) {
                    modal.error({
                        title: `${getRes().error.serviceException}[${error.response.status}]`,
                        content: (
                            <div
                                style={{ paddingTop: 20, overflow: "auto" }}
                                dangerouslySetInnerHTML={{ __html: error.response.data }}
                            />
                        ),
                        getContainer: getContainer ? getContainer() : undefined,
                    });
                    return Promise.reject(error.response);
                }
            } else {
                if ((error as AxiosError) && error.config && error.config.url) {
                    if (navigator.onLine) {
                        modal.error({
                            title: `${getRes().error.requestError}: ${error.config.url}`,
                            content: error.message,
                            getContainer: getContainer ? getContainer() : undefined,
                        });
                    } else {
                        message.error(
                            `${getRes().error.requestError}: ${error.config.url} ${error.toString()} ${
                                getRes().error.networkOffline
                            }`
                        );
                    }
                }
            }
            return Promise.reject(error);
        };

        axiosInstance.defaults.baseURL = getBackendServerUrl();

        axiosInstance.interceptors.response.use(
            (response) => {
                syncMessageCenterStatus(response.data?.messageCenter);
                const errorCode = response.data.error;
                if (errorCode === ADMIN_ERROR_CODE.authSessionExpired) {
                    let count = errorCountMap.get(errorCode);
                    if (count === null || count === undefined) {
                        count = 0;
                    }
                    if (count === 0) {
                        errorCountMap.set(errorCode, count + 1);
                        modal.error({
                            title: response.data.error,
                            content: response.data.message,
                            getContainer: getContainer ? getContainer() : undefined,
                            onOk: () => {
                                errorCountMap.set(errorCode, 0);
                            },
                        });
                    }

                    if (isStaticPage()) {
                        jumpToLoginPage(navigate);
                    }
                    return Promise.reject(response.data);
                }
                return response;
            },
            (error) => {
                return commonAxiosErrorHandle(error);
            }
        );
        return axiosInstance;
    }, [modal, message, navigate, getContainer]);
};

export const buildUriPaths = (uri: string) => {
    return [uri, uri + ".html"];
};

const AppBase = ({ offline }: { offline: boolean }) => {
    return (
        <Routes>
            {buildUriPaths("login").map((e) => {
                return (
                    <Route
                        key={e}
                        path={e}
                        element={
                            <ErrorBoundary>
                                <Suspense fallback={<Spin spinning={true} fullscreen delay={1000} />}>
                                    <AsyncLogin offline={offline} />
                                </Suspense>
                            </ErrorBoundary>
                        }
                    />
                );
            })}

            <Route
                path={"logout"}
                element={
                    <ErrorBoundary>
                        <Suspense fallback={<Spin spinning={true} fullscreen delay={1000} />}>
                            <AsyncLogin offline={offline} />
                        </Suspense>
                    </ErrorBoundary>
                }
            />
            <Route
                path={"*"}
                element={
                    <ErrorBoundary>
                        <Suspense>
                            <AdminDashboardPage offline={offline} />
                        </Suspense>
                    </ErrorBoundary>
                }
            />
        </Routes>
    );
};

export default AppBase;
