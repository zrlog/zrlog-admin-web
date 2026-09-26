import { ReactNode } from "react";
import { theme } from "antd";
import {
    UserOutlined,
    LockOutlined,
    SkinOutlined,
    EditOutlined,
    RobotOutlined,
    KeyOutlined,
    SafetyCertificateOutlined,
    ApiOutlined,
} from "@ant-design/icons";
import { getRes } from "../../utils/constants";
import { USER_ROUTES, UserPreferencePage, UserApplicationPage } from "../../utils/account-page-routes";
import { hasAction } from "../../utils/account-access";
import SettingsLayout, { SettingsNavigationGroup } from "./SettingsLayout";
import PermissionHelp from "./PermissionHelp";

export type UserSettingsPage = "profile" | "security" | UserPreferencePage | UserApplicationPage;

const UserSettingsLayout = ({
    activeKey,
    administrator,
    children,
}: {
    activeKey: UserSettingsPage;
    administrator?: boolean;
    children: ReactNode;
}) => {
    const res = getRes();
    const { token } = theme.useToken();
    const canManageClients = administrator ?? hasAction("oauth.client.manage");
    const groups: SettingsNavigationGroup[] = [
        {
            label: res.user.settings.navigation,
            items: [
                {
                    key: "profile",
                    label: res.user.title,
                    path: USER_ROUTES.profile,
                    icon: <UserOutlined />,
                },
                {
                    key: "security",
                    label: res.accountSecurity.title,
                    path: USER_ROUTES.security,
                    icon: <LockOutlined />,
                },
            ],
        },
        {
            label: res.user.preferences.title,
            items: [
                { key: "appearance", label: res.user.preferences.appearanceTitle, icon: <SkinOutlined /> },
                { key: "writing", label: res.user.preferences.writingTitle, icon: <EditOutlined /> },
                { key: "assistant", label: res.user.preferences.assistantTitle, icon: <RobotOutlined /> },
            ].map((item) => ({ ...item, path: USER_ROUTES[item.key as UserPreferencePage] })),
        },
        {
            label: res.oauth.title,
            items: [
                { key: "tokens", label: res.oauth.personalTokens.title, icon: <KeyOutlined /> },
                { key: "grants", label: res.oauth.grants, icon: <SafetyCertificateOutlined /> },
                ...(canManageClients ? [{ key: "clients", label: res.oauth.applications, icon: <ApiOutlined /> }] : []),
            ].map((item) => ({ ...item, path: USER_ROUTES[item.key as UserApplicationPage] })),
        },
    ];
    const active = groups.flatMap((group) => group.items).find((item) => item.key === activeKey);
    const applicationPage = ["tokens", "grants", "clients"].includes(activeKey);
    const summaries = {
        profile: res.user.settings.profileSummary,
        security: res.user.settings.securitySummary,
        appearance: res.user.preferences.description,
        writing: res.user.preferences.description,
        assistant: res.user.preferences.description,
        tokens: res.oauth.description,
        grants: res.oauth.description,
        clients: res.oauth.description,
    };
    return (
        <SettingsLayout
            activeKey={activeKey}
            title={active?.label ?? res.oauth.applications}
            summary={summaries[activeKey]}
            groups={groups}
            extra={applicationPage && <PermissionHelp initialView="scopes" />}
        >
            <div
                style={{
                    maxWidth: 800,
                    paddingBottom: applicationPage || activeKey === "security" ? token.padding : 0,
                }}
            >
                {children}
            </div>
        </SettingsLayout>
    );
};

export default UserSettingsLayout;
