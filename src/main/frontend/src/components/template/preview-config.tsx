import { Form } from "antd";
import Card from "antd/es/card";
import { getRes } from "../../utils/constants";
import { FunctionComponent, useEffect, useRef, useState } from "react";
import { useAxiosBaseInstance } from "../../base/AppBase";
import XxsTips from "../xxs-tips";

type PreviewConfigProps = {
    contentType: string;
    value: string;
    initPreviewValue?: string | null;
};

const PreviewConfig: FunctionComponent<PreviewConfigProps> = ({ contentType, value, initPreviewValue }) => {
    const [previewValue, setPreviewValue] = useState<string>(initPreviewValue != null ? initPreviewValue : "");

    const axiosInstance = useAxiosBaseInstance();

    const first = useRef<boolean>(true);

    useEffect(() => {
        if (first.current) {
            first.current = false;
            return;
        }
        //console.info(contentType);
        if (contentType === "html") {
            axiosInstance
                .get("/api/admin/template/previewConfigValue?value=" + encodeURIComponent(value))
                .then(({ data }) => {
                    setPreviewValue(data.data.previewValue);
                });
        }
    }, [value]);

    if (contentType != "html") {
        return <></>;
    }

    return (
        <Form.Item label={" "} colon={false}>
            <XxsTips />
            <Card
                title={getRes().preview + " (" + contentType + ")"}
                size={"small"}
                styles={{
                    body: {
                        minHeight: 36,
                    },
                }}
                style={{ overflow: "hidden" }}
            >
                <div
                    style={{ userSelect: "none" }}
                    dangerouslySetInnerHTML={{ __html: previewValue ? previewValue : "" }}
                />
            </Card>
        </Form.Item>
    );
};

export default PreviewConfig;
