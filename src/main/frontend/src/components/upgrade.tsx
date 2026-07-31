import { FunctionComponent, useEffect, useRef, useState } from "react";
import { App, Button, Card, Col, Grid, message, Row, Steps, theme } from "antd";
import { CheckCircleOutlined, CloseCircleOutlined, InfoCircleOutlined, LoadingOutlined } from "@ant-design/icons";
import Title from "antd/es/typography/Title";
import { getRealRouteUrl, getRes } from "../utils/constants";
import { getContextPath } from "../utils/helpers";
import { useAxiosBaseInstance } from "../base/AppBase";
import { API_ADMIN_STATIC_SITE_SYNC_PATH, API_DO_UPGRADE_PATH, getVersion } from "../api";
import { ApiResponse, UpgradeData } from "../type";
import UpgradeContent from "./upgrade-content";
import { markdownToHtml } from "@editor/dist/editor/utils/marked-utils";
import HtmlPreviewPanel from "@editor/dist/editor/html-preview-panel";
import { getAppState } from "../base/ConfigProviderApp";
import { postRefreshCacheSse, SseEvent } from "../utils/sse-utils";
import { createBackgroundTask, finishBackgroundTask, updateBackgroundTask } from "../utils/background-task-store";

const RESTART_CHECK_INITIAL_DELAY_MS = 2000;
const RESTART_CHECK_MAX_DELAY_MS = 10000;
const RESTART_CHECK_TIMEOUT_MS = 10 * 60 * 1000;

type UpgradeState = {
    current: number;
    manualMessageHtml: string;
    progressItems: UpgradeProgressItem[];
};

type StepInfo = {
    title: string;
    alias: "changeLog" | "doUpgrade";
};

export type UpgradeProps = {
    data: UpgradeData;
    offline: boolean;
    offlineData: boolean;
};

type UpgradeProcessResponse = {
    finish: boolean;
    message: string;
};

type StaticSiteSyncResponse = {
    synced: boolean;
};

type UpgradeProgressEvent = {
    stage?: string;
    status?: "running" | "complete" | "error" | "manual";
    message?: string;
    detail?: string;
};

type UpgradeProgressItem = UpgradeProgressEvent & {
    key: string;
};

