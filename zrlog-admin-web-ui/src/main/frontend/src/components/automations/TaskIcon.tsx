import CalendarIcon from "@zrlog/ui/icons/calendar";
import RobotIcon from "@zrlog/ui/icons/robot";
import NotificationIcon from "@zrlog/ui/icons/notifications";
import EditIcon from "@zrlog/ui/icons/edit";
import DatabaseIcon from "@zrlog/ui/icons/database";
import DownloadIcon from "@zrlog/ui/icons/cloud-download";
import SettingsIcon from "@zrlog/ui/icons/settings";
import { useAssistantStyles } from "./assistant-styles";

export default function TaskIcon({ type }: { type: string }) {
    const { styles } = useAssistantStyles();
    const Icon =
        type === "assistant.ai"
            ? RobotIcon
            : type === "assistant.reminder"
            ? NotificationIcon
            : type === "article.publish"
            ? EditIcon
            : type === "site.refresh-cache" || type === "system.cleanup-temporary"
            ? DatabaseIcon
            : type === "system.auto-upgrade" || type === "system.check-version"
            ? DownloadIcon
            : type.startsWith("plugin.")
            ? SettingsIcon
            : CalendarIcon;
    return (
        <span className={styles.icon} aria-hidden="true">
            <Icon />
        </span>
    );
}
