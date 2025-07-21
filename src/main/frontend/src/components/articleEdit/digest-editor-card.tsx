import { getRes } from "../../utils/constants";
import { CheckOutlined, EditOutlined } from "@ant-design/icons";
import Card from "antd/es/card";
import { FunctionComponent, memo, RefObject, useEffect, useRef, useState } from "react";
import { InputRef } from "antd";
import { ArticleChangeableValue } from "./index.types";
import { getAppState } from "../../base/ConfigProviderApp";
import HtmlPreviewPanel from "@editor/dist/src/editor/html-preview-panel";
import BaseTextArea from "@editor/dist/src/editor/common/BaseTextArea";

type DigestEditorCardProps = {
    digestRef: RefObject<InputRef>;
    initDigest: string;
    handleValuesChange: (cv: ArticleChangeableValue) => void;
};

const DigestEditorCard: FunctionComponent<DigestEditorCardProps> = memo(
    ({ digestRef, initDigest, handleValuesChange }) => {
        const [editDigest, setEditDigest] = useState<boolean>(initDigest.trim().length === 0);

        const [digest, setDigest] = useState<string>(initDigest);
        const [value, setValue] = useState<string>(initDigest);
        const hasMounted = useRef(false);

        useEffect(() => {
            if (!hasMounted.current) {
                hasMounted.current = true;
                return;
            }
            handleValuesChange({ digest: digest });
        }, [digest]);

        useEffect(() => {
            if (!editDigest) {
                setValue(digest);
            }
        }, [editDigest]);

        const getBody = () => {
            if (editDigest) {
                return (
                    <BaseTextArea
                        ref={digestRef}
                        variant={"borderless"}
                        defaultValue={digest}
                        placeholder={getRes().articleEdit.digest.tips}
                        onChange={(text: string) => {
                            setDigest(text);
                        }}
                        minRows={2}
                        maxRows={12}
                        style={{ padding: 0, borderRadius: 0 }}
                        formStyle={{ marginBottom: 0 }}
                    />
                );
            }
            return (
                <HtmlPreviewPanel
                    dark={getAppState().dark}
                    style={{ maxHeight: 264, overflowY: "auto", overflowX: "hidden" }}
                    onContentChange={(text: string) => {
                        setDigest(text);
                    }}
                    editable={true}
                    htmlContent={value}
                />
            );
        };

        const getActionBtn = () => {
            if (digest.length === 0) {
                return <></>;
            }
            if (editDigest) {
                return (
                    <CheckOutlined
                        onClick={() => {
                            setEditDigest(false);
                        }}
                        style={{ color: getAppState().colorPrimary, cursor: "pointer" }}
                    />
                );
            }
            return (
                <EditOutlined
                    onClick={() => {
                        setEditDigest(true);
                    }}
                    style={{ color: getAppState().colorPrimary, cursor: "pointer" }}
                />
            );
        };

        return (
            <Card title={getRes().articleEdit.digest.label} style={{ marginBottom: 36 }} extra={[getActionBtn()]}>
                {getBody()}
            </Card>
        );
    }
);

export default DigestEditorCard;
