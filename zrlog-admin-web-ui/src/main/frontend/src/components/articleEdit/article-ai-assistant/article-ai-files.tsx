import { Alert, Button, Dropdown, GetRef, Space, Tooltip, Typography, theme as antdTheme } from "antd";
import type { AxiosInstance, AxiosRequestConfig } from "axios";
import Image from "@zrlog/ui/antd/Image";
import AddIcon from "@zrlog/ui/icons/add";
import AttachmentIcon from "@zrlog/ui/icons/attachment";
import CloseIcon from "@zrlog/ui/icons/close";
import FileIcon from "@zrlog/ui/icons/file";
import ImageFileIcon from "@zrlog/ui/icons/image-file";
import FolderIcon from "@zrlog/ui/icons/folder";
import { lazy, ReactNode, Suspense, useEffect, useId, useRef, useState } from "react";
import { getRes } from "../../../utils/constants";
import { hasAction } from "../../../utils/account-access";
import { isModuleEnabled } from "../../../utils/module-capabilities";

const AiAssetPicker = lazy(() => import("./article-ai-asset-picker"));

export type AiFileCapabilities = {
    extensions: string[];
    maxFiles: number;
    maxFileBytes: number;
    maxTotalBytes: number;
};
export type AiFileInput = { name: string; data: string };
export type AiFileAttachment = { id?: string; name: string; size: number; mimeType?: string; expiresAt?: number };
export type AiFilePreviewContext = { api: AxiosInstance; articleId: number };

// Keep local bytes outside serializable messages, exports and browser storage.
const localPreviews = new WeakMap<AiFileAttachment, File>();
export const rememberAiFilePreviews = (attachments: AiFileAttachment[] | undefined, files?: File[]) => {
    attachments?.forEach((attachment, index) => {
        const file = files?.[index];
        if (file && file.name === attachment.name && file.size === attachment.size) localPreviews.set(attachment, file);
    });
    return attachments;
};

const imageMimeType = (name: string): string | undefined => {
    const extension = name.slice(name.lastIndexOf(".")).toLowerCase();
    if (extension === ".png") return "image/png";
    if (extension === ".jpg" || extension === ".jpeg") return "image/jpeg";
    if (extension === ".webp") return "image/webp";
};
const formatBytes = (bytes: number) =>
    bytes >= 1024 * 1024 ? `${+(bytes / (1024 * 1024)).toFixed(1)} MiB` : `${Math.ceil(bytes / 1024)} KiB`;
const formatLimits = (text: string, capabilities: AiFileCapabilities) =>
    text
        .replace("{count}", String(capabilities.maxFiles))
        .replace("{fileSize}", formatBytes(capabilities.maxFileBytes))
        .replace("{totalSize}", formatBytes(capabilities.maxTotalBytes));

export const encodeAiFiles = (files: File[]): Promise<AiFileInput[]> =>
    Promise.all(
        files.map(
            (file) =>
                new Promise<AiFileInput>((resolve, reject) => {
                    const reader = new FileReader();
                    reader.onload = () => {
                        const data = typeof reader.result === "string" ? reader.result.split(",")[1] : undefined;
                        if (data) resolve({ name: file.name, data });
                        else reject(new Error(getRes().articleEdit.files.readFailed));
                    };
                    reader.onerror = () => reject(new Error(getRes().articleEdit.files.readFailed));
                    reader.onabort = reader.onerror;
                    reader.readAsDataURL(file);
                })
        )
    );

export const validateAiFiles = (files: File[], capabilities: AiFileCapabilities): string | undefined => {
    const res = getRes().articleEdit.files;
    if (files.length > capabilities.maxFiles) return formatLimits(res.countLimit, capabilities);
    if (
        files.some((file) => !file.size || file.size > capabilities.maxFileBytes) ||
        files.reduce((size, file) => size + file.size, 0) > capabilities.maxTotalBytes
    )
        return formatLimits(res.sizeLimit, capabilities);
    if (
        files.some(
            (file) => !capabilities.extensions.includes(file.name.slice(file.name.lastIndexOf(".")).toLowerCase())
        )
    )
        return res.unsupported;
};

