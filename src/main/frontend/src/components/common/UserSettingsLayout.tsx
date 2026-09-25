import { ReactNode } from "react";
import { Button, theme } from "antd";
import { UserOutlined, SettingOutlined, LockOutlined, ApiOutlined, QuestionCircleOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import { getRealRouteUrl, getRes } from "../../utils/constants";
import { USER_ROUTES } from "../../utils/account-page-routes";
import SettingsLayout from "./SettingsLayout";

export type UserSettingsPage = "profile" | "preferences" | "security" | "applications";

const UserSettingsLayout = ({ activeKey, children }: { activeKey: UserSettingsPage; children: ReactNode }) => {
    const res = getRes();
    const navigate = useNavigate();
    const { token } = theme.useToken();
    const items = [
        {
            key: "profile",
            label: res.user.title,
            path: USER_ROUTES.profile,
            icon: <UserOutlined />,
            summary: res.user.settings.profileSummary,
        },
        {
            key: "preferences",
            label: res.user.preferences.title,
            path: USER_ROUTES.preferences,
            icon: <SettingOutlined />,
            summary: res.user.preferences.description,
        },
        {
            key: "security",
            label: res.accountSecurity.title,
            path: USER_ROUTES.security,
            icon: <LockOutlined />,
            summary: res.user.settings.securitySummary,
        },
        {
            key: "applications",
            label: res.oauth.title,
            path: USER_ROUTES.applications,
            icon: <ApiOutlined />,
            summary: res.oauth.description,
        },
    ];
    const active = items.find((item) => item.key === activeKey)!;
    return (
        <SettingsLayout
            activeKey={activeKey}
            title={active.label}
            summary={active.summary}
            groups={[{ label: res.user.settings.navigation, items }]}
            extra={
                activeKey === "applications" && (
                    <Button
                        icon={<QuestionCircleOutlined />}
                        onClick={() => navigate(getRealRouteUrl(USER_ROUTES.permissions))}
                    >
                        {res.access.title}
                    </Button>
                )
            }
        >
            <div
                style={{
                    maxWidth: 800,
                    paddingBottom: activeKey === "applications" || activeKey === "security" ? token.padding : 0,
                }}
            >
                {children}
            </div>
        </SettingsLayout>
    );
};

export default UserSettingsLayout;
