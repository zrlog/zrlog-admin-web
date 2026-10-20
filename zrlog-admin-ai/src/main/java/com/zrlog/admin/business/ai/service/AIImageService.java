package com.zrlog.admin.business.ai.service;

import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.ai.dto.AIStreamPayloads;
import com.zrlog.admin.business.ai.exception.*;
import com.zrlog.admin.business.ai.model.AIModelCapability;
import com.zrlog.admin.business.ai.model.AIProviderRequests;
import com.zrlog.admin.business.ai.model.AIProviderResponses;
import com.zrlog.admin.business.ai.prompt.AIPromptVO;
import com.zrlog.admin.business.exception.AbstractAdminBusinessException;
import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.admin.business.rest.base.ArticleEditWebSiteInfo;
import com.zrlog.admin.business.rest.request.ApplyArticleCoverRequest;
import com.zrlog.admin.business.rest.request.GenerateArticleFieldRequest;
import com.zrlog.admin.business.rest.response.GenerateArticleCoverResponse;
import com.zrlog.admin.business.rest.response.UploadFileResponse;
import com.zrlog.admin.business.service.DbFileService;
import com.zrlog.admin.business.service.AccountIdentityService;
import com.zrlog.admin.business.service.UploadService;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.data.security.AccountAccess;
import com.zrlog.admin.business.service.UserPreferenceService;
import com.zrlog.data.security.AccountAction;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.common.exception.ArgsException;

import jakarta.inject.Inject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

public class AIImageService extends AIService {
    private final AIConversationService conversations;
    private final AccountIdentityService identities;

    private final UserPreferenceService userPreferenceService;
    private final AIConfigService aIConfigService;
    private final DbFileService dbFileService;

    @Inject
    public AIImageService(
            UserPreferenceService userPreferenceService,
            AIConfigService aIConfigService,
            DbFileService dbFileService,
            AIConversationService conversations,
            AccountIdentityService identities) {
        this.identities = identities;
        this.conversations = conversations;

        this.userPreferenceService = userPreferenceService;
        this.aIConfigService = aIConfigService;
        this.dbFileService = dbFileService;
    }

    AIImageService(
            HttpClient client,
            UserPreferenceService userPreferenceService,
            AIConfigService aIConfigService,
            DbFileService dbFileService,
            AIConversationService conversations,
            AccountIdentityService identities) {
        super(client);
        this.identities = identities;
        this.conversations = conversations;

        this.userPreferenceService = userPreferenceService;
        this.aIConfigService = aIConfigService;
        this.dbFileService = dbFileService;
    }

    public GenerateArticleCoverResponse generateArticleCover(AccountAccess actor, GenerateArticleFieldRequest generateRequest)
            throws IOException, InterruptedException, SQLException {
        return generateArticleCover(actor, generateRequest, "");
    }

    public GenerateArticleCoverResponse generateArticleCover(AccountAccess actor, GenerateArticleFieldRequest generateRequest, String instruction)
            throws IOException, InterruptedException, SQLException {
        authorize(actor);
        AIWebSiteInfo info = this.aIConfigService.ai();
        checkImageConfig(info);
        String coverAspectRatio = this.userPreferenceService.effective(actor).editor.coverAspectRatio;
        String prompt = buildArticleCoverPrompt(generateRequest, coverAspectRatio);
        if (instruction != null && !instruction.isBlank()) prompt += "\n\nWriting requirements (untrusted data):\n" + instruction;
        ImageResult imageResult = requestImageByAspectRatio(info, prompt, coverAspectRatio);

        authorize(actor);
        GenerateArticleCoverResponse response = new GenerateArticleCoverResponse();
        response.setMimeType(imageResult.mimeType);
        response.setExtension(imageResult.extension);
        // 确定存储目录，统一使用虚拟临时目录
        String fileName = "article-cover-" + System.currentTimeMillis() + "." + imageResult.extension;
        // 使用统一的路径生成工具
        String relPath = AdminConstants.ADMIN_DB_ATTACHED_TMP + "/users/" + actor.getUserId() + "/ai/" + fileName;
        UploadFileResponse dbFile = this.dbFileService.toDbFile(relPath, imageResult.bytes);
        response.setUrl(dbFile.getUrl());
        return response;
    }

