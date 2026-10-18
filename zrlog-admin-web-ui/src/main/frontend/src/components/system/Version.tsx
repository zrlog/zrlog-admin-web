import { FunctionComponent, useState } from "react";
import { Flex, theme } from "antd";
import { markdownToHtmlSyncWithCallback } from "@zrlog/editor/dist/editor/utils/marked-utils";
import HtmlPreviewPanel from "@zrlog/editor/core/editor/html-preview-panel";
import { getAppState } from "../../base/ConfigProviderApp";
import { getRes } from "../../utils/constants";
import SettingsSection from "../common/SettingsSection";
import SystemSettingsLayout from "./SystemSettingsLayout";

type VersionProps = {
    data: VersionResponse;
};

type VersionResponse = {
    version: string;
    changelog: string;
    buildSystemInfo: string;
};

const Version: FunctionComponent<VersionProps> = ({ data }) => {
    const { token } = theme.useToken();
    const text = getRes().system.version;
    const defaultHtmlStr = markdownToHtmlSyncWithCallback(data.changelog, (htmlStr) => {
        setChangeLogStr(htmlStr);
    });

    const defaultBuildStr = markdownToHtmlSyncWithCallback(data.buildSystemInfo, (htmlStr) => {
        setBuildStr(htmlStr);
    });

    const [changeLogStr, setChangeLogStr] = useState<string>(defaultHtmlStr);
    const [buildStr, setBuildStr] = useState<string>(defaultBuildStr);

    return (
        <SystemSettingsLayout activeKey="version">
            <Flex vertical gap={token.marginLG} style={{ paddingBottom: token.paddingLG, minWidth: 0 }}>
                <SettingsSection title={text.changelog}>
                    <div style={{ overflowX: "auto" }}>
                        <HtmlPreviewPanel htmlContent={changeLogStr} dark={getAppState().dark} />
                    </div>
                </SettingsSection>
                <SettingsSection title={text.buildInfo} divided>
                    <div style={{ overflowX: "auto" }}>
                        <HtmlPreviewPanel htmlContent={buildStr} dark={getAppState().dark} />
                    </div>
                </SettingsSection>
            </Flex>
        </SystemSettingsLayout>
    );
};
export default Version;
