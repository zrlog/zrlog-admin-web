package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.rest.response.AdminAuditLogEntryResponse;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.blog.web.util.WebTools;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.UserAgentUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AdminAuditService {

    private static final String AUDIT_LOG_KEY = "admin_audit_log";
    private static final int MAX_LOG_SIZE = 100;
    private static final Gson GSON = new Gson();

    public void record(HttpRequest request, AdminAuditAction action) {
        record(request, action, "");
    }

    public void record(HttpRequest request, AdminAuditAction action, String content) {
        try {
            WebsiteKvService kvService = new WebsiteKvService();
            String json = kvService.getString(AUDIT_LOG_KEY);
            List<AdminAuditLogEntryResponse> logs = readLogs(json);

            AdminAuditLogEntryResponse newLog = new AdminAuditLogEntryResponse();
            newLog.setTimestamp(System.currentTimeMillis());
            newLog.setIp(WebTools.getRealIp(request));
            newLog.setAction(action.name());
            newLog.setType(action.getType());
            newLog.setContent(sanitizeContent(action, content));

            String uaString = request.getHeader("User-Agent");
            if (StringUtils.isNotEmpty(uaString)) {
                UserAgentUtils.UserAgentInfo uaInfo = UserAgentUtils.parse(uaString);
                newLog.setOs(uaInfo.getOs());
                newLog.setBrowser(uaInfo.getFullBrowser());
                newLog.setCrawler(uaInfo.isCrawler());
            }

            logs.add(0, newLog); // Add to the beginning

            if (logs.size() > MAX_LOG_SIZE) {
                logs = logs.subList(0, MAX_LOG_SIZE);
            }

            kvService.putString(AUDIT_LOG_KEY, GSON.toJson(logs));
        } catch (SQLException e) {
            // Ignore audit log error to not break business
        }
    }

    String sanitizeContent(AdminAuditAction action, String content) {
        if ("security".equals(action.getType())) {
            return "";
        }
        return content == null ? "" : content;
    }

    public List<AdminAuditLogEntryResponse> getRecentLogs() {
        String json = new WebsiteKvService().getString(AUDIT_LOG_KEY);
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
        displayLog.setTimestamp(log.getTimestamp());
        displayLog.setIp(log.getIp());
        displayLog.setAction(action);
        displayLog.setType(log.getType());
        displayLog.setContent(log.getContent());
        displayLog.setOs(log.getOs());
        displayLog.setBrowser(log.getBrowser());
        displayLog.setCrawler(log.getCrawler());
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
        entry.setTimestamp(toLong(log.get("timestamp")));
        entry.setIp(toStringValue(log.get("ip")));
        entry.setAction(toStringValue(log.get("action")));
        entry.setType(toStringValue(log.get("type")));
        entry.setContent(toStringValue(log.get("content")));
        entry.setOs(toStringValue(log.get("os")));
        entry.setBrowser(toStringValue(log.get("browser")));
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
