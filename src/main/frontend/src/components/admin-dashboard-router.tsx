import { Route, Routes } from "react-router-dom";
import {
    ComponentType,
    FunctionComponent,
    lazy,
    ReactElement,
    Suspense,
    useCallback,
    useEffect,
    useMemo,
    useRef,
    useState,
} from "react";
import { useLocation } from "react-router";
import { getCsrData, getTimeInfoBySearchStr } from "../api";
import MyLoadingComponent from "./my-loading-component";
import {
    addToCache,
    getCacheByKey,
    getLastOpenedPage,
    getPageBuildId,
    getPageDataCacheKey,
    getPageDataCacheKeyByPath,
    getPageFullState,
    savePageFullState,
} from "../utils/cache";
import { deepEqualWithSpecialJSON, getFullPath, updateDocumentTitle } from "../utils/helpers";
import { isPWA } from "../utils/env-utils";
import * as H from "history";
import { useAxiosBaseInstance } from "../base/AppBase";
import { AdminCommonProps, BasicUserInfo } from "../type";
import { getSsDate, getWindowPageBuildId } from "../base/SsData";
import { JSX } from "react/jsx-runtime";
import { createAdminDashboardRoutes } from "./admin-dashboard-routes";
import IntrinsicAttributes = JSX.IntrinsicAttributes;

const AsyncNotFoundPage = lazy(() => import("components/not-found-page"));
const AdminManageLayout = lazy(() => import("layout"));

const isLivePageDataPath = (pathname: string) => {
    return pathname.replace(".html", "") === "/article-edit";
};

type AdminDashboardRouterState = {
    axiosRequesting: boolean;
    fullScreen: boolean;
    lastAxiosRequestedCacheKey: string;
    visiblePageDataCacheKey: string;
    visiblePathname: string;
    pageBuildId: string;
};

type AdminDashboardRouterProps = {
    offline: boolean;
    userInfo: BasicUserInfo;
};

type AdminPageProps<P> = {
    LazyComponent: ComponentType<P>;
    FallbackComponent: ComponentType<P>;
    componentKey?: string;
    props: P;
};

interface LazyWithFallbackElementProps<P> {
    LazyComponent: ComponentType<P>;
    FallbackComponent: ComponentType<P>;
    props: P;
}

export function LazyWithFallbackElement<P extends IntrinsicAttributes>({
    LazyComponent,
    FallbackComponent,
    props,
}: LazyWithFallbackElementProps<P>) {
    return (
        <Suspense fallback={<FallbackComponent {...props} />}>
            <LazyComponent {...props} />
        </Suspense>
    );
}

export function AdminPage(props: AdminPageProps<any>): ReactElement<AdminPageProps<AdminCommonProps<any>>> {
    const { FallbackComponent, LazyComponent, componentKey, props: componentProps } = props;

    return (
        <AdminManageLayout
            basicUserInfo={props.props.userInfo}
            offline={props.props.offline}
            systemNotification={props.props.systemNotification}
            syncStaticSite={props.props.pageBuildId !== getWindowPageBuildId()}
            loading={props.props.offlineData && !props.props.offline}
            fullScreen={props.props.fullScreen}
        >
            {props.props.data ? (
                <LazyWithFallbackElement
                    key={componentKey}
                    LazyComponent={LazyComponent}
                    FallbackComponent={FallbackComponent}
                    props={componentProps}
                />
            ) : (
                <MyLoadingComponent />
            )}
        </AdminManageLayout>
    );
}

