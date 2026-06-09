package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.type.AdminAuditAction;
import com.zrlog.blog.web.util.WebTools;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.UserAgentUtils;

import java.sql.SQLException;
import java.util.*;

public class AdminAuditService {

    private static final String AUDIT_LOG_KEY = "admin_audit_log";
    private static final int MAX_LOG_SIZE = 100;

    public void record(HttpRequest request, AdminAuditAction action) {
        record(request, action, "");
    }

    public void record(HttpRequest request, AdminAuditAction action, String content) {
        try {
            WebsiteKvService kvService = new WebsiteKvService();
            String json = kvService.getString(AUDIT_LOG_KEY);
            List<Map<String, Object>> logs = readLogs(json);

            Map<String, Object> newLog = new java.util.HashMap<>();
            newLog.put("timestamp", System.currentTimeMillis());
            newLog.put("ip", WebTools.getRealIp(request));
            newLog.put("action", action.name());
            newLog.put("type", action.getType());
            newLog.put("content", sanitizeContent(action, content));

            String uaString = request.getHeader("User-Agent");
            if (StringUtils.isNotEmpty(uaString)) {
                UserAgentUtils.UserAgentInfo uaInfo = UserAgentUtils.parse(uaString);
                newLog.put("os", uaInfo.getOs());
                newLog.put("browser", uaInfo.getFullBrowser());
                newLog.put("crawler", uaInfo.isCrawler());
            }

            logs.add(0, newLog); // Add to the beginning

            if (logs.size() > MAX_LOG_SIZE) {
                logs = logs.subList(0, MAX_LOG_SIZE);
            }

            kvService.putString(AUDIT_LOG_KEY, new Gson().toJson(logs));
        } catch (SQLException e) {
            // Ignore audit log error to not break business
        }
    }

    private String sanitizeContent(AdminAuditAction action, String content) {
        if ("security".equals(action.getType())) {
            return "";
        }
        return content == null ? "" : content;
    }

    public List<Map<String, Object>> getRecentLogs() {
        String json = new WebsiteKvService().getString(AUDIT_LOG_KEY);
        List<Map<String, Object>> logs = readLogs(json);
        if (logs.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> displayLogs = new ArrayList<>();
        for (Map<String, Object> log : logs) {
            displayLogs.add(toDisplayLog(log));
        }
        return displayLogs;
    }

    private Map<String, Object> toDisplayLog(Map<String, Object> log) {
        Map<String, Object> displayLog = new HashMap<>(log);
        Object action = log.get("action");
        AdminAuditAction auditAction = toAuditAction(action);
        if (isSecurityLog(log, auditAction)) {
            displayLog.put("content", "");
        }
        if (auditAction == null) {
            return displayLog;
        }
        String label = I18nUtil.getAdminBackendStringFromRes(auditAction.getI18nKey());
        displayLog.put("action", StringUtils.isEmpty(label) ? action : label);
        return displayLog;
    }

    private List<Map<String, Object>> readLogs(String json) {
        if (StringUtils.isEmpty(json)) {
            return new ArrayList<>();
        }
        try {
            List<?> logs = new Gson().fromJson(json, new TypeToken<List<?>>() {}.getType());
            if (logs == null) {
                return new ArrayList<>();
            }
            List<Map<String, Object>> validLogs = new ArrayList<>();
            for (Object log : logs) {
                if (log instanceof Map) {
                    Map<String, Object> validLog = new HashMap<>();
                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) log).entrySet()) {
                        validLog.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    validLogs.add(validLog);
                }
            }
            return validLogs;
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
    }

    private AdminAuditAction toAuditAction(Object action) {
        if (!(action instanceof String)) {
            return null;
        }
        try {
            return AdminAuditAction.valueOf((String) action);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isSecurityLog(Map<String, Object> log, AdminAuditAction auditAction) {
        return Objects.equals(log.get("type"), "security")
                || (auditAction != null && Objects.equals(auditAction.getType(), "security"));
    }
}
