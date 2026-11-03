package com.zrlog.admin.business.service;

import com.hibegin.common.dao.entity.OptimisticLockException;
import com.hibegin.common.util.ObjectHelpers;
import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.content.ArticleAssistant;
import com.zrlog.admin.business.content.ArticleResourceReferences;
import com.zrlog.admin.business.exception.ArticleMissingTitleException;
import com.zrlog.admin.business.exception.ArticleMissingTypeException;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.exception.UpdateArticleExpireException;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.UpdateArticleRequest;
import com.zrlog.admin.business.rest.response.CreateOrUpdateArticleResponse;
import com.zrlog.admin.data.repository.AdminLogRepository;
import com.zrlog.common.exception.ResourceLockedException;
import com.zrlog.common.exception.UnknownException;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.data.service.DistributedLock;
import com.zrlog.model.Log;
import com.zrlog.util.ParseUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

@Singleton
public class AdminArticleService {
    private final DistributedLock.Factory locks;
    private final Supplier<ArticleAssistant> assistant;
    private final AccountIdentityService identities;
    private final AccountPermissionService permissionService;
    private final UserPreferenceService userPreferenceService;
    private final ArticleVersionService articleVersionService;
    private final ArticlePinningService articlePinningService;
    private final jakarta.inject.Provider<AdminLogRepository> logProvider;

