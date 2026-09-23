import Editor from "@editor/dist/editor";
import { EditorMode } from "@editor/dist/editor/editor.types";
import { useAxiosBaseInstance } from "../../base/AppBase";
import { getRes } from "../../utils/constants";
import { getAppState } from "../../base/ConfigProviderApp";

const TemplateConfigYaml = ({
    value,
    onChange,
    disabled,
}: {
    value: string;
    onChange: (value: string) => void;
    disabled?: boolean;
}) => {
    const axiosInstance = useAxiosBaseInstance();
    return (
        <div ref={(element) => element?.toggleAttribute("inert", Boolean(disabled))}>
            <Editor
                height="min(56dvh, 560px)"
                value={value}
                onChange={(content) => onChange(content.value)}
                fullscreen={false}
                previewContent=""
                config={{
                    mode: EditorMode.YML,
                    axiosInstance,
                    disableStatistics: true,
                    disableToolbar: true,
                    dark: getAppState().dark,
                    lang: getRes().lang === "en_US" ? "en_US" : "zh_CN",
                    preview: false,
                    uploadConfig: { buildUploadUrl: () => "", formName: "", axiosInstance },
                }}
            />
        </div>
    );
};

export default TemplateConfigYaml;
