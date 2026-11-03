package com.zrlog.admin.business.service;

import com.zrlog.model.Log;
import com.hibegin.common.dao.ResultValueConvertUtils;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

/** Article history and resource tools use column-keyed snapshots, separate from tracked Entities. */
final class ArticleRow {
    private ArticleRow() {}
    static Map<String, Object> snapshot(Log row) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("logId", row.getId());
        values.put("version", row.getVersion());
        values.put("createdAt", row.getCreatedAt());
        values.put("last_update_date", row.getUpdatedAt());
        values.put("alias", row.getAlias());
        values.put("canComment", row.getCanComment());
        values.put("click", row.getClick());
        values.put("content", row.getContent());
        values.put("plain_content", row.getPlainContent());
        values.put("markdown", row.getMarkdown());
        values.put("digest", row.getDigest());
        values.put("keywords", row.getKeywords());
        values.put("thumbnail", row.getThumbnail());
        values.put("recommended", row.getRecommended());
        values.put("sticky", row.getSticky());
        values.put("releaseTime", row.getReleaseTime());
        values.put("title", row.getTitle());
        values.put("typeId", row.getTypeId());
        values.put("userId", row.getUserId());
        values.put("hot", row.getHot());
        values.put("rubbish", row.getRubbish());
        values.put("privacy", row.getPrivacy());
        values.put("editor_type", row.getEditorType());
        values.put("arrange_plugin", row.getArrangePlugin());
        values.put("extensions", row.getExtensions());
        return values;
    }

    static void apply(Log row, Map<String, Object> values) {
        if (values.containsKey("alias")) row.setAlias((String) values.get("alias"));
        if (values.containsKey("canComment")) row.setCanComment(values.get("canComment") == null ? null : ResultValueConvertUtils.toBoolean(values.get("canComment")));
        if (values.containsKey("click")) row.setClick(values.get("click") == null ? null : ((Number) values.get("click")).intValue());
        if (values.containsKey("content")) row.setContent((String) values.get("content"));
        if (values.containsKey("plain_content")) row.setPlainContent((String) values.get("plain_content"));
        if (values.containsKey("markdown")) row.setMarkdown((String) values.get("markdown"));
        if (values.containsKey("digest")) row.setDigest((String) values.get("digest"));
        if (values.containsKey("keywords")) row.setKeywords((String) values.get("keywords"));
        if (values.containsKey("thumbnail")) row.setThumbnail((String) values.get("thumbnail"));
        if (values.containsKey("recommended")) row.setRecommended(values.get("recommended") == null ? null : ResultValueConvertUtils.toBoolean(values.get("recommended")));
        if (values.containsKey("sticky")) row.setSticky(values.get("sticky") == null ? null : ((Number) values.get("sticky")).intValue());
        if (values.containsKey("releaseTime")) row.setReleaseTime(values.get("releaseTime") == null ? null : new Timestamp(ResultValueConvertUtils.parseDate(values.get("releaseTime"))));
        if (values.containsKey("title")) row.setTitle((String) values.get("title"));
        if (values.containsKey("typeId")) row.setTypeId(values.get("typeId") == null ? null : ((Number) values.get("typeId")).intValue());
        if (values.containsKey("userId")) row.setUserId(values.get("userId") == null ? null : ((Number) values.get("userId")).intValue());
        if (values.containsKey("hot")) row.setHot(values.get("hot") == null ? null : ResultValueConvertUtils.toBoolean(values.get("hot")));
        if (values.containsKey("rubbish")) row.setRubbish(values.get("rubbish") == null ? null : ResultValueConvertUtils.toBoolean(values.get("rubbish")));
        if (values.containsKey("privacy")) row.setPrivacy(values.get("privacy") == null ? null : ResultValueConvertUtils.toBoolean(values.get("privacy")));
        if (values.containsKey("editor_type")) row.setEditorType((String) values.get("editor_type"));
        if (values.containsKey("arrange_plugin")) row.setArrangePlugin((String) values.get("arrange_plugin"));
        if (values.containsKey("extensions")) row.setExtensions((String) values.get("extensions"));
    }
}
