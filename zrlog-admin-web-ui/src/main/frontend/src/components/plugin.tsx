import { getBackendServerUrl } from "../utils/constants";
import { FunctionComponent } from "react";
import { AdminCommonProps } from "../type";
import Offline from "../common/Offline";
import { Grid } from "antd";
import PluginFrame from "../common/PluginFrame";

type PluginData = {
    includePagePath: string;
};

const Plugin: FunctionComponent<AdminCommonProps<PluginData>> = ({ data, offline }) => {
    const pluginUrl = getBackendServerUrl() + data.includePagePath;
    const screens = Grid.useBreakpoint();
    const iframeHeight = screens.xl ? 1200 : "calc(100vh - 112px)";
    const iframeMinHeight = screens.md ? 720 : 520;
    if (offline) {
        return <Offline />;
    }
    return (
        <div style={{ width: "100%", overflowX: "auto" }}>
            <PluginFrame
                title={data.includePagePath}
                height={iframeHeight}
                minHeight={iframeMinHeight}
                src={pluginUrl}
            />
        </div>
    );
};

export default Plugin;
