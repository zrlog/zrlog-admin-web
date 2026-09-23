import { DeleteOutlined, EyeOutlined, LockOutlined, SettingOutlined } from "@ant-design/icons";
import { Button, message, Popconfirm, Space, Tooltip } from "antd";
import { useState } from "react";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getRes } from "../../utils/constants";
import { postRefreshCacheSse } from "../../utils/sse-utils";
import type { TemplateEntry } from "./template-model";
import type { ApiResponse } from "../../type";

const TemplateActions = ({
    template,
    onUpdate,
    onConfigure,
}: {
    template: TemplateEntry;
    onUpdate: () => void;
    onConfigure: (template: TemplateEntry) => void;
}) => {
    const axiosInstance = useAxiosBaseInstance();
    const [applying, setApplying] = useState(false);
    const [messageApi, contextHolder] = message.useMessage({ maxCount: 3 });
    const res = getRes().websiteTemplate;
    const query = `shortTemplate=${encodeURIComponent(template.shortTemplate)}`;
    const preview = () => {
        axiosInstance.post(`/api/admin/template/preview?${query}`).then(({ data }) => {
            if (data.error) {
                void messageApi.error(data.message);
                return;
            }
            window.open(document.baseURI, "_blank");
            onUpdate();
        });
    };
    const apply = () => {
        setApplying(true);
        postRefreshCacheSse<ApiResponse<void>>(`/api/admin/template/apply?${query}`, {
            messageApi,
            messageKey: "templateApplyRefreshCache",
            backgroundTaskTitle: getRes().backgroundTask.title + " · " + template.name,
        })
            .then((data) => {
                if (data.error) {
                    void messageApi.error(data.message);
                    return;
                }
                onUpdate();
            })
            .finally(() => setApplying(false));
    };
    const deleteTemplate = () =>
        axiosInstance.post(`/api/admin/template/delete?${query}`).then(({ data }) => {
            if (data.error) {
                void messageApi.error(data.message);
                return;
            }
            onUpdate();
        });

    return (
        <Space wrap size="small">
            {contextHolder}
            {!template.use && (
                <>
                    <Button icon={<EyeOutlined />} onClick={preview}>
                        {res.actions.preview}
                    </Button>
                    <Button type="primary" loading={applying} onClick={apply}>
                        {res.actions.apply}
                    </Button>
                </>
            )}
            <Tooltip title={!template.use ? res.actions.config : undefined}>
                <Button
                    type={template.use ? "primary" : "text"}
                    icon={<SettingOutlined />}
                    aria-label={res.actions.config}
                    onClick={() => onConfigure(template)}
                >
                    {template.use ? res.actions.config : undefined}
                </Button>
            </Tooltip>
            {template.builtIn ? (
                <Tooltip title={res.builtInCannotDelete}>
                    <span>
                        <Button type="text" disabled icon={<LockOutlined />} aria-label={res.builtInCannotDelete} />
                    </span>
                </Tooltip>
            ) : (
                template.deleteAble &&
                !template.use && (
                    <Popconfirm title={getRes().deleteTips} onConfirm={deleteTemplate} okButtonProps={{ danger: true }}>
                        <Button type="text" danger icon={<DeleteOutlined />} aria-label={res.actions.delete} />
                    </Popconfirm>
                )
            )}
        </Space>
    );
};

export default TemplateActions;
