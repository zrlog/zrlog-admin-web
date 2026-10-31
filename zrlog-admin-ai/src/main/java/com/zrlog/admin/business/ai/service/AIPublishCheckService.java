package com.zrlog.admin.business.ai.service;

import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.ai.exception.AIMessageSaveException;
import com.zrlog.admin.business.rest.base.BlogWebSiteInfo;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.GenerateArticleFieldRequest;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.admin.business.service.AccountIdentityService;
import com.zrlog.admin.business.service.AccountPermissionService;
import com.zrlog.admin.business.service.MessageCenterOperationService;
import com.zrlog.admin.business.service.WebSiteService;
import com.zrlog.admin.util.AdminLanguageContext;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.data.security.AccountAction;
import com.zrlog.util.I18nUtil;
import jakarta.inject.Inject;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AIPublishCheckService {
    private static final Logger LOGGER = LoggerUtil.getLogger(AIPublishCheckService.class);
    private final AIConversationService conversations;
    private final AccountIdentityService identities;
    private final AccountPermissionService permissions;
    private final AIWritingSkillService.Factory writing;
    private final WebSiteService webSiteService;
    private final MessageCenterOperationService messageCenterOperationService;

    @Inject
    public AIPublishCheckService(
            WebSiteService webSiteService,
            MessageCenterOperationService messageCenterOperationService,
            AIWritingSkillService.Factory writing,
            AIConversationService conversations, AccountIdentityService identities, AccountPermissionService permissions) {
        this.identities = identities;
        this.permissions = permissions;
        this.conversations = conversations;

        this.writing = writing;

        this.webSiteService = webSiteService;
        this.messageCenterOperationService = messageCenterOperationService;
    }

    public PublishCheckTask startPublishCheck(AccountAccess actor, ArticleGlobalResponse detail,
                                              CreateArticleRequest body) {
        if (!(detail instanceof AIArticleGlobalResponse) || !Objects.equals(detail.getPublishCheckEnabled(), true)
                || !Objects.equals(((AIArticleGlobalResponse) detail).getAiConfigured(), true)) {
            return null;
        }
        identities.revalidate(actor);
        if (!AccountAction.ARTICLE_ASSIST.allowed(actor)) return null;
        Long articleId = Long.valueOf(detail.getArticle().getLogId());
        GenerateArticleFieldRequest context = publishCheckContext(body);
        PublishCheckPersistenceGuard guard = new PublishCheckPersistenceGuard();
        AIConversationService conversationStore = this.conversations.captureAccount(actor.getUserId());
        String language = I18nUtil.getCurrentLocale();
        CompletableFuture<PublishCheckResponse> future = CompletableFuture.supplyAsync(
                () -> {
                    try (AdminLanguageContext ignored = AdminLanguageContext.open(language)) {
                        return buildPublishCheckPayload(actor, articleId, context, guard, conversationStore);
                    }
                });
        return new PublishCheckTask(future, guard, articleId, context.getTitle());
    }

    public void fillPublishCheckContext(GenerateArticleFieldRequest context) {
        BlogWebSiteInfo blog = this.webSiteService.blogWebSiteInfo();
        context.setStaticSiteEnabled(blog.getGenerator_html_status());
        context.setStaticSitePluginEnabled(!StaticSitePlugin.isDisabled(Boolean.TRUE.equals(blog.getGenerator_html_status()), blog.getHost()));
    }

    public void recordPublishCheckError(Long articleId, String articleTitle, String messageKey) {
        try {
            this.messageCenterOperationService.recordPublishCheckError(
                    articleId, articleTitle, messageKey);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Record publish check error notice failed", e);
        }
    }

    private GenerateArticleFieldRequest publishCheckContext(CreateArticleRequest body) {
        GenerateArticleFieldRequest context = new GenerateArticleFieldRequest();
        context.setTitle(body.getTitle());
        context.setMarkdown(StringUtils.isNotEmpty(body.getMarkdown()) ? body.getMarkdown() : body.getContent());
        context.setDigest(body.getDigest());
        context.setKeywords(body.getKeywords());
        context.setAlias(body.getAlias());
        context.setThumbnail(body.getThumbnail());
        context.setTransparentPublish(body.isTransparentPublish());
        fillPublishCheckContext(context);
        return context;
    }

    PublishCheckResponse buildPublishCheckPayload(AccountAccess actor, Long articleId, GenerateArticleFieldRequest context,
                                                  PublishCheckPersistenceGuard guard, AIConversationService conversationStore) {
        try {
            authorize(actor, articleId);
            List<AIResponseEntry.AIContentEntry> messages =
                    writing.create(actor, conversationStore)
                            .runToolResponseWithoutPersistence(
                                    "publish-check", articleId, "publishCheck", context);
            return guard.commit(() -> {
                authorize(actor, articleId);
                if (!conversationStore.appendAIMessageEntries(messages, articleId)) {
                    throw new AIMessageSaveException();
                }
                AIResponseEntry.AIContentEntry assistant = messages.get(messages.size() - 1);
                Object payload = assistant.getPayload();
                PublishCheckResponse response = new PublishCheckResponse(
                        new PublishCheckToolPayload("publishCheck", payload), assistant.getContent(),
                        assistant.getMessageId(), messages);
                recordPublishCheckSuccess(articleId, context.getTitle(), payload);
                return response;
            });
        } catch (CancellationException e) {
            throw e;
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    private void authorize(AccountAccess actor, long articleId) throws java.sql.SQLException {
        identities.revalidate(actor);
        permissions.require(actor, AccountAction.ARTICLE_ASSIST);
        permissions.article(actor, articleId, false);
    }

    public void recordPublishCheckSuccess(Long articleId, String articleTitle, Object payload) {
        try {
            this.messageCenterOperationService.recordPublishCheckSuccess(
                    articleId, articleTitle, extractScore(payload), extractItemCount(payload));
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Record publish check operation notice failed", e);
        }
    }

    private Integer extractScore(Object checkPayload) {
        if (checkPayload instanceof ScoreArticleResponse) {
            return ((ScoreArticleResponse) checkPayload).getScore();
        }
        if (checkPayload instanceof Map) {
            return toInteger(((Map<?, ?>) checkPayload).get("score"));
        }
        return null;
    }

    private int extractItemCount(Object checkPayload) {
        if (checkPayload instanceof ScoreArticleResponse) {
            List<ScoreArticleResponse.ScoreItem> items = ((ScoreArticleResponse) checkPayload).getItems();
            return items == null ? 0 : items.size();
        }
        if (checkPayload instanceof Map) {
            Object items = ((Map<?, ?>) checkPayload).get("items");
            if (items instanceof Collection) {
                return ((Collection<?>) items).size();
            }
        }
        return 0;
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @FunctionalInterface
    public interface PublishCheckCommit {
        PublishCheckResponse commit() throws Exception;
    }

    public static final class PublishCheckPersistenceGuard {
        private boolean cancelled;
        private PublishCheckResponse committedResponse;

        public synchronized PublishCheckResponse commit(PublishCheckCommit commit) throws Exception {
            if (cancelled) {
                throw new CancellationException();
            }
            PublishCheckResponse response = commit.commit();
            committedResponse = response;
            return response;
        }

        public synchronized PublishCheckResponse cancelOrGetCommitted() {
            if (committedResponse != null) {
                return committedResponse;
            }
            cancelled = true;
            return null;
        }
    }

    public static final class PublishCheckTask {
        private final CompletableFuture<PublishCheckResponse> future;
        private final PublishCheckPersistenceGuard persistenceGuard;
        private final Long articleId;
        private final String articleTitle;

        public PublishCheckTask(CompletableFuture<PublishCheckResponse> future,
                                PublishCheckPersistenceGuard persistenceGuard,
                                Long articleId, String articleTitle) {
            this.future = future;
            this.persistenceGuard = persistenceGuard;
            this.articleId = articleId;
            this.articleTitle = articleTitle;
        }

        public CompletableFuture<PublishCheckResponse> getFuture() {
            return future;
        }

        public PublishCheckPersistenceGuard getPersistenceGuard() {
            return persistenceGuard;
        }

        public Long getArticleId() {
            return articleId;
        }

        public String getArticleTitle() {
            return articleTitle;
        }
    }
}
