import { Alert, Button, Collapse, InputNumber, Space, Spin, Typography } from "antd";
import Popconfirm from "@zrlog/ui/antd/Popconfirm";
import Tag from "@zrlog/ui/antd/Tag";
import { useAssistantSettingsStyles } from "./settings-styles";
import Switch from "@zrlog/ui/antd/Switch";
import { useCallback, useEffect, useState } from "react";
import { formatLabelValue, getBackendServerUrl, getRes } from "../../../utils/constants";
import { formatDateTime } from "../../../utils/date-time";
import type { Request, Settings, SettingsPage } from "./types";
import ExternalTriggerGuide from "./ExternalTriggerGuide";

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
    const res = getRes().websiteAssistant;
    const { styles } = useAssistantSettingsStyles();
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
                <Button onClick={() => void load().catch(() => undefined)}>{getRes().common.refresh}</Button>
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
        <section className={styles.section} aria-label={res.execution}>
            <h3 className={styles.title}>{res.execution}</h3>
            {loadError && <Alert type="error" showIcon title={loadError} />}
            {settings.faas ? (
                <Alert type="info" showIcon title={res.faas} />
            ) : (
                <div className={styles.row}>
                    <div className={styles.label}>
                        <Typography.Text strong>{res.internalEnabled}</Typography.Text>
                        <p className={styles.help}>{res.internalHelp}</p>
                        <span className={styles.metadata}>
                            {formatLabelValue(
                                res.healthInternal,
                                page.internal ? formatDateTime(page.internal.startedAt) : res.never
                            )}
                        </span>
                    </div>
                    <Switch
                        className={styles.toggle}
                        checked={settings.internalEnabled}
                        disabled={busy || disabled}
                        aria-label={res.internalEnabled}
                        onChange={(internalEnabled) => void change("settings/save", { ...settings, internalEnabled })}
                    />
                </div>
            )}
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
            <Collapse
                style={{ background: "transparent" }}
                defaultActiveKey={settings.faas || settings.externalEnabled ? ["external"] : []}
                items={[
                    {
                        key: "external",
                        label: (
                            <div className={styles.label}>
                                <Typography.Text strong>{res.externalTrigger}</Typography.Text>
                                <p className={styles.help}>{res.externalHelp}</p>
                            </div>
                        ),
                        extra: <Tag>{settings.externalEnabled ? getRes().assistant.on : getRes().assistant.off}</Tag>,
                        children: (
                            <div className={styles.details}>
                                <div className={styles.row}>
                                    <div className={styles.label}>
                                        <Typography.Text>{res.externalEnabled}</Typography.Text>
                                        {!settings.tokenConfigured && (
                                            <p className={styles.help}>{res.tokenMissingHelp}</p>
                                        )}
                                        <span className={styles.metadata}>
                                            {formatLabelValue(
                                                res.healthExternal,
                                                page.external ? formatDateTime(page.external.startedAt) : res.never
                                            )}
                                        </span>
                                    </div>
                                    <Switch
                                        className={styles.toggle}
                                        checked={settings.externalEnabled}
                                        disabled={busy || disabled || !settings.tokenConfigured}
                                        aria-label={res.externalEnabled}
                                        onChange={(externalEnabled) =>
                                            void change("settings/save", { ...settings, externalEnabled })
                                        }
                                    />
                                </div>
                                <div className={styles.field}>
                                    <Space wrap>
                                        <Typography.Text strong>{res.token}</Typography.Text>
                                        <Typography.Text type="secondary">
                                            {settings.tokenConfigured ? res.tokenConfigured : res.tokenMissing}
                                        </Typography.Text>
                                    </Space>
                                    <p className={styles.help}>{res.tokenHelp}</p>
                                    <Space wrap>
                                        <Popconfirm
                                            title={res.rotateHelp}
                                            okText={res.rotate}
                                            cancelText={getRes().common.cancel}
                                            onConfirm={() => change("token/rotate")}
                                        >
                                            <Button disabled={disabled || busy}>{res.rotate}</Button>
                                        </Popconfirm>
                                        <Popconfirm
                                            title={res.revokeHelp}
                                            okText={res.revoke}
                                            cancelText={getRes().common.cancel}
                                            onConfirm={() => change("token/revoke")}
                                        >
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
                                                <Typography.Paragraph
                                                    copyable
                                                    style={{ overflowWrap: "anywhere", marginBottom: 0 }}
                                                >
                                                    {secret}
                                                </Typography.Paragraph>
                                            }
                                            closable={{ closeIcon: true, "aria-label": getRes().common.close }}
                                            onClose={() => setSecret(undefined)}
                                        />
                                    )}
                                </div>
                                <div className={styles.field}>
                                    <Typography.Text strong>{res.endpoint}</Typography.Text>
                                    <Typography.Paragraph
                                        copyable
                                        style={{ overflowWrap: "anywhere", marginBottom: 0 }}
                                    >
                                        {endpoint}
                                    </Typography.Paragraph>
                                </div>
                                <div className={styles.field}>
                                    <label htmlFor="assistant-trigger-budget">{res.externalBudget}</label>
                                    <Space wrap>
                                        <InputNumber
                                            id="assistant-trigger-budget"
                                            aria-describedby="assistant-trigger-budget-help"
                                            min={10}
                                            max={660}
                                            precision={0}
                                            disabled={busy || disabled}
                                            value={budget ?? settings.externalBudgetSeconds}
                                            onChange={(value) => setBudget(value)}
                                        />
                                        <Button
                                            disabled={
                                                busy ||
                                                disabled ||
                                                budget === null ||
                                                budget === settings.externalBudgetSeconds
                                            }
                                            onClick={async () => {
                                                if (
                                                    await change("settings/save", {
                                                        ...settings,
                                                        externalBudgetSeconds: budget,
                                                    })
                                                )
                                                    setBudget(null);
                                            }}
                                        >
                                            {res.saveSettings}
                                        </Button>
                                    </Space>
                                    <p id="assistant-trigger-budget-help" className={styles.help}>
                                        {res.externalBudgetHelp}
                                    </p>
                                </div>
                                <Collapse
                                    items={[
                                        {
                                            key: "examples",
                                            label: res.example,
                                            children: (
                                                <ExternalTriggerGuide endpoint={endpoint} curlExample={example} />
                                            ),
                                        },
                                    ]}
                                />
                            </div>
                        ),
                    },
                ]}
            />
        </section>
    );
}
