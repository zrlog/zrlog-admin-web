import { ReactNode } from "react";
import { Card, Grid, Menu, Select, theme } from "antd";
import { Link, useNavigate } from "react-router-dom";
import { getAppState } from "../../base/ConfigProviderApp";
import { getRealRouteUrl } from "../../utils/constants";
import SidebarNavItem from "./SidebarNavItem";

export type SettingsNavigationGroup = {
    label: string;
    items: Array<{ key: string; label: string; path: string; icon?: ReactNode }>;
};
type SettingsLayoutProps = {
    activeKey: string;
    title: string;
    summary?: ReactNode;
    extra?: ReactNode;
    groups: SettingsNavigationGroup[];
    children: ReactNode;
    replaceNavigation?: boolean;
};

const SettingsLayout = ({
    activeKey,
    title,
    summary,
    extra,
    groups,
    children,
    replaceNavigation = false,
}: SettingsLayoutProps) => {
    const navigate = useNavigate();
    const screens = Grid.useBreakpoint();
    const { token } = theme.useToken();
    const compact = screens.md !== true;
    const border = `${token.lineWidth}px ${token.lineType} ${token.colorBorderSecondary}`;
    const headerHeight = getAppState().compactMode ? 54 : 64;
    const height = compact ? undefined : `calc(100vh - ${headerHeight + 60}px)`;
    const options = groups.map((group) => ({
        label: group.label,
        options: group.items.map((item) => ({
            value: item.key,
            label: <SidebarNavItem icon={item.icon} label={item.label} />,
        })),
    }));
    return (
        <Card
            style={{ height, maxHeight: height, minHeight: 0, width: "100%", overflow: "hidden" }}
            styles={{
                body: {
                    display: "flex",
                    flexDirection: compact ? "column" : "row",
                    height: compact ? undefined : "100%",
                    minHeight: 0,
                    padding: 0,
                    overflow: compact ? "visible" : "hidden",
                },
            }}
        >
            {compact ? (
                <div style={{ padding: token.padding, paddingBottom: 0 }}>
                    <Select
                        aria-label={title}
                        value={activeKey}
                        options={options}
                        listHeight={screens.sm ? 360 : 288}
                        virtual={false}
                        getPopupContainer={(node) => node.parentElement || document.body}
                        classNames={{ popup: { root: "settings-select-popup", list: "settings-select-popup-list" } }}
                        onChange={(key) => {
                            const item = groups.flatMap((group) => group.items).find((entry) => entry.key === key);
                            if (item) navigate(getRealRouteUrl(item.path), { replace: replaceNavigation });
                        }}
                        style={{ width: "100%" }}
                    />
                </div>
            ) : (
                <nav
                    aria-label={groups.map((group) => group.label).join(" / ")}
                    style={{
                        width: 248,
                        flexShrink: 0,
                        background: token.colorFillQuaternary,
                        borderRight: border,
                        padding: token.padding,
                        minHeight: 0,
                        overflow: "auto",
                    }}
                >
                    {groups.map((group, index) => (
                        <div key={group.label}>
                            {index > 0 && <div style={{ borderTop: border, margin: `${token.marginXS}px 0` }} />}
                            <div
                                style={{
                                    color: token.colorTextTertiary,
                                    fontSize: token.fontSizeSM,
                                    lineHeight: "20px",
                                    padding: `0 ${token.paddingSM}px`,
                                    marginBottom: token.marginXXS,
                                }}
                            >
                                {group.label}
                            </div>
                            <Menu
                                selectedKeys={[activeKey]}
                                mode="inline"
                                inlineIndent={0}
                                style={{ borderInlineEnd: "none", background: "transparent" }}
                                items={group.items.map((item) => ({
                                    key: item.key,
                                    style: {
                                        height: token.controlHeight,
                                        marginInline: 0,
                                        paddingLeft: token.paddingSM,
                                        width: "100%",
                                    },
                                    label: (
                                        <Link
                                            to={getRealRouteUrl(item.path)}
                                            replace={replaceNavigation}
                                            aria-current={item.key === activeKey ? "page" : undefined}
                                            style={{
                                                color: token.colorText,
                                                display: "inline-flex",
                                                alignItems: "center",
                                                justifyContent: "flex-start",
                                                width: "100%",
                                                height: "100%",
                                                textAlign: "left",
                                            }}
                                        >
                                            <SidebarNavItem icon={item.icon} label={item.label} />
                                        </Link>
                                    ),
                                }))}
                            />
                        </div>
                    ))}
                </nav>
            )}
            <div
                key={activeKey}
                style={{
                    flex: 1,
                    minWidth: 0,
                    minHeight: 0,
                    background: token.colorBgContainer,
                    padding: token.padding,
                    paddingBottom: 0,
                    overflow: compact ? "visible" : "auto",
                }}
            >
                <div style={{ marginBottom: token.marginLG }}>
                    {(!compact || extra) && (
                        <div
                            style={{
                                display: "flex",
                                alignItems: "center",
                                justifyContent: compact ? "flex-end" : "space-between",
                                flexWrap: "wrap",
                                gap: token.marginSM,
                                marginBottom: token.marginXS,
                            }}
                        >
                            {!compact && (
                                <div style={{ fontSize: token.fontSizeHeading5, fontWeight: 600 }}>{title}</div>
                            )}
                            {extra}
                        </div>
                    )}
                    {summary && (
                        <div
                            style={{
                                marginBottom: token.margin,
                                fontSize: token.fontSizeSM,
                                lineHeight: 1.7,
                                color: token.colorTextSecondary,
                            }}
                        >
                            {summary}
                        </div>
                    )}
                </div>
                {children}
            </div>
        </Card>
    );
};

export default SettingsLayout;