function AiFileCard({
    file,
    disabled,
    onRemove,
    previewContext,
}: {
    file: AiFileAttachment | File;
    disabled?: boolean;
    onRemove?: () => void;
    previewContext?: AiFilePreviewContext;
}) {
    const { token } = antdTheme.useToken();
    const [preview, setPreview] = useState<string>();
    const [previewOpen, setPreviewOpen] = useState(false);
    const [previewFailed, setPreviewFailed] = useState(false);
    const [retry, setRetry] = useState(0);
    const mime = imageMimeType(file.name);
    const localFile = typeof File !== "undefined" && file instanceof File ? file : localPreviews.get(file);
    const fileId = "id" in file ? file.id : undefined;
    const api = previewContext?.api;
    const articleId = previewContext?.articleId;
    useEffect(() => {
        setPreview(undefined);
        setPreviewFailed(false);
        if (!mime) return;
        let url: string | undefined;
        const controller = new AbortController();
        const show = (blob: Blob) => {
            if (controller.signal.aborted) return;
            url = URL.createObjectURL(blob);
            setPreview(url);
        };
        if (localFile) show(localFile.slice(0, localFile.size, mime));
        else if (fileId && api && articleId !== undefined) {
            const config: AxiosRequestConfig & { showError: boolean } = {
                params: { id: articleId, fileId },
                responseType: "blob",
                signal: controller.signal,
                showError: false,
            };
            void api
                .get<Blob>("/api/admin/article/ai/file", config)
                .then(({ data }) => {
                    if (
                        !(data instanceof Blob) ||
                        data.type.split(";")[0] !== mime ||
                        !data.size ||
                        data.size > 4 * 1024 * 1024
                    )
                        throw new Error("Invalid image response");
                    show(data);
                })
                .catch(() => {
                    if (!controller.signal.aborted) setPreviewFailed(true);
                });
        }
        return () => {
            controller.abort();
            if (url) URL.revokeObjectURL(url);
        };
    }, [localFile, mime, fileId, api, articleId, retry]);
    const res = getRes().articleEdit.files;
    return (
        <div
            style={{
                display: "flex",
                alignItems: "center",
                gap: token.marginXS,
                minWidth: 0,
                padding: token.paddingXS,
                border: `${token.lineWidth}px ${token.lineType} ${token.colorBorderSecondary}`,
                borderRadius: token.borderRadius,
                background: token.colorFillQuaternary,
            }}
        >
            {preview ? (
                <>
                    <button
                        type="button"
                        aria-label={`${res.preview} ${file.name}`}
                        onClick={() => setPreviewOpen(true)}
                        style={{
                            border: 0,
                            padding: 0,
                            background: "transparent",
                            cursor: "pointer",
                            flexShrink: 0,
                            width: 48,
                            height: 48,
                            borderRadius: token.borderRadiusSM,
                        }}
                    >
                        <img
                            src={preview}
                            alt=""
                            style={{ width: "100%", height: "100%", objectFit: "cover", borderRadius: "inherit" }}
                        />
                    </button>
                    <Image
                        src={preview}
                        alt={file.name}
                        style={{ display: "none" }}
                        preview={{ open: previewOpen, onOpenChange: setPreviewOpen }}
                    />
                </>
            ) : (
                <span
                    aria-hidden="true"
                    style={{ color: token.colorTextSecondary, fontSize: token.fontSizeHeading3, flexShrink: 0 }}
                >
                    {mime ? <ImageFileIcon /> : <FileIcon />}
                </span>
            )}
            <div style={{ flex: 1, minWidth: 0 }}>
                <Tooltip title={file.name} trigger={["hover", "focus", "click"]}>
                    <Typography.Text ellipsis tabIndex={0} style={{ display: "block" }}>
                        {file.name}
                    </Typography.Text>
                </Tooltip>
                <Typography.Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
                    {formatBytes(file.size)}
                </Typography.Text>
                {previewFailed && (
                    <Button type="link" size="small" onClick={() => setRetry((value) => value + 1)}>
                        {res.retryPreview}
                    </Button>
                )}
            </div>
            {onRemove && (
                <Button
                    type="text"
                    icon={<CloseIcon />}
                    disabled={disabled}
                    aria-label={`${res.remove} ${file.name}`}
                    title={`${res.remove} ${file.name}`}
                    onClick={onRemove}
                    style={{ flexShrink: 0 }}
                />
            )}
        </div>
    );
}

export function AiFileList({
    files,
    previewContext,
}: {
    files?: (AiFileAttachment | File)[];
    previewContext?: AiFilePreviewContext;
}) {
    const { token } = antdTheme.useToken();
    if (!files?.length) return null;
    return (
        <div style={{ display: "grid", gap: token.marginXS, width: "100%", minWidth: 0 }}>
            {files.map((file, index) => (
                <AiFileCard key={"id" in file ? file.id || index : index} file={file} previewContext={previewContext} />
            ))}
        </div>
    );
}

