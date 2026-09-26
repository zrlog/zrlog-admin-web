import { QuestionCircleOutlined } from "@ant-design/icons";
import { Alert, Button, Drawer, Spin } from "antd";
import type { AxiosRequestConfig } from "axios";
import { useEffect, useState } from "react";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getRes } from "../../utils/constants";
import PermissionHelpContent, { AccessPage, PermissionHelpView } from "./PermissionHelpContent";

export default function PermissionHelp({ initialView = "roles" }: { initialView?: PermissionHelpView }) {
    const api = useAxiosBaseInstance();
    const [open, setOpen] = useState(false);
    const [data, setData] = useState<AccessPage>();
    const [failed, setFailed] = useState(false);
    const [attempt, setAttempt] = useState(0);
    const res = getRes().access;

    useEffect(() => {
        if (!open) return;
        const controller = new AbortController();
        setData(undefined);
        setFailed(false);
        api.get<{ error: number; data: AccessPage }>("/api/admin/access", {
            signal: controller.signal,
            showError: false,
        } as AxiosRequestConfig)
            .then((response) => {
                if (controller.signal.aborted) return;
                if (response.data.error || !response.data.data) setFailed(true);
                else setData(response.data.data);
            })
            .catch(() => {
                if (!controller.signal.aborted) setFailed(true);
            });
        return () => controller.abort();
    }, [api, open, attempt]);

    return (
        <>
            <Button icon={<QuestionCircleOutlined />} onClick={() => setOpen(true)}>
                {res.title}
            </Button>
            <Drawer
                title={res.title}
                open={open}
                onClose={() => setOpen(false)}
                size="min(100vw, 960px)"
                destroyOnHidden
            >
                {failed ? (
                    <Alert
                        type="error"
                        showIcon
                        title={res.loadFailed}
                        action={<Button onClick={() => setAttempt((value) => value + 1)}>{res.retry}</Button>}
                    />
                ) : data ? (
                    <PermissionHelpContent data={data} initialView={initialView} />
                ) : (
                    <Spin />
                )}
            </Drawer>
        </>
    );
}
