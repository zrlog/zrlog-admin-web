import { describe, expect, it, jest } from "@jest/globals";
import { getAdminI18n } from "../../../../i18n/admin";
import { assistantTools } from "../article-ai-assistant.types";
import { buildAssistantToolButtons, buildAssistantToolGroups } from "./article-ai-assistant-tools";

jest.mock("../../../../utils/constants", () => ({
    getRes: () => require("../../../../i18n/admin").getAdminI18n("zh_CN"),
}));

describe("article AI assistant tool stages", () => {
    it("uses the frozen three-stage labels", () => {
        expect(buildAssistantToolGroups()).toEqual([
            { key: "ideation", label: "构思" },
            { key: "editing", label: "编辑" },
            { key: "publishCheck", label: "发布检查" },
        ]);

        const englishAssistant = getAdminI18n("en_US").articleEdit.assistant;
        expect([
            englishAssistant.groupIdeation,
            englishAssistant.groupEditing,
            englishAssistant.groupPublishCheck,
        ]).toEqual(["Ideation", "Editing", "Pre-publish Check"]);
    });

    it("assigns every supported tool to exactly one frozen stage", () => {
        const tools = buildAssistantToolButtons();
        const toolKeys = tools.map((tool) => tool.key);

        expect(new Set(toolKeys).size).toBe(toolKeys.length);
        expect([...toolKeys].sort()).toEqual([...assistantTools].sort());
        expect(
            tools.reduce<Record<string, string[]>>(
                (stages, tool) => {
                    stages[tool.group].push(tool.key);
                    return stages;
                },
                {
                    ideation: [],
                    editing: [],
                    publishCheck: [],
                }
            )
        ).toEqual({
            ideation: ["structure", "questions", "title"],
            editing: ["rewrite", "alias", "digest", "tags", "cover"],
            publishCheck: ["score", "publishCheck", "seo", "proofread"],
        });
    });
});