export function AiFilePicker({
    capabilities,
    files,
    disabled,
    onChange,
    children,
    toolbar,
    action,
}: {
    capabilities?: AiFileCapabilities;
    files: File[];
    disabled: boolean;
    onChange: (files: File[]) => void;
    children?: ReactNode;
    toolbar?: ReactNode;
    action?: ReactNode;
}) {
    const input = useRef<HTMLInputElement>(null);
    const assetTrigger = useRef<GetRef<typeof Button>>(null);
    const dragDepth = useRef(0);
    const [dragging, setDragging] = useState(false);
    const [error, setError] = useState<string>();
    const [assetsOpen, setAssetsOpen] = useState(false);
    const [menuOpen, setMenuOpen] = useState(false);
    const [focused, setFocused] = useState(false);
    const helpId = useId();
    const res = getRes().articleEdit.files;
    const supported = Boolean(capabilities?.extensions.length);
    const imageSupported = capabilities?.extensions.some((extension) => imageMimeType(`image${extension}`));
    const canBrowseAssets = isModuleEnabled("assets") && hasAction("file.manage");
    const atFileLimit = Boolean(capabilities && files.length >= capabilities.maxFiles);
    const assetMaxBytes = capabilities
        ? Math.min(
              capabilities.maxFileBytes,
              capabilities.maxTotalBytes - files.reduce((size, file) => size + file.size, 0)
          )
        : 0;
    const { token } = antdTheme.useToken();
    const closeAssets = () => {
        setAssetsOpen(false);
        assetTrigger.current?.focus();
    };
    const addFiles = (incoming: File[]) => {
        if (!incoming.length || disabled) return;
        const next = [...files, ...incoming];
        const failure = capabilities && supported ? validateAiFiles(next, capabilities) : res.modelUnsupported;
        setError(failure);
        if (!failure) onChange(next);
    };
    useEffect(() => {
        if (!files.length) setError(undefined);
        if (disabled) {
            dragDepth.current = 0;
            setDragging(false);
            setAssetsOpen(false);
            setMenuOpen(false);
        }
    }, [files.length, disabled]);
    return (
        <div
            onKeyDown={(event) => {
                if (menuOpen && event.key === "Escape" && !event.nativeEvent.isComposing) {
                    event.stopPropagation();
                    setMenuOpen(false);
                    assetTrigger.current?.focus();
                }
            }}
            onPaste={(event) => {
                const incoming = Array.from(event.clipboardData.files);
                if (!incoming.length) return;
                event.stopPropagation();
                if (!event.clipboardData.getData("text/plain")) event.preventDefault();
                addFiles(incoming);
            }}
            onDragEnter={(event) => {
                if (!Array.from(event.dataTransfer.types).includes("Files")) return;
                event.preventDefault();
                event.stopPropagation();
                dragDepth.current += 1;
                if (supported && !disabled) setDragging(true);
            }}
            onDragOver={(event) => {
                if (!Array.from(event.dataTransfer.types).includes("Files")) return;
                event.preventDefault();
                event.stopPropagation();
                event.dataTransfer.dropEffect = supported && !disabled ? "copy" : "none";
            }}
            onDragLeave={(event) => {
                event.stopPropagation();
                dragDepth.current = Math.max(0, dragDepth.current - 1);
                if (!dragDepth.current) setDragging(false);
            }}
            onDrop={(event) => {
                if (!Array.from(event.dataTransfer.types).includes("Files")) return;
                event.preventDefault();
                event.stopPropagation();
                dragDepth.current = 0;
                setDragging(false);
                addFiles(Array.from(event.dataTransfer.files));
            }}
            onFocusCapture={() => setFocused(true)}
            onBlurCapture={(event) => {
                if (!event.currentTarget.contains(event.relatedTarget as Node)) setFocused(false);
            }}
            style={{
                position: "relative",
                minWidth: 0,
                padding: token.paddingXS,
                border: `${token.lineWidth}px ${token.lineType} ${focused ? token.colorPrimary : token.colorBorder}`,
                borderRadius: token.borderRadiusLG,
                background: token.colorBgContainer,
                boxShadow: focused ? `0 0 0 ${token.controlOutlineWidth}px ${token.controlOutline}` : undefined,
                display: "flex",
                flexDirection: "column",
                gap: token.marginXS,
            }}
        >
            <input
                ref={input}
                type="file"
                multiple
                hidden
                disabled={disabled || !supported}
                aria-label={res.add}
                accept={capabilities?.extensions.join(",")}
                onChange={(event) => {
                    addFiles(Array.from(event.target.files || []));
                    event.target.value = "";
                }}
            />
            {files.length > 0 && (
                <div
                    style={{
                        display: "grid",
                        gridTemplateColumns: "repeat(auto-fill, minmax(min(100%, 180px), 1fr))",
                        gap: token.marginXS,
                    }}
                >
                    {files.map((file, index) => (
                        <AiFileCard
                            key={`${index}/${file.name}`}
                            file={file}
                            disabled={disabled}
                            onRemove={() => {
                                onChange(files.filter((_, position) => position !== index));
                                setError(undefined);
                            }}
                        />
                    ))}
                </div>
            )}
            {files.some((file) => /\.(xlsx?|csv|tsv)$/i.test(file.name)) && (
                <Typography.Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
                    {res.spreadsheetNote}
                </Typography.Text>
            )}
            {error && <Alert type="error" showIcon title={error} />}
            {children}
            <div
                style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: token.marginXS }}
            >
                <Space size={token.marginXXS}>
                    <Dropdown
                        trigger={["click"]}
                        placement="topLeft"
                        open={menuOpen}
                        onOpenChange={setMenuOpen}
                        autoFocus
                        menu={{
                            style: { boxShadow: "none" },
                            items: [
                                {
                                    key: "local",
                                    icon: <AttachmentIcon />,
                                    label: res.add,
                                    disabled: disabled || !supported || atFileLimit,
                                },
                                ...(canBrowseAssets
                                    ? [
                                          {
                                              key: "assets",
                                              icon: <FolderIcon />,
                                              label: getRes().articleEdit.actions.chooseFromAssets,
                                              disabled:
                                                  disabled || !imageSupported || atFileLimit || assetMaxBytes <= 0,
                                          },
                                      ]
                                    : []),
                            ],
                            onClick: ({ key }) => {
                                setMenuOpen(false);
                                if (key === "local") input.current?.click();
                                else if (key === "assets") setAssetsOpen(true);
                            },
                        }}
                        popupRender={(menu) => (
                            <div
                                style={{
                                    width: 280,
                                    maxWidth: "calc(100vw - 32px)",
                                    background: token.colorBgElevated,
                                    borderRadius: token.borderRadiusLG,
                                    boxShadow: token.boxShadowSecondary,
                                }}
                            >
                                {menu}
                                {supported && (
                                    <details
                                        style={{
                                            padding: token.paddingSM,
                                            color: token.colorTextSecondary,
                                            fontSize: token.fontSizeSM,
                                        }}
                                    >
                                        <summary style={{ cursor: "pointer" }}>{res.requirements}</summary>
                                        <div style={{ marginTop: token.marginXS, overflowWrap: "anywhere" }}>
                                            {formatLimits(res.limits, capabilities!)}
                                            <br />
                                            {res.formats} {capabilities!.extensions.join(", ")}
                                        </div>
                                    </details>
                                )}
                            </div>
                        )}
                    >
                        <Button
                            ref={assetTrigger}
                            type="text"
                            shape="circle"
                            icon={<AddIcon />}
                            disabled={disabled || !supported || atFileLimit || assetMaxBytes <= 0}
                            aria-label={res.add}
                            aria-haspopup="menu"
                            aria-expanded={menuOpen}
                            aria-describedby={!supported ? helpId : undefined}
                            title={supported ? res.add : res.modelUnsupported}
                        />
                    </Dropdown>
                    {toolbar}
                </Space>
                {action}
            </div>
            {!supported && (
                <span id={helpId} hidden>
                    {res.modelUnsupported}
                </span>
            )}
            {assetsOpen && !disabled && canBrowseAssets && (
                <Suspense fallback={<Typography.Text role="status">{res.assetLoading}</Typography.Text>}>
                    <AiAssetPicker
                        maxBytes={assetMaxBytes}
                        extensions={capabilities!.extensions.filter((extension) => imageMimeType(`image${extension}`))}
                        onClose={closeAssets}
                        onSelect={(file) => {
                            addFiles([file]);
                            closeAssets();
                        }}
                    />
                </Suspense>
            )}
            {dragging && (
                <div
                    role="status"
                    style={{
                        position: "absolute",
                        inset: 0,
                        display: "grid",
                        placeItems: "center",
                        padding: token.padding,
                        borderRadius: token.borderRadius,
                        pointerEvents: "none",
                        border: `${token.lineWidth}px dashed ${token.colorPrimary}`,
                        background: token.colorBgContainer,
                        color: token.colorPrimary,
                        zIndex: 1,
                    }}
                >
                    {res.dropHint}
                </div>
            )}
        </div>
    );
}
