package com.zrlog.admin.business.rest.request;

import com.hibegin.common.util.StringUtils;
import com.zrlog.common.exception.ArgsException;

import java.util.Locale;

public class MoveArticlePinningRequest extends ArticlePinningRequest {

    private String direction;

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    @Override
    public void doValid() {
        super.doValid();
        if (StringUtils.isEmpty(direction)) {
            throw new ArgsException("direction");
        }
        String normalized = direction.toUpperCase(Locale.ROOT);
        if (!"UP".equals(normalized) && !"DOWN".equals(normalized)) {
            throw new ArgsException("direction");
        }
    }
}