const AdminDashboardRouter: FunctionComponent<AdminDashboardRouterProps> = ({ offline, userInfo }) => {
    const location = useLocation();
    const pwaLastOpenedPage = isPWA() ? getLastOpenedPage() : null;
    const defaultFullScreen = getPageFullState(pwaLastOpenedPage ? pwaLastOpenedPage : getFullPath(location));
    const initCurrentPageDataKey = getPageDataCacheKey(location);
    const serverSideData = useRef<boolean>(getSsDate() && getSsDate().data);
    const latestRequestSeqRef = useRef(0);
    const locationRef = useRef(location);
    locationRef.current = location;

    const [state, setState] = useState<AdminDashboardRouterState>({
        axiosRequesting: false,
        lastAxiosRequestedCacheKey: serverSideData.current ? initCurrentPageDataKey : "",
        visiblePageDataCacheKey: initCurrentPageDataKey,
        visiblePathname: location.pathname,
        fullScreen: defaultFullScreen,
        pageBuildId: getSsDate().pageBuildId,
    });

    const getDataFromCache = () => {
        if (serverSideData.current) {
            return getSsDate().data;
        }
        if (state.visiblePathname !== location.pathname) {
            return getCacheByKey(getPageDataCacheKey(location));
        }
        return getCacheByKey(state.visiblePageDataCacheKey);
    };

    const axiosBaseInstance = useAxiosBaseInstance();

    const loadData = async (currentPageDataKey: string, cacheData: any, location: H.Location, requestSeq: number) => {
        const responseData = await getCsrData(
            currentPageDataKey,
            getTimeInfoBySearchStr(location.search),
            axiosBaseInstance
        );
        if (responseData.error) {
            setState((prevState) => {
                return {
                    ...prevState,
                    axiosRequesting: false,
                };
            });
            return;
        }
        if (requestSeq !== latestRequestSeqRef.current) {
            return;
        }
        const { data, documentTitle, pageBuildId } = responseData;
        if (documentTitle) {
            updateDocumentTitle(documentTitle);
        }
        getSsDate().data = data;
        //请求数据和当前缓存一致时，只更新路由状态。
        if (deepEqualWithSpecialJSON(cacheData, data)) {
            setState((prevState) => {
                return {
                    ...prevState,
                    axiosRequesting: false,
                    pageBuildId: pageBuildId,
                    lastAxiosRequestedCacheKey: currentPageDataKey,
                    visiblePageDataCacheKey: currentPageDataKey,
                    visiblePathname: location.pathname,
                };
            });
            return;
        }
        addToCache(currentPageDataKey, data);
        setState(() => {
            return {
                axiosRequesting: false,
                pageBuildId: pageBuildId,
                lastAxiosRequestedCacheKey: currentPageDataKey,
                visiblePageDataCacheKey: currentPageDataKey,
                visiblePathname: location.pathname,
                fullScreen: getPageFullState(getFullPath(location)),
            };
        });
    };

    useEffect(() => {
        const currentPageDataKey = getPageDataCacheKeyByPath(location.pathname, location.search);
        if (serverSideData.current) {
            addToCache(currentPageDataKey, getSsDate().data);
            serverSideData.current = false;
            return;
        }
        // 先使用缓存数据显示。
        const cacheData = getCacheByKey(currentPageDataKey);
        setState((prevState) => {
            const samePathname = prevState.visiblePathname === location.pathname;
            const canReuseVisiblePageCache = !isLivePageDataPath(location.pathname);
            const visibleCacheData = canReuseVisiblePageCache
                ? getCacheByKey(prevState.visiblePageDataCacheKey)
                : undefined;
            return {
                ...prevState,
                axiosRequesting: !offline,
                visiblePageDataCacheKey:
                    cacheData !== undefined
                        ? currentPageDataKey
                        : samePathname && visibleCacheData !== undefined
                        ? prevState.visiblePageDataCacheKey
                        : currentPageDataKey,
                visiblePathname: location.pathname,
                fullScreen: getPageFullState(getFullPath(location)),
            };
        });
        if (offline) {
            return;
        }
        const requestSeq = latestRequestSeqRef.current + 1;
        latestRequestSeqRef.current = requestSeq;
        void loadData(currentPageDataKey, cacheData, location, requestSeq).catch(() => {
            if (requestSeq !== latestRequestSeqRef.current) {
                return;
            }
            // 标记当前请求已结束。
            setState((prevState) => {
                return {
                    ...prevState,
                    axiosRequesting: false,
                    fullScreen: getPageFullState(getFullPath(location)),
                };
            });
        });
    }, [location.pathname, location.search]);

    const handleFullScreen = useCallback(() => {
        const currentLocation = locationRef.current;
        setState((prevState) => {
            savePageFullState(getFullPath(currentLocation), true);
            if (prevState.fullScreen) {
                return prevState;
            }
            return { ...prevState, fullScreen: true };
        });
    }, []);

    const handleExitFullScreen = useCallback(() => {
        const currentLocation = locationRef.current;
        setState((prevState) => {
            if (!prevState.fullScreen) {
                return prevState;
            }
            savePageFullState(getFullPath(currentLocation), false);
            return { ...prevState, fullScreen: false };
        });
    }, []);

    const routes = useMemo(
        () =>
            createAdminDashboardRoutes({
                onFullScreen: handleFullScreen,
                onExitFullScreen: handleExitFullScreen,
            }),
        [handleExitFullScreen, handleFullScreen]
    );

    const isOfflineData = () => {
        if (serverSideData.current) {
            return false;
        }
        if (state.axiosRequesting) {
            return true;
        }
        return state.lastAxiosRequestedCacheKey !== getPageDataCacheKey(location);
    };

    const getVisiblePageDataCacheKey = () => {
        if (state.visiblePathname !== location.pathname) {
            return getPageDataCacheKey(location);
        }
        return state.visiblePageDataCacheKey;
    };

    const visibleRouteData = getDataFromCache();
    const visiblePageDataCacheKey = getVisiblePageDataCacheKey();
    const offlineData = isOfflineData();
    const pageBuildId = getPageBuildId();

    const commonPageProps = {
        userInfo: userInfo,
        fullScreen: state.fullScreen,
        data: visibleRouteData,
        offline: offline,
        systemNotification: getSsDate().systemNotification,
        messageCenter: getSsDate().messageCenter,
        pageBuildId,
        offlineData,
        updateCache: (e: any, cacheKey: string) => {
            addToCache(cacheKey, e);
        },
    } as AdminCommonProps<any>;

    return (
        <Routes>
            {routes.flatMap(({ paths, lazy, fallback, props = {}, getComponentKey }, i) =>
                paths.map((path, j) => {
                    return (
                        <Route
                            key={`${i}-${j}`}
                            path={path}
                            element={
                                <AdminPage
                                    LazyComponent={lazy}
                                    FallbackComponent={fallback}
                                    componentKey={getComponentKey?.(visibleRouteData, visiblePageDataCacheKey)}
                                    props={
                                        {
                                            ...props,
                                            ...commonPageProps,
                                        } as AdminCommonProps<any>
                                    }
                                />
                            }
                        />
                    );
                })
            )}
            <Route
                path={"*"}
                element={
                    <Suspense fallback={<MyLoadingComponent />}>
                        <AsyncNotFoundPage />
                    </Suspense>
                }
            />
        </Routes>
    );
};
export default AdminDashboardRouter;
