package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.audit.AdminAuditContext;
import com.zrlog.admin.business.rest.response.AdminAuditLogEntryResponse;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.UserAgentUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
public class AdminAuditService {
    private static final String AUDIT_LOG_KEY = "admin_audit_log";
    private static final int MAX_LOG_SIZE = 100;
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = LoggerUtil.getLogger(AdminAuditService.class);
    private final WebsiteKvService websiteKvService;
    @Inject
    public AdminAuditService(WebsiteKvService websiteKvService) {
        this.websiteKvService = websiteKvService;
    }

    public void record(AdminAuditContext context, AdminAuditAction action) {
        record(context, action, "");
    }

    public void record(AdminAuditContext context, AdminAuditAction action, String content) {
        record(context, action, content, null);
    }

    public void recordLocalized(AdminAuditContext context, AdminAuditAction action, String contentKey) {
        record(context, action, "", contentKey);
    }

    private void record(AdminAuditContext context, AdminAuditAction action, String content, String contentKey) {
        try {
            WebsiteKvService kvService = this.websiteKvService;
            String json = kvService.getString(AUDIT_LOG_KEY);
            List<AdminAuditLogEntryResponse> logs = readLogs(json);

            AdminAuditLogEntryResponse newLog = new AdminAuditLogEntryResponse();
            newLog.setActorUserId(context.getActorUserId());
            newLog.setTimestamp(System.currentTimeMillis());
            newLog.setIp(context.getIp());
            newLog.setAction(action.name());
            newLog.setType(action.getType());
            newLog.setContent(sanitizeContent(action, content));
            if (!"security".equals(action.getType())) newLog.setContentKey(contentKey);

            boolean assistantRequest = context.getUri() != null && context.getUri().startsWith("/assistant/run/");
            if (assistantRequest) {
                newLog.setAssistantRunId(context.getUri().substring("/assistant/run/".length()));
            }
            boolean mcpRequest = "/mcp".equals(context.getUri());
            String uaString = context.getUserAgent();
            if (!assistantRequest && uaString != null && !uaString.isBlank()) {
                UserAgentUtils.UserAgentInfo uaInfo = UserAgentUtils.parse(uaString);
                newLog.setOs(knownDevice(uaInfo.getOs()));
                newLog.setBrowser(knownDevice(uaInfo.getFullBrowser()));
                newLog.setCrawler(uaInfo.isCrawler());
                newLog.setAgentName(knownDevice(uaInfo.getAgentName()));
                newLog.setAgentVersion(knownDevice(uaInfo.getAgentVersion()));
                if (mcpRequest && uaInfo.getAgentName() == null && !"Unknown".equals(uaInfo.getBrowser())) {
                    newLog.setAgentName(uaInfo.getBrowser());
                    if (!"Unknown".equals(uaInfo.getBrowserVersion())) {
                        newLog.setAgentVersion(uaInfo.getBrowserVersion());
                    }
                }
            }
            if (mcpRequest) {
                // Keep the MCP entry point visible alongside the caller's declared name/version.
                newLog.setBrowser("MCP");
            }

            // Store stable source identifiers; labels are resolved for the reader's language.
            newLog.setSource(assistantRequest ? "assistant" : mcpRequest ? "mcp"
                    : newLog.getBrowser() != null || newLog.getAgentName() != null ? "client" : "unknown");
            logs.add(0, newLog); // Add to the beginning

            if (logs.size() > MAX_LOG_SIZE) {
                logs = logs.subList(0, MAX_LOG_SIZE);
            }

            if (!kvService.putString(AUDIT_LOG_KEY, serializeLogs(logs))) {
                LOGGER.warning("Record admin audit log failed: action=" + action.name()
                        + ", uri=" + context.getUri() + ", reason=no rows affected");
            }
        } catch (SQLException e) {
            // The business operation is already saved, but an audit failure must remain diagnosable.
            LOGGER.log(Level.WARNING, "Record admin audit log failed: action=" + action.name()
                    + ", uri=" + context.getUri(), e);
        }
    }

