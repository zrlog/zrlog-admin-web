import ChevronDownIcon from "@zrlog/ui/icons/chevron-down";
import KeyIcon from "@zrlog/ui/icons/key";
import LogoutIcon from "@zrlog/ui/icons/logout";
import UserIcon from "@zrlog/ui/icons/user";
import { USER_ROUTES } from "../utils/account-page-routes";

import { Avatar, MenuProps, Modal, Typography } from "antd";
import { Link } from "react-router-dom";

import Dropdown from "antd/es/dropdown";
import { AdminResourceInfo, getBackendServerUrl, getRealRouteUrl, getRes, isStaticPage } from "../utils/constants";
import { BasicUserInfo } from "../type";
import { tryBlock } from "../utils/helpers";
import { resolveBackendImageSrc } from "../common/BackendImage";
import { useTheme } from "antd-style";

const { Text } = Typography;

const UserInfo = ({ data, offline }: { data: BasicUserInfo; offline: boolean }) => {
    const [modal, contextHolder] = Modal.useModal();
    const theme = useTheme();

    const adminSettings = (res: AdminResourceInfo): MenuProps["items"] => {
        const base: NonNullable<MenuProps["items"]> = [
            {
                key: "1",
                icon: <UserIcon />,
                label: (
                    <Link
                        style={{ whiteSpace: "nowrap" }}
                        to={getRealRouteUrl(USER_ROUTES.profile)}
                        onClick={(e) => tryBlock(e, modal)}
                    >
                        {res.user.title}
                    </Link>
                ),
            },
            {
                key: "2",
                icon: <KeyIcon />,
                label: (
                    <Link to={getRealRouteUrl(USER_ROUTES.security)} onClick={(e) => tryBlock(e, modal)}>
                        {res.accountSecurity.title}
                    </Link>
                ),
            },
            {
                key: "-",
                type: "divider",
            },
        ];
        if (!offline) {
            return [
                ...base,
                {
                    key: "3",
                    icon: <LogoutIcon />,
                    label: (
                        <a
                            href={getBackendServerUrl() + "admin/logout" + (isStaticPage() ? "?sp=true" : "")}
                            onClick={(e) => tryBlock(e, modal)}
                        >
                            {res.user.logout}
                        </a>
                    ),
                },
            ];
        }
        return base;
    };

    const items = adminSettings(getRes());

    return (
        <>
            {contextHolder}
            <Dropdown menu={{ items }} placement="bottomRight" styles={{ root: { width: "max-content", minWidth: 0 } }}>
                <div
                    style={{
                        color: theme.colorText,
                        marginRight: 16,
                        minHeight: 32,
                        display: "flex",
                        alignItems: "center",
                        cursor: "pointer",
                    }}
                >
                    <Avatar
                        className={"userAvatarImg"}
                        src={resolveBackendImageSrc(data.header)}
                        size={32}
                        icon={<UserIcon />}
                    />
                    <Text
                        style={{
                            color: theme.colorText,
                            paddingLeft: 8,
                        }}
                    >
                        {data.userName}
                    </Text>
                    <ChevronDownIcon />
                </div>
            </Dropdown>
        </>
    );
};

export default UserInfo;
