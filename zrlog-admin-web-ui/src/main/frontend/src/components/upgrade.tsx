import { Alert, Button, Checkbox, Empty, Grid, theme } from "antd";
import Progress from "@zrlog/ui/antd/Progress";
import { useUiMessage, useUiApp } from "@zrlog/ui/feedback";
import SuccessIcon from "@zrlog/ui/icons/success";
import ErrorIcon from "@zrlog/ui/icons/error";
import InfoIcon from "@zrlog/ui/icons/info";
import LoadingIcon from "@zrlog/ui/icons/loading";
import { FunctionComponent, useEffect, useId, useRef, useState } from "react";

import Title from "antd/es/typography/Title";
import { Link } from "react-router-dom";
import AdminSettingsLayout from "./common/AdminSettingsLayout";
import { getRealRouteUrl, getRes } from "../utils/constants";
import { getContextPath } from "../utils/helpers";
import { useAxiosBaseInstance } from "../base/AppBase";
import { API_ADMIN_STATIC_SITE_SYNC_PATH, API_DO_UPGRADE_PATH, getVersion } from "../api";
import { ApiResponse, UpgradeData } from "../type";
import UpgradeContent from "./upgrade-content";
import { markdownToHtml } from "@zrlog/editor/dist/editor/utils/marked-utils";
import HtmlPreviewPanel from "@zrlog/editor/core/editor/html-preview-panel";
import { getAppState } from "../base/ConfigProviderApp";
import { getRefreshCacheSse, postRefreshCacheSse, SseEvent } from "../utils/sse-utils";
import { createBackgroundTask, finishBackgroundTask, updateBackgroundTask } from "../utils/background-task-store";
import UpgradeReadiness from "./upgrade-readiness";
import {
    getUpgradeStepProgress,
    mergeUpgradeProgress,
    UpgradeProgressEvent,
    UpgradeProgressItem,
} from "../utils/upgrade-progress";
import {
    clearDockerUpgrade,
    DockerUpgradeJob,
    DockerUpgradeRequest,
    matchesDockerJob,
    newDockerRequestId,
    readDockerUpgrade,
    saveDockerUpgrade,
} from "../utils/docker-upgrade";

const RESTART_CHECK_INITIAL_DELAY_MS = 2000;
const RESTART_CHECK_MAX_DELAY_MS = 10000;
const RESTART_CHECK_TIMEOUT_MS = 10 * 60 * 1000;

