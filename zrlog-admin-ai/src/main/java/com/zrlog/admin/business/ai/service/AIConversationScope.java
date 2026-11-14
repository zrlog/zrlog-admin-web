package com.zrlog.admin.business.ai.service;

import com.zrlog.common.exception.ArgsException;

/**
 * A missing article ID is a general conversation; zero explicitly identifies an unsaved article.
 */
public final class AIConversationScope {
    private AIConversationScope() {}

    public static String key(Long articleId, String taskId) {
        if (taskId != null) {
            if (articleId != null
                    || taskId.isBlank()
                    || taskId.length() > 256
                    || !taskId.matches("[a-zA-Z0-9_.:-]+")) throw new ArgsException();
            return "task_" + taskId;
        }
        if (articleId == null) return "general";
        if (articleId < 0 || articleId > Integer.MAX_VALUE) throw new ArgsException();
        return articleId.toString();
    }
}
