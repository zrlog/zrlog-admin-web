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

// This setting is owned by the independent updater and has its own save action.
export default function DockerRepositorySetting({ disabled, preview }: { disabled: boolean; preview: boolean }) {
    const axiosInstance = useAxiosBaseInstance();
    const [data, setData] = useState<RepositorySettings>();
    const [repository, setRepository] = useState("");
    const [busy, setBusy] = useState(false);
    const [result, setResult] = useState<"error" | "success">();
    const operation = useRef(0);
    const channel = preview ? "preview" : "release";
    const load = async () => {
        const request = ++operation.current;
        setBusy(true);
        setResult(undefined);
        try {
            const requestConfig = { params: { channel }, showError: false };
            const response = await axiosInstance.get<ApiResponse<RepositorySettings>>(
                "/api/admin/upgrade/docker-settings",
                requestConfig
            );
            if (request !== operation.current) {
                return;
            }
            if (response.data.error || !response.data.data?.images) {
                throw new Error();
            }
            setData(response.data.data);
            setRepository(response.data.data.settings.repository);
        } catch {
            if (request !== operation.current) {
                return;
            }
            setData(undefined);
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
                        message={
                            result === "success"
                                ? getRes().websiteUpgrade.repositorySaved
                                : getRes().websiteUpgrade.repositoryUnavailable
                        }
                    />
                )}
            </Space>
        </Form.Item>
    );
}
