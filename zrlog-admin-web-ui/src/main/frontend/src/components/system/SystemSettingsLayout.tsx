import DashboardIcon from "@zrlog/ui/icons/dashboard";
import ContainerIcon from "@zrlog/ui/icons/container";
import CpuIcon from "@zrlog/ui/icons/cpu";
import InfoIcon from "@zrlog/ui/icons/info";
import ExperimentIcon from "@zrlog/ui/icons/experiment";
import LinkIcon from "@zrlog/ui/icons/link";
import { ReactNode } from "react";
import { getRes, isServerDevelopmentMode } from "../../utils/constants";
import { actionForPath, hasAction } from "../../utils/account-access";
import SettingsLayout from "../common/SettingsLayout";

export type SystemInfoPage = "overview" | "runtime" | "resources";
type SystemPage = SystemInfoPage | "proxy" | "version" | "dev";

const SystemSettingsLayout = ({ activeKey, children }: { activeKey: SystemPage; children: ReactNode }) => {
    const res = getRes();
    const text = res.system;
    const pages = {
        overview: { title: text.navigation.overview, summary: text.summaries.overview },
        runtime: { title: text.navigation.runtime, summary: text.summaries.runtime },
        resources: { title: text.navigation.resources, summary: text.summaries.resources },
        proxy: { title: text.proxy.title, summary: text.proxy.summary },
        version: { title: text.version.title, summary: text.version.summary },
        dev: { title: res.dev.title, summary: res.dev.summary },
    };
    const { title, summary } = pages[activeKey];
    return (
        <SettingsLayout
            activeKey={activeKey}
            title={title}
            summary={summary}
            groups={[
                {
                    label: text.navigation.title,
                    items: [
                        {
                            key: "overview",
                            label: text.navigation.overview,
                            path: "/system",
                            icon: <DashboardIcon selected={activeKey === "overview"} />,
                        },
                        {
                            key: "runtime",
                            label: text.navigation.runtime,
                            path: "/system/runtime",
                            icon: <ContainerIcon selected={activeKey === "runtime"} />,
                        },
                        {
                            key: "resources",
                            label: text.navigation.resources,
                            path: "/system/resources",
                            icon: <CpuIcon selected={activeKey === "resources"} />,
                        },
                        {
                            key: "proxy",
                            label: text.proxy.title,
                            path: "/system/proxy",
                            icon: <LinkIcon selected={activeKey === "proxy"} />,
                        },
                    ],
                },
                {
                    label: text.navigation.maintenance,
                    items: [
                        {
                            key: "version",
                            label: text.version.title,
                            path: "/system/version",
                            icon: <InfoIcon selected={activeKey === "version"} />,
                        },
                        {
                            key: "dev",
                            label: res.dev.title,
                            path: "/dev",
                            icon: <ExperimentIcon selected={activeKey === "dev"} />,
                        },
                    ],
                },
            ]
                .map((group) => ({
                    ...group,
                    items: group.items.filter(
                        (item) =>
                            hasAction(actionForPath(item.path)) && (item.key !== "dev" || isServerDevelopmentMode())
                    ),
                }))
                .filter((group) => group.items.length > 0)}
        >
            {children}
        </SettingsLayout>
    );
};

export default SystemSettingsLayout;
