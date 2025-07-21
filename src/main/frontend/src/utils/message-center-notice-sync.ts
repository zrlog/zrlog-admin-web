import { MessageCenterNotice } from "../type";
import { getRes } from "./constants";
import { getBackgroundTasks, removeBackgroundTaskByKey, upsertBackgroundTaskByKey } from "./background-task-store";

const SERVER_TASK_KEY_PREFIX = "server.";

const buildVersionUpdateDescription = (notice: MessageCenterNotice) => {
    if (!notice.version) {
        return "";
    }
    return `${getRes().backgroundTask.versionUpdate.current} ${notice.version.version} (${notice.version.type})`;
};

const buildUnreadCommentDescription = (notice: MessageCenterNotice) => {
    const count = notice.count ?? 0;
    return getRes().backgroundTask.unreadComment.pending.replace("{count}", `${count}`);
};

const parseVersionReleaseTime = (releaseDate?: string, fallback?: number) => {
    if (!releaseDate) {
        return fallback ?? Date.now();
    }
    const normalized = releaseDate.replace(" ", "T");
    const timestamp = new Date(normalized).getTime();
    return Number.isNaN(timestamp) ? (fallback ?? Date.now()) : timestamp;
};

export const syncMessageCenterNotices = (notices: MessageCenterNotice[]) => {
    const incomingTaskKeys = new Set(notices.map((notice) => notice.taskKey));
    getBackgroundTasks()
        .filter((task) => task.taskKey?.startsWith(SERVER_TASK_KEY_PREFIX) && !incomingTaskKeys.has(task.taskKey))
        .forEach((task) => {
            if (task.taskKey) {
                removeBackgroundTaskByKey(task.taskKey);
            }
        });

    notices.forEach((notice) => {
        if (notice.type === "versionUpdate" && notice.version) {
            upsertBackgroundTaskByKey(notice.taskKey, {
                title: getRes().backgroundTask.versionUpdate.title,
                description: buildVersionUpdateDescription(notice),
                actionLabel: getRes().backgroundTask.versionUpdate.action,
                actionPath: "/upgrade",
                timeLabel: getRes().backgroundTask.versionUpdate.publishedAt,
                closable: false,
                status: notice.status,
                updatedAt: parseVersionReleaseTime(notice.version.releaseDate, notice.updatedAt),
            });
            return;
        }
        if (notice.type !== "unreadComment") {
            return;
        }
        upsertBackgroundTaskByKey(notice.taskKey, {
            title: getRes().backgroundTask.unreadComment.title,
            description: buildUnreadCommentDescription(notice),
            actionLabel: getRes().backgroundTask.unreadComment.action,
            actionPath: "/comment",
            timeLabel: getRes().backgroundTask.updatedAt,
            closable: false,
            status: notice.status,
            updatedAt: notice.updatedAt,
        });
    });
};
