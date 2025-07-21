import { DownOutlined, KeyOutlined, LogoutOutlined, UserOutlined } from "@ant-design/icons";
import { MenuProps, Modal, Typography } from "antd";
import { Link } from "react-router-dom";

import Dropdown from "antd/es/dropdown";
import Image from "antd/es/image";
import Constants, {
    AdminResourceInfo,
    getBackendServerUrl,
    getRealRouteUrl,
    getRes,
    isStaticPage,
} from "../utils/constants";
import Divider from "antd/es/divider";
import { BasicUserInfo } from "../type";
import { tryBlock } from "../utils/helpers";
import { getAppState } from "../base/ConfigProviderApp";

const { Text } = Typography;

const UserInfo = ({ data, offline }: { data: BasicUserInfo; offline: boolean }) => {
    const [modal, contextHolder] = Modal.useModal();

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

    const getImgSize = () => {
        if (getAppState().compactMode) {
            return 32;
        }
        return 40;
    };

    return (
        <>
            {contextHolder}
            <Dropdown menu={{ items }} placement="bottom">
                <div
                    style={{
                        color: getAppState().dark ? "#ffffff" : "#333333",
                        marginRight: 16,
                        height: 50,
                        display: "flex",
                        alignItems: "center",
                    }}
                >
                    <Image
                        preview={false}
                        fallback={Constants.getFillBackImg()}
                        className={"userAvatarImg"}
                        src={data.header}
                        style={{ lineHeight: getImgSize(), width: getImgSize(), height: getImgSize() }}
                    />
                    <Text
                        style={{
                            color: getAppState().dark ? "#ffffff" : "#333333",
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
