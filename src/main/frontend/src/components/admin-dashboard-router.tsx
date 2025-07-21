import { Route, Routes } from "react-router-dom";
import { ComponentType, FunctionComponent, lazy, ReactElement, Suspense, useEffect, useRef, useState } from "react";
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
import Upgrade from "./upgrade";
import { isPWA } from "../utils/env-utils";
import * as H from "history";
import Plugin from "./plugin";
import WebSite, { WebSiteProps } from "./website";
import TemplateConfig from "./template/template-config";
import AccountSecurity from "./account-security";
import TemplateCenter from "./template/template-center";
import User from "./user";
import ArticleEdit from "./articleEdit";
import { ArticleEditProps } from "./articleEdit/index.types";
import System from "./system";
import Link from "./link";
import Nav from "./nav";
import Article from "./article";
import Type from "./type";
import UnknownErrorPage, { ErrorPageProps } from "./unknown-error-page";
import Offline from "../common/Offline";
import Index from "./index";
import Comment from "./comment";
import { buildUriPaths, useAxiosBaseInstance } from "../base/AppBase";
import { AdminCommonProps, BasicUserInfo } from "../type";
import { getSsDate, getWindowPageBuildId } from "../base/SsData";
import Version from "./website/version";
import StaticSite from "./StaticSite";
import { JSX } from "react/jsx-runtime";
import Dev from "./dev";
import IntrinsicAttributes = JSX.IntrinsicAttributes;

const AsyncArticleEdit = lazy(() => import("components/articleEdit"));
const AsyncOffline = lazy(() => import("common/Offline"));

const AsyncComment = lazy(() => import("components/comment"));

const AsyncPlugin = lazy(() => import("components/plugin"));

const AsyncIndex = lazy(() => import("components/index"));

const AsyncWebSite = lazy(() => import("components/website"));

const AsyncType = lazy(() => import("components/type"));

const AsyncLink = lazy(() => import("components/link"));

const AsyncNav = lazy(() => import("components/nav"));

const AsyncNotFoundPage = lazy(() => import("components/not-found-page"));

const AsyncUpgrade = lazy(() => import("components/upgrade"));

const AsyncTemplateCenter = lazy(() => import("components/template/template-center"));

const AsyncTemplateConfig = lazy(() => import("components/template/template-config"));

const AsyncAccountSecurity = lazy(() => import("components/account-security"));

const AsyncArticle = lazy(() => import("components/article"));

const AsyncUser = lazy(() => import("components/user"));
const AsyncError = lazy(() => import("components/unknown-error-page"));
const AsyncSystem = lazy(() => import("components/system"));
const AsyncStaticSite = lazy(() => import("components/StaticSite"));

const AdminManageLayout = lazy(() => import("layout"));
const DevAsync = lazy(() => import("components/dev"));

type AdminDashboardRouterState = {
    axiosRequesting: boolean;
    fullScreen: boolean;
    lastAxiosRequestedCacheKey: string;
    pageBuildId: string;
};

type AdminDashboardRouterProps = {
    offline: boolean;
    userInfo: BasicUserInfo;
};

