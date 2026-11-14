import { Button, Dropdown } from "antd";
import { useUiApp } from "@zrlog/ui/feedback";
import MoreIcon from "@zrlog/ui/icons/more";
import { getRes } from "../../utils/constants";
import type { useAssistantConversation } from "./use-assistant-conversation";

export default function AssistantConversationActions({
    actions,
}: {
    actions: ReturnType<typeof useAssistantConversation>["conversationActions"];
}) {
    const { modal } = useUiApp();
    const res = getRes().articleEdit.assistant;
    const disabled = actions.disabled || actions.exporting || actions.clearing;
    return (
        <Dropdown
            trigger={["click"]}
            menu={{
                items: [
                    { key: "export", label: res.exportAiMessages, disabled, onClick: () => void actions.onExport() },
                    {
                        key: "clear",
                        label: res.clearAiMessages,
                        danger: true,
                        disabled,
                        onClick: () =>
                            modal.confirm({
                                title: res.clearAiMessagesConfirmTitle,
                                content: res.clearAiMessagesConfirmDescription,
                                okText: res.clearAiMessages,
                                cancelText: getRes().common.cancel,
                                okButtonProps: { danger: true },
                                onOk: actions.onClear,
                            }),
                    },
                ],
            }}
        >
            <Button type="text" icon={<MoreIcon />} aria-label={res.conversationActions} />
        </Dropdown>
    );
}
