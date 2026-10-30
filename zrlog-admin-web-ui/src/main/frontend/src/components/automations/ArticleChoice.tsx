import { Alert, Button, InputNumber, Space, Typography } from "antd";
import { useUiMessage } from "@zrlog/ui/feedback";
import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getSsDate } from "../../base/SsData";
import { getRealRouteUrl, getRes } from "../../utils/constants";
import type { ApiResponse } from "../../type";
import type { ArticleEditInfo } from "../articleEdit/index.types";

export default function ArticleChoice({
    value,
    onChange,
    title,
    version,
    readOnly = false,
    onConfirm,
}: {
    value?: number;
    onChange?: (id: number | null) => void;
    title?: string;
    version?: number;
    readOnly?: boolean;
    onConfirm: (title?: string, version?: number) => void;
}) {
    const active = useRef(true);
    useEffect(() => {
        active.current = true;
        return () => {
            active.current = false;
        };
    }, []);
    const api = useAxiosBaseInstance();
    const [busy, setBusy] = useState(false);
    const [excerpt, setExcerpt] = useState("");
    const [message, holder] = useUiMessage();
    const res = getRes().automations;
    const load = async () => {
        if (!value || busy) return;
        setBusy(true);
        const session = getSsDate().key;
        try {
            const response = await api.get<ApiResponse<ArticleEditInfo>>(`/api/admin/article-edit?id=${value}`);
            if (!active.current || session !== getSsDate().key) return;
            if (response.data.error) throw new Error(response.data.message);
            const article = response.data.data.article;
            if (article.logId !== value || !article.rubbish || !Number.isInteger(article.version))
                throw new Error(res.chooseDraft);
            onConfirm(article.title, article.version);
            setExcerpt(
                (
                    article.markdown ||
                    new DOMParser().parseFromString(article.content || "", "text/html").body.textContent ||
                    ""
                ).slice(0, 800)
            );
        } catch (error) {
            message.error(error instanceof Error ? error.message : res.failedRequest);
        } finally {
            setBusy(false);
        }
    };
    return (
        <Space orientation="vertical" style={{ width: "100%" }}>
            {holder}
            <Space wrap>
                <InputNumber
                    disabled={busy || readOnly}
                    aria-label={res.articleId}
                    value={value}
                    min={1}
                    precision={0}
                    onChange={(id) => {
                        onChange?.(id);
                        onConfirm();
                        setExcerpt("");
                    }}
                />
                <Button loading={busy} disabled={!value} onClick={() => void load()}>
                    {res.loadDraft}
                </Button>
            </Space>
            {title && version !== undefined && (
                <Alert
                    type="info"
                    title={title}
                    description={
                        <Space orientation="vertical">
                            <Typography.Text>
                                {res.confirmedVersion}: {version}
                            </Typography.Text>
                            {excerpt && (
                                <Typography.Paragraph ellipsis={{ rows: 4, expandable: true }}>
                                    {excerpt}
                                </Typography.Paragraph>
                            )}
                            <Link
                                to={getRealRouteUrl(`/article-edit?id=${value}`)}
                                target="_blank"
                                rel="noopener noreferrer"
                            >
                                {res.viewArticle}
                            </Link>
                        </Space>
                    }
                />
            )}
        </Space>
    );
}
