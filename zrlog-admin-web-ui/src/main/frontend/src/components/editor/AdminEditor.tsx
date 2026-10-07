import type {sharedEditorIcons} from "./AdminEditorIconProvider";
import Editor from "@zrlog/editor/core";
import type {ZrLogEditorProps} from "@zrlog/editor/core";
import {EditorIconProvider} from "@zrlog/editor/icons";
import type {EditorIconComponent, EditorIconMap} from "@zrlog/editor/icons";
import {useUiIconSet} from "@zrlog/ui/icons";
import Materialbold from "@zrlog/ui/material-icons/bold";
import Legacybold from "@zrlog/editor/default-icons/bold";
import Materialstrikethrough from "@zrlog/ui/material-icons/strikethrough";
import Legacystrikethrough from "@zrlog/editor/default-icons/strikethrough";
import Materialitalic from "@zrlog/ui/material-icons/italic";
import Legacyitalic from "@zrlog/editor/default-icons/italic";
import Materialquote from "@zrlog/ui/material-icons/quote";
import Legacyquote from "@zrlog/editor/default-icons/quote";
import Materialheading2 from "@zrlog/ui/material-icons/heading-2";
import Legacyheading2 from "@zrlog/editor/default-icons/heading2";
import Materialheading3 from "@zrlog/ui/material-icons/heading-3";
import Legacyheading3 from "@zrlog/editor/default-icons/heading3";
import Materialheading4 from "@zrlog/ui/material-icons/heading-4";
import Legacyheading4 from "@zrlog/editor/default-icons/heading4";
import MaterialunorderedList from "@zrlog/ui/material-icons/unordered-list";
import LegacyunorderedList from "@zrlog/editor/default-icons/unorderedList";
import MaterialorderedList from "@zrlog/ui/material-icons/ordered-list";
import LegacyorderedList from "@zrlog/editor/default-icons/orderedList";
import MaterialhorizontalRule from "@zrlog/ui/material-icons/horizontal-rule";
import LegacyhorizontalRule from "@zrlog/editor/default-icons/horizontalRule";
import Materiallink from "@zrlog/ui/material-icons/link";
import Legacylink from "@zrlog/editor/default-icons/link";
import Materialimage from "@zrlog/ui/material-icons/image";
import Legacyimage from "@zrlog/editor/default-icons/image";
import Materialvideo from "@zrlog/ui/material-icons/video";
import Legacyvideo from "@zrlog/editor/default-icons/video";
import Materialattachment from "@zrlog/ui/material-icons/attachment";
import Legacyattachment from "@zrlog/editor/default-icons/attachment";
import Materialcode from "@zrlog/ui/material-icons/code";
import Legacycode from "@zrlog/editor/default-icons/code";
import Materialtable from "@zrlog/ui/material-icons/table";
import Legacytable from "@zrlog/editor/default-icons/table";
import MaterialcopyHtml from "@zrlog/ui/material-icons/copy";
import LegacycopyHtml from "@zrlog/editor/default-icons/copyHtml";
import MaterialpreviewOff from "@zrlog/ui/material-icons/eye-off";
import LegacypreviewOff from "@zrlog/editor/default-icons/previewOff";
import Materialpreview from "@zrlog/ui/material-icons/eye";
import Legacypreview from "@zrlog/editor/default-icons/preview";
import Materialhelp from "@zrlog/ui/material-icons/help";
import Legacyhelp from "@zrlog/editor/default-icons/help";
import Materialupload from "@zrlog/ui/material-icons/cloud-upload";
import Legacyupload from "@zrlog/editor/default-icons/upload";
import MaterialalignLeft from "@zrlog/ui/material-icons/align-left";
import LegacyalignLeft from "@zrlog/editor/default-icons/alignLeft";
import MaterialalignCenter from "@zrlog/ui/material-icons/align-center";
import LegacyalignCenter from "@zrlog/editor/default-icons/alignCenter";
import MaterialalignRight from "@zrlog/ui/material-icons/align-right";
import LegacyalignRight from "@zrlog/editor/default-icons/alignRight";

// Choose only the explicitly used glyphs. This adapter loads with editor pages.
const themed = (Material: EditorIconComponent, Legacy: EditorIconComponent): EditorIconComponent => {
    return function ThemedEditorIcon(props) {
        const Icon = useUiIconSet() === "material-symbols-rounded" ? Material : Legacy;
        return <Icon {...props}/>;
    };
};
const icons = {
    bold: themed(Materialbold, Legacybold),
    strikethrough: themed(Materialstrikethrough, Legacystrikethrough),
    italic: themed(Materialitalic, Legacyitalic),
    quote: themed(Materialquote, Legacyquote),
    heading2: themed(Materialheading2, Legacyheading2),
    heading3: themed(Materialheading3, Legacyheading3),
    heading4: themed(Materialheading4, Legacyheading4),
    unorderedList: themed(MaterialunorderedList, LegacyunorderedList),
    orderedList: themed(MaterialorderedList, LegacyorderedList),
    horizontalRule: themed(MaterialhorizontalRule, LegacyhorizontalRule),
    link: themed(Materiallink, Legacylink),
    image: themed(Materialimage, Legacyimage),
    video: themed(Materialvideo, Legacyvideo),
    attachment: themed(Materialattachment, Legacyattachment),
    code: themed(Materialcode, Legacycode),
    table: themed(Materialtable, Legacytable),
    copyHtml: themed(MaterialcopyHtml, LegacycopyHtml),
    previewOff: themed(MaterialpreviewOff, LegacypreviewOff),
    preview: themed(Materialpreview, Legacypreview),
    help: themed(Materialhelp, Legacyhelp),
    upload: themed(Materialupload, Legacyupload),
    alignLeft: themed(MaterialalignLeft, LegacyalignLeft),
    alignCenter: themed(MaterialalignCenter, LegacyalignCenter),
    alignRight: themed(MaterialalignRight, LegacyalignRight),
} satisfies Pick<EditorIconMap, Exclude<keyof EditorIconMap, keyof typeof sharedEditorIcons>>;

export {insertTextAtCursor} from "@zrlog/editor/core";
export default function AdminEditor(props: ZrLogEditorProps) {
    return <EditorIconProvider icons={icons}><Editor {...props}/></EditorIconProvider>;
}