type UpgradeState = {
    phase: "review" | "execution";
    online: boolean;
    manualMessageHtml: string;
    progressItems: UpgradeProgressItem[];
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

const Upgrade: FunctionComponent<UpgradeProps> = ({ data: initialData, offline, offlineData }) => {
    const riskDescriptionId = useId();
    const mounted = useRef(true);
    const connectionGeneration = useRef(0);
    const dockerStream = useRef<AbortController>();
    const dockerTask = useRef<DockerUpgradeRequest>();
    const [dockerPending, setDockerPending] = useState(false);
    const [dockerRetry, setDockerRetry] = useState(false);
    const upgradeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
    const upgradeTaskId = useRef<string | undefined>();
    const progressEventReceived = useRef(false);
    const failureEventReceived = useRef(false);
    const upgradeFinishing = useRef(false);
    const readinessRequest = useRef(0);
    const readinessRefreshing = useRef(false);
    const upgradeStarting = useRef(false);
    const screens = Grid.useBreakpoint();
    const { token } = theme.useToken();
    const breakpointReady = screens.xs !== undefined;
    const narrow = screens.md !== true;

    const [state, setState] = useState<UpgradeState>({
        phase: "review",
        online: initialData.onlineUpgradable,
        manualMessageHtml: "",
        progressItems: [],
    });
    const [upgradeRiskAccepted, setUpgradeRiskAccepted] = useState(false);
    const [data, setData] = useState(initialData);
    const [refreshing, setRefreshing] = useState(false);
    const [refreshError, setRefreshError] = useState<string>();
    const [checkedAt, setCheckedAt] = useState<number>();
    const { modal } = useUiApp();

    const [messageApi, contextHolder] = useUiMessage({ maxCount: 3 });
    const axiosInstance = useAxiosBaseInstance();

    useEffect(() => {
        setData(initialData);
        setUpgradeRiskAccepted(false);
        setRefreshing(false);
        setRefreshError(undefined);
        setCheckedAt(undefined);
        readinessRefreshing.current = false;
        return () => {
            readinessRequest.current += 1;
        };
    }, [initialData, offline]);

    const refreshReadiness = async () => {
        if (offline || readinessRefreshing.current || upgradeStarting.current || state.phase !== "review") {
            return;
        }
        const request = ++readinessRequest.current;
        readinessRefreshing.current = true;
        setRefreshing(true);
        setRefreshError(undefined);
        setUpgradeRiskAccepted(false);
        try {
            const requestConfig = { params: { fetch: true }, showError: false, timeout: 30000 };
            const { data: response } = await axiosInstance.get<ApiResponse<UpgradeData>>(
                "/api/admin/upgrade",
                requestConfig
            );
            if (request !== readinessRequest.current) {
                return;
            }
            if (
                response.error !== 0 ||
                typeof response.data?.upgrade !== "boolean" ||
                typeof response.data.onlineUpgradable !== "boolean" ||
                (response.data.upgrade && !response.data.version)
            ) {
                setRefreshError(getRes().upgrade.maintenance.refreshFailed);
                return;
            }
            setData(response.data);
            setCheckedAt(Date.now());
        } catch {
            if (request === readinessRequest.current) {
                setRefreshError(getRes().upgrade.maintenance.refreshFailed);
            }
        } finally {
            if (request === readinessRequest.current) {
                readinessRefreshing.current = false;
                setRefreshing(false);
            }
        }
    };

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
        if (!mounted.current || upgradeFinishing.current) {
            return;
        }
        upgradeFinishing.current = true;
        clearUpgradeTimer();
        if (dockerTask.current?.buildId === newBuildId) {
            clearDockerUpgrade(dockerTask.current.requestId);
            dockerTask.current = undefined;
            setDockerPending(false);
        }
        upsertProgressItem({
            stage: "restart-check",
            status: "complete",
            message: getRes().upgrade.restartDetected,
        });
        try {
            await syncAdminStaticResources();
            void recordUpgradeRestartNotice("success", newBuildId);
            finishUpgradeTask("success", message || getRes().upgrade.restartDetected);
            showUpgradeSuccess(newBuildId, message);
        } catch (e) {
            console.error(e);
            void recordUpgradeRestartNotice("warning", newBuildId);
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
        if (!mounted.current || upgradeFinishing.current || failureEventReceived.current) {
            return;
        }
        clearUpgradeTimer();
        if (Date.now() - startedAt >= RESTART_CHECK_TIMEOUT_MS) {
            failureEventReceived.current = true;
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
        if (!mounted.current || upgradeFinishing.current || failureEventReceived.current) {
            return;
        }
        const generation = connectionGeneration.current;
        upsertProgressItem({
            stage: "restart-check",
            status: "running",
            message: getRes().upgrade.waitingRestart,
            detail: getRes().upgrade.waitingRestartDetail,
        });
        getVersion(newBuildId, axiosInstance)
            .then((version) => {
                if (!mounted.current || failureEventReceived.current || generation !== connectionGeneration.current) {
                    return;
                }
                if (!version.error && newBuildId === version.data?.buildId) {
                    void completeUpgrade(newBuildId, version.message);
                    return;
                }
                scheduleRestartCheck(newBuildId, startedAt, Math.min(delayMs * 1.5, RESTART_CHECK_MAX_DELAY_MS));
            })
            .catch(() => {
                if (!mounted.current || generation !== connectionGeneration.current) {
                    return;
                }
                scheduleRestartCheck(newBuildId, startedAt, Math.min(delayMs * 1.5, RESTART_CHECK_MAX_DELAY_MS));
            });
    };

    const watchDockerUpgrade = async (task: DockerUpgradeRequest, submit: boolean) => {
        dockerStream.current?.abort();
        const controller = new AbortController();
        dockerStream.current = controller;
        dockerTask.current = task;
        failureEventReceived.current = false;
        const generation = connectionGeneration.current;
        const isCurrent = () =>
            mounted.current && !controller.signal.aborted && generation === connectionGeneration.current;
        let failed = false;
        let terminalFailure = false;
        let receivedProgress = false;
        setDockerPending(true);
        setState((previous) => ({ ...previous, online: true }));
        setDockerRetry(false);
        const options = {
            signal: controller.signal,
            messageApi,
            messageKey: "upgrade",
            responseEvents: ["response"],
            waitForComplete: true,
            showErrorMessage: false,
            onEvent: (event: SseEvent) => {
                if (!isCurrent() || upgradeFinishing.current) {
                    return;
                }
                if (event.event === "upgrade-progress") {
                    receivedProgress = true;
                }
                if (
                    event.event === "sse-error" ||
                    (event.event === "upgrade-progress" && event.data?.status === "error")
                ) {
                    failed = true;
                }
                if (event.event === "upgrade-progress" && event.data?.status === "error") {
                    terminalFailure = true;
                }
                onUpgradeEvent(event);
            },
        };
        try {
            type DockerResponse = ApiResponse<UpgradeProcessResponse | DockerUpgradeJob | null>;
            const response = submit
                ? await postRefreshCacheSse<DockerResponse>(API_DO_UPGRADE_PATH, {
                      ...options,
                      body: { upgradeRiskAccepted: true, docker: task },
                  })
                : await getRefreshCacheSse<DockerResponse>(
                      "/api/admin/upgrade/docker-status?requestId=" + encodeURIComponent(task.requestId),
                      options
                  );
            if (!isCurrent() || upgradeFinishing.current) {
                return;
            }
            if (submit && response.error && !receivedProgress) {
                messageApi.error(response.message);
                // A rejection may follow an accepted request. Recover by ID without submitting again.
                setState((previous) => ({ ...previous, progressItems: [] }));
                await watchDockerUpgrade(task, false);
                return;
            }
            if (response.error === 0 && response.data == null) {
                // A missing durable task requires an explicit retry with the same ID.
                setDockerRetry(true);
                upsertProgressItem({ stage: "error", status: "error", message: getRes().upgrade.docker.unknown });
                return;
            }
            if (response.data && "phase" in response.data) {
                // JSON fallback for deployments that disable SSE, including an older server during replacement.
                const job = response.data;
                if (!matchesDockerJob(job, task)) {
                    failed = true;
                    throw new Error(getRes().upgrade.docker.unknown);
                }
                failed = job.phase === "failed" || job.phase === "interrupted";
                terminalFailure = failed;
                onUpgradeEvent({
                    event: "upgrade-progress",
                    data: {
                        stage: failed
                            ? "error"
                            : job.phase === "succeeded"
                            ? "complete"
                            : job.phase === "queued" || job.phase === "pulling"
                            ? "download"
                            : "restart",
                        status: failed ? "error" : job.phase === "succeeded" ? "complete" : "running",
                        message: getRes().upgrade.docker.phases[job.phase],
                        detail: failed && job.recoveryRequired ? getRes().upgrade.docker.recovery : undefined,
                    },
                });
                await handleUpgradeResponse(
                    {
                        ...response,
                        error: failed ? 1 : response.error,
                        message: failed ? getRes().upgrade.docker.failed : response.message,
                        data: { finish: !failed, message: "" },
                    },
                    task.buildId,
                    true
                );
            } else {
                failed = failed || !!response.error;
                await handleUpgradeResponse(response as ApiResponse<UpgradeProcessResponse>, task.buildId, true);
            }
        } catch (error) {
            if (!isCurrent()) {
                return;
            }
            // Keep the durable ID while the common online-upgrade flow verifies the new version.
            handleUpgradeFailure(error, task.buildId, !failed);
        } finally {
            if (isCurrent() && terminalFailure) {
                clearDockerUpgrade(task.requestId);
                dockerTask.current = undefined;
                setDockerPending(false);
            }
        }
    };

    const startDockerUpgrade = async () => {
        let task = readDockerUpgrade();
        if (!task) {
            const version = data.version;
            if (!version?.artifactVersion || !version.channel) {
                throw new Error(getRes().upgrade.maintenance.refreshFailed);
            }
            task = {
                requestId: newDockerRequestId(),
                version: version.artifactVersion,
                buildId: version.buildId,
                channel: version.channel,
            };
        }
        try {
            saveDockerUpgrade(task);
        } catch {
            throw new Error(getRes().upgrade.docker.storageUnavailable);
        }
        await watchDockerUpgrade(task, true);
    };

    const retryDockerSubmission = async () => {
        if (upgradeStarting.current || offline) {
            return;
        }
        upgradeStarting.current = true;
        upgradeFinishing.current = false;
        progressEventReceived.current = false;
        clearUpgradeTimer();
        setState((previous) => ({ ...previous, progressItems: [], manualMessageHtml: "" }));
        try {
            await startDockerUpgrade();
        } catch (error) {
            const message = error instanceof Error ? error.message : getRes().upgrade.docker.failed;
            finishUpgradeTask("error", message);
            messageApi.error(message);
            returnToReview();
        } finally {
            upgradeStarting.current = false;
        }
    };

    const showExecution = state.phase === "execution" || dockerPending;
    const hasExecutionOutput = state.progressItems.length > 0 || !!state.manualMessageHtml;
    const executionTitle = state.online ? getRes().upgrade.execute : getRes().upgrade.manualTitle;
    const stepProgress = getUpgradeStepProgress(state.progressItems);
    const progressFailed = state.progressItems.some((item) => item.status === "error");

    const updateManualMessage = async (message: string) => {
        if (!message) {
            return;
        }
        const htmlContent = await markdownToHtml(message);
        setState((prevState) => {
            return {
                ...prevState,
                manualMessageHtml: htmlContent,
                phase: "execution",
            };
        });
    };

    const upsertProgressItem = (eventData: UpgradeProgressEvent) => {
        if (!eventData.message && !eventData.detail) {
            return;
        }
        updateUpgradeTask(eventData);
        const key = eventData.stage || `event-${Date.now()}`;
        setState((prevState) => ({
            ...prevState,
            phase: "execution",
            progressItems: mergeUpgradeProgress(prevState.progressItems, eventData, key),
        }));
    };

    const onUpgradeEvent = (event: SseEvent) => {
        if (!mounted.current || upgradeFinishing.current || failureEventReceived.current) return;
        if (event.event === "sse-error" || (event.event === "response" && event.data?.error)) {
            failureEventReceived.current = true;
            upsertProgressItem({
                stage: "error",
                status: "error",
                message: event.data?.message || getRes().error.requestError,
            });
            return;
        }
        if (event.event === "upgrade-progress") {
            const eventData = event.data as UpgradeProgressEvent;
            progressEventReceived.current = true;
            if (eventData.status === "error") {
                failureEventReceived.current = true;
            }
            if (eventData.stage === "manual" || eventData.status === "manual") {
                upsertProgressItem({ stage: "manual", status: "manual", message: getRes().upgrade.manualTitle });
                void updateManualMessage(eventData.message || "");
                return;
            }
            upsertProgressItem(eventData);
        }
    };

    const renderProgressIcon = (item: UpgradeProgressItem) => {
        if (item.status === "error") {
            return <ErrorIcon style={{ color: token.colorError }} />;
        }
        if (item.status === "complete") {
            return <SuccessIcon style={{ color: token.colorSuccess }} />;
        }
        if (item.status === "manual") {
            return <InfoIcon style={{ color: token.colorInfo }} />;
        }
        return <LoadingIcon style={{ color: token.colorPrimary }} />;
    };

    const renderProgressItem = (item: UpgradeProgressItem) => {
        return (
            <div
                key={item.key}
                role="listitem"
                aria-busy={!item.status || item.status === "running"}
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
                    <div style={{ color: token.colorTextSecondary, fontSize: token.fontSizeSM }}>
                        {item.status === "complete"
                            ? getRes().backgroundTask.status.success
                            : item.status === "error"
                            ? getRes().backgroundTask.status.error
                            : item.status === "manual"
                            ? getRes().backgroundTask.status.warning
                            : getRes().backgroundTask.status.running}
                    </div>
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

    const returnToReview = () => {
        setState((prevState) => ({
            ...prevState,
            phase: "review",
        }));
    };

    const handleUpgradeResponse = async (
        response: ApiResponse<UpgradeProcessResponse>,
        buildId: string,
        online: boolean
    ) => {
        if (!mounted.current || upgradeFinishing.current || failureEventReceived.current) return;
        if (response.error) {
            failureEventReceived.current = true;
            messageApi.error(response.message);
            upsertProgressItem({
                stage: "error",
                status: "error",
                message: response.message || getRes().error.requestError,
            });
            return;
        }
        if (response.data?.message && !progressEventReceived.current) {
            await updateManualMessage(response.data.message);
            if (!(response.data.finish && online)) {
                finishUpgradeTask(response.data.finish ? "success" : "warning", response.data.message);
            }
        }
        if (response.data?.finish && online) {
            checkRestartSuccess(buildId);
        }
    };

    const handleUpgradeFailure = (error: unknown, buildId: string, checkRestart: boolean) => {
        if (!mounted.current || upgradeFinishing.current) return;
        if (checkRestart && !failureEventReceived.current) {
            checkRestartSuccess(buildId);
            return;
        }
        const errorMessage = error instanceof Error ? error.message : getRes().error.requestError;
        messageApi.error(errorMessage);
        finishUpgradeTask("error", errorMessage);
        if (!failureEventReceived.current) {
            failureEventReceived.current = true;
            upsertProgressItem({ stage: "error", status: "error", message: errorMessage });
        }
    };

    const newBuildId = data.version?.buildId || "";

    const upgrade = async () => {
        if (upgradeStarting.current || readinessRefreshing.current || upgradeDisabled()) {
            return;
        }
        upgradeStarting.current = true;
        setState((prevState) => {
            return {
                ...prevState,
                phase: "execution",
                online: data.onlineUpgradable || !!data.dockerUpdaterAvailable,
                manualMessageHtml: "",
                progressItems: [],
            };
        });
        progressEventReceived.current = false;
        failureEventReceived.current = false;
        upgradeFinishing.current = false;
        clearUpgradeTimer();
        upgradeTaskId.current = undefined;
        ensureUpgradeTask(getRes().upgrade.preparing);
        try {
            if (data.dockerUpdaterAvailable) {
                await startDockerUpgrade();
                return;
            }
            const response = await postRefreshCacheSse<ApiResponse<UpgradeProcessResponse>>(API_DO_UPGRADE_PATH, {
                body: { upgradeRiskAccepted },
                messageApi,
                messageKey: "upgrade",
                onEvent: onUpgradeEvent,
                responseEvents: ["response"],
                waitForComplete: true,
                showErrorMessage: false,
            });
            await handleUpgradeResponse(response, newBuildId, data.onlineUpgradable);
        } catch (error) {
            // Any process replacement can close the stream before its restart event is delivered.
            handleUpgradeFailure(error, newBuildId, data.onlineUpgradable);
        } finally {
            upgradeStarting.current = false;
        }
    };

    const upgradeDisabled = (): boolean => {
        if (dockerPending || refreshing || refreshError || (offlineData && checkedAt === undefined)) {
            return true;
        }
        if (offline) {
            return true;
        }
        if (!data.upgrade || !data.version) {
            return true;
        }
        if (!upgradeRiskAccepted) {
            return true;
        }
        return false;
    };

    useEffect(() => {
        mounted.current = !offline;
        const pending = readDockerUpgrade();
        if (pending && !offline) {
            setDockerPending(true);
            void watchDockerUpgrade(pending, false);
        }
        return () => {
            mounted.current = false;
            connectionGeneration.current += 1;
            dockerStream.current?.abort();
            clearUpgradeTimer();
        };
    }, [offline]);

    if (!breakpointReady) {
        return null;
    }

    return (
        <AdminSettingsLayout
            activeKey="upgrade"
            title={getRes().upgrade.wizard}
            summary={null}
            extra={<Link to={getRealRouteUrl("/system/upgrade")}>{getRes().upgrade.backToSettings}</Link>}
            footer={
                !showExecution &&
                data.upgrade && (
                    <div
                        role="group"
                        aria-label={getRes().upgrade.risk.title}
                        style={{
                            display: "flex",
                            flexDirection: narrow ? "column" : "row",
                            alignItems: narrow ? "stretch" : "center",
                            gap: token.marginSM,
                        }}
                    >
                        <Checkbox
                            checked={upgradeRiskAccepted}
                            disabled={offline || refreshing || !!refreshError}
                            aria-describedby={riskDescriptionId}
                            style={{ flex: 1 }}
                            onChange={(event) => setUpgradeRiskAccepted(event.target.checked)}
                        >
                            {getRes().upgrade.risk.acceptance}
                        </Checkbox>
                        <Button
                            type="primary"
                            loading={offlineData && checkedAt === undefined}
                            disabled={upgradeDisabled()}
                            onClick={() => void upgrade()}
                            block={narrow}
                            style={{ flexShrink: 0 }}
                        >
                            {data.onlineUpgradable ? getRes().upgrade.doUpgrade : getRes().upgrade.manualSteps}
                        </Button>
                    </div>
                )
            }
        >
            {contextHolder}
            {dockerRetry && (
                <Button disabled={offline} onClick={() => void retryDockerSubmission()}>
                    {getRes().upgrade.docker.retrySubmission}
                </Button>
            )}
            <div style={{ minWidth: 0, paddingBottom: token.paddingLG }}>
                {!showExecution && (
                    <>
                        {data.upgrade ? (
                            <>
                                <Title level={4} style={{ marginTop: 0 }}>
                                    {getRes().upgrade.changeLog}
                                </Title>
                                <UpgradeContent data={data} />
                                <UpgradeReadiness
                                    data={data}
                                    offline={offline}
                                    onRefresh={() => void refreshReadiness()}
                                    refreshing={refreshing}
                                    refreshDisabled={offline || upgradeStarting.current}
                                    refreshError={refreshError}
                                    checkedAt={checkedAt}
                                />
                                <Alert
                                    id={riskDescriptionId}
                                    type="warning"
                                    showIcon
                                    style={{ marginTop: token.marginLG }}
                                    message={getRes().upgrade.risk.title}
                                    description={
                                        <>
                                            <div>{getRes().upgrade.risk.description}</div>
                                            <div style={{ marginTop: token.marginXS }}>
                                                {getRes().upgrade.risk.rollback}
                                            </div>
                                        </>
                                    }
                                />
                            </>
                        ) : (
                            <>
                                {refreshError && <Alert type="error" showIcon message={refreshError} />}
                                <Empty
                                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                                    description={getRes().upgrade.noPendingUpdate}
                                >
                                    <Button
                                        type="primary"
                                        loading={refreshing}
                                        disabled={offline || refreshing}
                                        onClick={() => void refreshReadiness()}
                                    >
                                        {getRes().upgrade.check}
                                    </Button>
                                </Empty>
                            </>
                        )}
                    </>
                )}
                {showExecution && (
                    <>
                        <Title level={4} style={{ marginTop: 0 }}>
                            {executionTitle}
                        </Title>
                        {state.online && (
                            <div style={{ marginBottom: token.marginSM }}>
                                <div>
                                    {getRes()
                                        .upgrade.stepProgress.replace("{completed}", String(stepProgress.completed))
                                        .replace("{total}", String(stepProgress.total))}
                                </div>
                                <Progress
                                    percent={stepProgress.percent}
                                    status={
                                        progressFailed
                                            ? "exception"
                                            : stepProgress.percent === 100
                                            ? "success"
                                            : "normal"
                                    }
                                    format={(percent) => `${percent}%`}
                                    aria-label={getRes().upgrade.execute}
                                />
                                <div style={{ color: token.colorTextSecondary, fontSize: token.fontSizeSM }}>
                                    {getRes().upgrade.stepProgressDetail}
                                </div>
                            </div>
                        )}
                        {(!hasExecutionOutput || state.progressItems.length > 0) && (
                            <div
                                role="list"
                                aria-label={getRes().upgrade.execute}
                                style={{ paddingTop: token.paddingXXS }}
                            >
                                {!hasExecutionOutput
                                    ? renderProgressItem({
                                          key: "preparing",
                                          status: "running",
                                          message:
                                              dockerPending && state.phase === "review"
                                                  ? getRes().upgrade.docker.restoring
                                                  : getRes().upgrade.preparing,
                                          detail:
                                              dockerPending && state.phase === "review"
                                                  ? undefined
                                                  : getRes().upgrade.preparingDetail,
                                      })
                                    : state.progressItems.map(renderProgressItem)}
                            </div>
                        )}
                        {state.manualMessageHtml && (
                            <HtmlPreviewPanel dark={getAppState().dark} htmlContent={state.manualMessageHtml} />
                        )}
                    </>
                )}
            </div>
        </AdminSettingsLayout>
    );
};

export default Upgrade;