const Upgrade: FunctionComponent<UpgradeProps> = ({ data, offline, offlineData }) => {
    const steps: StepInfo[] = [
        {
            title: getRes().upgrade.changeLog,
            alias: "changeLog",
        },
        {
            title: getRes().upgrade.execute,
            alias: "doUpgrade",
        },
    ];

    const upgradeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
    const upgradeTaskId = useRef<string | undefined>();
    const restartCheckReady = useRef(false);
    const progressEventReceived = useRef(false);
    const upgradeFinishing = useRef(false);
    const screens = Grid.useBreakpoint();
    const { token } = theme.useToken();
    const breakpointReady = screens.xs !== undefined;
    const narrow = screens.md !== true;
    const contentMaxWidth = narrow ? "100%" : screens.xl ? 760 : 720;

    const [state, setState] = useState<UpgradeState>({
        current: 0,
        manualMessageHtml: "",
        progressItems: [],
    });
    const { modal } = App.useApp();

    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });
    const axiosInstance = useAxiosBaseInstance();

    const clearUpgradeTimer = () => {
        if (upgradeTimer.current) {
            clearTimeout(upgradeTimer.current);
            upgradeTimer.current = null;
        }
    };

    const getUpgradeTaskDescription = (eventData: UpgradeProgressEvent) => {
        return [eventData.message, eventData.detail].filter(Boolean).join(" / ") || getRes().backgroundTask.started;
    };

    const ensureUpgradeTask = (description = getRes().backgroundTask.started) => {
        if (!upgradeTaskId.current) {
            upgradeTaskId.current = createBackgroundTask(getRes().backgroundTask.upgrade.title, description);
        }
        updateBackgroundTask(upgradeTaskId.current, {
            actionLabel: getRes().backgroundTask.upgrade.action,
            actionPath: "/upgrade",
            timeLabel: getRes().backgroundTask.updatedAt,
        });
        return upgradeTaskId.current;
    };

    const updateUpgradeTask = (eventData: UpgradeProgressEvent) => {
        if (!eventData.message && !eventData.detail) {
            return;
        }
        const taskId = ensureUpgradeTask(getUpgradeTaskDescription(eventData));
        const description = getUpgradeTaskDescription(eventData);
        if (eventData.status === "error") {
            finishBackgroundTask(taskId, "error", description);
            return;
        }
        if (eventData.status === "manual") {
            finishBackgroundTask(taskId, "warning", description);
            return;
        }
        if (eventData.status === "complete" && eventData.stage === "admin-static-sync") {
            finishBackgroundTask(taskId, "success", description);
            return;
        }
        updateBackgroundTask(taskId, {
            status: "running",
            description,
        });
    };

    const finishUpgradeTask = (
        status: "success" | "warning" | "error" | "cancelled",
        description = getRes().backgroundTask.finished
    ) => {
        const taskId = ensureUpgradeTask(description);
        finishBackgroundTask(taskId, status, description);
    };

    const redirectToAdminIndex = (newBuildId: string) => {
        window.location.href = getRealRouteUrl(getContextPath() + "admin/index?buildId=" + newBuildId);
    };

    const showUpgradeSuccess = (newBuildId: string, message?: string) => {
        modal.success({
            title: message || getRes().upgrade.restartDetected,
            content: "",
            onOk: function () {
                redirectToAdminIndex(newBuildId);
            },
        });
    };

    const syncAdminStaticResources = async () => {
        upsertProgressItem({
            stage: "admin-static-sync",
            status: "running",
            message: getRes().upgrade.syncAdminStatic,
            detail: getRes().upgrade.syncAdminStaticDetail,
        });
        const response = await postRefreshCacheSse<ApiResponse<StaticSiteSyncResponse>>(
            API_ADMIN_STATIC_SITE_SYNC_PATH,
            {
                messageApi,
                messageKey: "upgradeStaticSiteSync",
                responseEvents: ["response"],
                waitForComplete: true,
                showErrorMessage: false,
            }
        );
        if (response.error || !response.data?.synced) {
            throw new Error(response.message || getRes().upgrade.staticSyncFailed);
        }
        upsertProgressItem({
            stage: "admin-static-sync",
            status: "complete",
            message: getRes().staticSite.syncComplete,
        });
    };

    const recordUpgradeRestartNotice = async (status: "success" | "warning" | "error", buildId: string) => {
        try {
            await axiosInstance.post(
                "/api/admin/message-center/operation/upgrade-restart",
                {
                    status,
                    buildId,
                },
                { showError: false } as any
            );
        } catch {
            // This notice is best-effort; the upgrade flow state should not depend on it.
        }
    };

    const completeUpgrade = async (newBuildId: string, message?: string) => {
        if (upgradeFinishing.current) {
            return;
        }
        upgradeFinishing.current = true;
        clearUpgradeTimer();
        upsertProgressItem({
            stage: "restart-check",
            status: "complete",
            message: getRes().upgrade.restartDetected,
        });
        try {
            await syncAdminStaticResources();
            await recordUpgradeRestartNotice("success", newBuildId);
            finishUpgradeTask("success", message || getRes().upgrade.restartDetected);
            showUpgradeSuccess(newBuildId, message);
        } catch (e) {
            console.error(e);
            await recordUpgradeRestartNotice("warning", newBuildId);
            upsertProgressItem({
                stage: "admin-static-sync",
                status: "error",
                message: getRes().upgrade.staticSyncFailed,
                detail: getRes().upgrade.staticSyncFailedDetail,
            });
            finishUpgradeTask("warning", getRes().upgrade.staticSyncFailedDetail);
            modal.warning({
                title: getRes().upgrade.staticSyncFailed,
                content: getRes().upgrade.staticSyncFailedDetail,
                onOk: function () {
                    redirectToAdminIndex(newBuildId);
                },
            });
        }
    };

    const scheduleRestartCheck = (newBuildId: string, startedAt: number, delayMs: number) => {
        clearUpgradeTimer();
        if (Date.now() - startedAt >= RESTART_CHECK_TIMEOUT_MS) {
            upsertProgressItem({
                stage: "restart-check",
                status: "error",
                message: getRes().upgrade.restartCheckTimeout,
                detail: getRes().upgrade.restartCheckTimeoutDetail,
            });
            void recordUpgradeRestartNotice("error", newBuildId);
            finishUpgradeTask("error", getRes().upgrade.restartCheckTimeoutDetail);
            modal.warning({
                title: getRes().upgrade.restartCheckTimeout,
                content: getRes().upgrade.restartCheckTimeoutDetail,
            });
            return;
        }
        const remainingMs = Math.max(RESTART_CHECK_TIMEOUT_MS - (Date.now() - startedAt), 0);
        const nextDelayMs = Math.min(delayMs, remainingMs);
        upgradeTimer.current = setTimeout(() => {
            checkRestartSuccess(newBuildId, startedAt, delayMs);
        }, nextDelayMs);
    };

    const checkRestartSuccess = (
        newBuildId: string,
        startedAt = Date.now(),
        delayMs = RESTART_CHECK_INITIAL_DELAY_MS
    ) => {
        if (upgradeFinishing.current) {
            return;
        }
        upsertProgressItem({
            stage: "restart-check",
            status: "running",
            message: getRes().upgrade.waitingRestart,
            detail: getRes().upgrade.waitingRestartDetail,
        });
        getVersion(newBuildId, axiosInstance)
            .then((version) => {
                if (newBuildId === version.data?.buildId) {
                    void completeUpgrade(newBuildId, version.message);
                    return;
                }
                scheduleRestartCheck(newBuildId, startedAt, Math.min(delayMs * 1.5, RESTART_CHECK_MAX_DELAY_MS));
            })
            .catch(() => {
                scheduleRestartCheck(newBuildId, startedAt, Math.min(delayMs * 1.5, RESTART_CHECK_MAX_DELAY_MS));
            });
    };

    const getStepIndex = (alias: StepInfo["alias"]) => {
        const index = steps.findIndex((step) => step.alias === alias);
        return index >= 0 ? index : 0;
    };

    const currentStepAlias = steps[state.current]?.alias || "changeLog";
    const hasExecutionOutput = state.progressItems.length > 0 || !!state.manualMessageHtml;
    const executionTitle = data.onlineUpgradable ? getRes().upgrade.execute : getRes().upgrade.manualTitle;

    const updateManualMessage = async (message: string) => {
        if (!message) {
            return;
        }
        const htmlContent = await markdownToHtml(message);
        setState((prevState) => {
            return {
                ...prevState,
                manualMessageHtml: htmlContent,
                current: getStepIndex("doUpgrade"),
            };
        });
    };

    const upsertProgressItem = (eventData: UpgradeProgressEvent) => {
        if (!eventData.message && !eventData.detail) {
            return;
        }
        updateUpgradeTask(eventData);
        const key = eventData.stage || `event-${Date.now()}`;
        setState((prevState) => {
            const item = { ...eventData, key };
            const index = prevState.progressItems.findIndex((entry) => entry.key === key);
            if (index < 0) {
                return {
                    ...prevState,
                    current: getStepIndex("doUpgrade"),
                    progressItems: [...prevState.progressItems, item],
                };
            }
            const nextItems = [...prevState.progressItems];
            nextItems[index] = item;
            return {
                ...prevState,
                current: getStepIndex("doUpgrade"),
                progressItems: nextItems,
            };
        });
    };

    const onUpgradeEvent = (event: SseEvent) => {
        if (event.event === "upgrade-progress") {
            const eventData = event.data as UpgradeProgressEvent;
            progressEventReceived.current = true;
            if (eventData.stage === "manual" || eventData.status === "manual") {
                updateUpgradeTask(eventData);
                void updateManualMessage(eventData.message || "");
                return;
            }
            if (eventData.stage === "restart" || eventData.stage === "complete") {
                restartCheckReady.current = true;
            }
            upsertProgressItem(eventData);
        }
    };

    const renderProgressIcon = (item: UpgradeProgressItem) => {
        if (item.status === "error") {
            return <CloseCircleOutlined style={{ color: token.colorError }} />;
        }
        if (item.status === "complete") {
            return <CheckCircleOutlined style={{ color: token.colorSuccess }} />;
        }
        if (item.status === "manual") {
            return <InfoCircleOutlined style={{ color: token.colorInfo }} />;
        }
        return <LoadingOutlined style={{ color: token.colorPrimary }} />;
    };

    const renderProgressItem = (item: UpgradeProgressItem) => {
        return (
            <div
                key={item.key}
                style={{
                    display: "flex",
                    alignItems: "flex-start",
                    columnGap: token.marginSM,
                    padding: `${token.paddingXS}px 0`,
                    minWidth: 0,
                }}
            >
                <span style={{ lineHeight: token.lineHeight, width: 18, flex: "0 0 18px", paddingTop: 1 }}>
                    {renderProgressIcon(item)}
                </span>
                <div style={{ minWidth: 0, flex: 1 }}>
                    <div style={{ whiteSpace: "pre-wrap", wordBreak: "break-word" }}>{item.message}</div>
                    {item.detail && (
                        <div
                            style={{
                                color: token.colorTextSecondary,
                                whiteSpace: "pre-wrap",
                                wordBreak: "break-word",
                                fontSize: token.fontSizeSM,
                            }}
                        >
                            {item.detail}
                        </div>
                    )}
                </div>
            </div>
        );
    };

    const returnToChangeLog = () => {
        setState((prevState) => ({
            ...prevState,
            current: getStepIndex("changeLog"),
        }));
    };

    const newBuildId = data.version.buildId;

    const upgrade = async () => {
        const current = getStepIndex("doUpgrade");
        setState((prevState) => {
            return {
                ...prevState,
                current: current,
                manualMessageHtml: "",
                progressItems: [],
            };
        });
        restartCheckReady.current = false;
        progressEventReceived.current = false;
        upgradeFinishing.current = false;
        clearUpgradeTimer();
        upgradeTaskId.current = undefined;
        ensureUpgradeTask(getRes().upgrade.preparing);
        try {
            const response = await postRefreshCacheSse<ApiResponse<UpgradeProcessResponse>>(API_DO_UPGRADE_PATH, {
                // Keep cached gate-enabled backends compatible while the backup gate is disabled.
                body: { backupRiskAccepted: true },
                messageApi,
                messageKey: "upgrade",
                onEvent: onUpgradeEvent,
                responseEvents: ["response"],
                waitForComplete: true,
            });
            if (response.error) {
                messageApi.error(response.message);
                finishUpgradeTask("error", response.message);
                returnToChangeLog();
                return;
            }
            if (response.data?.message && !progressEventReceived.current) {
                await updateManualMessage(response.data.message);
                if (!(response.data.finish && data.onlineUpgradable)) {
                    finishUpgradeTask(response.data.finish ? "success" : "warning", response.data.message);
                }
            }
            if (response.data?.finish && data.onlineUpgradable) {
                checkRestartSuccess(newBuildId);
            }
        } catch (e) {
            console.error(e);
            if (data.onlineUpgradable && restartCheckReady.current) {
                checkRestartSuccess(newBuildId);
                return;
            }
            const errorMessage = e instanceof Error ? e.message : getRes().error.requestError;
            messageApi.error(errorMessage);
            finishUpgradeTask("error", errorMessage);
            returnToChangeLog();
        }
    };

    const next = async () => {
        if (currentStepAlias === "changeLog") {
            await upgrade();
        }
    };

    const nextDisabled = (): boolean => {
        if (offlineData) {
            return true;
        }
        if (offline) {
            return true;
        }
        if (!data.upgrade) {
            return true;
        }
        return false;
    };

    useEffect(() => {
        return () => {
            clearUpgradeTimer();
        };
    }, []);

    if (!breakpointReady) {
        return null;
    }

    return (
        <>
            {contextHolder}
            <Row key={data.version.buildId || data.version.version} justify="start" style={{ width: "100%" }}>
                <Col style={{ maxWidth: contentMaxWidth, width: "100%" }} xs={24} md={22} xl={18} xxl={14}>
                    <Card styles={{ body: { padding: narrow ? token.padding : token.paddingLG } }}>
                        <Steps
                            current={state.current}
                            direction={narrow ? "vertical" : "horizontal"}
                            size={narrow ? "small" : "default"}
                            style={{ paddingTop: narrow ? 0 : token.paddingSM }}
                            items={steps}
                        />
                        <div
                            className="steps-content"
                            style={{
                                marginTop: token.marginLG,
                                minWidth: 0,
                                overflowX: "auto",
                            }}
                        >
                            {currentStepAlias === "changeLog" && (
                                <>
                                    <Title level={4} style={{ marginTop: 0 }}>
                                        {getRes().upgrade.changeLog}
                                    </Title>
                                    <UpgradeContent data={data} />
                                </>
                            )}
                            {currentStepAlias === "doUpgrade" && (
                                <>
                                    <Title level={4} style={{ marginTop: 0 }}>
                                        {executionTitle}
                                    </Title>
                                    {!hasExecutionOutput &&
                                        renderProgressItem({
                                            key: "preparing",
                                            status: "running",
                                            message: getRes().upgrade.preparing,
                                            detail: getRes().upgrade.preparingDetail,
                                        })}
                                    {state.progressItems.length > 0 && (
                                        <div style={{ paddingTop: token.paddingXXS }}>
                                            {state.progressItems.map(renderProgressItem)}
                                        </div>
                                    )}
                                    {state.manualMessageHtml && (
                                        <HtmlPreviewPanel
                                            dark={getAppState().dark}
                                            htmlContent={state.manualMessageHtml}
                                        />
                                    )}
                                </>
                            )}
                        </div>
                        <div className="steps-action" style={{ paddingTop: token.marginLG }}>
                            {state.current < steps.length - 1 && (
                                <Button
                                    type="primary"
                                    loading={offlineData}
                                    disabled={nextDisabled()}
                                    onClick={() => next()}
                                    block={narrow}
                                >
                                    {data.onlineUpgradable ? getRes().upgrade.doUpgrade : getRes().upgrade.manualSteps}
                                </Button>
                            )}
                        </div>
                    </Card>
                </Col>
            </Row>
        </>
    );
};

export default Upgrade;
