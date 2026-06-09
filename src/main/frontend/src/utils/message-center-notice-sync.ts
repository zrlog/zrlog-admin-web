import { MessageCenterNotice } from "../type";
import { getRes } from "./constants";
import { getBackgroundTasks, removeBackgroundTaskByKey, upsertBackgroundTaskByKey } from "./background-task-store";

const SERVER_TASK_KEY_PREFIX = "server.";

const isVersionUpdateNotice = (
    notice: MessageCenterNotice
): notice is Extract<MessageCenterNotice, { type: "versionUpdate" }> => {
    return notice.type === "versionUpdate" && !!notice.payload?.version;
};

const isUnreadCommentNotice = (
    notice: MessageCenterNotice
): notice is Extract<MessageCenterNotice, { type: "unreadComment" }> => {
    return notice.type === "unreadComment" && typeof notice.payload?.count === "number";
};

const isWebhookMessageNotice = (
    notice: MessageCenterNotice
): notice is Extract<MessageCenterNotice, { type: "webhookMessage" }> => {
    return notice.type === "webhookMessage" && typeof notice.payload?.title === "string";
};

const isOperationTaskNotice = (
    notice: MessageCenterNotice
): notice is Extract<MessageCenterNotice, { type: "operationTask" }> => {
    return notice.type === "operationTask" && typeof notice.payload?.title === "string";
};

const buildVersionUpdateDescription = (notice: MessageCenterNotice) => {
    if (!isVersionUpdateNotice(notice)) {
        return "";
    }
    const version = notice.payload.version;
    const versionText = getRes()
        .backgroundTask.versionUpdate.current.replace("{version}", version.version)
        .replace("{type}", version.type);
    if (!version.buildId) {
        return versionText;
    }
    return `${versionText}\n${getRes().backgroundTask.versionUpdate.buildId.replace("{buildId}", version.buildId)}`;
};

const buildUnreadCommentDescription = (notice: MessageCenterNotice) => {
    const count = isUnreadCommentNotice(notice) ? notice.payload.count : 0;
    return getRes().backgroundTask.unreadComment.pending.replace("{count}", `${count}`);
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
        if (isVersionUpdateNotice(notice)) {
            upsertBackgroundTaskByKey(notice.taskKey, {
                title: getRes().backgroundTask.versionUpdate.title,
                description: buildVersionUpdateDescription(notice),
                actionLabel: getRes().backgroundTask.versionUpdate.action,
                actionPath: "/upgrade",
                timeLabel: getRes().backgroundTask.versionUpdate.publishedAt,
                closable: false,
                status: notice.status,
                updatedAt: notice.updatedAt,
            });
            return;
        }
        if (!isUnreadCommentNotice(notice)) {
            if (isWebhookMessageNotice(notice) || isOperationTaskNotice(notice)) {
                upsertBackgroundTaskByKey(notice.taskKey, {
                    title: notice.payload.title,
                    description: notice.payload.description,
                    actionLabel: notice.payload.actionLabel,
                    actionPath: notice.payload.actionPath,
                    timeLabel: notice.payload.source || getRes().backgroundTask.updatedAt,
                    closable: notice.payload.closable !== false,
                    dismissPath: "/api/admin/message-center/read",
                    dismissPayload: { taskKey: notice.taskKey },
                    status: notice.status,
                    updatedAt: notice.updatedAt,
                });
            }
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
