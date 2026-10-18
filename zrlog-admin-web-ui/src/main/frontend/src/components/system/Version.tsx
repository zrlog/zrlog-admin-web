import { FunctionComponent, useEffect, useState } from "react";
import { Descriptions, Flex, theme } from "antd";
import { Link } from "react-router-dom";
import { markdownToHtmlSyncWithCallback } from "@zrlog/editor/dist/editor/utils/marked-utils";
import HtmlPreviewPanel from "@zrlog/editor/core/editor/html-preview-panel";
import { getAppState } from "../../base/ConfigProviderApp";
import { getRealRouteUrl, getRes } from "../../utils/constants";
import { actionForPath, hasAction } from "../../utils/account-access";
import SettingsSection from "../common/SettingsSection";
import AdminSettingsLayout from "../common/AdminSettingsLayout";

type VersionProps = {
    data: VersionResponse;
};

type VersionResponse = {
    version: string;
    buildId: string;
    changelog: string;
    buildSystemInfo: string;
};

const useMarkdownHtml = (markdown: string) => {
    const [html, setHtml] = useState("");
    useEffect(() => {
        let active = true;
        const initialHtml = markdownToHtmlSyncWithCallback(markdown, (nextHtml) => {
            if (active) setHtml(nextHtml);
        });
        setHtml(initialHtml);
        return () => {
            active = false;
        };
    }, [markdown]);
    return html;
};

const Version: FunctionComponent<VersionProps> = ({ data }) => {
    const { token } = theme.useToken();
    const text = getRes().system.version;
    const changeLogStr = useMarkdownHtml(data.changelog);
    const buildStr = useMarkdownHtml(data.buildSystemInfo);
    const canViewSystem = hasAction(actionForPath("/system"));

    return (
        <AdminSettingsLayout
            activeKey={canViewSystem ? "system" : "basic"}
            title={text.title}
            summary={text.summary}
            extra={
                <Link to={getRealRouteUrl(canViewSystem ? "/system" : "/website")}>
                    {canViewSystem ? text.backToSystem : text.backToSettings}
                </Link>
            }
        >
            <Flex vertical gap={token.marginLG} style={{ paddingBottom: token.paddingLG, minWidth: 0 }}>
                <Descriptions
                    size="small"
                    column={{ xs: 1, sm: 2 }}
                    styles={{ label: { whiteSpace: "normal" }, content: { overflowWrap: "anywhere" } }}
                    items={[
                        { key: "version", label: text.currentVersion, children: data.version },
                        { key: "buildId", label: text.buildId, children: data.buildId },
                    ]}
                />
                <SettingsSection title={text.buildInfo}>
                    <div style={{ overflowX: "auto" }}>
                        <HtmlPreviewPanel htmlContent={buildStr} dark={getAppState().dark} />
                    </div>
                </SettingsSection>
                <SettingsSection title={text.changelog} divided>
                    <div style={{ overflowX: "auto" }}>
                        <HtmlPreviewPanel htmlContent={changeLogStr} dark={getAppState().dark} />
                    </div>
                </SettingsSection>
            </Flex>
        </AdminSettingsLayout>
    );
};
export default Version;