type AdminPageProps<P> = {
    LazyComponent: ComponentType<P>;
    FallbackComponent: ComponentType<P>;
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
    const { FallbackComponent, LazyComponent, props: componentProps } = props;

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

    const [state, setState] = useState<AdminDashboardRouterState>({
        axiosRequesting: false,
        lastAxiosRequestedCacheKey: serverSideData.current ? initCurrentPageDataKey : "",
        fullScreen: defaultFullScreen,
        pageBuildId: getSsDate().pageBuildId,
    });

    const getDataFromCache = () => {
        const pageDataCacheKey = getPageDataCacheKey(location);
        if (serverSideData.current) {
            return getSsDate().data;
        }
        return getCacheByKey(pageDataCacheKey);
    };

    const axiosBaseInstance = useAxiosBaseInstance();

    const loadData = async (currentPageDataKey: string, cacheData: any, location: H.Location, requestSeq: number) => {
        const responseData = await getCsrData(
            currentPageDataKey,
            getTimeInfoBySearchStr(location.search),
            axiosBaseInstance
        );
        console.info(responseData);
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
        //如果请求回来的和请求回来的一致的情况
        if (deepEqualWithSpecialJSON(cacheData, data)) {
            console.info(currentPageDataKey + " cache hits");
            setState((prevState) => {
                return {
                    ...prevState,
                    axiosRequesting: false,
                    pageBuildId: pageBuildId,
                    lastAxiosRequestedCacheKey: currentPageDataKey,
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
        //使用缓存先显示
        const cacheData = getCacheByKey(currentPageDataKey);
        //console.info(currentPageDataKey + "=> " + JSON.stringify(cacheData))
        setState((prevState) => {
            return {
                ...prevState,
                axiosRequesting: !offline,
                fullScreen: getPageFullState(getFullPath(location)),
            };
        });
        if (offline) {
            return;
        }
        const requestSeq = latestRequestSeqRef.current + 1;
        latestRequestSeqRef.current = requestSeq;
        loadData(currentPageDataKey, cacheData, location, requestSeq)
            .then(() => {
                //ignore
            })
            .catch(() => {
                if (requestSeq !== latestRequestSeqRef.current) {
                    return;
                }
                //标记未没有请求了
                setState((prevState) => {
                    return {
                        ...prevState,
                        axiosRequesting: false,
                        fullScreen: getPageFullState(getFullPath(location)),
                    };
                });
            });
    }, [location.pathname, location.search]);

    const routes = [
        {
            paths: [...buildUriPaths("index"), ...buildUriPaths("")],
            lazy: AsyncIndex,
            fallback: Index,
        },
        {
            paths: buildUriPaths("comment"),
            lazy: AsyncComment,
            fallback: Comment,
        },
        {
            paths: buildUriPaths("plugin"),
            lazy: AsyncPlugin,
            fallback: Plugin,
        },
        {
            paths: buildUriPaths("website"),
            lazy: AsyncWebSite,
            fallback: WebSite,
            props: { activeKey: "basic" } as WebSiteProps,
        },
        {
            paths: buildUriPaths("website/admin"),
            lazy: AsyncWebSite,
            fallback: WebSite,
            props: { activeKey: "admin" } as WebSiteProps,
        },
        {
            paths: buildUriPaths("website/template"),
            lazy: AsyncWebSite,
            fallback: WebSite,
            props: { activeKey: "template" } as WebSiteProps,
        },
        {
            paths: buildUriPaths("website/other"),
            lazy: AsyncWebSite,
            fallback: WebSite,
            props: { activeKey: "other" } as WebSiteProps,
        },
        {
            paths: buildUriPaths("website/blog"),
            lazy: AsyncWebSite,
            fallback: WebSite,
            props: { activeKey: "blog" } as WebSiteProps,
        },
        {
            paths: buildUriPaths("website/ai"),
            lazy: AsyncWebSite,
            fallback: WebSite,
            props: { activeKey: "ai" } as WebSiteProps,
        },
        {
            paths: buildUriPaths("website/upgrade"),
            lazy: AsyncWebSite,
            fallback: WebSite,
            props: { activeKey: "upgrade" } as WebSiteProps,
        },
        {
            paths: buildUriPaths("website/version"),
            lazy: Version,
            fallback: Version,
        },
        {
            paths: buildUriPaths("article-type"),
            lazy: AsyncType,
            fallback: Type,
        },
        {
            paths: buildUriPaths("link"),
            lazy: AsyncLink,
            fallback: Link,
        },
        {
            paths: buildUriPaths("nav"),
            lazy: AsyncNav,
            fallback: Nav,
        },
        {
            paths: buildUriPaths("article"),
            lazy: AsyncArticle,
            fallback: Article,
        },
        {
            paths: buildUriPaths("article-edit"),
            lazy: AsyncArticleEdit,
            fallback: ArticleEdit,
            props: {
                onFullScreen: () => {
                    setState((prevState) => {
                        savePageFullState(getFullPath(location), true);
                        return { ...prevState, fullScreen: true };
                    });
                },
                onExitFullScreen: () => {
                    if (state.fullScreen) {
                        setState((prevState) => {
                            savePageFullState(getFullPath(location), false);
                            return { ...prevState, fullScreen: false };
                        });
                    }
                },
            } as ArticleEditProps,
        },
        {
            paths: buildUriPaths("user"),
            lazy: AsyncUser,
            fallback: User,
        },
        {
            paths: buildUriPaths("template-center"),
            lazy: AsyncTemplateCenter,
            fallback: TemplateCenter,
        },
        {
            paths: [...buildUriPaths("account-security"), ...buildUriPaths("user-update-password")],
            lazy: AsyncAccountSecurity,
            fallback: AccountSecurity,
        },
        {
            paths: buildUriPaths("upgrade"),
            lazy: AsyncUpgrade,
            fallback: Upgrade,
        },
        {
            paths: buildUriPaths("template-config"),
            lazy: AsyncTemplateConfig,
            fallback: TemplateConfig,
        },
        {
            paths: buildUriPaths("403"),
            lazy: AsyncError,
            fallback: UnknownErrorPage,
            props: {
                code: 403,
            } as ErrorPageProps,
        },
        {
            paths: buildUriPaths("500"),
            lazy: AsyncError,
            fallback: UnknownErrorPage,
            props: {
                code: 500,
            } as ErrorPageProps,
        },
        {
            paths: buildUriPaths("offline"),
            lazy: AsyncOffline,
            fallback: Offline,
        },
        {
            paths: buildUriPaths("system"),
            lazy: AsyncSystem,
            fallback: System,
        },
        {
            paths: buildUriPaths("static-site"),
            lazy: AsyncStaticSite,
            fallback: StaticSite,
        },
        {
            paths: buildUriPaths("dev"),
            lazy: DevAsync,
            fallback: Dev,
        },
    ];

    const isOfflineData = () => {
        if (serverSideData.current) {
            return false;
        }
        if (state.axiosRequesting) {
            return true;
        }
        return state.lastAxiosRequestedCacheKey !== getPageDataCacheKey(location);
    };

    //console.info(location.pathname + "," + JSON.stringify(state));

    return (
        <Routes>
            {routes.flatMap(({ paths, lazy, fallback, props = {} }, i) =>
                paths.map((path, j) => (
                    <Route
                        key={`${i}-${j}`}
                        path={path}
                        element={
                            <AdminPage
                                LazyComponent={lazy}
                                FallbackComponent={fallback}
                                props={
                                    {
                                        ...props,
                                        userInfo: userInfo,
                                        fullScreen: state.fullScreen,
                                        data: getDataFromCache(),
                                        offline: offline,
                                        systemNotification: getSsDate().systemNotification,
                                        pageBuildId: getPageBuildId(),
                                        offlineData: isOfflineData(),
                                        updateCache: (e, cacheKey) => {
                                            addToCache(cacheKey, e);
                                        },
                                    } as AdminCommonProps<any>
                                }
                            />
                        }
                    />
                ))
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
