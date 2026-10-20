import {Alert, Grid, Modal, theme, Typography} from "antd";
import {useEffect, useRef, useState} from "react";
import {useAxiosBaseInstance} from "../../../base/AppBase";
import {getRes} from "../../../utils/constants";
import FileManagerPicker from "../../file-manager/picker";
import type {FileEntry} from "../../file-manager/shared";
import {aiAssetSizeLimit, loadAiAsset} from "./article-ai-assets";

export default function AiAssetPicker({
                                          maxBytes,
                                          extensions,
                                          onSelect,
                                          onClose,
                                      }: {
    maxBytes: number;
    extensions: string[];
    onSelect: (file: File) => void;
    onClose: () => void;
}) {
    const client = useAxiosBaseInstance();
    const screens = Grid.useBreakpoint();
    const {token} = theme.useToken();
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string>();
    const request = useRef<AbortController>();
    const res = getRes().articleEdit.files;
    useEffect(() => () => request.current?.abort(), []);
    const select = async (entry: FileEntry) => {
        if (request.current) return;
        const controller = new AbortController();
        request.current = controller;
        setLoading(true);
        setError(undefined);
        try {
            const file = await loadAiAsset(entry, client, maxBytes, controller.signal);
            if (!controller.signal.aborted) onSelect(file);
        } catch (failure) {
            if (!controller.signal.aborted)
                setError(
                    failure instanceof Error && [aiAssetSizeLimit(maxBytes), res.unsupported].includes(failure.message)
                        ? failure.message
                        : res.assetReadFailed
                );
        } finally {
            if (!controller.signal.aborted) {
                request.current = undefined;
                setLoading(false);
            }
        }
    };
    const narrow = screens.md !== true;
    return (
        <Modal
            open
            title={getRes().articleEdit.actions.chooseFromAssets}
            footer={null}
            onCancel={() => {
                request.current?.abort();
                onClose();
            }}
            width={narrow ? "100vw" : 860}
            style={narrow ? {top: 0, maxWidth: "100vw", paddingBottom: 0} : undefined}
            styles={{
                body: {
                    height: narrow ? "calc(100dvh - 112px)" : "70vh",
                    display: "flex",
                    flexDirection: "column",
                    gap: token.marginXS,
                },
            }}
        >
            {loading && <Typography.Text role="status">{res.assetLoading}</Typography.Text>}
            {error && <Alert type="error" showIcon title={error}/>}
            <FileManagerPicker
                onlyImage
                acceptExtensions={extensions}
                disabled={loading}
                style={{flex: 1, minHeight: 0}}
                onSelectFile={(_, entry) => void select(entry)}
            />
        </Modal>
    );
}
