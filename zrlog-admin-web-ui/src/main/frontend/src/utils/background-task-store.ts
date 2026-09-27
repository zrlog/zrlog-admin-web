export type BackgroundTaskStatus = "pending" | "running" | "success" | "warning" | "error" | "cancelled" | "notice";

export type BackgroundTaskFinishStatus = Extract<BackgroundTaskStatus, "success" | "warning" | "error" | "cancelled">;

export type BackgroundTask = {
    id: string;
    taskKey?: string;
    title: string;
    description?: string;
    actionLabel?: string;
    actionPath?: string;
    timeLabel?: string;
    closable?: boolean;
    dismissPath?: string;
    dismissPayload?: Record<string, unknown>;
    status: BackgroundTaskStatus;
    createdAt: number;
    updatedAt: number;
};

const STORAGE_KEY = "zrlog-admin-background-tasks-v1";
const taskStatuses: BackgroundTaskStatus[] = [
    "pending",
    "running",
    "success",
    "warning",
    "error",
    "cancelled",
    "notice",
];
const finishedTaskRetentionLimits: Record<BackgroundTaskFinishStatus, number> = {
    success: 4,
    warning: 8,
    error: 8,
    cancelled: 4,
};

const isTaskStatus = (value: unknown): value is BackgroundTaskStatus => {
    return typeof value === "string" && taskStatuses.includes(value as BackgroundTaskStatus);
};

const normalizeRestoredStatus = (status: BackgroundTaskStatus): BackgroundTaskStatus => {
    if (status === "pending" || status === "running") {
        return "warning";
    }
    return status;
};

const normalizeStoredTask = (value: unknown): BackgroundTask | undefined => {
    if (!value || typeof value !== "object") {
        return undefined;
    }
    const record = value as Record<string, unknown>;
    if (
        typeof record.id !== "string" ||
        typeof record.title !== "string" ||
        !isTaskStatus(record.status) ||
        typeof record.createdAt !== "number" ||
        typeof record.updatedAt !== "number"
    ) {
        return undefined;
    }
    return {
        id: record.id,
        taskKey: typeof record.taskKey === "string" ? record.taskKey : undefined,
        title: record.title,
        description: typeof record.description === "string" ? record.description : undefined,
        actionLabel: typeof record.actionLabel === "string" ? record.actionLabel : undefined,
        actionPath: typeof record.actionPath === "string" ? record.actionPath : undefined,
        timeLabel: typeof record.timeLabel === "string" ? record.timeLabel : undefined,
        closable: typeof record.closable === "boolean" ? record.closable : undefined,
        dismissPath: typeof record.dismissPath === "string" ? record.dismissPath : undefined,
        dismissPayload:
            record.dismissPayload && typeof record.dismissPayload === "object"
                ? (record.dismissPayload as Record<string, unknown>)
                : undefined,
        status: normalizeRestoredStatus(record.status),
        createdAt: record.createdAt,
        updatedAt: record.updatedAt,
    };
};

const loadStoredTasks = () => {
    if (typeof localStorage === "undefined") {
        return [];
    }
    try {
        const rawValue = localStorage.getItem(STORAGE_KEY);
        if (!rawValue) {
            return [];
        }
        const parsedValue = JSON.parse(rawValue);
        if (!Array.isArray(parsedValue)) {
            return [];
        }
        return parsedValue.map(normalizeStoredTask).filter((task): task is BackgroundTask => !!task);
    } catch {
        return [];
    }
};

let tasks: BackgroundTask[] = loadStoredTasks();

const listeners = new Set<() => void>();

const emit = () => {
    persistTasks();
    listeners.forEach((listener) => {
        listener();
    });
};

export const isBackgroundTaskActive = (status: BackgroundTaskStatus) => {
    return status === "pending" || status === "running" || status === "notice";
};

const trimTasks = () => {
    const activeTasks = tasks.filter((task) => isBackgroundTaskActive(task.status));
    const retainedCountByStatus: Record<BackgroundTaskFinishStatus, number> = {
        success: 0,
        warning: 0,
        error: 0,
        cancelled: 0,
    };
    const finishedTasks = tasks
        .filter((task) => !isBackgroundTaskActive(task.status))
        .sort((a, b) => b.updatedAt - a.updatedAt)
        .filter((task) => {
            const status = task.status as BackgroundTaskFinishStatus;
            if (retainedCountByStatus[status] >= finishedTaskRetentionLimits[status]) {
                return false;
            }
            retainedCountByStatus[status] += 1;
            return true;
        });
    tasks = [...activeTasks, ...finishedTasks].sort((a, b) => b.updatedAt - a.updatedAt);
};

const persistTasks = () => {
    if (typeof localStorage === "undefined") {
        return;
    }
    try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(tasks));
    } catch {
        // Storage can be unavailable in private mode or near quota; task UI should still work in memory.
    }
};

trimTasks();
persistTasks();

export const subscribeBackgroundTasks = (listener: () => void) => {
    listeners.add(listener);
    return () => {
        listeners.delete(listener);
    };
};

export const getBackgroundTasks = () => {
    return tasks;
};

export const createBackgroundTask = (title: string, description?: string) => {
    const id = `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
    const now = Date.now();
    tasks = [{ id, title, description, status: "running", createdAt: now, updatedAt: now }, ...tasks];
    trimTasks();
    emit();
    return id;
};

export const upsertBackgroundTaskByKey = (
    taskKey: string,
    task: Omit<BackgroundTask, "id" | "createdAt" | "taskKey"> & { updatedAt?: number }
) => {
    const exists = tasks.find((item) => item.taskKey === taskKey);
    const now = Date.now();
    const taskUpdatedAt = task.updatedAt ?? now;
    if (exists) {
        tasks = tasks.map((item) => {
            if (item.taskKey !== taskKey) {
                return item;
            }
            return {
                ...item,
                ...task,
                taskKey,
                updatedAt: taskUpdatedAt,
            };
        });
    } else {
        tasks = [
            {
                id: `${taskKey}-${Math.random().toString(36).slice(2, 10)}`,
                taskKey,
                createdAt: taskUpdatedAt,
                ...task,
                updatedAt: taskUpdatedAt,
            },
            ...tasks,
        ];
    }
    trimTasks();
    emit();
};

export const updateBackgroundTask = (id: string, patch: Partial<Omit<BackgroundTask, "id" | "createdAt">>) => {
    tasks = tasks.map((task) => {
        if (task.id !== id) {
            return task;
        }
        return {
            ...task,
            ...patch,
            updatedAt: Date.now(),
        };
    });
    trimTasks();
    emit();
};

export const finishBackgroundTask = (id: string, status: BackgroundTaskFinishStatus, description?: string) => {
    updateBackgroundTask(id, {
        status,
        description,
    });
};

export const clearFinishedBackgroundTasks = () => {
    tasks = tasks.filter((task) => isBackgroundTaskActive(task.status));
    emit();
};

export const removeBackgroundTask = (id: string) => {
    tasks = tasks.filter((task) => task.id !== id);
    emit();
};

export const removeBackgroundTaskByKey = (taskKey: string) => {
    tasks = tasks.filter((task) => task.taskKey !== taskKey);
    emit();
};
