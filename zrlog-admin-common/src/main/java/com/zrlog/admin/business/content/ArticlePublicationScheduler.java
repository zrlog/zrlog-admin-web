package com.zrlog.admin.business.content;

import com.zrlog.admin.business.audit.AdminAuditContext;
import com.zrlog.data.security.AccountAccess;

/** Content owns publication; the host supplies durable scheduling through this boundary. */
public interface ArticlePublicationScheduler {
    Schedule schedule(AccountAccess actor, long articleId, int version, String title, long publishAt,
                      AdminAuditContext audit) throws Exception;

    void cancel(AccountAccess actor, long articleId, AdminAuditContext audit) throws Exception;

    class Schedule {
        public String state;
        public String taskId;
        public Long taskRevision;
        public long publishAt;
        public int articleVersion;
    }
}
