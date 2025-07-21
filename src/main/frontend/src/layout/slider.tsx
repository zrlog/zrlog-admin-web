import { ReactElement, useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { getRealRouteUrl, getRes } from "../utils/constants";
import {
    ApiFilled,
    ApiOutlined,
    BarsOutlined,
    CommentOutlined,
    ContainerFilled,
    ContainerOutlined,
    DashboardFilled,
    DashboardOutlined,
    EditFilled,
    EditOutlined,
    LinkOutlined,
    SettingFilled,
    SettingOutlined,
    TagsFilled,
    TagsOutlined,
} from "@ant-design/icons";
import { Menu, MenuProps, Modal } from "antd";
import { parseQueryParamsToMap, tryBlock } from "../utils/helpers";
import { getAppState } from "../base/ConfigProviderApp";

type MenuItem = Required<MenuProps>["items"][number];

type MenuEntry = {
    key: string;
    link: string;
    selectIcon: ReactElement;
    icon: ReactElement;
    text: string;
};

type SliderMenuProps = {
    expanded?: boolean;
};

export function colorToRgba(color: string, alpha: number) {
    if (color.startsWith("#")) {
        const hex = color.slice(1);
        let bigint;
        if (hex.length === 3) {
            bigint = parseInt(hex, 16) * 0x10101;
        } else if (hex.length === 6) {
            bigint = parseInt(hex, 16);
        } else {
            throw new Error("Invalid hexadecimal color format");
        }
        const r = (bigint >> 16) & 255;
        const g = (bigint >> 8) & 255;
        const b = bigint & 255;
        return `rgba(${r}, ${g}, ${b}, ${alpha})`;
    } else if (color.startsWith("rgba(")) {
        return color.replace(/[^,]+(?=\))/, alpha.toString());
    } else if (color.startsWith("rgb(")) {
        return `rgba(${color.slice(color.indexOf("(") + 1, color.lastIndexOf(","))}, ${alpha})`;
    } else {
        throw new Error("Unsupported color format");
    }
}

