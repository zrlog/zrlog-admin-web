import { ActivityDay } from "./components/index/ActivityGraph";

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
    preUpgradeKey: string;
    version: UpgradeVersion;
};

export type ApiResponse<T> = {
    data: T;
    error: number;
    message: string;
    pageBuildId: string;
    documentTitle?: string;
};

export type UpgradeVersion = {
    changeLog: string;
    buildId: string;
    releaseDate?: string;
    version: string;
    type: string;
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
    auditLogs?: { timestamp: number, ip: string, action: string, type: string, os?: string, browser?: string, crawler?: boolean }[];
    loading: boolean;
    usedCacheSpace: number | string;
    usedDiskSpace: number | string;
};

export type Version = {
    releaseDate?: string;
    type: string;
    version: string;
};

export type MessageCenterNotice = {
    taskKey: string;
    type: "versionUpdate" | "unreadComment" | string;
    status: "notice" | "running" | "success" | "error";
    updatedAt: number;
    version?: UpgradeVersion;
    count?: number;
};

export type BasicUserInfo = {
    userName: string;
    header: string;
    key: string;
};

export enum AIProviderType {
    DEEP_SEEK = "DEEP_SEEK",
    OPEN_AI = "OPEN_AI",
    QWEN = "QWEN",
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
};

export type SystemData = {
    serverInfos: ServerInfoEntry[];
    serverInfos2: ServerInfoEntry[];
    dockerMode: boolean;
    nativeImageMode: boolean;
};

export type HealthCheckIssue = {
    key: string;
    severity: "warning" | "info" | "error";
    count: number;
    samples: string[];
    actionUri?: string | null;
};

export type HealthCheckSuggestion = {
    key: string;
    actionUri?: string | null;
};

export type SystemHealthData = {
    checkedAt: number;
    score: number;
    brokenLinkCount: number;
    seoIssueCount: number;
    databaseFragmentValue: number;
    databaseFragmentLabel: string;
    databaseEngine: string;
    canOptimizeDatabase: boolean;
    issues: HealthCheckIssue[];
    suggestions: HealthCheckSuggestion[];
};
