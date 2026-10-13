import { Alert, Button, Space, Typography, theme as antdTheme } from "antd";
import { useRef, useState } from "react";
import { getRes } from "../../../utils/constants";

export type AiFileCapabilities = {
    extensions: string[];
    maxFiles: number;
    maxFileBytes: number;
    maxTotalBytes: number;
};
export type AiFileInput = { name: string; data: string };
export type AiFileAttachment = { id?: string; name: string; size: number; mimeType?: string; expiresAt?: number };

export const encodeAiFiles = (files: File[]): Promise<AiFileInput[]> =>
    Promise.all(
        files.map(
            (file) =>
                new Promise<AiFileInput>((resolve, reject) => {
                    const reader = new FileReader();
                    reader.onload = () => resolve({ name: file.name, data: String(reader.result).split(",")[1] });
                    reader.onerror = () => reject(new Error(getRes().articleEdit.files.readFailed));
                    reader.onabort = reader.onerror;
                    reader.readAsDataURL(file);
                })
        )
    );

export const validateAiFiles = (files: File[], capabilities: AiFileCapabilities): string | undefined => {
    const res = getRes().articleEdit.files;
    if (files.length > capabilities.maxFiles) return res.countLimit;
    if (
        files.some((file) => !file.size || file.size > capabilities.maxFileBytes) ||
        files.reduce((size, file) => size + file.size, 0) > capabilities.maxTotalBytes
    )
        return res.sizeLimit;
    if (
        files.some(
            (file) => !capabilities.extensions.includes(file.name.slice(file.name.lastIndexOf(".")).toLowerCase())
        )
    )
        return res.unsupported;
};

export function AiFileList({ files }: { files?: AiFileAttachment[] }) {
    if (!files?.length) return null;
    return (
        <Space orientation="vertical" style={{ width: "100%", minWidth: 0 }}>
            {files.map((file, index) => (
                <Typography.Text key={file.id || index} style={{ overflowWrap: "anywhere" }}>
                    {file.name} · {Math.ceil(file.size / 1024)} KiB
                </Typography.Text>
            ))}
        </Space>
    );
}

export function AiFilePicker({
    capabilities,
    files,
    disabled,
    onChange,
}: {
    capabilities?: AiFileCapabilities;
    files: File[];
    disabled: boolean;
    onChange: (files: File[]) => void;
}) {
    const input = useRef<HTMLInputElement>(null);
    const [error, setError] = useState<string>();
    const res = getRes().articleEdit.files;
    const supported = Boolean(capabilities?.extensions.length);
    const { token: theme } = antdTheme.useToken();
    return (
        <Space orientation="vertical" style={{ width: "100%", minWidth: 0, marginBottom: theme.marginXS }}>
            <Space wrap>
                <Button size="small" disabled={disabled || !supported} onClick={() => input.current?.click()}>
                    {res.add}
                </Button>
                <Typography.Text type="secondary">{supported ? res.limits : res.modelUnsupported}</Typography.Text>
            </Space>
            {supported && (
                <Typography.Text type="secondary" style={{ fontSize: theme.fontSizeSM, overflowWrap: "anywhere" }}>
                    {res.formats} {capabilities!.extensions.join(", ")}
                </Typography.Text>
            )}
            <input
                ref={input}
                type="file"
                multiple
                hidden
                aria-label={res.add}
                accept={capabilities?.extensions.join(",")}
                onChange={(event) => {
                    const next = [...files, ...Array.from(event.target.files || [])];
                    event.target.value = "";
                    if (!capabilities || disabled) return;
                    const failure = validateAiFiles(next, capabilities);
                    setError(failure);
                    if (!failure) onChange(next);
                }}
            />
            {files.map((file, index) => (
                <div
                    key={`${index}/${file.name}`}
                    style={{ display: "flex", gap: theme.marginXS, width: "100%", alignItems: "start" }}
                >
                    <Typography.Text style={{ flex: 1, minWidth: 0, overflowWrap: "anywhere" }}>
                        {file.name} · {Math.ceil(file.size / 1024)} KiB
                    </Typography.Text>
                    <Button
                        size="small"
                        type="text"
                        disabled={disabled}
                        aria-label={`${res.remove} ${file.name}`}
                        onClick={() => {
                            onChange(files.filter((_, position) => position !== index));
                            setError(undefined);
                        }}
                    >
                        {res.remove}
                    </Button>
                </div>
            ))}
            {files.some((file) => /\.(xlsx?|csv|tsv)$/i.test(file.name)) && (
                <Typography.Text type="secondary">{res.spreadsheetNote}</Typography.Text>
            )}
            {error && <Alert type="error" title={error} />}
        </Space>
    );
}
