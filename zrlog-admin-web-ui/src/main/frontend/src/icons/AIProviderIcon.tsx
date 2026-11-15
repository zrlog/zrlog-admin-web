import type { AIIconProps } from "@zrlog/editor/core/ai/AIIcon";
import { useEditorIcons } from "@zrlog/editor/icons";
import AiIcon from "@zrlog/ui/icons/ai";

export default function AIProviderIcon({ name, style }: AIIconProps) {
    const { brands } = useEditorIcons();
    const Brand = name ? brands[name] : undefined;
    return Brand ? <Brand style={style} /> : <AiIcon style={style} />;
}
