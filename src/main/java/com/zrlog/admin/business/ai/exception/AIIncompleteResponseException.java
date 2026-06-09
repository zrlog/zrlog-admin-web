package com.zrlog.admin.business.ai.exception;

import com.zrlog.admin.business.exception.AbstractAdminBusinessException;

public class AIIncompleteResponseException extends AbstractAdminBusinessException {

    private final String finishReason;
    private final Integer continuationRounds;

    public AIIncompleteResponseException(String finishReason) {
        this(finishReason, null);
    }

    public AIIncompleteResponseException(String finishReason, Integer continuationRounds) {
        super("admin.ai.error.incomplete", finishReason);
        this.finishReason = finishReason;
        this.continuationRounds = continuationRounds;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public Integer getContinuationRounds() {
        return continuationRounds;
    }

    @Override
    public int getError() {
        return 9037;
    }
}
