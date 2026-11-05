import { Alert, Button, Select, Space } from "antd";
import { useCallback, useEffect, useRef, useState } from "react";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { ApiResponse } from "../../type";
import { getRes } from "../../utils/constants";
import type { Upgrade } from "./index";
import { getErrorMessage } from "../../utils/error-message";

export default function UpgradeSourceSetting({
    data,
    value,
    onChange,
    onLoadingChange,
    disabled,
    preview,
}: {
    data: Upgrade;
    value?: string;
    onChange: (repository: string) => void;
    onLoadingChange: (loading: boolean) => void;
    disabled: boolean;
    preview: boolean;
}) {
    const axiosInstance = useAxiosBaseInstance();
    const [settings, setSettings] = useState(data.sourceSettings);
    const [error, setError] = useState(
        data.sourceSettings?.errorMessage || (data.sourceSettings?.errorCode ? getRes().error.requestError : undefined)
    );
    const [busy, setBusy] = useState(false);
    const operation = useRef(0);
    const previousPreview = useRef(preview);
    const load = useCallback(async () => {
        const request = ++operation.current;
        setBusy(true);
        onLoadingChange(true);
        try {
            const requestConfig = { params: { channel: preview ? "preview" : "release" }, showError: false };
            const response = await axiosInstance.get<ApiResponse<Upgrade>>("/api/admin/system/upgrade", requestConfig);
            if (request !== operation.current) return;
            if (
                response.data.error ||
                !response.data.data?.sourceSettings?.options ||
                response.data.data.sourceSettings.errorCode ||
                response.data.data.sourceSettings.errorMessage
            ) {
                setError(
                    response.data.data?.sourceSettings?.errorMessage ||
                        response.data.message ||
                        getRes().error.requestError
                );
                return;
            }
            setSettings(response.data.data.sourceSettings);
            setError(undefined);
        } catch (failure) {
            if (request === operation.current) setError(getErrorMessage(failure, getRes().error.requestError));
        } finally {
            if (request === operation.current) {
                setBusy(false);
                onLoadingChange(false);
            }
        }
    }, [axiosInstance, preview, onLoadingChange]);
    useEffect(() => {
        operation.current += 1;
        setSettings(data.sourceSettings);
        setError(
            data.sourceSettings?.errorMessage ||
                (data.sourceSettings?.errorCode ? getRes().error.requestError : undefined)
        );
        setBusy(false);
        onLoadingChange(false);
        previousPreview.current = !!data.upgradePreview;
    }, [data, onLoadingChange]);
    useEffect(() => {
        if (preview !== previousPreview.current && !disabled) {
            previousPreview.current = preview;
            void load();
        }
    }, [preview, disabled, load]);
    useEffect(
        () => () => {
            operation.current += 1;
        },
        []
    );

    const repository = value ?? settings?.source;
    return (
        <Space orientation="vertical" style={{ width: "100%" }}>
            <Select
                aria-label={settings?.label}
                value={repository}
                onChange={onChange}
                disabled={disabled || busy || !!error || !settings}
                loading={busy}
                style={{ width: "100%" }}
                options={settings?.options || []}
            />
            {error && (
                <Alert
                    showIcon
                    type="error"
                    message={error}
                    action={
                        <Button
                            htmlType="button"
                            disabled={disabled || busy}
                            loading={busy}
                            onClick={() => void load()}
                        >
                            {getRes().common.retry}
                        </Button>
                    }
                />
            )}
        </Space>
    );
}