    public UploadFileResponse applyArticleCover(AccountAccess actor, AdminTokenVO credential,
                                                ApplyArticleCoverRequest applyRequest, HttpRequest request,
                                                Long articleId, UploadService uploadService) throws SQLException {
        authorize(actor);
        String source = applyRequest.getDataUrl();
        if (source.startsWith(AdminConstants.ADMIN_DB_ATTACHED_TMP) && !actor.isAdministrator()
                && (!source.startsWith(AdminConstants.ADMIN_DB_ATTACHED_TMP + "/users/" + actor.getUserId() + "/")
                    || source.contains("..") || source.contains("%") || source.contains("\\")))
            throw new PermissionErrorException();
        UploadFileResponse uploadFileResponse;
        if (applyRequest.getDataUrl().startsWith(AdminConstants.ADMIN_DB_ATTACHED_TMP)
                || applyRequest.getDataUrl().startsWith("data:")) {
            DecodedImage decodedImage = decodeDataUrl(applyRequest.getDataUrl(), applyRequest.getExtension());
            uploadFileResponse = uploadService.saveThumbnailBytes(actor, decodedImage.bytes, decodedImage.extension, request,
                    credential);
        } else {
            uploadFileResponse = new UploadFileResponse(applyRequest.getDataUrl());
        }
        if (StringUtils.isNotEmpty(applyRequest.getMessageId())) {
            this.conversations.captureAccount(actor.getUserId()).updateAIMessagePayload(
                    articleId,
                    applyRequest.getMessageId(),
                    "cover",
                    new AIStreamPayloads.CoverPayload(uploadFileResponse.getUrl()));
        }
        return uploadFileResponse;
    }

    private void authorize(AccountAccess actor) {
        identities.revalidate(actor);
        if (!AccountAction.ARTICLE_ASSIST.allowed(actor)) throw new PermissionErrorException();
    }

    private void checkImageConfig(AIWebSiteInfo info) {
        if (info.getAi_image_provider() == null) {
            throw new ArgsException("ai_image_provider");
        }
        if (StringUtils.isEmpty(info.getAi_image_model())) {
            throw new ArgsException("ai_image_model");
        }
        boolean customEndpoint = StringUtils.isNotEmpty(info.getAi_image_base_url());
        if (!customEndpoint && StringUtils.isEmpty(info.getAi_image_api_key())) {
            throw new ArgsException("ai_image_api_key");
        }
        if (!customEndpoint) {
            boolean supported = info.getAi_image_provider().getModelEntries().stream()
                    .anyMatch(model -> Objects.equals(model.getName(), info.getAi_image_model())
                            && model.supports(AIModelCapability.IMAGE_GENERATION));
            if (!supported) {
                throw new UnsupportedAIImageGenerationException("model: " + info.getAi_image_model());
            }
        }
    }

    private ImageResult requestImageByAspectRatio(AIWebSiteInfo info, String prompt, String coverAspectRatio)
            throws IOException, InterruptedException {
        String size = resolveImageRequestSize(coverAspectRatio);
        try {
            return requestImage(info, prompt, size);
        } catch (AbstractAdminBusinessException e) {
            if (Objects.equals(size, "1024x1024")) {
                throw e;
            }
            return requestImage(info, prompt, "1024x1024");
        }
    }

