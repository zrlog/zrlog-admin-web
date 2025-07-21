import { FunctionComponent, useState } from "react";
import Divider from "antd/es/divider";
import { UpgradeData } from "../type";
import { markdownToHtmlSyncWithCallback } from "@editor/dist/src/editor/utils/marked-utils";
import HtmlPreviewPanel from "@editor/dist/src/editor/html-preview-panel";
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
            <HtmlPreviewPanel htmlContent={data.version ? (htmlStr as string) : ""} dark={getAppState().dark} />
            {!data.onlineUpgradable && (
                <>
                    <Divider />
                    <HtmlPreviewPanel dark={getAppState().dark} htmlContent={disableHtmlStr} />
                </>
            )}
        </>
    );
};

export default UpgradeContent;
