export type MessageCenterStatus = {
    revision: number;
    hasUnread: boolean;
};

const defaultStatus: MessageCenterStatus = {
    revision: 0,
    hasUnread: false,
};

let status = defaultStatus;
const listeners = new Set<(status: MessageCenterStatus) => void>();

const normalizeStatus = (value?: Partial<MessageCenterStatus> | null): MessageCenterStatus | undefined => {
    if (!value || typeof value.revision !== "number") {
        return undefined;
    }
    return {
        revision: value.revision,
        hasUnread: value.hasUnread === true,
    };
};

export const subscribeMessageCenterStatus = (listener: (status: MessageCenterStatus) => void) => {
    listeners.add(listener);
    return () => {
        listeners.delete(listener);
    };
};

export const getMessageCenterStatus = () => status;

export const syncMessageCenterStatus = (value?: Partial<MessageCenterStatus> | null) => {
    const nextStatus = normalizeStatus(value);
    if (!nextStatus) {
        return;
    }
    if (nextStatus.revision === status.revision && nextStatus.hasUnread === status.hasUnread) {
        return;
    }
    status = nextStatus;
    listeners.forEach((listener) => listener(status));
};
