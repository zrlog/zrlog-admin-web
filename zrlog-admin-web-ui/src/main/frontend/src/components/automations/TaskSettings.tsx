import { Alert, Button, Descriptions, Divider, InputNumber, Popconfirm, Space, Spin, Typography } from "antd";
import Switch from "@zrlog/ui/antd/Switch";
import { useCallback, useEffect, useState } from "react";
import { getBackendServerUrl, getRes } from "../../utils/constants";
import { formatDateTime } from "../../utils/date-time";
import type { Request, Settings, SettingsPage } from "./types";

export default function TaskSettings({
    request,
    disabled,
    initialPage,
}: {
    request: Request;
    disabled: boolean;
    initialPage?: SettingsPage;
}) {
    const [page, setPage] = useState<SettingsPage | undefined>(initialPage);
    const [busy, setBusy] = useState(false);
    const [loadError, setLoadError] = useState<string>();
    const [secret, setSecret] = useState<string>();
    const [budget, setBudget] = useState<number | null>(null);
    const res = getRes().automations;
    const load = useCallback(async () => {
        setLoadError(undefined);
        try {
            setPage(await request<SettingsPage>("settings"));
        } catch (error) {
            setLoadError(error instanceof Error ? error.message : res.failedRequest);
            throw error;
        }
    }, [request, res.failedRequest]);
    useEffect(() => {
        if (initialPage) setPage(initialPage);
        else void load().catch(() => undefined);
    }, [load, initialPage]);
    const change = async (path: string, extra: Record<string, unknown> = {}) => {
        if (!page || busy || disabled) return false;
        setBusy(true);
        try {
            const result = await request<Settings | { token: string; settings: Settings }>(path, {
                revision: page.settings.revision,
                ...extra,
            });
            setSecret("token" in result ? result.token : undefined);
            await load();
            return true;
        } catch {
            /* The request boundary displays the error. */
            return false;
        } finally {
            setBusy(false);
        }
    };
    if (!page)
        return (
            <Space>
                {loadError ? <Alert type="error" showIcon title={loadError} /> : <Spin />}
                <Button onClick={() => void load().catch(() => undefined)}>{res.reload}</Button>
            </Space>
        );
    const settings = page.settings;
    const endpoint = new URL(
        settings.triggerPath.replace(/^\//, ""),
        new URL(getBackendServerUrl(), window.location.origin)
    ).href;
    const example = `curl --request POST '${endpoint.replace(/'/g, "'\\''")}' \\\n  --header "Authorization: Bearer ${
        secret ?? "$ZRLOG_ASSISTANT_TASK_TOKEN"
    }"`;
    return (
        <Space orientation="vertical" size="large" style={{ width: "100%" }}>
            <Typography.Paragraph>{res.settingsHelp}</Typography.Paragraph>
            {settings.faas && <Alert type="info" showIcon title={res.faas} />}
            <Space wrap>
                <Switch
                    checked={settings.internalEnabled && !settings.faas}
                    disabled={busy || disabled || settings.faas}
                    aria-label={res.internalEnabled}
                    onChange={(internalEnabled) => void change("settings/save", { ...settings, internalEnabled })}
                />
                <span>{res.internalEnabled}</span>
            </Space>
            <Space wrap>
                <Switch
                    checked={settings.externalEnabled}
                    disabled={busy || disabled || !settings.tokenConfigured}
                    aria-label={res.externalEnabled}
                    onChange={(externalEnabled) => void change("settings/save", { ...settings, externalEnabled })}
                />
                <span>{res.externalEnabled}</span>
            </Space>
            <Space wrap>
                <label htmlFor="automation-budget">{res.externalBudget}</label>
                <InputNumber
                    id="automation-budget"
                    min={10}
                    max={660}
                    precision={0}
                    disabled={busy || disabled}
                    value={budget ?? settings.externalBudgetSeconds}
                    onChange={(value) => setBudget(value)}
                />
                <Button
                    disabled={busy || disabled || budget === null || budget === settings.externalBudgetSeconds}
                    onClick={async () => {
                        if (await change("settings/save", { ...settings, externalBudgetSeconds: budget }))
                            setBudget(null);
                    }}
                >
                    {res.saveSettings}
                </Button>
            </Space>
            <Typography.Paragraph type="secondary">{res.externalBudgetHelp}</Typography.Paragraph>
            {!!page.external?.result?.insufficientBudgetTaskIds?.length && (
                <Alert
                    type="warning"
                    showIcon
                    title={res.insufficientBudget.replace(
                        "{seconds}",
                        String(page.external.result.requiredBudgetSeconds)
                    )}
                />
            )}
            <Descriptions
                column={1}
                items={[
                    {
                        key: "internal",
                        label: res.healthInternal,
                        children: page.internal ? formatDateTime(page.internal.startedAt) : res.never,
                    },
                    {
                        key: "external",
                        label: res.healthExternal,
                        children: page.external ? formatDateTime(page.external.startedAt) : res.never,
                    },
                    {
                        key: "token",
                        label: res.token,
                        children: settings.tokenConfigured ? res.tokenConfigured : res.tokenMissing,
                    },
                ]}
            />
            <Divider />
            <Typography.Title level={4}>{res.endpoint}</Typography.Title>
            <Typography.Paragraph copyable style={{ overflowWrap: "anywhere" }}>
                {endpoint}
            </Typography.Paragraph>
            <Typography.Paragraph type="secondary">{res.tokenHelp}</Typography.Paragraph>
            <Space wrap>
                <Popconfirm title={res.rotateHelp} onConfirm={() => change("token/rotate")}>
                    <Button disabled={disabled || busy}>{res.rotate}</Button>
                </Popconfirm>
                <Popconfirm title={res.revokeHelp} onConfirm={() => change("token/revoke")}>
                    <Button danger disabled={disabled || busy || !settings.tokenConfigured}>
                        {res.revoke}
                    </Button>
                </Popconfirm>
            </Space>
            {secret && (
                <Alert
                    type="warning"
                    showIcon
                    title={res.tokenOnce}
                    description={
                        <Typography.Paragraph copyable style={{ overflowWrap: "anywhere" }}>
                            {secret}
                        </Typography.Paragraph>
                    }
                    closable
                    onClose={() => setSecret(undefined)}
                />
            )}
            <Typography.Title level={4}>{res.example}</Typography.Title>
            <Typography.Paragraph copyable={{ text: example }}>
                <pre style={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>{example}</pre>
            </Typography.Paragraph>
        </Space>
    );
}
