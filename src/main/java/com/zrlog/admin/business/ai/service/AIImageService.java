package com.zrlog.admin.business.ai.service;

import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.ai.exception.*;
import com.zrlog.admin.business.rest.base.AIWebSiteInfo;
import com.zrlog.admin.business.rest.base.ArticleEditWebSiteInfo;
import com.zrlog.admin.business.rest.request.ApplyArticleCoverRequest;
import com.zrlog.admin.business.rest.request.GenerateArticleFieldRequest;
import com.zrlog.admin.business.rest.response.GenerateArticleCoverResponse;
import com.zrlog.admin.business.rest.response.UploadFileResponse;
import com.zrlog.admin.business.ai.model.AIModelCapability;
import com.zrlog.admin.business.ai.prompt.AIPromptVO;
import com.zrlog.admin.business.exception.AbstractAdminBusinessException;
import com.zrlog.admin.business.service.DbFileService;
import com.zrlog.admin.business.service.UploadService;
import com.zrlog.admin.business.service.WebSiteService;
import com.zrlog.admin.web.token.AdminTokenThreadLocal;
import com.zrlog.common.exception.ArgsException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.util.*;

public class AIImageService extends AIService {

    public AIImageService() {
    }

    AIImageService(HttpClient client) {
        super(client);
    }

    public GenerateArticleCoverResponse generateArticleCover(GenerateArticleFieldRequest generateRequest)
            throws IOException, InterruptedException, SQLException {
        AIWebSiteInfo info = new WebSiteService().ai();
        checkImageConfig(info);
        ArticleEditWebSiteInfo articleEdit = new WebSiteService().articleEditWebSiteInfo();
        String coverAspectRatio = articleEdit.getArticle_cover_aspect_ratio();
        String prompt = buildArticleCoverPrompt(generateRequest, coverAspectRatio);
        ImageResult imageResult = requestImageByAspectRatio(info, prompt, coverAspectRatio);

        GenerateArticleCoverResponse response = new GenerateArticleCoverResponse();
        response.setMimeType(imageResult.mimeType);
        response.setExtension(imageResult.extension);
        // 确定存储目录，统一使用虚拟临时目录
        String fileName = "article-cover-" + System.currentTimeMillis() + "." + imageResult.extension;
        // 使用统一的路径生成工具
        String relPath = AdminConstants.ADMIN_DB_ATTACHED_TMP + "/ai/" + fileName;
        UploadFileResponse dbFile = new DbFileService().toDbFile(relPath, imageResult.bytes);
        response.setUrl(dbFile.getUrl());
        return response;
    }

    public UploadFileResponse applyArticleCover(ApplyArticleCoverRequest applyRequest, HttpRequest request,
                                                Long articleId) throws SQLException {
        UploadFileResponse uploadFileResponse;
        if (applyRequest.getDataUrl().startsWith(AdminConstants.ADMIN_DB_ATTACHED_TMP)
                || applyRequest.getDataUrl().startsWith("data:")) {
            DecodedImage decodedImage = decodeDataUrl(applyRequest.getDataUrl(), applyRequest.getExtension());
            uploadFileResponse = new UploadService().saveThumbnailBytes(decodedImage.bytes, decodedImage.extension, request,
                    AdminTokenThreadLocal.getUser());
        } else {
            uploadFileResponse = new UploadFileResponse(applyRequest.getDataUrl());
        }
        if (StringUtils.isNotEmpty(applyRequest.getMessageId())) {
            new WebSiteService().updateAIMessagePayload(articleId, applyRequest.getMessageId(), "cover",
                    Map.of("url", uploadFileResponse.getUrl()));
        }
        return uploadFileResponse;
    }

    private void checkImageConfig(AIWebSiteInfo info) {
        if (info.getAi_image_provider() == null) {
            throw new ArgsException("ai_image_provider");
        }
        if (StringUtils.isEmpty(info.getAi_image_model())) {
            throw new ArgsException("ai_image_model");
        }
        if (StringUtils.isEmpty(info.getAi_image_api_key())) {
            throw new ArgsException("ai_image_api_key");
        }
        if (StringUtils.isEmpty(info.getAi_image_provider().getImageGenerationBaseUrl())) {
            throw new UnsupportedAIImageGenerationException("provider: " + info.getAi_image_provider());
        }
        boolean supported = info.getAi_image_provider().getModelEntries().stream()
                .anyMatch(model -> Objects.equals(model.getName(), info.getAi_image_model())
                        && model.supports(AIModelCapability.IMAGE_GENERATION));
        if (!supported) {
            throw new UnsupportedAIImageGenerationException("model: " + info.getAi_image_model());
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
        Map<String, Object> params = new HashMap<>();
        params.put("model", info.getAi_image_model());
        params.put("prompt", prompt);
        params.put("n", 1);
        params.put("size", size);

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(info.getAi_image_provider().getImageGenerationBaseUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + info.getAi_image_api_key())
                .header("Accept-Encoding", "identity")
                .POST(BodyPublishers.ofString(gson.toJson(params)))
                .build();
        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new AIRequestException(buildProviderErrorDetail(response.statusCode(), response.body()));
        }
        Map responseMap = gson.fromJson(response.body(), Map.class);
        List<Map<String, Object>> data = (List<Map<String, Object>>) responseMap.get("data");
        if (data == null || data.isEmpty()) {
            throw new AIResponseException("image data is empty");
        }
        Map<String, Object> first = data.get(0);
        Object b64Json = first.get("b64_json");
        if (b64Json != null && StringUtils.isNotEmpty(b64Json.toString())) {
            return new ImageResult(Base64.getDecoder().decode(b64Json.toString()), "png", "image/png");
        }
        Object url = first.get("url");
        if (url != null && StringUtils.isNotEmpty(url.toString())) {
            return downloadImage(url.toString());
        }
        throw new AIResponseException("image data has no b64_json or url");
    }

    private String resolveImageRequestSize(String coverAspectRatio) {
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

    private String toExtension(String contentType, String url) {
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

    private String toMimeType(String extension, String fallbackContentType) {
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


    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private DecodedImage decodeDataUrl(String dataUrl, String fallbackExtension) {
        if (dataUrl.startsWith(AdminConstants.ADMIN_DB_ATTACHED_TMP)) {
            return new DecodedImage(new DbFileService().loadDbFile(dataUrl),
                    StringUtils.isNotEmpty(fallbackExtension) ? fallbackExtension : toExtension("", dataUrl));
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

    private static class DecodedImage {
        private final byte[] bytes;
        private final String extension;

        private DecodedImage(byte[] bytes, String extension) {
            this.bytes = bytes;
            this.extension = extension;
        }
    }
}
