export type Schedule = {
    kind: "once" | "interval" | "daily" | "weekly";
    timezone: string;
    at?: number;
    intervalMinutes?: number;
    time?: string;
    weekdays?: number[];
};
export type Definition = {
    type: string; title: string; description: string; category: string;
    requiresAi: boolean; available: boolean; requiresAuthorization: boolean;
    unavailableReason?: string;
    requiredActions: string[]; optionalActions: string[];
    fields: {name: string; type: string; label: string; required: boolean; readOnly?: boolean; minimum?: number; maximum?: number}[];
    defaultSchedule?: Schedule;
    defaultParameters?: Record<string, unknown>;
};
export type Plan = {
    id?: string; revision?: number; name: string; type: string; enabled: boolean;
    schedule: Schedule; parameters: Record<string, unknown>; authorizedActions: string[];
};
export type Task = Plan & {
    id: string; revision: number; nextRunAt?: number; lastRunAt?: number; lastStatus?: string; activeRunId?: string;
};
export type Overview = {definitions: Definition[]; tasks: Task[]; total: number; aiAvailable: boolean; canManageScheduler: boolean};
export type ChildResult = {
    id: string; name: string; status: string; summary?: string; text?: string; actionPath?: string; actionLabel?: string;
    externalRunId?: string; startedAt?: number; finishedAt?: number; children?: ChildResult[];
};
export type Run = {
    id: string; taskId: string; taskName: string; taskType: string; status: string; trigger: string;
    startedAt: number; finishedAt?: number; summary?: string; planSnapshot: Plan;
    result?: {text?: string; actionPath?: string; actionLabel?: string; externalRunId?: string; children?: ChildResult[]};
};
export type Runs = {items: Run[]; total: number; page: number; pageSize: number};
export type Settings = {
    revision: number; internalEnabled: boolean; externalEnabled: boolean; externalBudgetSeconds: number;
    tokenConfigured: boolean; tokenCreatedAt?: number; faas: boolean; triggerPath: string;
};
export type Health = {startedAt: number; result?: {executed: number; failed: number; needsAttention: number;
    insufficientBudgetTaskIds: string[]; requiredBudgetSeconds: number}; errorCode?: string};
export type SettingsPage = {settings: Settings; internal?: Health; external?: Health};
export type Request = <T>(path: string, body?: unknown) => Promise<T>;
