import { ReactNode } from "react";
import { Link, useLocation } from "react-router-dom";
import { getRealRouteUrl, getRes } from "../utils/constants";
import { Menu, MenuProps, Modal } from "antd";
import { parseQueryParamsToMap, tryBlock } from "../utils/helpers";
import { getAppState } from "../base/ConfigProviderApp";
import { AdminDashboardRouteIconKey, renderAdminDashboardRouteIcon } from "../components/admin-dashboard-routes";
import { getAdminNavigationGroup } from "./admin-navigation-model";

type MenuItem = Required<MenuProps>["items"][number];

type MenuEntry = {
    key: string;
    link: string;
    iconKey: AdminDashboardRouteIconKey;
    text: string;
    className?: string;
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
        if (selectPath.startsWith("/template")) {
            return ["/template"];
        }
        if (selectPath === "/upgrade" || selectPath === "/system") {
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
        if (selectPath.startsWith("/tag")) {
            return ["/tag"];
        }
        return [selectPath];
    };

    const getIconSize = () => {
        if (expanded) {
            return 19;
        }
        return getAppState().compactMode ? 22 : 24;
    };

    const selectMenu = getSelectMenu();

    const getRailSelectedKey = () => {
        const selectedKey = selectMenu[0];
        if (selectedKey === "/comment") {
            return "/comment";
        }
        const group = getAdminNavigationGroup(selectedKey);
        if (group === "content" && selectedKey !== "/article-edit") {
            return "/article";
        }
        if (group === "site") {
            return "/nav";
        }
        if (group === "extension") {
            return "/plugin";
        }
        return selectedKey;
    };

    const getInfo = (entry: MenuEntry) => {
        const selected = (expanded ? selectMenu[0] : getRailSelectedKey()) === entry.key;
        return {
            selected,
            icon: renderAdminDashboardRouteIcon(entry.iconKey, selected, getIconSize()) as ReactNode,
        };
    };

    const createLabel = (entry: MenuEntry) => {
        const info = getInfo(entry);
        return (
            <Link
                to={getRealRouteUrl(entry.link)}
                title={expanded ? undefined : entry.text}
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
            className: entry.className,
            label: createLabel(entry),
        } as MenuItem;
    };

    const railEntries: MenuEntry[] = [
        {
            key: "/index",
            text: getRes().index.title,
            link: "/index",
            iconKey: "dashboard",
        },
        {
            key: "/article-edit",
            text: getRes().articleEdit.title,
            link: getArticleEditUrl(),
            iconKey: "edit",
        },
        {
            key: "/article",
            text: getRes().article.title,
            link: "/article",
            iconKey: "container",
        },
        {
            key: "/comment",
            text: getRes().comment.title,
            link: "/comment",
            iconKey: "comment",
        },
        {
            key: "/plugin",
            text: getRes().plugin.title,
            link: "/plugin",
            iconKey: "api",
        },
        {
            key: "/website",
            text: getRes().common.settings,
            link: "/website",
            iconKey: "setting",
        },
    ];

    const panelItems: MenuItem[] = [
        getItem({
            key: "/index",
            text: getRes().index.title,
            link: "/index",
            iconKey: "dashboard",
            className: "sidebar-panel-standalone-head",
        }),
        {
            type: "divider",
            className: "sidebar-panel-head-divider",
        },
        {
            type: "group",
            label: getRes().common.content,
            children: [
                getItem({
                    key: "/article-edit",
                    text: getRes().articleEdit.title,
                    link: getArticleEditUrl(),
                    iconKey: "edit",
                }),
                getItem({
                    key: "/article",
                    text: getRes().article.title,
                    link: "/article",
                    iconKey: "container",
                }),
                getItem({
                    key: "/article-type",
                    text: getRes().articleType.title,
                    link: "/article-type",
                    iconKey: "appstore",
                }),
                getItem({
                    key: "/tag",
                    text: getRes().tagManage.title,
                    link: "/tag",
                    iconKey: "tags",
                }),
                getItem({
                    key: "/comment",
                    text: getRes().comment.title,
                    link: "/comment",
                    iconKey: "comment",
                }),
                getItem({
                    key: "/file-manager",
                    text: getRes().fileManager.title,
                    link: "/file-manager",
                    iconKey: "folder",
                }),
            ],
        },
        {
            type: "group",
            label: getRes().common.site,
            children: [
                getItem({
                    key: "/nav",
                    text: getRes().nav.title,
                    link: "/nav",
                    iconKey: "bars",
                }),
                getItem({
                    key: "/link",
                    text: getRes().link.title,
                    link: "/link",
                    iconKey: "link",
                }),
            ],
        },
        {
            type: "group",
            label: getRes().common.extension,
            children: [
                getItem({
                    key: "/plugin",
                    text: getRes().plugin.title,
                    link: "/plugin",
                    iconKey: "api",
                }),
                getItem({
                    key: "/template",
                    text: getRes().websiteTemplate.title,
                    link: "/template",
                    iconKey: "skin",
                }),
            ],
        },
    ];

    const panelFooterItems: MenuItem[] = [
        getItem({
            key: "/website",
            text: getRes().common.settings,
            link: "/website",
            iconKey: "setting",
        }),
    ];

    return (
        <>
            {contextHolder}
            {expanded ? (
                <div className="sidebar-panel-layout">
                    <Menu
                        selectedKeys={selectMenu}
                        items={panelItems}
                        theme={getAppState().dark ? "dark" : "light"}
                        className="sidebar-panel sidebar-panel-main"
                        style={{
                            borderInlineEnd: "none",
                            background: "transparent",
                        }}
                    />
                    <Menu
                        selectedKeys={selectMenu}
                        items={panelFooterItems}
                        theme={getAppState().dark ? "dark" : "light"}
                        className="sidebar-panel sidebar-panel-footer"
                        style={{
                            borderInlineEnd: "none",
                            background: "transparent",
                        }}
                    />
                </div>
            ) : (
                <Menu
                    selectedKeys={[getRailSelectedKey()]}
                    items={railEntries.map(getItem)}
                    theme={getAppState().dark ? "dark" : "light"}
                    className="sidebar-rail"
                    style={{
                        borderInlineEnd: "none",
                        minHeight: "100%",
                        background: "transparent",
                    }}
                />
            )}
        </>
    );
};

export default SliderMenu;
