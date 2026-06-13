import { FunctionComponent, useState } from "react";
import Divider from "antd/es/divider";
import { UpgradeData } from "../type";
import { markdownToHtmlSyncWithCallback } from "@editor/dist/editor/utils/marked-utils";
import HtmlPreviewPanel from "@editor/dist/editor/html-preview-panel";
import { getAppState } from "../base/ConfigProviderApp";

export type UpgradeContentProps = {
    data: UpgradeData;
};

const UpgradeContent: FunctionComponent<UpgradeContentProps> = ({ data }) => {
    const changeLogMd = data.version ? data.version.changeLog : "";
    const disableUpgradeMd = data.disableUpgradeReason ? data.disableUpgradeReason : "";

    const defaultHtmlStr = markdownToHtmlSyncWithCallback(changeLogMd, (htmlStr) => {
        setHtmlStr(htmlStr);
    });

    const defaultDisableHtmlStr = markdownToHtmlSyncWithCallback(disableUpgradeMd, (htmlStr) => {
        setDisableHtmlStr(htmlStr);
    });

    const [htmlStr, setHtmlStr] = useState<string>(defaultHtmlStr);
    const [disableHtmlStr, setDisableHtmlStr] = useState<string>(defaultDisableHtmlStr);

    return (
        <>
            <div style={{ overflowX: "auto" }}>
                <HtmlPreviewPanel htmlContent={data.version ? (htmlStr as string) : ""} dark={getAppState().dark} />
            </div>
            {!data.onlineUpgradable && (
                <>
                    <Divider />
                    <div style={{ overflowX: "auto" }}>
                        <HtmlPreviewPanel dark={getAppState().dark} htmlContent={disableHtmlStr} />
                    </div>
                </>
            )}
        </>
    );
};

export default UpgradeContent;
