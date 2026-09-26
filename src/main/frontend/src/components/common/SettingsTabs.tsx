import { Tabs, TabsProps } from "antd";
import { useLocation, useNavigate } from "react-router-dom";

// Fragments keep tab navigation bookmarkable without reloading the page's data or draft.
const SettingsTabs = ({ items }: { items: NonNullable<TabsProps["items"]> }) => {
    const location = useLocation();
    const navigate = useNavigate();
    const requestedKey = location.hash.slice(1);
    const activeKey = items.some((item) => item.key === requestedKey) ? requestedKey : items[0]?.key;

    return (
        <Tabs
            activeKey={activeKey}
            items={items}
            onChange={(key) => navigate({ pathname: location.pathname, search: location.search, hash: `#${key}` })}
        />
    );
};

export default SettingsTabs;
