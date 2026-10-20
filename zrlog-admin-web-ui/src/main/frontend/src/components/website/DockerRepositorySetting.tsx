import {Alert, Button, Select, Space} from "antd";
import {useCallback, useEffect, useRef, useState} from "react";
import {useAxiosBaseInstance} from "../../base/AppBase";
import {ApiResponse} from "../../type";
import {getRes} from "../../utils/constants";
import type {Upgrade} from "./index";

const loadErrorMessage = (code?: string) => {
    const res = getRes().websiteUpgrade;
    switch (code) {
        case "metadata_unavailable":
        case "metadata_not_found":
            return res.repositoryMetadataUnavailable;
        case "updater_unavailable":
            return res.repositoryServiceUnavailable;
        case "updater_unauthorized":
            return res.repositoryUnauthorized;
        case "updater_not_configured":
        case "updater_configuration_mismatch":
            return res.repositoryConfigurationInvalid;
        default:
            return res.repositoryUnavailable;
    }
};

export default function DockerRepositorySetting({
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
    const [settings, setSettings] = useState(data.dockerSettings);
    const [error, setError] = useState(data.dockerErrorCode);
    const [busy, setBusy] = useState(false);
    const operation = useRef(0);
    const previousPreview = useRef(preview);
    const load = useCallback(async () => {
        const request = ++operation.current;
        setBusy(true);
        onLoadingChange(true);
        try {
            const requestConfig = {params: {channel: preview ? "preview" : "release"}, showError: false};
            const response = await axiosInstance.get<ApiResponse<Upgrade>>("/api/admin/system/upgrade", requestConfig);
            if (request !== operation.current) return;
            if (response.data.error || !response.data.data?.dockerSettings?.images) {
                setError(response.data.data?.dockerErrorCode ?? "updater_request_failed");
                return;
            }
            setSettings(response.data.data.dockerSettings);
            setError(undefined);
        } catch {
            if (request === operation.current) setError("updater_request_failed");
        } finally {
            if (request === operation.current) {
                setBusy(false);
                onLoadingChange(false);
            }
        }
    }, [axiosInstance, preview, onLoadingChange]);
    useEffect(() => {
        operation.current += 1;
        setSettings(data.dockerSettings);
        setError(data.dockerErrorCode);
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

    const repository = value ?? settings?.settings.repository;
    const repositories = new Set(settings?.images.map((image) => image.repository));
    if (repository) repositories.add(repository);
    return (
        <Space orientation="vertical" style={{width: "100%"}}>
            <Select
                aria-label={getRes().websiteUpgrade.dockerRepository}
                value={repository}
                onChange={onChange}
                disabled={disabled || busy || !!error || !settings}
                loading={busy}
                style={{width: "100%"}}
                options={[
                    {
                        value: "",
                        label: settings?.currentRepository
                            ? `${getRes().websiteUpgrade.currentRepository} (${settings.currentRepository})`
                            : getRes().websiteUpgrade.currentRepository,
                    },
                    ...Array.from(repositories).map((repository) => ({value: repository, label: repository})),
                ]}
            />
            {error && (
                <Alert
                    showIcon
                    type="error"
                    message={loadErrorMessage(error)}
                    action={
                        <Button
                            htmlType="button"
                            disabled={disabled || busy}
                            loading={busy}
                            onClick={() => void load()}
                        >
                            {getRes().websiteUpgrade.retryRepository}
                        </Button>
                    }
                />
            )}
        </Space>
    );
}
