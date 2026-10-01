import { EditorSnapshot, ToolAwareAIContent } from "./article-ai-assistant.types";

const md5: (value: string) => string = require("md5");

// A content identity, not an authorization token. Selection is transient and does not change the article.
export const editorContextRevision = (context: EditorSnapshot["editorContext"]) =>
    md5(
        JSON.stringify([
            context.title,
            context.alias,
            context.markdown,
            context.digest,
            context.keywords,
            context.thumbnail,
        ])
    );

export const canApplySkillValues = (message: ToolAwareAIContent, revision: string, values: object) => {
    const contract = message.skillContract;
    if (!contract) return message.messageType !== "writingSkill";
    return (
        contract.version === 1 &&
        contract.contextRevision === revision &&
        Array.isArray(contract.applicableFields) &&
        Object.keys(values).every((field) => contract.applicableFields.includes(field))
    );
};
