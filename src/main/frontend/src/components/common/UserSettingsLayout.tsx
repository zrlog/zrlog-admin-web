import { ReactNode } from "react";
import { Tabs, theme } from "antd";
import { UserOutlined, SettingOutlined, ApiOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import { getRealRouteUrl, getRes } from "../../utils/constants";
import { USER_ROUTES } from "../../utils/account-page-routes";
import SettingsLayout from "./SettingsLayout";
import PermissionHelp from "./PermissionHelp";

export type UserSettingsPage = "profile" | "preferences" | "security" | "applications";

const UserSettingsLayout = ({ activeKey, children }: { activeKey: UserSettingsPage; children: ReactNode }) => {
    const res = getRes();
    const navigate = useNavigate();
    const { token } = theme.useToken();
    const accountPage = activeKey === "profile" || activeKey === "security";
    const groupKey = accountPage ? "account" : activeKey;
    const items = [
        {
            key: "account",
            label: res.user.settings.navigation,
            path: USER_ROUTES.profile,
            icon: <UserOutlined />,
            summary: activeKey === "security" ? res.user.settings.securitySummary : res.user.settings.profileSummary,
        },
        {
            key: "preferences",
            label: res.user.preferences.title,
            path: USER_ROUTES.preferences,
            icon: <SettingOutlined />,
            summary: res.user.preferences.description,
        },
        {
            key: "applications",
            label: res.oauth.title,
            path: USER_ROUTES.applications,
            icon: <ApiOutlined />,
            summary: res.oauth.description,
        },
    ];
    const active = items.find((item) => item.key === groupKey)!;
    return (
        <SettingsLayout
            activeKey={groupKey}
            title={active.label}
            summary={active.summary}
            groups={[{ label: res.common.settings, items }]}
            extra={activeKey === "applications" && <PermissionHelp initialView="scopes" />}
        >
            <div
                style={{
                    maxWidth: 800,
                    paddingBottom: activeKey === "applications" || activeKey === "security" ? token.padding : 0,
                }}
            >
                {accountPage && (
                    <Tabs
                        activeKey={activeKey}
                        onChange={(key) =>
                            navigate(getRealRouteUrl(key === "security" ? USER_ROUTES.security : USER_ROUTES.profile))
                        }
                        items={[
                            {
                                key: "profile",
                                label: res.user.title,
                                children: activeKey === "profile" ? children : null,
                            },
                            {
                                key: "security",
                                label: res.accountSecurity.title,
                                children: activeKey === "security" ? children : null,
                            },
                        ]}
                    />
                )}
                {!accountPage && children}
            </div>
        </SettingsLayout>
    );
};

export default UserSettingsLayout;
