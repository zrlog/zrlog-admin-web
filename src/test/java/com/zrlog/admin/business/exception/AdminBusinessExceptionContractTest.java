package com.zrlog.admin.business.exception;

import com.zrlog.admin.business.ai.exception.AIImageDownloadException;
import com.zrlog.admin.business.ai.exception.AIIncompleteResponseException;
import com.zrlog.admin.business.ai.exception.AIMessageSaveException;
import com.zrlog.admin.business.ai.exception.AIPromptResourceException;
import com.zrlog.admin.business.ai.exception.AIRequestException;
import com.zrlog.admin.business.ai.exception.AIResponseException;
import com.zrlog.admin.business.ai.exception.UnsupportedAIImageGenerationException;
import com.zrlog.admin.business.ai.exception.UnsupportedAIToolException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AdminBusinessExceptionContractTest {

    @Test
    public void shouldExposeAdminBusinessErrorCodes() {
        assertAdminError(new AdminAuthException(), AdminErrorCode.AUTH_SESSION_EXPIRED);
        assertAdminError(new UserNameAndPasswordRequiredException(), AdminErrorCode.LOGIN_USERNAME_PASSWORD_REQUIRED);
        assertAdminError(new UserNameOrPasswordException(), AdminErrorCode.LOGIN_USERNAME_PASSWORD_INVALID);
        assertAdminError(new OldPasswordException(), AdminErrorCode.USER_OLD_PASSWORD_INVALID);
        assertAdminError(new MfaCodeRequiredException(), AdminErrorCode.MFA_CODE_REQUIRED);
        assertAdminError(new InvalidMfaCodeException(), AdminErrorCode.MFA_CODE_INVALID);
        assertAdminError(new PermissionErrorException(), AdminErrorCode.PERMISSION_DENIED);
        assertAdminError(new PasskeyVerificationException(), AdminErrorCode.PASSKEY_REQUEST_INVALID);
        assertAdminError(new PasskeyRequestBusyException(), AdminErrorCode.PASSKEY_REQUEST_BUSY);
        assertAdminError(new PasskeyLimitExceededException(), AdminErrorCode.PASSKEY_LIMIT_EXCEEDED);
        assertAdminError(new DeleteTypeException(), AdminErrorCode.ARTICLE_TYPE_DELETE_HAS_ARTICLE);
        assertAdminError(new ArticleMissingTitleException(), AdminErrorCode.ARTICLE_TITLE_REQUIRED);
        assertAdminError(new ArticleMissingTypeException(), AdminErrorCode.ARTICLE_TYPE_REQUIRED);
        assertAdminError(new StaticHtmlConfigException(), AdminErrorCode.WEBSITE_STATIC_HOST_REQUIRED);
        assertAdminError(new UpdateArticleExpireException(), AdminErrorCode.ARTICLE_UPDATE_EXPIRED);
        assertAdminError(new ArticlePinningNotAllowedException(), AdminErrorCode.ARTICLE_PINNING_NOT_ALLOWED);
        assertAdminError(new ArticleNotPinnedException(), AdminErrorCode.ARTICLE_NOT_PINNED);
    }

    @Test
    public void shouldExposeAiBusinessErrorCodesAndDetails() {
        assertAdminError(new AIRequestException("timeout"), AdminErrorCode.AI_REQUEST_FAILED);
        assertAdminError(new AIResponseException("bad-json"), AdminErrorCode.AI_RESPONSE_INVALID);
        assertAdminError(new AIPromptResourceException("missing"), AdminErrorCode.AI_PROMPT_RESOURCE_UNAVAILABLE);
        assertAdminError(new AIMessageSaveException(), AdminErrorCode.AI_MESSAGE_SAVE_FAILED);
        assertAdminError(new UnsupportedAIToolException("tool"), AdminErrorCode.AI_TOOL_UNSUPPORTED);
        assertAdminError(new UnsupportedAIImageGenerationException("model"), AdminErrorCode.AI_IMAGE_GENERATION_UNSUPPORTED);
        assertAdminError(new AIImageDownloadException("404"), AdminErrorCode.AI_IMAGE_DOWNLOAD_FAILED);

        AIIncompleteResponseException exception = new AIIncompleteResponseException("length", 2);
        assertAdminError(exception, AdminErrorCode.AI_RESPONSE_INCOMPLETE);
        assertEquals("length", exception.getFinishReason());
        assertEquals(Integer.valueOf(2), exception.getContinuationRounds());
        assertEquals(null, new AIIncompleteResponseException("content_filter").getContinuationRounds());
    }

    private static void assertAdminError(AbstractAdminBusinessException exception, AdminErrorCode errorCode) {
        assertEquals(errorCode.getLegacyCode(), exception.getError());
        assertEquals(errorCode.getCode(), exception.getErrorCode());
        assertNotNull(exception.getMessage());
        assertTrue(errorCode.getMessageKey().startsWith("admin."));
    }
}