    @Inject
    public AdminArticleService(
            Supplier<ArticleAssistant> assistant,
            AccountPermissionService permissionService,
            AccountIdentityService identities,
            UserPreferenceService userPreferenceService,
            ArticleVersionService articleVersionService,
            ArticlePinningService articlePinningService,
            jakarta.inject.Provider<AdminLogRepository> logProvider,
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
            Log row = permissionService.article(access, logId, false);
            var revision = articleVersionService.beforeUpdate(row);
            var changes = references.replace(row, from, to, prefix);
            if (changes.isEmpty()) return 0;
            int changedFields = changes.size();
            changes.applyTo(row);
            if (changes.getContent() != null) {
                row.setPlainContent(ParseUtil.getPlainSearchText(row.getContent()));
            }
            commitUpdate(row, revision, Math.toIntExact(row.getVersion()), actor.getUserId());
            return changedFields;
        } finally {
            lock.unlock();
        }
    }

    private void commitUpdate(
            Log row,
            ArticleVersionService.Revision revision,
            Integer expectedVersion,
            Integer userId)
            throws SQLException {
        Long dbVersion = row.getVersion();
        if (expectedVersion == null
                || dbVersion.longValue() != expectedVersion
                || expectedVersion < 0
                || expectedVersion == Integer.MAX_VALUE) {
            throw new UpdateArticleExpireException();
        }
        try {
            if (logProvider.get().update(row)) {
                articleVersionService.recordReversePatch(revision, row, userId);
            }
        } catch (OptimisticLockException conflict) {
            throw new UpdateArticleExpireException();
        }
    }

    public CreateOrUpdateArticleResponse create(
            AccountAccess actor, CreateArticleRequest createArticleRequest) throws SQLException {
        CreateOrUpdateArticleResponse response = save(actor, createArticleRequest);
        if (!createArticleRequest.isPreserveDraftAiMessages()) {
            Objects.requireNonNullElse(assistant.get(), ArticleAssistant.NONE)
                    .articleCreated(response.getLogId(), actor.getUserId());
        }
        return response;
    }

    public CreateOrUpdateArticleResponse update(
            AccountAccess actor, UpdateArticleRequest updateArticleRequest) throws SQLException {
        return save(actor, updateArticleRequest);
    }

    private CreateOrUpdateArticleResponse save(
            AccountAccess actor, CreateArticleRequest createArticleRequest) throws SQLException {
        if (Objects.isNull(createArticleRequest)
                || StringUtils.isEmpty(createArticleRequest.getTitle())) {
            throw new ArticleMissingTitleException();
        }
        if (Objects.isNull(createArticleRequest.getTypeId())
                || createArticleRequest.getTypeId() < 1) {
            throw new ArticleMissingTypeException();
        }
        createArticleRequest.validateArrangePlugin();
        Integer logId =
                (createArticleRequest instanceof UpdateArticleRequest
                        ? ((UpdateArticleRequest) createArticleRequest).getLogId()
                        : null);
        Lock lock = lockForWrite(logId);
        try {
            AccountAccess access = identities.revalidate(actor);
            permissionService.require(
                    access,
                    createArticleRequest instanceof UpdateArticleRequest
                            ? AccountAction.ARTICLE_UPDATE
                            : AccountAction.ARTICLE_CREATE);
            if ("contributor".equals(access.getRole()) && !createArticleRequest.isRubbish())
                throw new PermissionErrorException();
            if (!createArticleRequest.isRubbish()
                    && !createArticleRequest.isPrivacy()
                    && !access.canPublish()) throw new PermissionErrorException();
            Log row = null;
            if (createArticleRequest instanceof UpdateArticleRequest) {
                row =
                        permissionService.article(
                                access,
                                ((UpdateArticleRequest) createArticleRequest).getLogId(),
                                false);
                if ("contributor".equals(access.getRole())
                        && !Boolean.TRUE.equals(row.getRubbish()))
                    throw new PermissionErrorException();
                if (AccountPermissionService.isPublic(row) && !access.canPublish())
                    throw new PermissionErrorException();
            }
            Log trackedRow = row;
            boolean clearsPublicState =
                    AccountPermissionService.isPublic(row)
                            && (createArticleRequest.isPrivacy()
                                    || createArticleRequest.isRubbish());
            if (clearsPublicState) {
                return articlePinningService.withOrderLock(
                        () -> persistArticle(actor, createArticleRequest, trackedRow, true));
            }
            return persistArticle(actor, createArticleRequest, trackedRow, false);
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
            Log row = permissionService.article(access, logId, false);
            deleted =
                    articlePinningService.withOrderLock(
                            () -> {
                                this.logProvider.get().delete(row);
                                this.articlePinningService.normalizeOrderLocked();
                                return true;
                            });
        } finally {
            lock.unlock();
        }
        if (deleted) {
            Objects.requireNonNullElse(assistant.get(), ArticleAssistant.NONE)
                    .articleDeleted(logId);
        }
        return deleted;
    }

    private CreateOrUpdateArticleResponse persistArticle(
            AccountAccess actor, CreateArticleRequest request, Log row, boolean normalizePinning)
            throws SQLException {
        boolean wasPublic = AccountPermissionService.isPublic(row);
        if (row != null) {
            // History, authorization and the optimistic write share this loaded entity.
            var revision = articleVersionService.beforeUpdate(row);
            applyRequest(row, actor, request, normalizePinning);
            commitUpdate(
                    row,
                    revision,
                    ((UpdateArticleRequest) request).getVersion(),
                    actor.getUserId());
            if (normalizePinning) articlePinningService.normalizeOrderLocked();
        } else {
            row = new Log();
            row.setId(logProvider.get().findMaxId() + 1);
            row.setReleaseTime(new Timestamp(System.currentTimeMillis()));
            row.setUserId(actor.getUserId());
            applyRequest(row, actor, request, true);
            logProvider.get().insert(row);
        }
        CreateOrUpdateArticleResponse response = new CreateOrUpdateArticleResponse();
        response.setLogId(row.getId());
        response.setVersion(Math.toIntExact(row.getVersion()));
        response.setPrivacy(request.isPrivacy());
        response.setRubbish(request.isRubbish());
        response.setPublicCacheRefreshRequired(wasPublic || AccountPermissionService.isPublic(row));
        return response;
    }

    private void applyRequest(
            Log row, AccountAccess actor, CreateArticleRequest request, boolean normalizePinning)
            throws SQLException {
        row.setContent(request.getContent());
        // Null/omitted values preserve arrangements selected by older clients.
        if (request.getArrange_plugin() != null) row.setArrangePlugin(request.getArrange_plugin());
        row.setTitle(Jsoup.clean(request.getTitle(), Safelist.basic()));
        row.setKeywords(
                StringUtils.isNotEmpty(request.getKeywords())
                        ? Jsoup.clean(request.getKeywords(), Safelist.basic())
                        : null);
        row.setMarkdown(request.getMarkdown());
        row.setTypeId(Math.toIntExact(request.getTypeId()));
        row.setCanComment(request.isCanComment());
        row.setRecommended(request.isRecommended());
        row.setPrivacy(request.isPrivacy());
        row.setRubbish(request.isRubbish());
        if (normalizePinning && (request.isPrivacy() || request.isRubbish())) row.setSticky(0);
        row.setThumbnail(StringUtils.isEmpty(request.getThumbnail()) ? "" : request.getThumbnail());
        String digest =
                Jsoup.clean(
                        ObjectHelpers.requireNonNullElse(request.getDigest(), ""),
                        Safelist.basicWithImages());
        if (StringUtils.isEmpty(digest) && !request.isRubbish()) {
            long autoSize = userPreferenceService.effective(actor).editor.autoDigestLength;
            if (autoSize < 0) row.setDigest(row.getContent());
            else if (autoSize == 0) row.setDigest("");
            else row.setDigest(ParseUtil.autoDigest(row.getContent(), (int) autoSize));
        } else {
            row.setDigest(digest);
        }
        row.setPlainContent(ParseUtil.getPlainSearchText(row.getContent()));
        row.setEditorType(request.getEditorType());
        String alias =
                StringUtils.isEmpty(request.getAlias())
                        ? Long.toString(row.getId())
                        : request.getAlias();
        row.setAlias(
                Jsoup.clean(alias.trim().replace(" ", "-").replace(".", "-"), Safelist.basic()));
    }
}