const SliderMenu = ({ expanded = false }: SliderMenuProps) => {
    const location = useLocation();
    const [modal, contextHolder] = Modal.useModal();

    const getArticleEditUrl = () => {
        const paramMap = parseQueryParamsToMap(location.search);
        const articleEditorId = paramMap.get("id") as string;
        if (articleEditorId && articleEditorId !== "") {
            return `/article-edit?id=${articleEditorId}`;
        }
        return "/article-edit";
    };

    const getSelectMenu = (): string[] => {
        const selectPath = location.pathname.split(".")[0];
        if (selectPath === "" || selectPath === "/") {
            return ["/index"];
        }
        if (selectPath === "/upgrade" || selectPath === "/template-config" || selectPath === "/system") {
            return ["/website"];
        }
        if (selectPath.startsWith("/website")) {
            return ["/website"];
        }
        if (selectPath.startsWith("/link")) {
            return ["/link"];
        }
        if (selectPath.startsWith("/nav")) {
            return ["/nav"];
        }
        if (selectPath.startsWith("/article-type")) {
            return ["/article-type"];
        }
        return [selectPath];
    };

    const getIconSize = () => {
        if (expanded) {
            return 19;
        }
        return getAppState().compactMode ? 22 : 24;
    };

    const getRailSelectedKey = () => {
        const selectedKey = getSelectMenu()[0];
        if (selectedKey === "/article-type" || selectedKey === "/nav" || selectedKey === "/link") {
            return "/article";
        }
        return selectedKey;
    };

    const getInfo = (entry: MenuEntry) => {
        const selected = (expanded ? getSelectMenu()[0] : getRailSelectedKey()) === entry.key;
        return { selected, icon: selected ? entry.selectIcon : entry.icon };
    };

    const createLabel = (entry: MenuEntry) => {
        const info = getInfo(entry);
        return (
            <Link
                to={getRealRouteUrl(entry.link)}
                title={entry.text}
                style={{
                    color: "inherit",
                    background: "transparent",
                }}
                onClick={(e) => {
                    tryBlock(e, modal);
                }}
            >
                {info.icon}
                <span className="menu-title">{entry.text}</span>
            </Link>
        );
    };

    const getItem = (entry: MenuEntry): MenuItem => {
        return {
            key: entry.key,
            label: createLabel(entry),
        } as MenuItem;
    };

    const railEntries: MenuEntry[] = [
        {
            key: "/index",
            text: getRes().index.title,
            link: "/index",
            selectIcon: <DashboardFilled style={{ fontSize: getIconSize() }} />,
            icon: <DashboardOutlined style={{ fontSize: getIconSize() }} />,
        },
        {
            key: "/article-edit",
            text: getRes().articleEdit.title,
            link: getArticleEditUrl(),
            selectIcon: <EditFilled style={{ fontSize: getIconSize() }} />,
            icon: <EditOutlined style={{ fontSize: getIconSize() }} />,
        },
        {
            key: "/article",
            text: getRes().article.title,
            link: "/article",
            selectIcon: <ContainerFilled style={{ fontSize: getIconSize() }} />,
            icon: <ContainerOutlined style={{ fontSize: getIconSize() }} />,
        },
        {
            key: "/comment",
            text: getRes().comment.title,
            link: "/comment",
            selectIcon: <CommentOutlined style={{ fontSize: getIconSize() }} />,
            icon: <CommentOutlined style={{ fontSize: getIconSize() }} />,
        },
        {
            key: "/plugin",
            text: getRes().plugin.title,
            link: "/plugin",
            selectIcon: <ApiFilled style={{ fontSize: getIconSize() }} />,
            icon: <ApiOutlined style={{ fontSize: getIconSize() }} />,
        },
        {
            key: "/website",
            text: getRes().common.settings,
            link: "/website",
            selectIcon: <SettingFilled style={{ fontSize: getIconSize() }} />,
            icon: <SettingOutlined style={{ fontSize: getIconSize() }} />,
        },
    ];

    const panelItems: MenuItem[] = [
        {
            type: "group",
            label: getRes().index.title,
            children: [
                getItem({
                    key: "/index",
                    text: getRes().index.title,
                    link: "/index",
                    selectIcon: <DashboardFilled style={{ fontSize: getIconSize() }} />,
                    icon: <DashboardOutlined style={{ fontSize: getIconSize() }} />,
                }),
            ],
        },
        {
            type: "group",
            label: getRes().article.title,
            children: [
                getItem({
                    key: "/article-edit",
                    text: getRes().articleEdit.title,
                    link: getArticleEditUrl(),
                    selectIcon: <EditFilled style={{ fontSize: getIconSize() }} />,
                    icon: <EditOutlined style={{ fontSize: getIconSize() }} />,
                }),
                getItem({
                    key: "/article",
                    text: getRes().article.title,
                    link: "/article",
                    selectIcon: <ContainerFilled style={{ fontSize: getIconSize() }} />,
                    icon: <ContainerOutlined style={{ fontSize: getIconSize() }} />,
                }),
                getItem({
                    key: "/article-type",
                    text: getRes().articleType.title,
                    link: "/article-type",
                    selectIcon: <TagsFilled style={{ fontSize: getIconSize() }} />,
                    icon: <TagsOutlined style={{ fontSize: getIconSize() }} />,
                }),
                getItem({
                    key: "/nav",
                    text: getRes().nav.title,
                    link: "/nav",
                    selectIcon: <BarsOutlined style={{ fontSize: getIconSize() }} />,
                    icon: <BarsOutlined style={{ fontSize: getIconSize() }} />,
                }),
                getItem({
                    key: "/link",
                    text: getRes().link.title,
                    link: "/link",
                    selectIcon: <LinkOutlined style={{ fontSize: getIconSize() }} />,
                    icon: <LinkOutlined style={{ fontSize: getIconSize() }} />,
                }),
            ],
        },
        {
            type: "group",
            label: getRes().common.settings,
            children: [
                getItem({
                    key: "/comment",
                    text: getRes().comment.title,
                    link: "/comment",
                    selectIcon: <CommentOutlined style={{ fontSize: getIconSize() }} />,
                    icon: <CommentOutlined style={{ fontSize: getIconSize() }} />,
                }),
                getItem({
                    key: "/plugin",
                    text: getRes().plugin.title,
                    link: "/plugin",
                    selectIcon: <ApiFilled style={{ fontSize: getIconSize() }} />,
                    icon: <ApiOutlined style={{ fontSize: getIconSize() }} />,
                }),
                getItem({
                    key: "/website",
                    text: getRes().common.settings,
                    link: "/website",
                    selectIcon: <SettingFilled style={{ fontSize: getIconSize() }} />,
                    icon: <SettingOutlined style={{ fontSize: getIconSize() }} />,
                }),
            ],
        },
    ];

    const [selectMenu, setSelectMenu] = useState<string[]>(getSelectMenu());

    useEffect(() => {
        setSelectMenu(getSelectMenu());
    }, [location.pathname, location.search]);

    return (
        <>
            {contextHolder}
            <Menu
                selectedKeys={selectMenu}
                items={expanded ? panelItems : railEntries.map(getItem)}
                theme={getAppState().dark ? "dark" : "light"}
                className={expanded ? "sidebar-panel" : "sidebar-rail"}
                style={{
                    borderInlineEnd: "none",
                    minHeight: "100%",
                    background: "transparent",
                }}
            />
        </>
    );
};

export default SliderMenu;