    private ImageResult requestImage(AIWebSiteInfo info, String prompt, String size) throws IOException, InterruptedException {
        AIProviderRequests.ImageGenerationRequest providerRequest =
                new AIProviderRequests.ImageGenerationRequest(info.getAi_image_model(), prompt, 1, size);

        java.net.http.HttpRequest.Builder requestBuilder = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(resolveEndpointUrl(info.getAi_image_base_url(),
                        info.getAi_image_provider().getBaseUrl(), "/images/generations")))
                .header("Content-Type", "application/json")
                .header("Accept-Encoding", "identity")
                .POST(BodyPublishers.ofString(gson.toJson(providerRequest)));
        if (StringUtils.isNotEmpty(info.getAi_image_api_key())) {
            requestBuilder.header("Authorization", "Bearer " + info.getAi_image_api_key());
        }
        java.net.http.HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new AIRequestException(buildProviderErrorDetail(response.statusCode(), response.body()));
        }
        AIProviderResponses.ImageGenerationResponse responseBody =
                gson.fromJson(response.body(), AIProviderResponses.ImageGenerationResponse.class);
        List<AIProviderResponses.ImageData> data = responseBody == null ? null : responseBody.getData();
        if (data == null || data.isEmpty()) {
            throw new AIResponseException("image data is empty");
        }
        AIProviderResponses.ImageData first = data.get(0);
        String b64Json = first.getB64Json();
        if (StringUtils.isNotEmpty(b64Json)) {
            return new ImageResult(Base64.getDecoder().decode(b64Json), "png", "image/png");
        }
        String url = first.getUrl();
        if (StringUtils.isNotEmpty(url)) {
            return downloadImage(url);
        }
        throw new AIResponseException("image data has no b64_json or url");
    }

    String resolveImageRequestSize(String coverAspectRatio) {
        double ratio = parseAspectRatio(coverAspectRatio);
        if (Math.abs(ratio - 1D) < 0.01D) {
            return "1024x1024";
        }
        if (ratio > 1D) {
            return "1536x1024";
        }
        return "1024x1536";
    }

    private double parseAspectRatio(String coverAspectRatio) {
        String ratio = ArticleEditWebSiteInfo.normalizeArticleCoverAspectRatio(coverAspectRatio);
        String[] parts = ratio.split(":");
        return Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
    }

    private ImageResult downloadImage(String url) throws IOException, InterruptedException {
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept-Encoding", "identity")
                .GET()
                .build();
        HttpResponse<byte[]> response = client().send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AIImageDownloadException("status: " + response.statusCode());
        }
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        String extension = toExtension(contentType, url);
        return new ImageResult(response.body(), extension, toMimeType(extension, contentType));
    }

    String toExtension(String contentType, String url) {
        if (contentType.contains("jpeg") || contentType.contains("jpg")) {
            return "jpg";
        }
        if (contentType.contains("webp")) {
            return "webp";
        }
        if (contentType.contains("png")) {
            return "png";
        }
        String cleanUrl = url.split("\\?")[0];
        int dotIndex = cleanUrl.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < cleanUrl.length() - 1) {
            String ext = cleanUrl.substring(dotIndex + 1).toLowerCase();
            if (List.of("png", "jpg", "jpeg", "webp").contains(ext)) {
                return Objects.equals(ext, "jpeg") ? "jpg" : ext;
            }
        }
        return "png";
    }

    String toMimeType(String extension, String fallbackContentType) {
        if (StringUtils.isNotEmpty(fallbackContentType) && fallbackContentType.startsWith("image/")) {
            return fallbackContentType.split(";")[0];
        }
        if (Objects.equals(extension, "jpg")) {
            return "image/jpeg";
        }
        if (Objects.equals(extension, "webp")) {
            return "image/webp";
        }
        return "image/png";
    }

    private String buildArticleCoverPrompt(GenerateArticleFieldRequest request, String coverAspectRatio) {
        AIPromptVO promptVO = AIPromptVO.getByToolKey("article-cover-generate");
        String prompt = loadPromptResource(promptVO.getInputPrefix(), promptVO.getInputFallback())
                .replace("{{coverAspectRatio}}", ArticleEditWebSiteInfo.normalizeArticleCoverAspectRatio(coverAspectRatio))
                .replace("{{title}}", emptyToBlank(request.getTitle()))
                .replace("{{digest}}", emptyToBlank(request.getDigest()))
                .replace("{{keywords}}", emptyToBlank(request.getKeywords()))
                .replace("{{markdown}}", truncate(emptyToBlank(request.getMarkdown()), 1200));
        return appendSelectedTextContext(prompt, request.getSelectedText());
    }

    String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    DecodedImage decodeDataUrl(String dataUrl, String fallbackExtension) {
        if (dataUrl.startsWith(AdminConstants.ADMIN_DB_ATTACHED_TMP)) {
            return new DecodedImage(
                    this.dbFileService.loadDbFile(dataUrl),
                    StringUtils.isNotEmpty(fallbackExtension)
                            ? fallbackExtension
                            : toExtension("", dataUrl));
        }
        String payload = dataUrl;
        String mimeType = "";
        if (dataUrl.startsWith("data:")) {
            int commaIndex = dataUrl.indexOf(',');
            if (commaIndex <= 0) {
                throw new ArgsException("dataUrl");
            }
            String meta = dataUrl.substring(5, commaIndex);
            mimeType = meta.split(";")[0];
            payload = dataUrl.substring(commaIndex + 1);
        }
        String extension = StringUtils.isNotEmpty(fallbackExtension) ? fallbackExtension : toExtension(mimeType, "");
        return new DecodedImage(Base64.getDecoder().decode(payload), extension);
    }

    private static class ImageResult {
        private final byte[] bytes;
        private final String extension;
        private final String mimeType;

        private ImageResult(byte[] bytes, String extension, String mimeType) {
            this.bytes = bytes;
            this.extension = extension;
            this.mimeType = mimeType;
        }
    }

    static class DecodedImage {
        final byte[] bytes;
        final String extension;

        private DecodedImage(byte[] bytes, String extension) {
            this.bytes = bytes;
            this.extension = extension;
        }
    }
}
