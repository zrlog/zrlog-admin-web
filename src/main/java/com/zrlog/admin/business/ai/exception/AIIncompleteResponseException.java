package com.zrlog.admin.business.ai.exception;

import com.zrlog.admin.business.exception.AbstractAdminBusinessException;
import com.zrlog.admin.business.exception.AdminErrorCode;

public class AIIncompleteResponseException extends AbstractAdminBusinessException {

    private final String finishReason;
    private final Integer continuationRounds;

    public AIIncompleteResponseException(String finishReason) {
        this(finishReason, null);
    }

    public AIIncompleteResponseException(String finishReason, Integer continuationRounds) {
        super(AdminErrorCode.AI_RESPONSE_INCOMPLETE, finishReason);
        this.finishReason = finishReason;
        this.continuationRounds = continuationRounds;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public Integer getContinuationRounds() {
        return continuationRounds;
    }

}
