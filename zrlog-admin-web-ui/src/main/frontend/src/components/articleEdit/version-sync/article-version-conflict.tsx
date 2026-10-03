import { RefObject, useState } from "react";
import { Alert, Button, Grid, Input, Modal, Space, Tag, Typography } from "antd";
import { useTheme } from "antd-style";
import { getRes } from "../../../utils/constants";
import { ArticleEditState } from "../index.types";
import { ArticleMergeField, MergeValue } from "./article-merge";
import { ArticleVersionSync } from "./use-article-version-sync";

type Props = {
    conflict: NonNullable<ArticleEditState["contentConflict"]>;
    sync: ArticleVersionSync;
    typeOptions: Array<{ value: number; label: string }>;
    containerRef: RefObject<HTMLDivElement>;
    offline: boolean;
    onRetry: () => void;
};

const ArticleVersionConflict = ({ conflict, sync, typeOptions, containerRef, offline, onRetry }: Props) => {
    const res = getRes().articleEdit.contentConflict;
    const theme = useTheme();
    const screens = Grid.useBreakpoint();
    const [open, setOpen] = useState(false);
    const { plan, loading, saving, saveError, resolutions, drafts, merged, remaining, choose, edit, save } = sync;
    const display = (value: MergeValue | undefined, field?: ArticleMergeField) =>
        field === "typeId"
            ? typeOptions.find((option) => option.value === value)?.label || String(value)
            : field === "rubbish"
            ? value
                ? getRes().articleEdit.status.draft
                : getRes().articleEdit.status.published
            : field === "privacy"
            ? value
                ? getRes().articleEdit.status.private
                : getRes().articleEdit.status.published
            : typeof value === "boolean"
            ? value
                ? getRes().yes
                : getRes().no
            : value === undefined || value === ""
            ? getRes().articleEdit.version.emptyValue
            : String(value);
    const detail = res.detail
        .replace("{localVersion}", String(conflict.localVersion))
        .replace("{serverVersion}", String(conflict.serverVersion));
    const unavailable = conflict.loadError || offline;
    const border = `${theme.lineWidth}px ${theme.lineType} ${theme.colorBorderSecondary}`;

    return (
        <>
            <Alert
                type={unavailable ? "error" : "warning"}
                showIcon
                message={res.title}
                description={
                    <Space direction="vertical" style={{ width: "100%" }}>
                        <span>
                            {unavailable
                                ? res.loadFailed
                                : loading || conflict.loading
                                ? res.loading
                                : saving
                                ? res.autoMerged
                                : detail}
                        </span>
                        <Button
                            loading={loading && !unavailable}
                            disabled={Boolean(conflict.loading) || offline || saving}
                            onClick={() => (unavailable ? onRetry() : setOpen(true))}
                        >
                            {unavailable ? res.retry : plan?.conflicts.length ? res.review : res.reviewMerged}
                        </Button>
                    </Space>
                }
            />
            <Modal
                title={res.review}
                open={open}
                onCancel={() => !saving && setOpen(false)}
                okText={res.save}
                cancelText={res.later}
                onOk={() => void save()}
                okButtonProps={{ disabled: !merged || loading || offline || saving }}
                confirmLoading={saving}
                cancelButtonProps={{ disabled: saving }}
                closable={!saving}
                maskClosable={!saving}
                getContainer={() => containerRef.current || document.body}
                width={screens.lg ? 1080 : "calc(100vw - 24px)"}
                centered
                style={{ paddingBottom: 0 }}
                styles={{
                    container: { maxHeight: "calc(100dvh - 32px)", display: "flex", flexDirection: "column" },
                    header: { flexShrink: 0 },
                    footer: { flexShrink: 0 },
                    body: { maxHeight: screens.lg ? "72dvh" : undefined, minHeight: 0, overflowY: "auto" },
                }}
            >
                <Space direction="vertical" size={theme.margin} style={{ width: "100%" }}>
                    <Typography.Paragraph>{detail}</Typography.Paragraph>
                    {plan && !plan.baseAvailable && <Alert type="warning" showIcon message={res.baseMissing} />}
                    {saveError && <Alert type="error" showIcon message={res.saveFailed} />}
                    <div role="status" aria-live="polite">
                        {remaining ? res.pending.replace("{count}", String(remaining)) : res.ready}
                    </div>
                    {plan?.conflicts.map((item, index) => {
                        const label = res.conflictLabel
                            .replace("{field}", getRes().articleEdit.version.fields[item.field])
                            .replace("{index}", String(index + 1));
                        const resolved = Object.prototype.hasOwnProperty.call(resolutions, item.id);
                        const draft = drafts[item.id] ?? String(resolutions[item.id] ?? item.local);
                        return (
                            <section
                                key={item.id}
                                aria-label={label}
                                style={{
                                    width: "100%",
                                    padding: theme.padding,
                                    border,
                                    borderRadius: theme.borderRadiusLG,
                                    boxSizing: "border-box",
                                }}
                            >
                                <Space wrap>
                                    <Typography.Text strong>{label}</Typography.Text>
                                    {resolved && (
                                        <Tag color="success" style={{ color: theme.colorText }}>
                                            {res.resolved}
                                        </Tag>
                                    )}
                                </Space>
                                {item.base !== undefined && (
                                    <details style={{ marginBlock: theme.marginSM }}>
                                        <summary>{res.base}</summary>
                                        <pre style={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>
                                            {display(item.base, item.field)}
                                        </pre>
                                    </details>
                                )}
                                <div
                                    style={{
                                        display: "grid",
                                        gridTemplateColumns: screens.md
                                            ? "minmax(0,1fr) minmax(0,1fr)"
                                            : "minmax(0,1fr)",
                                        gap: theme.margin,
                                    }}
                                >
                                    {(["local", "server"] as const).map((side) => (
                                        <div key={side}>
                                            <Typography.Text strong>
                                                {side === "local" ? res.local : res.server}
                                            </Typography.Text>
                                            <Input.TextArea
                                                aria-label={`${label} ${side === "local" ? res.local : res.server}`}
                                                value={display(item[side], item.field)}
                                                readOnly
                                                autoSize={{ minRows: 2, maxRows: 8 }}
                                            />
                                            <Button
                                                style={{
                                                    marginTop: theme.marginXS,
                                                    whiteSpace: "normal",
                                                    height: "auto",
                                                    minHeight: 44,
                                                }}
                                                type={
                                                    resolved && resolutions[item.id] === item[side]
                                                        ? "primary"
                                                        : "default"
                                                }
                                                aria-pressed={resolved && resolutions[item.id] === item[side]}
                                                onClick={() => choose(item.id, item[side])}
                                            >
                                                {side === "local" ? res.useLocal : res.useServer}
                                            </Button>
                                        </div>
                                    ))}
                                </div>
                                {typeof item.local === "string" && (
                                    <div style={{ marginTop: theme.margin }}>
                                        <label htmlFor={`merge-${item.id}`}>{res.custom}</label>
                                        <Input.TextArea
                                            id={`merge-${item.id}`}
                                            value={draft}
                                            autoSize={{ minRows: 2, maxRows: 10 }}
                                            onChange={(event) => edit(item.id, event.target.value)}
                                        />
                                        <Button
                                            style={{ marginTop: theme.marginXS }}
                                            onClick={() => choose(item.id, draft)}
                                        >
                                            {res.resolve}
                                        </Button>
                                    </div>
                                )}
                            </section>
                        );
                    })}
                    {merged && (
                        <section style={{ width: "100%" }} aria-label={res.preview}>
                            <Typography.Title level={5}>{res.preview}</Typography.Title>
                            <Typography.Paragraph strong>{merged.title}</Typography.Paragraph>
                            <Input.TextArea
                                aria-label={res.preview}
                                readOnly
                                value={merged.markdown ?? merged.content ?? ""}
                                autoSize={{ minRows: 4, maxRows: 12 }}
                            />
                        </section>
                    )}
                </Space>
            </Modal>
        </>
    );
};
export default ArticleVersionConflict;
