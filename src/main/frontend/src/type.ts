import {ActivityDay} from "./components/index/ActivityGraph";
import {InteractiveSurface} from "./common/plugin-surface/types";
import type {BackgroundTaskStatus} from "./utils/background-task-store";

export type AppCompactModeState = {
    compactMode: boolean;
};

export type AppColorPrimaryState = {
    colorPrimary: string;
};

export type AppDarkState = {
    dark: boolean;
};

export type AppLangState = {
    lang: "en_US" | "zh_CN";
};

export type AppThemeState = {
    theme: string;
};

export type AppState = AppCompactModeState &
    AppColorPrimaryState &
    AppDarkState &
    AppLangState &
    AppThemeState & {
        offline: boolean;
    };

export type UpgradeData = {
    upgrade: boolean;
    onlineUpgradable: boolean;
    disableUpgradeReason: string;
    version: UpgradeVersion;
};

export type ApiResponse<T> = {
    data: T;
    error: number;
    message: string;
    pageBuildId: string;
    documentTitle?: string;
    messageCenter?: {
        revision: number;
        hasUnread: boolean;
    };
};

export type UpgradeVersion = {
    changeLog: string;
    buildId: string;
    releaseDate?: string;
    version: string;
    type: string;
};

export type PublicVersionResponse = {
    buildId?: string;
};

export type StatisticsInfoState = {
    clickCount: number;
    articleCount: number;
    commCount: number;
    toDayCommCount: number;
    draftCount: number;
    privateCount: number;
    publishedCount: number;
    typeData: { typeName: string, alias: string, typeamount: number }[];
    tagData: { text: string, count: number }[];
    auditLogs?: { timestamp: number, ip: string, action: string, type: string, content?: string, os?: string, browser?: string, crawler?: boolean }[];
    loading: boolean;
    usedCacheSpace: number | string;
    usedDiskSpace: number | string;
};

export type Version = {
    releaseDate?: string;
    type: string;
    version: string;
};

type MessageCenterNoticeBase = {
    taskKey: string;
    type: "versionUpdate" | "unreadComment" | string;
    status: BackgroundTaskStatus;
    updatedAt: number;
};

export type VersionUpdateNotice = MessageCenterNoticeBase & {
    type: "versionUpdate";
    payload: {
        version: UpgradeVersion;
    };
};

export type UnreadCommentNotice = MessageCenterNoticeBase & {
    type: "unreadComment";
    payload: {
        count: number;
    };
};

export type WebhookMessageNotice = MessageCenterNoticeBase & {
    type: "webhookMessage";
    payload: {
        title: string;
        description?: string;
        actionLabel?: string;
        actionPath?: string;
        source?: string;
        closable?: boolean;
        payload?: Record<string, unknown>;
    };
};

export type OperationTaskNotice = MessageCenterNoticeBase & {
    type: "operationTask";
    payload: {
        title: string;
        description?: string;
        actionLabel?: string;
        actionPath?: string;
        source?: string;
        closable?: boolean;
        payload?: Record<string, unknown>;
    };
};

export type MessageCenterNotice =
    | VersionUpdateNotice
    | UnreadCommentNotice
    | WebhookMessageNotice
    | OperationTaskNotice
    | (MessageCenterNoticeBase & {
          payload?: Record<string, unknown>;
      });

export type BasicUserInfo = {
    userName: string;
    header: string;
    key: string;
};

export enum AIProviderType {
    DEEP_SEEK = "DEEP_SEEK",
    OPEN_AI = "OPEN_AI",
    QWEN = "QWEN",
    GOOGLE_GEMINI = "GOOGLE_GEMINI",
}

export type LoginUserResponseInfo = BasicUserInfo & {
    key: string;
    mfaEnabled?: boolean;
    cacheableApiUris?: string[];
};

export type MfaStatusResponse = {
    enabled: boolean;
    secret: string;
    issuer: string;
    accountName: string;
    otpauthUrl: string;
};

export type AdminCommonProps<P> = {
    data: P;
    offlineData: boolean;
    offline: boolean;
    fullScreen?: boolean;
    userInfo?: BasicUserInfo;
    pageBuildId?: string;
    systemNotification?: string;
    messageCenter?: {
        revision: number;
        hasUnread: boolean;
    };
    updateCache?: (cache: P, cacheKey: string) => void;
};

export type ServerInfoEntry = {
    name: string;
    key: string;
    value: string;
};

export type IndexData = {
    statisticsInfo: StatisticsInfoState;
    activityData: ActivityDay[];
    tips: string[];
    welcomeTip: string;
    versionInfo: string;
    dashboardConfig: AdminDashboardConfig;
};

export type AdminDashboardCardConfig = {
    id: "welcome" | "localDraft" | "quickAction" | "statistics" | "activity" | "pluginPanels" | "auditTrail" | "dataInsights" | string;
    enabled: boolean;
    sort?: number;
    title?: string;
    data?: Record<string, unknown>;
};

export type AdminDashboardPluginPanelConfig = {
    id?: string;
    enabled?: boolean;
    type?: "surface" | "view";
    pluginName?: string;
    title?: string;
    surfaceUrl?: string;
    actionUrl?: string;
    viewUrl?: string;
    maxItems?: number;
    height?: number;
    sort?: number;
    order?: number;
    data?: Record<string, unknown> | InteractiveSurface;
    error?: string;
    surfaceLoaded?: boolean;
};

export type AdminDashboardConfig = {
    cards: AdminDashboardLayoutItem[];
    autoRefreshEnabled?: boolean;
    autoRefreshIntervalSeconds?: number;
};

export type AdminDashboardLayoutItem = ({
    kind: "card";
} & AdminDashboardCardConfig) | ({
    kind: "plugin";
} & AdminDashboardPluginPanelConfig);

export type SystemData = {
    serverInfos: ServerInfoEntry[];
    serverInfos2: ServerInfoEntry[];
    dockerMode: boolean;
    nativeImageMode: boolean;
};
