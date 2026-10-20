package com.zrlog.admin.business.service;

import com.hibegin.common.dao.ResultValueConvertUtils;
import com.hibegin.common.util.*;
import com.zrlog.admin.business.content.ArticleAssistant;
import com.zrlog.admin.business.content.ArticleResourceReferences;
import com.zrlog.admin.business.exception.ArticleMissingTitleException;
import com.zrlog.admin.business.exception.ArticleMissingTypeException;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.exception.UpdateArticleExpireException;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.UpdateArticleRequest;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.common.exception.ResourceLockedException;
import com.zrlog.common.exception.UnknownException;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.data.service.DistributedLock;
import com.zrlog.model.Log;
import com.zrlog.util.ParseUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

@Singleton
public class AdminArticleService {
    private final DistributedLock.Factory locks;
    private final Supplier<ArticleAssistant> assistant;
    private final AccountIdentityService identities;
    private final AccountPermissionService permissionService;
    private final UserPreferenceService userPreferenceService;
    private final ArticleVersionService articleVersionService;
    private final ArticlePinningService articlePinningService;
    private final jakarta.inject.Provider<Log> logProvider;

    @Inject
    public AdminArticleService(
            Supplier<ArticleAssistant> assistant,
            AccountPermissionService permissionService,
            AccountIdentityService identities,
            UserPreferenceService userPreferenceService,
            ArticleVersionService articleVersionService,
            ArticlePinningService articlePinningService,
            jakarta.inject.Provider<Log> logProvider,
            DistributedLock.Factory locks) {
        this.assistant = Objects.requireNonNull(assistant);
        this.permissionService = permissionService;
        this.identities = identities;
        this.userPreferenceService = userPreferenceService;
        this.articleVersionService = articleVersionService;
        this.articlePinningService = articlePinningService;
        this.logProvider = logProvider;
        this.locks = locks;
    }

    private Lock getWriteLock(Integer logId) {
        // Existing articles share a lock across accounts; creation shares the max-id allocation
        // lock.
        return locks.create("write_article_" + (logId == null ? "create" : logId));
    }

