import { Alert, Button, Radio, Space, Typography } from "antd";
import Input from "@zrlog/ui/antd/Input";
import { useEffect, useRef, useState } from "react";

import { getRes } from "../../../utils/constants";
import type { ChatRun } from "./use-article-chat";

type Props = {
    run: ChatRun;
    disabled: boolean;
    contextRevision: string;
    onRespond: (decision: "submit" | "cancel", value?: string) => void;
    onExpired?: () => void;
};

export default function ArticleAiInput({ run, disabled, contextRevision, onRespond, onExpired }: Props) {
    const [value, setValue] = useState("");
    const [now, setNow] = useState(Date.now());
    const input = run.interaction;
    const onExpiredRef = useRef(onExpired);
    onExpiredRef.current = onExpired;
    const res = getRes().articleEdit.interaction;
    useEffect(() => {
        setValue("");
        setNow(Date.now());
        if (!input || run.status !== "awaiting_input") return;
        const timer = window.setTimeout(() => {
            setNow(Date.now());
            onExpiredRef.current?.();
        }, Math.max(0, input.expiresAt - Date.now()) + 1);
        return () => window.clearTimeout(timer);
    }, [input?.id, input?.expiresAt, run.status]);
    if (!input) return null;
    const expired = run.status === "expired" || input.expiresAt <= now;
    const changed = input.contextRevision !== contextRevision;
    const waiting = run.status === "awaiting_input" && !expired;
    const invalid = !value.trim() || (input.kind === "select" && !input.options.includes(value));
    return (
        <Alert
            type={changed || run.status === "failed" ? "warning" : "info"}
            title={expired ? res.expired : changed ? res.contextChanged : waiting ? res.waiting : res.finished}
            description={
                <Space orientation="vertical" style={{ width: "100%", minWidth: 0 }}>
                    <Typography.Text style={{ overflowWrap: "anywhere" }}>{input.question}</Typography.Text>
                    {waiting && !changed && (
                        <>
                            {input.kind === "select" ? (
                                <Radio.Group
                                    value={value}
                                    onChange={(event) => setValue(event.target.value)}
                                    aria-label={input.question}
                                    disabled={disabled}
                                >
                                    <Space orientation="vertical">
                                        {input.options.map((option, index) => (
                                            <Radio key={index} value={option}>
                                                {option}
                                            </Radio>
                                        ))}
                                    </Space>
                                </Radio.Group>
                            ) : (
                                <Input.TextArea
                                    value={value}
                                    onChange={(event) => setValue(event.target.value)}
                                    maxLength={8000}
                                    aria-label={input.question}
                                    autoSize={{ minRows: 2, maxRows: 6 }}
                                    disabled={disabled}
                                />
                            )}
                            <Typography.Text type="secondary">{res.hint}</Typography.Text>
                        </>
                    )}
                    {waiting && (
                        <Space wrap>
                            {!changed && (
                                <Button
                                    type="primary"
                                    disabled={disabled || invalid}
                                    onClick={() => onRespond("submit", value)}
                                >
                                    {res.continue}
                                </Button>
                            )}
                            <Button disabled={disabled} onClick={() => onRespond("cancel")}>
                                {changed ? res.end : res.cancel}
                            </Button>
                        </Space>
                    )}
                </Space>
            }
        />
    );
}