    private String serializeLogs(List<AdminAuditLogEntryResponse> logs) {
        String json = GSON.toJson(logs);
        StringBuilder stored = new StringBuilder(json.length());
        for (int i = 0; i < json.length(); i++) {
            char character = json.charAt(i);
            if (Character.isSurrogate(character)) {
                // Legacy website.value columns use MySQL utf8. JSON escapes preserve
                // supplementary characters (including emoji) without a schema migration.
                stored.append("\\u").append(Integer.toHexString(character));
            } else {
                stored.append(character);
            }
        }
        return stored.toString();
    }

    String sanitizeContent(AdminAuditAction action, String content) {
        if ("security".equals(action.getType())) {
            return "";
        }
        return content == null ? "" : content;
    }

    public List<AdminAuditLogEntryResponse> getRecentLogs(AccountAccess actor) {
        AccountPermissionService.requireAction(actor, AccountAction.SYSTEM_MANAGE.getId());
        return getRecentLogs();
    }

    List<AdminAuditLogEntryResponse> getRecentLogs() {
        String json = this.websiteKvService.getString(AUDIT_LOG_KEY);
        List<AdminAuditLogEntryResponse> logs = readLogs(json);
        if (logs.isEmpty()) {
            return Collections.emptyList();
        }
        List<AdminAuditLogEntryResponse> displayLogs = new ArrayList<>();
        for (AdminAuditLogEntryResponse log : logs) {
            displayLogs.add(toDisplayLog(log));
        }
        return displayLogs;
    }

    AdminAuditLogEntryResponse toDisplayLog(AdminAuditLogEntryResponse log) {
        String action = log.getAction();
        AdminAuditAction auditAction = toAuditAction(action);
        AdminAuditLogEntryResponse displayLog = new AdminAuditLogEntryResponse();
        displayLog.setActorUserId(log.getActorUserId());
        displayLog.setTimestamp(log.getTimestamp());
        displayLog.setIp(log.getIp());
        displayLog.setAction(action);
        displayLog.setType(log.getType());
        displayLog.setContent(log.getContentKey() == null ? log.getContent()
                : I18nUtil.getAdminBackendStringFromRes(log.getContentKey()));
        displayLog.setOs(knownDevice(log.getOs()));
        displayLog.setBrowser(knownDevice(log.getBrowser()));
        displayLog.setCrawler(log.getCrawler());
        displayLog.setAgentName(log.getAgentName());
        displayLog.setAgentVersion(log.getAgentVersion());
        displayLog.setAssistantRunId(log.getAssistantRunId());
        // Existing assistant audit entries used content for the execution reference.
        // Keep those entries readable without rewriting stored history.
        String referencePrefix = auditAction == AdminAuditAction.EXECUTE_UPGRADE ? "assistant:"
                : auditAction == AdminAuditAction.SYNC_ADMIN_STATIC_SITE ? "upgrade:" : null;
        if (displayLog.getAssistantRunId() == null && "Assistant".equals(log.getBrowser())
                && referencePrefix != null && log.getContent() != null
                && log.getContent().startsWith(referencePrefix)) {
            String runId = log.getContent().substring(referencePrefix.length());
            if (runId.matches("[0-9]{10}\\.[0-9]{20}\\.[0-9a-f-]{36}")) {
                displayLog.setAssistantRunId(runId);
                displayLog.setContent("");
            }
        }
        if (displayLog.getAssistantRunId() != null) {
            displayLog.setOs(null);
            displayLog.setBrowser(null);
            displayLog.setAgentName(null);
            displayLog.setAgentVersion(null);
            displayLog.setCrawler(false);
        }
        String source = log.getSource();
        if (displayLog.getAssistantRunId() != null) source = "assistant";
        if (source == null) {
            source = "MCP".equals(displayLog.getBrowser()) ? "mcp"
                    : displayLog.getBrowser() != null || displayLog.getAgentName() != null ? "client" : "unknown";
        }
        if (!List.of("assistant", "mcp", "client", "unknown").contains(source)) source = "unknown";
        displayLog.setSource(source);
        displayLog.setSourceLabel(I18nUtil.getAdminBackendStringFromRes("admin.audit.source." + source));
        if (auditAction == AdminAuditAction.EXECUTE_UPGRADE && "upgradeRiskAccepted".equals(log.getContent())) {
            displayLog.setContent("");
        }
        if (isSecurityLog(log, auditAction)) {
            displayLog.setContent("");
        }
        if (auditAction == null) {
            return displayLog;
        }
        String label = I18nUtil.getAdminBackendStringFromRes(auditAction.getI18nKey());
        displayLog.setAction(StringUtils.isEmpty(label) ? action : label);
        return displayLog;
    }

