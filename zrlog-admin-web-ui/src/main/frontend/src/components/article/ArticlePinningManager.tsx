import { ArrowDownOutlined, ArrowUpOutlined, PushpinOutlined, ReloadOutlined } from "@ant-design/icons";
import { App, Button, Drawer, Empty, Grid, List, Space, Spin, Tooltip, Typography, theme } from "antd";
import { useEffect, useState } from "react";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getRes } from "../../utils/constants";
import {
    ArticlePinningData,
    ArticlePinningDirection,
    ArticlePinningEntry,
    loadArticlePinning,
    updateArticlePinning,
} from "./article-pinning";

type ArticlePinningManagerProps = {
    offline: boolean;
    open: boolean;
    onOpenChange: (open: boolean) => void;
    onItemsChange: (items: ArticlePinningEntry[]) => void;
};

const ArticlePinningManager = ({ offline, open, onOpenChange, onItemsChange }: ArticlePinningManagerProps) => {
    const axiosInstance = useAxiosBaseInstance();
    const { message } = App.useApp();
    const screens = Grid.useBreakpoint();
    const { token } = theme.useToken();
    const res = getRes().article.pinning;
    const [items, setItems] = useState<ArticlePinningEntry[]>([]);
    const [loading, setLoading] = useState(false);
    const [actionKey, setActionKey] = useState("");

    const applyData = (data: ArticlePinningData) => {
        const nextItems = data.items || [];
        setItems(nextItems);
        onItemsChange(nextItems);
    };

    const load = async () => {
        setLoading(true);
        try {
            const response = await loadArticlePinning(axiosInstance);
            if (response.error) {
                await message.error(response.message || res.loadFailed);
                return;
            }
            applyData(response.data);
        } catch {
            await message.error(res.loadFailed);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (open) {
            void load();
        }
    }, [open]);

    const mutate = async (entry: ArticlePinningEntry, direction?: ArticlePinningDirection) => {
        const key = `${entry.logId}:${direction || "unpin"}`;
        setActionKey(key);
        try {
            const response = await updateArticlePinning(direction ? "move" : "unpin", entry.logId, {
                direction,
                messageApi: message,
                backgroundTaskTitle: res.syncTask,
            });
            if (response.error) {
                await message.error(response.message || res.actionFailed);
                return;
            }
            applyData(response.data);
            if (response.message) {
                await message.success(response.message);
            }
        } catch {
            await message.error(res.actionFailed);
        } finally {
            setActionKey("");
        }
    };

    const mutating = actionKey.length > 0;
    return (
        <Drawer
            title={res.manage}
            open={open}
            width={screens.sm === false ? "100%" : 520}
            onClose={() => onOpenChange(false)}
            maskClosable={!mutating}
            extra={
                <Tooltip title={res.reload}>
                    <Button
                        type="text"
                        icon={<ReloadOutlined />}
                        aria-label={res.reload}
                        disabled={offline || loading || mutating}
                        onClick={() => void load()}
                    />
                </Tooltip>
            }
            styles={{ body: { padding: token.paddingSM } }}
        >
            <Spin spinning={loading}>
                {items.length === 0 && !loading ? <Empty description={res.empty} /> : null}
                <List
                    dataSource={items}
                    renderItem={(entry, index) => (
                        <List.Item
                            style={{ alignItems: "center", gap: token.marginSM }}
                            actions={[
                                <Tooltip title={res.moveUp} key="up">
                                    <Button
                                        type="text"
                                        icon={<ArrowUpOutlined />}
                                        aria-label={res.moveUp}
                                        disabled={offline || mutating || index === 0}
                                        loading={actionKey === `${entry.logId}:UP`}
                                        onClick={() => void mutate(entry, "UP")}
                                    />
                                </Tooltip>,
                                <Tooltip title={res.moveDown} key="down">
                                    <Button
                                        type="text"
                                        icon={<ArrowDownOutlined />}
                                        aria-label={res.moveDown}
                                        disabled={offline || mutating || index === items.length - 1}
                                        loading={actionKey === `${entry.logId}:DOWN`}
                                        onClick={() => void mutate(entry, "DOWN")}
                                    />
                                </Tooltip>,
                                <Tooltip title={res.unpin} key="unpin">
                                    <Button
                                        type="text"
                                        danger
                                        icon={<PushpinOutlined />}
                                        aria-label={res.unpin}
                                        disabled={offline || mutating}
                                        loading={actionKey === `${entry.logId}:unpin`}
                                        onClick={() => void mutate(entry)}
                                    />
                                </Tooltip>,
                            ]}
                        >
                            <Space size={token.marginSM} style={{ minWidth: 0 }}>
                                <Typography.Text type="secondary" style={{ minWidth: 24 }}>
                                    {index + 1}
                                </Typography.Text>
                                <Typography.Text
                                    ellipsis={{ tooltip: entry.title }}
                                    style={{ maxWidth: screens.sm === false ? 150 : 280 }}
                                >
                                    {entry.title}
                                </Typography.Text>
                            </Space>
                        </List.Item>
                    )}
                />
            </Spin>
        </Drawer>
    );
};

export default ArticlePinningManager;
