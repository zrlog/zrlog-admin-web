import { FunctionComponent, useEffect, useState } from "react";
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
    const [htmlStr, setHtmlStr] = useState("");
    const [disableHtmlStr, setDisableHtmlStr] = useState("");

    useEffect(() => {
        let active = true;
        const initialHtml = markdownToHtmlSyncWithCallback(changeLogMd, (nextHtml) => {
            if (active) {
                setHtmlStr(nextHtml);
            }
        });
        setHtmlStr(initialHtml);
        return () => {
            active = false;
        };
    }, [changeLogMd]);

    useEffect(() => {
        let active = true;
        const initialHtml = markdownToHtmlSyncWithCallback(disableUpgradeMd, (nextHtml) => {
            if (active) {
                setDisableHtmlStr(nextHtml);
            }
        });
        setDisableHtmlStr(initialHtml);
        return () => {
            active = false;
        };
    }, [disableUpgradeMd]);

    return (
        <>
            <div style={{ overflowX: "auto" }}>
                <HtmlPreviewPanel htmlContent={data.version ? htmlStr : ""} dark={getAppState().dark} />
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
