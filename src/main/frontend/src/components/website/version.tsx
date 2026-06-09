import { FunctionComponent, useState } from "react";
import { Grid } from "antd";
import Card from "antd/es/card";
import { markdownToHtmlSyncWithCallback } from "@editor/dist/src/editor/utils/marked-utils";
import HtmlPreviewPanel from "@editor/dist/src/editor/html-preview-panel";
import { getAppState } from "../../base/ConfigProviderApp";
import { useTheme } from "antd-style";

type VersionProps = {
    data: VersionResponse;
};

type VersionResponse = {
    version: string;
    changelog: string;
    buildSystemInfo: string;
};

const Version: FunctionComponent<VersionProps> = ({ data }) => {
    const theme = useTheme();
    const screens = Grid.useBreakpoint();
    const cardMaxWidth = screens.lg ? 720 : "100%";
    const surface = {
        card: {
            maxWidth: cardMaxWidth,
            width: "100%",
            padding: theme.paddingSM,
        },
        sectionTop: {
            marginTop: theme.marginSM,
        },
        preview: {
            overflowX: "auto" as const,
        },
    };
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
            <Card title={""} style={surface.card}>
                <div style={surface.preview}>
                    <HtmlPreviewPanel htmlContent={changeLogStr} dark={getAppState().dark} />
                </div>
            </Card>
            <Card title={""} style={{ ...surface.card, ...surface.sectionTop }}>
                <div style={surface.preview}>
                    <HtmlPreviewPanel htmlContent={buildStr} dark={getAppState().dark} />
                </div>
            </Card>
        </>
    );
};
export default Version;
