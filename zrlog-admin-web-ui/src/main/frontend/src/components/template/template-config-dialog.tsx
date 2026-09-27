import { lazy, Suspense, useEffect, useId, useRef, useState } from "react";
import { Alert, Button, Drawer, Grid, Modal, Space, Spin } from "antd";
import type { AxiosRequestConfig } from "axios";
import { useTheme } from "antd-style";
import { useAxiosBaseInstance } from "../../base/AppBase";
import type { ApiResponse } from "../../type";
import { getRes } from "../../utils/constants";
import { hasConfigFields, isLargeConfig, TemplateConfigData } from "./template-config-model";
import type { TemplateEntry } from "./template-model";
import { useTemplateConfig } from "./use-template-config";

const TemplateConfigForm = lazy(() => import("./template-config-form"));

const TemplateConfigDialog = ({
    template,
    offline,
    onClose,
}: {
    template: TemplateEntry;
    offline: boolean;
    onClose: () => void;
}) => {
    const [open, setOpen] = useState(true);
    const [data, setData] = useState<TemplateConfigData>();
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string>();
    const [attempt, setAttempt] = useState(0);
    const [modal, modalContextHolder] = Modal.useModal();
    const confirming = useRef(false);
    const axiosInstance = useAxiosBaseInstance();
    const screens = Grid.useBreakpoint();
    const theme = useTheme();
    const formId = useId();
    const editor = useTemplateConfig(data, offline);
    const res = getRes().templateConfig;

    useEffect(() => {
        if (!open || offline) return;
        const controller = new AbortController();
        setLoading(true);
        setError(undefined);
        axiosInstance
            .get<ApiResponse<TemplateConfigData>>(
                `/api/admin/template-config?shortTemplate=${encodeURIComponent(template.shortTemplate)}`,
                { signal: controller.signal, showError: false } as AxiosRequestConfig
            )
            .then(({ data: response }) => {
                if (controller.signal.aborted) return;
                if (response.error || !response.data?.config || !response.data.template) {
                    setError(response.message || getRes().templateConfig.loadFailed);
                    return;
                }
                setData(response.data);
            })
            .catch(() => {
                if (!controller.signal.aborted) setError(getRes().templateConfig.loadFailed);
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });
        return () => controller.abort();
    }, [axiosInstance, template.shortTemplate, attempt, open, offline]);

    const close = async () => {
        if (editor.saving || confirming.current) return;
        if (editor.dirty) {
            confirming.current = true;
            try {
                const discard = await modal.confirm({
                    title: res.unsavedTitle,
                    content: res.unsavedDescription,
                    okText: res.discard,
                    cancelText: res.continueEditing,
                    okButtonProps: { danger: true },
                });
                if (!discard) return;
            } finally {
                confirming.current = false;
            }
        }
        setOpen(false);
    };

    const spinner = (
        <div style={{ padding: theme.paddingLG, textAlign: "center" }}>
            <Spin />
        </div>
    );
    const content = offline ? (
        <Alert type="warning" showIcon title={res.offline} />
    ) : error ? (
        <Alert
            type="error"
            showIcon
            title={res.loadFailed}
            description={error}
            action={<Button onClick={() => setAttempt((value) => value + 1)}>{res.retry}</Button>}
        />
    ) : loading || !data ? (
        spinner
    ) : (
        <Suspense fallback={spinner}>
            <TemplateConfigForm
                data={data}
                values={editor.values}
                onChange={editor.changeValues}
                onSubmit={editor.save}
                formId={formId}
            />
        </Suspense>
    );
    const footer = (
        <Space style={{ width: "100%", justifyContent: "flex-end" }}>
            <Button onClick={close} disabled={editor.saving}>
                {getRes().close}
            </Button>
            <Button
                type="primary"
                htmlType="submit"
                form={formId}
                loading={editor.saving}
                disabled={offline || loading || !hasConfigFields(data) || !editor.dirty}
            >
                {res.save}
            </Button>
        </Space>
    );
    const title = `${res.title} · ${data?.name || template.name}`;
    const afterOpenChange = (visible: boolean) => {
        if (!visible && !open) onClose();
    };
    return (
        <>
            {modalContextHolder}
            {editor.contextHolder}
            {screens.md === false ? (
                <Drawer
                    open={open}
                    title={title}
                    placement="right"
                    size="100%"
                    onClose={close}
                    footer={footer}
                    closable={editor.saving ? false : undefined}
                    keyboard={!editor.saving}
                    mask={{ closable: !editor.saving }}
                    afterOpenChange={afterOpenChange}
                >
                    {content}
                </Drawer>
            ) : (
                <Modal
                    open={open}
                    title={title}
                    centered
                    width={isLargeConfig(data) ? 1120 : 760}
                    onCancel={close}
                    footer={footer}
                    closable={!editor.saving}
                    keyboard={!editor.saving}
                    mask={{ closable: !editor.saving }}
                    afterOpenChange={afterOpenChange}
                    styles={{ body: { maxHeight: "70dvh", overflowY: "auto", paddingTop: theme.paddingSM } }}
                >
                    {content}
                </Modal>
            )}
        </>
    );
};

export default TemplateConfigDialog;
