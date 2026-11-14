import AssistantWorkspace from "../assistant";
import TaskSettings from "../assistant/tasks/TaskSettings";
import type { SettingsPage } from "../assistant/tasks/types";
import { useAssistantRequest } from "../assistant/use-assistant-request";
import { useAssistantSettingsStyles } from "../assistant/tasks/settings-styles";

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
    const { styles } = useAssistantSettingsStyles();
    return (
        <div className={styles.page}>
            <TaskSettings request={request} disabled={offline || offlineData} initialPage={data} />
            {data.maintenance && (
                <AssistantWorkspace
                    data={data.maintenance}
                    offline={offline}
                    offlineData={offlineData}
                    maintenanceOnly
                    activePage="system"
                />
            )}
        </div>
    );
}
