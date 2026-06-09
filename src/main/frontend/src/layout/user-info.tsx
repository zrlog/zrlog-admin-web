import { DownOutlined, KeyOutlined, LogoutOutlined, UserOutlined } from "@ant-design/icons";
import { Avatar, MenuProps, Modal, Typography } from "antd";
import { Link } from "react-router-dom";

import Dropdown from "antd/es/dropdown";
import { AdminResourceInfo, getBackendServerUrl, getRealRouteUrl, getRes, isStaticPage } from "../utils/constants";
import Divider from "antd/es/divider";
import { BasicUserInfo } from "../type";
import { tryBlock } from "../utils/helpers";
import { resolveBackendImageSrc } from "../common/BackendImage";
import { useTheme } from "antd-style";

const { Text } = Typography;

const UserInfo = ({ data, offline }: { data: BasicUserInfo; offline: boolean }) => {
    const [modal, contextHolder] = Modal.useModal();
    const theme = useTheme();

    const adminSettings = (res: AdminResourceInfo): MenuProps["items"] => {
        const base = [
            {
                key: "1",
                label: (
                    <Link
                        style={{ whiteSpace: "nowrap" }}
                        to={getRealRouteUrl("/user")}
                        onClick={(e) => tryBlock(e, modal)}
                    >
                        <UserOutlined />
                        <Text style={{ paddingLeft: "5px", paddingRight: 16 }}>{res.user.title}</Text>
                    </Link>
                ),
            },
            {
                key: "2",
                label: (
                    <Link to={getRealRouteUrl("/account-security")} onClick={(e) => tryBlock(e, modal)}>
                        <KeyOutlined />
                        <Text style={{ paddingLeft: "5px", paddingRight: 16 }}>{res.accountSecurity.title}</Text>
                    </Link>
                ),
            },
            {
                key: "-",
                label: (
                    <Divider style={{ marginTop: "5px", marginBottom: "5px", userSelect: "none", cursor: "none" }} />
                ),
            },
        ];
        if (!offline) {
            return [
                ...base,
                {
                    key: "3",
                    label: (
                        <a
                            href={getBackendServerUrl() + "admin/logout" + (isStaticPage() ? "?sp=true" : "")}
                            onClick={(e) => tryBlock(e, modal)}
                        >
                            <LogoutOutlined />
                            <Text style={{ paddingLeft: "5px", paddingRight: 16 }}>{res.user.logout}</Text>
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
            <Dropdown menu={{ items }} placement="bottom">
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
                        icon={<UserOutlined />}
                    />
                    <Text
                        style={{
                            color: theme.colorText,
                            paddingLeft: 8,
                        }}
                    >
                        {data.userName}
                    </Text>
                    <DownOutlined />
                </div>
            </Dropdown>
        </>
    );
};

export default UserInfo;
