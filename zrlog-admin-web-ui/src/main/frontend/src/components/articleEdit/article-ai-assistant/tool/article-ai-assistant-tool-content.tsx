import { FunctionComponent } from "react";
import { AssistantToolPayload } from "../article-ai-assistant.types";
import AliasToolContent from "./content/alias-tool-content";
import CoverToolContent from "./content/cover-tool-content";
import DigestToolContent from "./content/digest-tool-content";
import ProofreadToolContent from "./content/proofread-tool-content";
import QuestionsToolContent from "./content/questions-tool-content";
import RewriteToolContent from "./content/rewrite-tool-content";
import ScoreToolContent from "./content/score-tool-content";
import SeoToolContent from "./content/seo-tool-content";
import StructureToolContent from "./content/structure-tool-content";
import TagsToolContent from "./content/tags-tool-content";
import TitleToolContent from "./content/title-tool-content";
import { ArticleAiAssistantToolContentCommonProps } from "./article-ai-assistant-tool-content.types";

type ArticleAiAssistantToolContentProps = ArticleAiAssistantToolContentCommonProps & {
    toolPayload: AssistantToolPayload;
};

const ArticleAiAssistantToolContent: FunctionComponent<ArticleAiAssistantToolContentProps> = (props) => {
    switch (props.toolPayload.tool) {
        case "rewrite":
            return <RewriteToolContent {...props} toolPayload={props.toolPayload} />;
        case "title":
            return <TitleToolContent {...props} toolPayload={props.toolPayload} />;
        case "alias":
            return <AliasToolContent {...props} toolPayload={props.toolPayload} />;
        case "digest":
            return <DigestToolContent {...props} toolPayload={props.toolPayload} />;
        case "tags":
            return <TagsToolContent {...props} toolPayload={props.toolPayload} />;
        case "cover":
            return <CoverToolContent {...props} toolPayload={props.toolPayload} />;
        case "seo":
            return <SeoToolContent {...props} toolPayload={props.toolPayload} />;
        case "proofread":
            return <ProofreadToolContent {...props} toolPayload={props.toolPayload} />;
        case "structure":
            return <StructureToolContent {...props} toolPayload={props.toolPayload} />;
        case "questions":
            return <QuestionsToolContent {...props} toolPayload={props.toolPayload} />;
        case "publishCheck":
            return <ScoreToolContent {...props} toolPayload={props.toolPayload} />;
        default:
            return <ScoreToolContent {...props} toolPayload={props.toolPayload} />;
    }
};

export default ArticleAiAssistantToolContent;
