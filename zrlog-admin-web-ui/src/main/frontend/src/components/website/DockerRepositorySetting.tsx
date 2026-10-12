import { Alert, Button, Form, Select, Space } from "antd";
import { useEffect, useRef, useState } from "react";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { ApiResponse } from "../../type";
import { getRes } from "../../utils/constants";

type RepositorySettings = {
    settings: { repository: string; channel: string };
    currentRepository: string;
    images: { repository: string; platforms: string[] }[];
};

type RepositoryResponse = ApiResponse<RepositorySettings> & { errorCode?: string };

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

// This setting is owned by the independent updater and has its own save action.
export default function DockerRepositorySetting({ disabled, preview }: { disabled: boolean; preview: boolean }) {
    const axiosInstance = useAxiosBaseInstance();
    const [data, setData] = useState<RepositorySettings>();
    const [repository, setRepository] = useState("");
    const [busy, setBusy] = useState(false);
    const [result, setResult] = useState<"error" | "success">();
    const [errorMessage, setErrorMessage] = useState<string>();
    const operation = useRef(0);
    const channel = preview ? "preview" : "release";
    const load = async () => {
        const request = ++operation.current;
        setBusy(true);
        setResult(undefined);
        try {
            const requestConfig = { params: { channel }, showError: false };
            const response = await axiosInstance.get<RepositoryResponse>(
                "/api/admin/upgrade/docker-settings",
                requestConfig
            );
            if (request !== operation.current) {
                return;
            }
            if (response.data.error || !response.data.data?.images) {
                setData(undefined);
                setErrorMessage(loadErrorMessage(response.data.errorCode));
                setResult("error");
                return;
            }
            setData(response.data.data);
            setRepository(response.data.data.settings.repository);
        } catch {
            if (request !== operation.current) {
                return;
            }
            setData(undefined);
            setErrorMessage(getRes().websiteUpgrade.repositoryUnavailable);
            setResult("error");
        } finally {
            if (request === operation.current) {
                setBusy(false);
            }
        }
    };
    useEffect(() => {
        setData(undefined);
        setResult(undefined);
        setBusy(false);
        return () => {
            operation.current += 1;
        };
    }, [channel]);
    const save = async () => {
        const request = ++operation.current;
        setBusy(true);
        setResult(undefined);
        try {
            const requestConfig = { showError: false, timeout: 30000 };
            const response = await axiosInstance.post<ApiResponse<{ repository: string }>>(
                "/api/admin/upgrade/save-docker-settings",
                { repository, channel },
                requestConfig
            );
            if (request !== operation.current) {
                return;
            }
            if (response.data.error) {
                throw new Error();
            }
            setRepository(response.data.data.repository);
            setResult("success");
        } catch {
            if (request === operation.current) {
                setErrorMessage(getRes().websiteUpgrade.repositorySaveFailed);
                setResult("error");
            }
        } finally {
            if (request === operation.current) {
                setBusy(false);
            }
        }
    };
    return (
        <Form.Item label={getRes().websiteUpgrade.dockerRepository} extra={getRes().websiteUpgrade.dockerRepositoryTip}>
            <Space direction="vertical" style={{ width: "100%" }}>
                {data && (
                    <Select
                        aria-label={getRes().websiteUpgrade.dockerRepository}
                        value={repository}
                        onChange={setRepository}
                        disabled={disabled || busy}
                        style={{ width: "100%" }}
                        options={[
                            {
                                value: "",
                                label: `${getRes().websiteUpgrade.currentRepository} (${data.currentRepository})`,
                            },
                            ...Array.from(new Set(data.images.map((image) => image.repository))).map((value) => ({
                                value,
                                label: value,
                            })),
                        ]}
                    />
                )}
                <Space wrap>
                    <Button htmlType="button" disabled={disabled || busy} loading={busy} onClick={() => void load()}>
                        {getRes().websiteUpgrade.loadRepository}
                    </Button>
                    {data && (
                        <Button htmlType="button" disabled={disabled || busy} onClick={() => void save()}>
                            {getRes().websiteUpgrade.saveRepository}
                        </Button>
                    )}
                </Space>
                {result && (
                    <Alert
                        showIcon
                        type={result}
                        message={result === "success" ? getRes().websiteUpgrade.repositorySaved : errorMessage}
                    />
                )}
            </Space>
        </Form.Item>
    );
}
