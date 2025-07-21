export type BackgroundTaskStatus = "running" | "success" | "error" | "notice";

export type BackgroundTask = {
    id: string;
    taskKey?: string;
    title: string;
    description?: string;
    actionLabel?: string;
    actionPath?: string;
    timeLabel?: string;
    closable?: boolean;
    status: BackgroundTaskStatus;
    createdAt: number;
    updatedAt: number;
};

let tasks: BackgroundTask[] = [];

const listeners = new Set<() => void>();

const emit = () => {
    listeners.forEach((listener) => {
        listener();
    });
};

const trimTasks = () => {
    const activeTasks = tasks.filter((task) => task.status === "running" || task.status === "notice");
    const finishedTasks = tasks
        .filter((task) => task.status !== "running" && task.status !== "notice")
        .sort((a, b) => b.updatedAt - a.updatedAt)
        .slice(0, 8);
    tasks = [...activeTasks, ...finishedTasks].sort((a, b) => b.updatedAt - a.updatedAt);
};

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

export const finishBackgroundTask = (id: string, status: Exclude<BackgroundTaskStatus, "running">, description?: string) => {
    updateBackgroundTask(id, {
        status,
        description,
    });
};

export const clearFinishedBackgroundTasks = () => {
    tasks = tasks.filter((task) => task.status === "running" || task.status === "notice");
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
