import { FunctionComponent, useState } from "react";
import Card from "antd/es/card";
import { markdownToHtmlSyncWithCallback } from "@editor/dist/src/editor/utils/marked-utils";
import HtmlPreviewPanel from "@editor/dist/src/editor/html-preview-panel";
import { getAppState } from "../../base/ConfigProviderApp";

type VersionProps = {
    data: VersionResponse;
};

type VersionResponse = {
    version: string;
    changelog: string;
    buildSystemInfo: string;
};

const Version: FunctionComponent<VersionProps> = ({ data }) => {
    const defaultHtmlStr = markdownToHtmlSyncWithCallback(data.changelog, (htmlStr) => {
        setChangeLogStr(htmlStr);
    });

    const defaultBuildStr = markdownToHtmlSyncWithCallback(data.buildSystemInfo, (htmlStr) => {
        setBuildStr(htmlStr);
    });

    const [changeLogStr, setChangeLogStr] = useState<string>(defaultHtmlStr);
    const [buildStr, setBuildStr] = useState<string>(defaultBuildStr);

    return (
        <>
            <Card title={""} style={{ padding: 8, maxWidth: 600 }}>
                <HtmlPreviewPanel htmlContent={changeLogStr} dark={getAppState().dark} />
            </Card>
            <Card title={""} style={{ padding: 8, maxWidth: 600, marginTop: 12 }}>
                <HtmlPreviewPanel htmlContent={buildStr} dark={getAppState().dark} />
            </Card>
        </>
    );
};
export default Version;
