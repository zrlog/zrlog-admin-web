import { ReactNode } from "react";
import { theme } from "antd";
import { getRes } from "../../utils/constants";
import { AdminSettingsPage, getAdminSettingsItems, getVisibleSettingsItems } from "../../utils/settings-navigation";
import { renderAdminDashboardRouteIcon } from "../admin-dashboard-routes";
import SettingsLayout from "./SettingsLayout";

const AdminSettingsLayout = ({
    activeKey,
    children,
    extra,
}: {
    activeKey: AdminSettingsPage;
    children: ReactNode;
    extra?: ReactNode;
}) => {
    const { token } = theme.useToken();
    const res = getRes();
    const activeMeta = getAdminSettingsItems().find((item) => item.key === activeKey)!;
    const visibleItems = getVisibleSettingsItems(activeKey);
    const groups = [res.website.nav.site, res.website.nav.feature, res.websiteLab.title, res.website.nav.system]
        .map((label) => ({
            label,
            items: visibleItems
                .filter((item) => item.group === label)
                .map((item) => ({
                    key: item.key,
                    label: item.text,
                    path: item.path,
                    icon: renderAdminDashboardRouteIcon(item.iconKey, item.key === activeKey, 16),
                })),
        }))
        .filter((group) => group.items.length > 0);

    return (
        <SettingsLayout
            activeKey={activeKey}
            title={activeMeta.text}
            summary={activeMeta.summary}
            groups={groups}
            extra={extra}
        >
            <div
                style={{
                    maxWidth: activeKey === "system" || activeKey === "dev" ? undefined : 800,
                    paddingBottom: activeKey === "members" ? token.padding : 0,
                }}
            >
                {children}
            </div>
        </SettingsLayout>
    );
};

export default AdminSettingsLayout;
