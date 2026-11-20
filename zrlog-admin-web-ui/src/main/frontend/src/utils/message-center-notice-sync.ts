import type {MessageCenterNotice, MessageCenterNoticeScope, MessageCenterNoticeSource} from "../type";
import {getRes} from "./constants";
import {getBackgroundTasks, removeBackgroundTask, upsertBackgroundTaskByKey} from "./background-task-store";

const LEGACY_ASSISTANT_NOTICE_PREFIX = "server.assistant.";

const sourceForNotice = (notice: MessageCenterNotice, scope: MessageCenterNoticeScope) => {
    if (notice.source) return notice.source;
    if (scope === "account") return "assistant";
    switch (notice.type) {
        case "versionUpdate": return "versionUpdate";
        case "unreadComment": return "unreadComment";
        case "webhookMessage": return "webhook";
        case "operationTask": return "operation";
        default: return undefined;
    }
};

export const removeNotice = (source: MessageCenterNoticeSource, referenceId?: string) => {
    for (const task of getBackgroundTasks()) {
        if (task.noticeSource === source && task.noticeReferenceId === referenceId) {
            removeBackgroundTask(task.id);
        }
    }
};

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

export const syncMessageCenterNotices = (notices: MessageCenterNotice[], scope: MessageCenterNoticeScope) => {
    const visibleNotices = notices.filter((notice) => !notice.scope || notice.scope === scope);
    const incomingTaskKeys = new Set(visibleNotices.map((notice) => notice.taskKey));
    getBackgroundTasks()
        .filter((task) => task.taskKey && task.noticeScope === scope && !incomingTaskKeys.has(task.taskKey))
        .forEach((task) => removeBackgroundTask(task.id));

    visibleNotices.forEach((notice) => {
        const source = sourceForNotice(notice, scope);
        const referenceId = notice.referenceId ?? (source === "assistant" && !notice.source
            && notice.taskKey.startsWith(LEGACY_ASSISTANT_NOTICE_PREFIX)
            ? notice.taskKey.slice(LEGACY_ASSISTANT_NOTICE_PREFIX.length) : undefined);
        const identity = {noticeScope: scope, noticeSource: source, noticeReferenceId: referenceId};
        if (isVersionUpdateNotice(notice)) {
            upsertBackgroundTaskByKey(notice.taskKey, {
                ...identity,
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
                    ...identity,
                    title: notice.payload.title,
                    description: notice.payload.description,
                    actionLabel: notice.payload.actionLabel,
                    actionPath: notice.payload.actionPath,
                    timeLabel: notice.payload.source || getRes().backgroundTask.updatedAt,
                    closable: notice.payload.closable !== false,
                    dismissPath: scope === "account" ? "/api/admin/assistant/readNotice" : "/api/admin/message-center/read",
                    dismissPayload: {taskKey: notice.taskKey, source, referenceId},
                    status: notice.status,
                    updatedAt: notice.updatedAt,
                });
            }
            return;
        }
        upsertBackgroundTaskByKey(notice.taskKey, {
            ...identity,
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
