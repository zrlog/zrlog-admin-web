import { Space, Typography } from "antd";
import Automations from "../automations";
import { getRes } from "../../utils/constants";
import TaskSettings from "../automations/TaskSettings";
import type { SettingsPage } from "../automations/types";
import { useAssistantRequest } from "../automations/use-assistant-request";

export default function AssistantSettings({
    data,
    offline,
    offlineData,
}: {
    data: SettingsPage;
    offline: boolean;
    offlineData: boolean;
}) {
    const request = useAssistantRequest();
    return (
        <Space orientation="vertical" size="large" style={{ width: "100%" }}>
            {data.maintenance && (
                <section aria-label={getRes().assistant.systemTasks}>
                    <Typography.Title level={4}>{getRes().assistant.systemTasks}</Typography.Title>
                    <Automations
                        data={data.maintenance}
                        offline={offline}
                        offlineData={offlineData}
                        maintenanceOnly
                        activePage="system"
                    />
                </section>
            )}
            <TaskSettings request={request} disabled={offline || offlineData} initialPage={data} />
        </Space>
    );
}
