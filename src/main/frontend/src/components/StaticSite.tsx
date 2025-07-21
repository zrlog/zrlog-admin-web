import { FunctionComponent, useState } from "react";
import { AdminCommonProps } from "../type";
import { FloatButton, message } from "antd";
import { LoadingOutlined, SyncOutlined } from "@ant-design/icons";
import { getRes } from "../utils/constants";
import { postRefreshCacheSse } from "../utils/sse-utils";
import { refreshLocationForce } from "../utils/helpers";

type StaticSiteData = {
    synced: boolean;
};

type StaticSiteState = {
    synced: boolean;
    syncing: boolean;
};

const StaticSite: FunctionComponent<AdminCommonProps<StaticSiteData>> = ({ data }) => {
    const [state, setState] = useState<StaticSiteState>({
        synced: data.synced,
        syncing: false,
    });

    const [messageApi, messageContextHolder] = message.useMessage({
        maxCount: 3,
    });

    return (
        <>
            {messageContextHolder}
            <FloatButton
                style={{
                    display: state.synced ? "none" : "inherit",
                }}
                onClick={async () => {
                    setState({
                        synced: false,
                        syncing: true,
                    });
                    let responseData;
                    try {
                        responseData = await postRefreshCacheSse<{
                            data: StaticSiteData;
                            error: number;
                            message: string;
                        }>("/api/admin/static-site/startSync", {
                            messageApi,
                            messageKey: "staticSiteSync",
                            resolveWhenStarted: true,
                            removeBackgroundTaskOnSuccess: true,
                            waitForComplete: true,
                            backgroundTaskTitle:
                                getRes().backgroundTask.title + " · " + getRes().staticSite.syncingAdmin,
                        });
                    } catch (e) {
                        messageApi.error(e instanceof Error ? e.message : getRes().staticSite.syncFailed);
                        setState({
                            synced: false,
                            syncing: false,
                        });
                        return;
                    }
                    if (responseData && responseData.error) {
                        messageApi.error(`${getRes().staticSite.syncFailed} -> ${responseData.message}`);
                        setState({
                            synced: false,
                            syncing: false,
                        });
                        return;
                    }
                    if (responseData && (responseData.data as StaticSiteData)) {
                        if (responseData.data.synced) {
                            messageApi.success(getRes().staticSite.syncComplete);
                            setState({
                                synced: responseData.data.synced,
                                syncing: false,
                            });
                            refreshLocationForce();
                        } else {
                            messageApi.info(getRes().staticSite.syncIncomplete);
                            setState({
                                synced: false,
                                syncing: false,
                            });
                        }
                    } else {
                        setState({
                            synced: false,
                            syncing: false,
                        });
                    }
                }}
                icon={state.syncing ? <LoadingOutlined /> : <SyncOutlined />}
            />
        </>
    );
};

export default StaticSite;
