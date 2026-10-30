import { Alert, Button, ConfigProvider, Space, Spin, Tabs, theme } from "antd";
import { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useUiApp } from "@zrlog/ui/feedback";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getSsDate } from "../../base/SsData";
import { getRes } from "../../utils/constants";
import { isModuleEnabled } from "../../utils/module-capabilities";
import TaskSettings from "../automations/TaskSettings";
import { useAssistantRequest } from "../automations/use-assistant-request";
import AIForm from "./AIForm";
import type { AI } from "./index";

export interface AssistantConfiguration {
    canManageScheduler: boolean;
    canConfigureAi: boolean;
}

function AiConfiguration({ offline }: { offline: boolean }) {
    const api = useAxiosBaseInstance();
    const { message } = useUiApp();
    const [data, setData] = useState<AI>();
    const [error, setError] = useState<string>();
    const [loading, setLoading] = useState(false);
    const failed = getRes().automations.failedRequest;
    const load = useCallback(async () => {
        if (offline) return;
        const session = getSsDate().key;
        setError(undefined);
        try {
            const response = await api.get("/api/admin/website/ai");
            if (getSsDate().key !== session) return;
            if (response.data.error) throw new Error(response.data.message || failed);
            setData(response.data.data);
        } catch (e) {
            if (getSsDate().key === session) setError(e instanceof Error ? e.message : failed);
        }
    }, [api, failed, offline]);
    useEffect(() => {
        void load();
    }, [load]);
    const save = async (value: AI) => {
        if (offline || loading) return;
        const session = getSsDate().key;
        setLoading(true);
        setError(undefined);
        try {
            const response = await api.post("/api/admin/website/ai", value);
            if (getSsDate().key !== session) return;
            if (response.data.error) throw new Error(response.data.message || failed);
            setData(response.data.data);
            message.success(response.data.message);
        } catch (e) {
            if (getSsDate().key === session) setError(e instanceof Error ? e.message : failed);
        } finally {
            setLoading(false);
        }
    };
    return (
        <Space orientation="vertical" size="large" style={{ width: "100%" }}>
            {offline && <Alert type="info" showIcon title={getRes().automations.offline} />}
            {error && (
                <Alert
                    type="error"
                    showIcon
                    title={error}
                    action={!data && <Button onClick={() => void load()}>{getRes().automations.reload}</Button>}
                />
            )}
            {data ? (
                <AIForm
                    data={data}
                    offline={offline}
                    offlineData={offline}
                    loading={loading}
                    onSubmit={(value) => void save(value)}
                />
            ) : (
                !error && !offline && <Spin />
            )}
        </Space>
    );
}

export default function AssistantSettings({
    data,
    offline,
    offlineData,
}: {
    data: AssistantConfiguration;
    offline: boolean;
    offlineData: boolean;
}) {
    const [params, setParams] = useSearchParams();
    const request = useAssistantRequest();
    const { token } = theme.useToken();
    const res = getRes();
    const items = [
        ...(data.canManageScheduler
            ? [
                  {
                      key: "execution",
                      label: res.automations.settings,
                      children: <TaskSettings request={request} disabled={offline || offlineData} />,
                  },
              ]
            : []),
        ...(data.canConfigureAi
            ? [
                  {
                      key: "ai",
                      label: res.websiteAi.configuration,
                      children: isModuleEnabled("ai") ? (
                          <AiConfiguration offline={offline || offlineData} />
                      ) : (
                          <Alert type="info" showIcon title={res.websiteAi.aiUnavailable} />
                      ),
                  },
              ]
            : []),
    ];
    const activeKey = items.some((item) => item.key === params.get("section")) ? params.get("section")! : items[0]?.key;
    return (
        <ConfigProvider
            theme={{ components: { Tabs: { itemSelectedColor: token.colorText, inkBarColor: token.colorText } } }}
        >
            <Tabs
                activeKey={activeKey}
                items={items}
                onChange={(section) =>
                    setParams(
                        (current) => {
                            current.set("section", section);
                            return current;
                        },
                        { replace: true }
                    )
                }
            />
        </ConfigProvider>
    );
}