    private Lock lockForWrite(Integer logId) {
        Lock lock = getWriteLock(logId);
        try {
            if (!lock.tryLock(20, TimeUnit.SECONDS)) {
                throw new ResourceLockedException();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UnknownException(e);
        }
        return lock;
    }

    // Re-read and recompute inside the same lock as editor saves. Never apply a scanned snapshot.
    int replaceResourceReferences(
            AccountAccess actor,
            Integer logId,
            String from,
            String to,
            boolean prefix,
            ArticleResourceReferences references)
            throws SQLException {
        Lock lock = lockForWrite(logId);
        try {
            AccountAccess access = identities.revalidate(actor);
            permissionService.require(access, AccountAction.FILE_MANAGE);
            Map<String, Object> oldLog = permissionService.article(access, logId, false);
            Map<String, Object> updates = references.replace(oldLog, from, to, prefix);
            if (updates.isEmpty()) return 0;
            int changedFields = updates.size();
            if (updates.containsKey("content")) {
                updates.put(
                        "plain_content",
                        ParseUtil.getPlainSearchText(Objects.toString(updates.get("content"), "")));
            }
            updates.put("last_update_date", new Date());
            commitUpdate(
                    oldLog, updates, ((Number) oldLog.get("version")).intValue(), actor.getUserId());
            return changedFields;
        } finally {
            lock.unlock();
        }
    }

    private void commitUpdate(
            Map<String, Object> oldLog,
            Map<String, Object> updates,
            Integer expectedVersion,
            Integer userId)
            throws SQLException {
        Number dbVersion = (Number) oldLog.get("version");
        if (expectedVersion == null
                || dbVersion.longValue() != expectedVersion
                || expectedVersion < 0
                || expectedVersion == Integer.MAX_VALUE) {
            throw new UpdateArticleExpireException();
        }
        updates.put("version", expectedVersion + 1);
        Log dao = logProvider.get();
        updates.forEach(dao::set);
        // The predicate remains authoritative when a separate process bypasses the lock.
        if (!dao.update(Map.of("logId", oldLog.get("logId"), "version", expectedVersion))) {
            throw new UpdateArticleExpireException();
        }
        Map<String, Object> saved = new HashMap<>(oldLog);
        saved.putAll(updates);
        articleVersionService.recordReversePatch(oldLog, saved, userId);
    }

    public CreateOrUpdateArticleResponse create(AccountAccess actor, CreateArticleRequest createArticleRequest) throws SQLException {
        CreateOrUpdateArticleResponse response = save(actor, createArticleRequest);
        if (!createArticleRequest.isPreserveDraftAiMessages()) {
            Objects.requireNonNullElse(assistant.get(), ArticleAssistant.NONE).articleCreated(response.getLogId(), actor.getUserId());
        }
        return response;
    }

    public CreateOrUpdateArticleResponse update(AccountAccess actor, UpdateArticleRequest updateArticleRequest) throws SQLException {
        return save(actor, updateArticleRequest);
    }

    private CreateOrUpdateArticleResponse save(AccountAccess actor, CreateArticleRequest createArticleRequest) throws SQLException {
        if (Objects.isNull(createArticleRequest) || StringUtils.isEmpty(createArticleRequest.getTitle())) {
            throw new ArticleMissingTitleException();
        }
        if (Objects.isNull(createArticleRequest.getTypeId()) || createArticleRequest.getTypeId() < 1) {
            throw new ArticleMissingTypeException();
        }
        createArticleRequest.validateArrangePlugin();
        Integer logId = (createArticleRequest instanceof UpdateArticleRequest ? ((UpdateArticleRequest) createArticleRequest).getLogId() : null);
        Lock lock = lockForWrite(logId);
        try {
            AccountAccess access = identities.revalidate(actor);
            permissionService.require(access, createArticleRequest instanceof UpdateArticleRequest
                    ? AccountAction.ARTICLE_UPDATE : AccountAction.ARTICLE_CREATE);
            if ("contributor".equals(access.getRole()) && !createArticleRequest.isRubbish())
                throw new PermissionErrorException();
            if (!createArticleRequest.isRubbish() && !createArticleRequest.isPrivacy() && !access.canPublish())
                throw new PermissionErrorException();
            Map<String, Object> oldLog = null;
            if (createArticleRequest instanceof UpdateArticleRequest) {
                oldLog =
                        permissionService.article(
                                access,
                                ((UpdateArticleRequest) createArticleRequest).getLogId(),
                                false);
                if ("contributor".equals(access.getRole())
                        && !AccountAccess.truth(oldLog.get("rubbish")))
                    throw new PermissionErrorException();
                if (AccountPermissionService.isPublic(oldLog) && !access.canPublish())
                    throw new PermissionErrorException();
            }
            Map<String, Object> previousLog = oldLog;
            boolean clearsPublicState = isPublicArticle(previousLog)
                    && (createArticleRequest.isPrivacy() || createArticleRequest.isRubbish());
            if (clearsPublicState) {
                return articlePinningService.withOrderLock(
                        () ->
                                persistArticle(
                                        actor, createArticleRequest, previousLog, true));
            }
            return persistArticle(actor, createArticleRequest, previousLog, false);
        } finally {
            lock.unlock();
        }

    }

    public boolean delete(AccountAccess actor, Long logId) throws SQLException {
        boolean deleted;
        Lock lock = lockForWrite(Math.toIntExact(logId));
        try {
            AccountAccess access = identities.revalidate(actor);
            permissionService.require(access, AccountAction.ARTICLE_DELETE);
            permissionService.article(access, logId, false);
            deleted =
                    articlePinningService.withOrderLock(
                            () -> {
                                boolean removed =
                                        this.logProvider.get().deleteById(Math.toIntExact(logId));
                                if (removed) {
                                    this.articlePinningService.normalizeOrderLocked();
                                }
                                return removed;
                            });
        } finally {
            lock.unlock();
        }
        if (deleted) {
            Objects.requireNonNullElse(assistant.get(), ArticleAssistant.NONE).articleDeleted(logId);
        }
        return deleted;
    }

    private CreateOrUpdateArticleResponse persistArticle(AccountAccess actor,
                                                         CreateArticleRequest createArticleRequest,
                                                         Map<String, Object> oldLog,
                                                         boolean normalizePinning) throws SQLException {
        Map<String, Object> log = getLog(actor, createArticleRequest, oldLog);
        if (createArticleRequest instanceof UpdateArticleRequest) {
            UpdateArticleRequest updateRequest = (UpdateArticleRequest) createArticleRequest;
            Map<String, Object> updates = new HashMap<>(log);
            updates.remove("logId");
            if (!normalizePinning) updates.remove("sticky");
            commitUpdate(oldLog, updates, updateRequest.getVersion(), actor.getUserId());
            if (normalizePinning) {
                this.articlePinningService.normalizeOrderLocked();
            }
        } else {
            Log dbLog = this.logProvider.get();
            dbLog.getAttrs().putAll(log);
            dbLog.save();
        }
        CreateOrUpdateArticleResponse response = new CreateOrUpdateArticleResponse();
        response.setLogId((long) Double.parseDouble(log.get("logId") + ""));
        response.setPrivacy(createArticleRequest.isPrivacy());
        response.setRubbish(createArticleRequest.isRubbish());
        response.setPublicCacheRefreshRequired(
                isPublicArticle(oldLog)
                        || (!createArticleRequest.isPrivacy() && !createArticleRequest.isRubbish()));
        return response;
    }

    private static boolean isPublicArticle(Map<String, Object> log) {
        return log != null
                && !ResultValueConvertUtils.toBoolean(log.get("privacy"))
                && !ResultValueConvertUtils.toBoolean(log.get("rubbish"));
    }

    private Map<String, Object> getLog(AccountAccess actor, CreateArticleRequest createArticleRequest,
                                       Map<String, Object> oldLog) throws SQLException {
        Map<String, Object> log;
        long articleId;
        if (createArticleRequest instanceof UpdateArticleRequest) {
            // Authorization, version checking and reverse patches must use the same snapshot.
            log = new HashMap<>(oldLog);
            articleId = Objects.requireNonNull(((UpdateArticleRequest) createArticleRequest).getLogId());
        } else {
            log = new HashMap<>();
            articleId = this.logProvider.get().findMaxId() + 1;
            log.put("releaseTime", new Date());
            log.put("version", 0);
            log.put("logId", articleId);
            log.put("sticky", 0);
        }
        log.put("content", createArticleRequest.getContent());
        // Null/omitted values preserve existing arrangements for older clients.
        if (createArticleRequest.getArrange_plugin() != null) {
            log.put("arrange_plugin", createArticleRequest.getArrange_plugin());
        }
        log.put("title", Jsoup.clean(createArticleRequest.getTitle(), Safelist.basic()));
        if (StringUtils.isNotEmpty(createArticleRequest.getKeywords())) {
            log.put("keywords", Jsoup.clean(createArticleRequest.getKeywords(), Safelist.basic()));
        } else {
            log.put("keywords", null);
        }
        log.put("markdown", createArticleRequest.getMarkdown());
        if (!(createArticleRequest instanceof UpdateArticleRequest)) {
            log.put("userId", actor.getUserId());
        }
        log.put("typeId", createArticleRequest.getTypeId());
        log.put("last_update_date", new Date());
        log.put("canComment", createArticleRequest.isCanComment());
        log.put("recommended", createArticleRequest.isRecommended());
        log.put("privacy", createArticleRequest.isPrivacy());
        log.put("rubbish", createArticleRequest.isRubbish());
        if (createArticleRequest.isPrivacy() || createArticleRequest.isRubbish()) {
            log.put("sticky", 0);
        }
        if (StringUtils.isEmpty(createArticleRequest.getThumbnail())) {
            log.put("thumbnail", "");
        } else {
            log.put("thumbnail", createArticleRequest.getThumbnail());
        }
        //fix digest xss
        String parseInputDigest = Jsoup.clean(ObjectHelpers.requireNonNullElse(createArticleRequest.getDigest(), ""), Safelist.basicWithImages());
        // 自动摘要
        if (StringUtils.isEmpty(parseInputDigest) && Objects.equals(createArticleRequest.isRubbish(), false)) {
            long autoSize =
                    this.userPreferenceService.effective(actor)
                            .editor
                            .autoDigestLength;
            if (autoSize < 0) {
                log.put("digest", log.get("content"));
            } else if (autoSize == 0) {
                log.put("digest", "");
            } else {
                log.put("digest", ParseUtil.autoDigest((String) log.get("content"), (int) autoSize));
            }
        } else {
            log.put("digest", parseInputDigest);
        }
        log.put("plain_content", ParseUtil.getPlainSearchText((String) log.get("content")));
        log.put("editor_type", createArticleRequest.getEditorType());
        String alias;
        if (StringUtils.isEmpty(createArticleRequest.getAlias())) {
            alias = Long.toString(articleId);
        } else {
            alias = createArticleRequest.getAlias();
        }
        log.put("alias", Jsoup.clean(alias.trim().replace(" ", "-").replace(".", "-"), Safelist.basic()));
        return log;
    }

}
