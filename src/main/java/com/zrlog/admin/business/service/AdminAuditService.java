package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.blog.web.util.WebTools;
import com.zrlog.model.WebSite;
import com.zrlog.util.UserAgentUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AdminAuditService {

    private static final String AUDIT_LOG_KEY = "admin_audit_log";
    private static final int MAX_LOG_SIZE = 100;

    public void record(HttpRequest request, String action, String type) {
        try {
            WebSite webSite = new WebSite();
            String json = webSite.getStringValueByName(AUDIT_LOG_KEY);
            List<Map<String, Object>> logs;
            if (StringUtils.isEmpty(json)) {
                logs = new ArrayList<>();
            } else {
                logs = new Gson().fromJson(json, new TypeToken<List<Map<String, Object>>>() {}.getType());
            }

            Map<String, Object> newLog = new java.util.HashMap<>();
            newLog.put("timestamp", System.currentTimeMillis());
            newLog.put("ip", WebTools.getRealIp(request));
            newLog.put("action", action);
            newLog.put("type", type);

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

            webSite.updateByKV(AUDIT_LOG_KEY, new Gson().toJson(logs));
        } catch (SQLException e) {
            // Ignore audit log error to not break business
        }
    }

    public List<Map<String, Object>> getRecentLogs() {
        String json = new WebSite().getStringValueByName(AUDIT_LOG_KEY);
        if (StringUtils.isEmpty(json)) {
            return Collections.emptyList();
        }
        return new Gson().fromJson(json, new TypeToken<List<Map<String, Object>>>() {}.getType());
    }
}
