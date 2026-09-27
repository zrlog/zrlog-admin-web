package com.zrlog.admin.business.rest.request;

import com.hibegin.common.util.StringUtils;
import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.util.Map;
import java.util.Objects;

public class WebhookMessageNoticeRequest implements Validator {

    private String taskKey;
    private String title;
    private String description;
    private String actionLabel;
    private String actionPath;
    private String source;
    private Boolean closable;
    private Long updatedAt;
    private Map<String, Object> payload;

    @Override
    public void doValid() {
        if (StringUtils.isEmpty(title)) {
            throw new ArgsException("title");
        }
    }

    @Override
    public void doClean() {
        taskKey = truncate(clean(taskKey), 120);
        title = truncate(clean(title), 120);
        description = truncate(clean(description), 2000);
        actionLabel = truncate(clean(actionLabel), 80);
        actionPath = truncate(clean(actionPath), 255);
        source = truncate(clean(source), 80);
        if (Objects.isNull(closable)) {
            closable = true;
        }
    }

    private String clean(String value) {
        if (StringUtils.isEmpty(value)) {
            return value;
        }
        return Jsoup.clean(value, Safelist.none()).trim();
    }

    private String truncate(String value, int length) {
        if (StringUtils.isEmpty(value) || value.length() <= length) {
            return value;
        }
        return value.substring(0, length);
    }

    public String getTaskKey() {
        return taskKey;
    }

    public void setTaskKey(String taskKey) {
        this.taskKey = taskKey;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getActionLabel() {
        return actionLabel;
    }

    public void setActionLabel(String actionLabel) {
        this.actionLabel = actionLabel;
    }

    public String getActionPath() {
        return actionPath;
    }

    public void setActionPath(String actionPath) {
        this.actionPath = actionPath;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Boolean getClosable() {
        return closable;
    }

    public void setClosable(Boolean closable) {
        this.closable = closable;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}
