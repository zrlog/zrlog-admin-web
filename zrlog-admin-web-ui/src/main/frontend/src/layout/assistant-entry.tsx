import { Modal, Tooltip } from "antd";
import RobotIcon from "@zrlog/ui/icons/robot";
import { Link, useLocation } from "react-router-dom";
import { isAssistantPage } from "../utils/assistant-page-routes";
import { getRealRouteUrl, getRes } from "../utils/constants";
import { tryBlock } from "../utils/helpers";

export default function AssistantEntry() {
    const [modal, contextHolder] = Modal.useModal();
    const location = useLocation();
    const selected = isAssistantPage(location.pathname);
    const title = getRes().assistant.title;

    return (
        <>
            {contextHolder}
            <Tooltip title={title}>
                <Link
                    className="admin-header-icon-entry admin-assistant-entry"
                    to={getRealRouteUrl("/assistant")}
                    aria-label={title}
                    aria-current={selected ? "page" : undefined}
                    onClick={(event) => tryBlock(event, modal)}
                >
                    <RobotIcon selected={selected} style={{ fontSize: 20 }} />
                </Link>
            </Tooltip>
        </>
    );
}
