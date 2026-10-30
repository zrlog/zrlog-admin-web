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
    return <TaskSettings request={request} disabled={offline || offlineData} initialPage={data} />;
}
