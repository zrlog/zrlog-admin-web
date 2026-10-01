package com.zrlog.admin.business.ai.service;

import com.google.gson.*;
import com.zrlog.admin.business.ai.model.AIChatModels.*;
import com.zrlog.admin.business.knowledge.ContentToolCatalog;
import com.zrlog.admin.business.knowledge.KnowledgeModels.Tool;
import com.zrlog.admin.business.knowledge.ToolProvider;
import com.zrlog.admin.business.rest.request.GenerateArticleFieldRequest;
import com.zrlog.admin.business.rest.response.AIResponseEntry.AIContentEntry;
import com.zrlog.common.exception.ArgsException;
import java.util.*;

/** Internal writing tools, independent of knowledge-library permissions. */
final class AIWritingToolProvider implements ToolProvider {
    private final Run run;
    private final AIWritingSkillService skills;
    AIWritingToolProvider(Run run, AIWritingSkillService skills) { this.run = run; this.skills = skills; }
    @Override public List<Tool> definitions(String language) {
        return run.editorContext == null ? List.of() : AIWritingSkillCatalog.tools();
    }
    @Override public Object call(String name, JsonObject args) throws Exception {
        validate(name, args);
        String key = name.substring("writing_".length());
        // Keep runtime settings and per-call adjustments out of the original editor snapshot.
        GenerateArticleFieldRequest context = new Gson().fromJson(new Gson().toJson(run.editorContext), GenerateArticleFieldRequest.class);
        if (key.equals("publishCheck")) new AIPublishCheckService().fillPublishCheckContext(context);
        StringBuilder instruction = new StringBuilder();
        for (int i = Math.max(0, run.messages.size() - 8); i < run.messages.size(); i++) {
            var message = run.messages.get(i);
            if ("system".equals(message.getRole()) || message.getContent() == null) continue;
            String content = message.getContent();
            instruction.append(message.getRole()).append(": ").append(content.substring(0, Math.min(content.length(), 4000))).append('\n');
        }
        instruction.append("Current writing request: ").append(args.get("instruction").getAsString());
        AIContentEntry entry = skills.generate(key, context, instruction.toString());
        // Validate again at the execution boundary: no generator implementation can bypass the contract.
        AIWritingSkillCatalog.validateResult(key, entry.getPayload());
        entry.setMessageId(run.id + ":skill:" + run.calls + ":" + run.nextTool);
        entry.setMessageType("writingSkill");
        entry.setProvider(run.provider); entry.setModel(run.model);
        SkillContract contract = new SkillContract(); contract.contextRevision = run.contextRevision;
        contract.applicableFields = AIWritingSkillCatalog.applicableFields(key);
        entry.setSkillContract(contract);
        return entry;
    }
    void validate(String name, JsonObject args) {
        Tool tool = definitions(run.language).stream().filter(t -> t.name.equals(name)).findFirst().orElseThrow(ArgsException::new);
        ContentToolCatalog.validate(tool, args, run.language);
    }
    Interaction prepareInput(JsonObject args) {
        validate(AIWritingSkillCatalog.INPUT_TOOL, args);
        Interaction input = new Interaction(); input.question = args.get("question").getAsString();
        if (input.question.isBlank()) throw new ArgsException();
        input.kind = "text"; input.contextRevision = run.contextRevision;
        input.expiresAt = System.currentTimeMillis() + AIApprovalStore.APPROVAL_TTL;
        if (args.has("resultId")) {
            input.resultId = args.get("resultId").getAsString();
            AIContentEntry result = run.skillMessages.stream().filter(message -> input.resultId.equals(message.getMessageId())
                    && "title".equals(message.getTool())).findFirst().orElseThrow(ArgsException::new);
            AIWritingSkillCatalog.validateResult("title", result.getPayload());
            JsonArray titles = new Gson().toJsonTree(result.getPayload()).getAsJsonObject().getAsJsonArray("titles");
            for (JsonElement title : titles) input.options.add(title.getAsString());
            input.kind = "select";
        }
        return input;
    }
    static JsonObject modelResult(AIContentEntry entry) {
        JsonObject result = new JsonObject(); result.addProperty("resultId", entry.getMessageId());
        result.addProperty("skill", entry.getTool()); result.addProperty("status", "candidate");
        if (!"cover".equals(entry.getTool())) result.add("payload", new Gson().toJsonTree(entry.getPayload()));
        result.addProperty("notice", "Validated candidate displayed in the editor. It has not been applied, saved or published.");
        return result;
    }
}
