import { FunctionComponent, useState } from "react";
import { AdminCommonProps } from "../type";
import { Button, Card, Empty, Grid, message, Space, Switch, Table, TableColumnsType, Tag, Typography } from "antd";
import { useNavigate } from "react-router-dom";
import { useAxiosBaseInstance } from "../base/AppBase";
import { getLabelValueSeparator, getRes } from "../utils/constants";

type Lock = {
    name: string;
    remark: string;
};

type CacheEntry = {
    key: string;
    size: number;
    value: string;
};

type DevResponse = {
    locks: Lock[];
    cacheEntries?: CacheEntry[];
    devMode: boolean;
};

const Dev: FunctionComponent<AdminCommonProps<DevResponse>> = ({ data }) => {
    const axiosInstance = useAxiosBaseInstance();
    const navigate = useNavigate();
    const screens = Grid.useBreakpoint();
    const res = getRes().dev;
    const [devMode, setDevMode] = useState(data.devMode);
    const [devModeUpdating, setDevModeUpdating] = useState(false);
    const [locks, setLocks] = useState(data.locks ?? []);
    const cacheEntries = data.cacheEntries ?? [];
    const renderEntryCount = (count: number) => res.entryCount.replace("{count}", `${count}`);

    const [messageApi, messageContextHolder] = message.useMessage({
        maxCount: 3,
    });

    const updateDevMode = async (checked: boolean) => {
        setDevModeUpdating(true);
        try {
            const { data } = await axiosInstance.get(`/api/admin/dev/mode?enabled=${checked ? "true" : "false"}`);
            if (data.error) {
                messageApi.error(data.message);
                return;
            }
            setDevMode(data.data);
            messageApi.success(data.data ? res.enableDevModeSuccess : res.disableDevModeSuccess);
        } finally {
            setDevModeUpdating(false);
        }
    };

    const releaseLocks = async () => {
        const { data } = await axiosInstance.get("/api/admin/dev/releaseLocks");
        if (data.error) {
            messageApi.error(data.message);
        } else {
            setLocks([]);
            messageApi.success(data.message);
        }
    };

    const cacheColumns: TableColumnsType<CacheEntry> = screens.md
        ? [
              {
                  title: res.cacheKey,
                  dataIndex: "key",
                  key: "key",
                  width: 260,
                  render: (value: string) => (
                      <Typography.Text ellipsis={{ tooltip: value }} style={{ width: "100%" }}>
                          {value}
                      </Typography.Text>
                  ),
              },
              {
                  title: res.size,
                  dataIndex: "size",
                  key: "size",
                  width: 100,
              },
              {
                  title: getRes().value,
                  dataIndex: "value",
                  key: "value",
                  render: (value: string) => (
                      <pre
                          style={{
                              margin: 0,
                              maxHeight: 160,
                              overflow: "auto",
                              whiteSpace: "pre-wrap",
                              wordBreak: "break-all",
                          }}
                      >
                          {value}
                      </pre>
                  ),
              },
          ]
        : [
              {
                  title: res.cacheEntries,
                  key: "cacheEntry",
                  render: (_, record) => (
                      <Space direction="vertical" size={4} style={{ width: "100%" }}>
                          <Typography.Text strong ellipsis={{ tooltip: record.key }}>
                              {record.key}
                          </Typography.Text>
                          <Typography.Text type="secondary">
                              {res.size}
                              {getLabelValueSeparator()}
                              {record.size}
                          </Typography.Text>
                          <pre
                              style={{
                                  margin: 0,
                                  maxHeight: 160,
                                  overflow: "auto",
                                  whiteSpace: "pre-wrap",
                                  wordBreak: "break-all",
                              }}
                          >
                              {record.value}
                          </pre>
                      </Space>
                  ),
              },
          ];

    const lockColumns: TableColumnsType<Lock> = screens.md
        ? [
              {
                  title: getRes().key,
                  dataIndex: "name",
                  key: "name",
              },
              {
                  title: getRes().value,
                  dataIndex: "remark",
                  key: "remark",
              },
          ]
        : [
              {
                  title: res.locks,
                  key: "lock",
                  render: (_, record) => (
                      <Space direction="vertical" size={4} style={{ width: "100%" }}>
                          <Typography.Text strong>{record.name}</Typography.Text>
                          <Typography.Text type="secondary">{record.remark}</Typography.Text>
                      </Space>
                  ),
              },
          ];

    return (
        <>
            {messageContextHolder}
            <Space direction="vertical" size="middle" style={{ width: "100%" }}>
                <Card
                    title={res.devMode}
                    extra={<Tag color={devMode ? "success" : "default"}>{devMode ? res.enabled : res.disabled}</Tag>}
                >
                    <Space direction="vertical" size="middle" style={{ width: "100%" }}>
                        <Space direction={screens.sm ? "horizontal" : "vertical"} size="middle" wrap>
                            <Switch checked={devMode} loading={devModeUpdating} onChange={updateDevMode} />
                            <Typography.Text type="secondary">{res.devModeTip}</Typography.Text>
                        </Space>
                        <Space direction={screens.sm ? "horizontal" : "vertical"} wrap style={{ width: "100%" }}>
                            <Button
                                block={!screens.sm}
                                disabled={!devMode}
                                onClick={() => navigate("/file-manager?path=/admin/dev/file")}
                            >
                                {res.viewProjectFiles}
                            </Button>
                        </Space>
                    </Space>
                </Card>
                <Card
                    title={res.cacheTitle}
                    extra={<Typography.Text type="secondary">{renderEntryCount(cacheEntries.length)}</Typography.Text>}
                >
                    <Table
                        rowKey="key"
                        columns={cacheColumns}
                        dataSource={cacheEntries}
                        tableLayout="fixed"
                        pagination={screens.md ? undefined : false}
                        locale={{
                            emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={res.cacheEmpty} />,
                        }}
                    />
                </Card>
                <Card
                    title={res.lockTitle}
                    extra={<Typography.Text type="secondary">{renderEntryCount(locks.length)}</Typography.Text>}
                >
                    <Space direction="vertical" size="middle" style={{ width: "100%" }}>
                        <Space direction={screens.sm ? "horizontal" : "vertical"} wrap style={{ width: "100%" }}>
                            <Button
                                type="primary"
                                block={!screens.sm}
                                disabled={locks.length === 0}
                                onClick={releaseLocks}
                            >
                                {res.releaseAllLocks}
                            </Button>
                        </Space>
                        <Table
                            rowKey="name"
                            columns={lockColumns}
                            dataSource={locks}
                            tableLayout="fixed"
                            pagination={screens.md ? undefined : false}
                            locale={{
                                emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={res.lockEmpty} />,
                            }}
                        />
                    </Space>
                </Card>
            </Space>
        </>
    );
};
export default Dev;
