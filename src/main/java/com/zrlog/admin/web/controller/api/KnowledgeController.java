package com.zrlog.admin.web.controller.api;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.zrlog.admin.business.ai.dto.AIStreamResponse;
import com.zrlog.admin.business.ai.service.AIKnowledgeService;
import com.zrlog.admin.business.knowledge.KnowledgeModels;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.common.controller.BaseController;
import com.zrlog.data.security.AccountAction;
import java.io.IOException;

public class KnowledgeController extends BaseController {
    @RequestMethod(method = HttpMethod.POST)
    @RequiresAction(value = AccountAction.ARTICLE_ASSIST, descriptionKey = "knowledge.chat")
    public void chat() throws IOException {
        java.nio.ByteBuffer body = request.getRequestBodyByteBuffer();
        if (body == null || body.remaining() > 256 * 1024) throw new com.zrlog.common.exception.ArgsException();
        AIStreamResponse stream = new AIKnowledgeService().start(getRequestBodyWithNullCheck(KnowledgeModels.ChatRequest.class));
        com.zrlog.admin.util.AdminSseEmitter.setHeaders(response);
        response.addHeader("Cache-Control", "no-store");
        response.write(stream.getInputStream(), stream.getStatusCode());
    }
}
