import UserIcon from "@zrlog/ui/icons/user";
import LockIcon from "@zrlog/ui/icons/lock";
import AppearanceIcon from "@zrlog/ui/icons/appearance";
import EditIcon from "@zrlog/ui/icons/edit";
import RobotIcon from "@zrlog/ui/icons/robot";
import KeyIcon from "@zrlog/ui/icons/key";
import SecurityIcon from "@zrlog/ui/icons/security";
import ApiIcon from "@zrlog/ui/icons/api";
import { isFeaturePathEnabled } from "../../utils/module-capabilities";
import { ReactNode } from "react";
import { theme } from "antd";

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
                    icon: <UserIcon style={activeKey === "profile" ? { color: token.colorPrimary } : undefined} />,
                },
                {
                    key: "security",
                    label: res.accountSecurity.title,
                    path: USER_ROUTES.security,
                    icon: activeKey === "security" ? <LockIcon selected /> : <LockIcon />,
                },
            ],
        },
        {
            label: res.user.preferences.title,
            items: [
                {
                    key: "appearance",
                    label: res.user.preferences.appearanceTitle,
                    icon: activeKey === "appearance" ? <AppearanceIcon selected /> : <AppearanceIcon />,
                },
                {
                    key: "writing",
                    label: res.user.preferences.writingTitle,
                    icon: activeKey === "writing" ? <EditIcon selected /> : <EditIcon />,
                },
                {
                    key: "session",
                    label: res.user.preferences.sessionTitle,
                    icon: activeKey === "session" ? <LockIcon selected /> : <LockIcon />,
                },
                {
                    key: "assistant",
                    label: res.user.preferences.assistantTitle,
                    icon: activeKey === "assistant" ? <RobotIcon selected /> : <RobotIcon />,
                },
            ].map((item) => ({ ...item, path: USER_ROUTES[item.key as UserPreferencePage] })),
        },
        {
            label: res.oauth.title,
            items: [
                {
                    key: "tokens",
                    label: res.oauth.personalTokens.title,
                    icon: <KeyIcon style={activeKey === "tokens" ? { color: token.colorPrimary } : undefined} />,
                },
                {
                    key: "grants",
                    label: res.oauth.grants,
                    icon: activeKey === "grants" ? <SecurityIcon selected /> : <SecurityIcon />,
                },
                ...(canManageClients
                    ? [
                          {
                              key: "clients",
                              label: res.oauth.applications,
                              icon: activeKey === "clients" ? <ApiIcon selected /> : <ApiIcon />,
                          },
                      ]
                    : []),
            ].map((item) => ({ ...item, path: USER_ROUTES[item.key as UserApplicationPage] })),
        },
    ];
    const active = groups.flatMap((group) => group.items).find((item) => item.key === activeKey);
    const applicationPage = ["tokens", "grants", "clients"].includes(activeKey);
    const summaries = {
        profile: res.user.settings.profileSummary,
        security: res.user.settings.securitySummary,
        appearance: res.user.preferences.appearanceDescription,
        writing: res.user.preferences.writingDescription,
        assistant: res.user.preferences.assistantDescription,
        session: res.user.preferences.sessionHelp,
        tokens: res.oauth.personalTokens.description,
        grants: res.oauth.grantsDescription,
        clients: res.oauth.clientsDescription,
    };
    return (
        <SettingsLayout
            activeKey={activeKey}
            title={active?.label ?? res.oauth.applications}
            summary={summaries[activeKey]}
            groups={groups
                .map((group) => ({ ...group, items: group.items.filter((item) => isFeaturePathEnabled(item.path)) }))
                .filter((group) => group.items.length > 0)}
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