    private static String knownDevice(String value) {
        return value == null || value.isBlank() || "Unknown".equalsIgnoreCase(value) ? null : value;
    }

    List<AdminAuditLogEntryResponse> readLogs(String json) {
        if (StringUtils.isEmpty(json)) {
            return new ArrayList<>();
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root == null || !root.isJsonArray()) {
                return new ArrayList<>();
            }
            List<AdminAuditLogEntryResponse> validLogs = new ArrayList<>();
            for (JsonElement log : root.getAsJsonArray()) {
                if (log != null && log.isJsonObject()) {
                    validLogs.add(toAuditLogEntry(log.getAsJsonObject()));
                }
            }
            return validLogs;
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
    }

    private AdminAuditLogEntryResponse toAuditLogEntry(JsonObject log) {
        AdminAuditLogEntryResponse entry = new AdminAuditLogEntryResponse();
        if (log.has("actorUserId") && !log.get("actorUserId").isJsonNull())
            entry.setActorUserId(toLong(log.get("actorUserId")).intValue());
        entry.setTimestamp(toLong(log.get("timestamp")));
        entry.setIp(toStringValue(log.get("ip")));
        entry.setAction(toStringValue(log.get("action")));
        entry.setType(toStringValue(log.get("type")));
        entry.setContent(toStringValue(log.get("content")));
        entry.setContentKey(toStringValue(log.get("contentKey")));
        entry.setOs(toStringValue(log.get("os")));
        entry.setBrowser(toStringValue(log.get("browser")));
        entry.setAgentName(toStringValue(log.get("agentName")));
        entry.setAgentVersion(toStringValue(log.get("agentVersion")));
        entry.setAssistantRunId(toStringValue(log.get("assistantRunId")));
        entry.setSource(toStringValue(log.get("source")));
        entry.setCrawler(toBoolean(log.get("crawler")));
        return entry;
    }

    AdminAuditAction toAuditAction(String action) {
        if (action == null) {
            return null;
        }
        try {
            return AdminAuditAction.valueOf(action);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isSecurityLog(AdminAuditLogEntryResponse log, AdminAuditAction auditAction) {
        return Objects.equals(log.getType(), "security")
                || (auditAction != null && Objects.equals(auditAction.getType(), "security"));
    }

    private Long toLong(JsonElement value) {
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return null;
        }
        if (value.getAsJsonPrimitive().isNumber()) {
            return value.getAsNumber().longValue();
        }
        String stringValue = value.getAsString();
        if (StringUtils.isNotEmpty(stringValue)) {
            try {
                return Long.parseLong(stringValue);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Boolean toBoolean(JsonElement value) {
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return null;
        }
        if (value.getAsJsonPrimitive().isBoolean()) {
            return value.getAsBoolean();
        }
        String stringValue = value.getAsString();
        if (StringUtils.isNotEmpty(stringValue)) {
            return Boolean.parseBoolean(stringValue);
        }
        return null;
    }

    private String toStringValue(JsonElement value) {
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return null;
        }
        return value.getAsString();
    }
}
