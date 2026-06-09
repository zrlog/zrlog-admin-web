import { FileImageOutlined, FileOutlined, FolderOpenFilled, FolderOpenOutlined, LockOutlined } from "@ant-design/icons";

export type FileEntryAccess = "PUBLIC_URL" | "ADMIN_ONLY" | "VIRTUAL";
export type FileEntryAction =
    | "OPEN"
    | "PREVIEW"
    | "DOWNLOAD"
    | "COPY_URL"
    | "RENAME"
    | "DELETE"
    | "REUPLOAD"
    | "UPDATE_REFERENCES"
    | "SELECT";
export type FileDirectoryAction = "UPLOAD" | "MKDIR";

export interface FileEntry {
    name: string;
    path: string;
    type: "file" | "directory";
    size: number;
    mimeType: string;
    lastModified: number;
    image?: boolean;
    textPreviewable?: boolean;
    iconType?: "library" | "directory" | "directory_locked" | "image" | "code" | "archive" | "file";
    virtual?: boolean;
    access?: FileEntryAccess;
    actions?: FileEntryAction[];
    directoryActions?: FileDirectoryAction[];
    referenced?: boolean;
    referenceCount?: number;
    references?: FileReference[];
    missing?: boolean;
    missingReason?: "targetMissing" | string;
}

export interface FileReference {
    logId: number;
    title: string;
    alias?: string;
    thumbnail?: boolean;
    content?: boolean;
}

export const isExternalPath = (path: string) => path.startsWith("http") || path.startsWith("//");
export const isImage = (entry: FileEntry) => entry.image === true;
export const hasAction = (entry: FileEntry | null | undefined, action: FileEntryAction) =>
    entry?.actions?.includes(action) === true;
export const hasDirectoryAction = (actions: FileDirectoryAction[] | undefined, action: FileDirectoryAction) =>
    actions?.includes(action) === true;

export const formatSize = (bytes?: number | null) => {
    if (bytes == null || bytes < 0) return "-";
    if (bytes === 0) return "0 B";
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
    return (bytes / 1024 / 1024).toFixed(1) + " MB";
};

export const getShortcutIcon = (entry: FileEntry, active: boolean) => {
    if (entry.iconType === "directory_locked") {
        return <LockOutlined />;
    }
    return active ? <FolderOpenFilled /> : <FolderOpenOutlined />;
};

export const getFileIcon = (entry: FileEntry, token: any, size?: number) => {
    const iconStyle = size ? { fontSize: size } : {};
    if (entry.iconType === "directory_locked") {
        return <LockOutlined style={{ color: token.colorWarning, ...iconStyle }} />;
    }
    if (entry.iconType === "library" || entry.iconType === "directory") {
        return <FolderOpenOutlined style={{ color: token.colorPrimary, ...iconStyle }} />;
    }
    if (entry.iconType === "image") {
        return <FileImageOutlined style={{ color: token.colorInfo, ...iconStyle }} />;
    }
    if (entry.iconType === "code") {
        return <FileOutlined style={{ color: token.colorSuccess, ...iconStyle }} />;
    }
    if (entry.iconType === "archive") {
        return <FileOutlined style={{ color: token.colorWarning, ...iconStyle }} />;
    }
    return <FileOutlined style={{ color: token.colorTextSecondary, ...iconStyle }} />;
};
