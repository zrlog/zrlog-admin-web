package com.zrlog.admin.business.ai.service;

import com.zrlog.admin.business.service.WebSiteService;
import com.zrlog.admin.business.service.MessageCenterOperationService;
import com.zrlog.admin.business.rest.response.AIArticleGlobalResponse;

import com.zrlog.admin.business.ai.service.AIConversationService;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.ai.exception.AIMessageSaveException;
import com.zrlog.admin.business.ai.service.AIWritingSkillService;
import com.zrlog.admin.business.rest.base.BlogWebSiteInfo;
import com.zrlog.admin.business.rest.request.CreateArticleRequest;
import com.zrlog.admin.business.rest.request.GenerateArticleFieldRequest;
import com.zrlog.admin.business.rest.response.AIResponseEntry;
import com.zrlog.admin.business.rest.response.ArticleGlobalResponse;
import com.zrlog.admin.business.rest.response.PublishCheckResponse;
import com.zrlog.admin.business.rest.response.PublishCheckToolPayload;
import com.zrlog.business.plugin.StaticSitePlugin;

import java.util.*;
import com.zrlog.admin.business.rest.response.ScoreArticleResponse;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AIPublishCheckService {

    private static final Logger LOGGER = LoggerUtil.getLogger(AIPublishCheckService.class);

    public PublishCheckTask startPublishCheck(ArticleGlobalResponse detail,
                                              CreateArticleRequest body) {
        if (!(detail instanceof AIArticleGlobalResponse) || !Objects.equals(detail.getPublishCheckEnabled(), true)
                || !Objects.equals(((AIArticleGlobalResponse) detail).getAiConfigured(), true)) {
            return null;
        }
        Long articleId = Long.valueOf(detail.getArticle().getLogId());
        GenerateArticleFieldRequest context = publishCheckContext(body);
        PublishCheckPersistenceGuard guard = new PublishCheckPersistenceGuard();
        AIConversationService conversationStore = new AIConversationService().captureAccount();
        CompletableFuture<PublishCheckResponse> future = CompletableFuture.supplyAsync(
                () -> buildPublishCheckPayload(articleId, context, guard, conversationStore));
        return new PublishCheckTask(future, guard, articleId, context.getTitle());
    }

    public void fillPublishCheckContext(GenerateArticleFieldRequest context) {
        BlogWebSiteInfo blog = new WebSiteService().blogWebSiteInfo();
        context.setStaticSiteEnabled(blog.getGenerator_html_status());
        context.setStaticSitePluginEnabled(!StaticSitePlugin.isDisabled());
    }

    public void recordPublishCheckError(Long articleId, String articleTitle, String message) {
        try {
            new MessageCenterOperationService().recordPublishCheckError(articleId, articleTitle, message);
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

    private PublishCheckResponse buildPublishCheckPayload(Long articleId, GenerateArticleFieldRequest context,
                                                          PublishCheckPersistenceGuard guard, AIConversationService conversationStore) {
        try {
            List<AIResponseEntry.AIContentEntry> messages = new AIWritingSkillService(conversationStore)
                    .runToolResponseWithoutPersistence("publish-check", articleId, "publishCheck", context);
            return guard.commit(() -> {
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

    public void recordPublishCheckSuccess(Long articleId, String articleTitle, Object payload) {
        try {
            new MessageCenterOperationService().recordPublishCheckSuccess(articleId, articleTitle, extractScore(payload), extractItemCount(payload));
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
