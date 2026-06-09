package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.zrlog.admin.business.rest.response.MessageCenterStatusResponse;
import com.zrlog.model.WebSite;

import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MessageCenterStateService {

    private static final String STATUS_KEY = "message_center_status";
    private static final String STATUS_DB_KEY = WebsiteCacheService.KEY_PREFIX + STATUS_KEY;
    private static final int UPDATE_RETRY_COUNT = 3;
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = LoggerUtil.getLogger(MessageCenterStateService.class);
    private final WebsiteCacheService cacheService = new WebsiteCacheService();

    public MessageCenterStatusResponse current() {
        return parseStatus(cacheService.getString(STATUS_KEY));
    }

    public void markChanged() {
        update(current -> new MessageCenterStatusResponse(nextRevision(current.getRevision()), current.isHasUnread()));
    }

    public void markMayHaveUnread() {
        update(current -> new MessageCenterStatusResponse(nextRevision(current.getRevision()), true));
    }

    public void syncActual(boolean hasUnread) {
        syncActual(current().getRevision(), hasUnread);
    }

    public void syncActual(long observedRevision, boolean hasUnread) {
        update(current -> {
            if (!hasUnread && current.getRevision() != observedRevision) {
                return current;
            }
            if (current.isHasUnread() == hasUnread) {
                return current;
            }
            return new MessageCenterStatusResponse(current.getRevision(), hasUnread);
        });
    }

    private long nextRevision(long currentRevision) {
        long now = System.currentTimeMillis();
        return now > currentRevision ? now : currentRevision + 1;
    }

    private void update(Function<MessageCenterStatusResponse, MessageCenterStatusResponse> updater) {
        for (int i = 0; i < UPDATE_RETRY_COUNT; i++) {
            String raw = cacheService.getString(STATUS_KEY);
            MessageCenterStatusResponse current = parseStatus(raw);
            MessageCenterStatusResponse next = updater.apply(current);
            if (sameStatus(current, next)) {
                return;
            }
            if (writeIfUnchanged(raw, next)) {
                return;
            }
        }
        MessageCenterStatusResponse current = current();
        MessageCenterStatusResponse next = updater.apply(current);
        if (!sameStatus(current, next)) {
            cacheService.putJson(STATUS_KEY, next);
        }
    }

    private boolean writeIfUnchanged(String expectedRaw, MessageCenterStatusResponse next) {
        if (StringUtils.isEmpty(expectedRaw)) {
            return cacheService.putJson(STATUS_KEY, next);
        }
        try {
            return new WebSite().execute(
                    "update website set value=? where name=? and value=?",
                    GSON.toJson(next),
                    STATUS_DB_KEY,
                    expectedRaw
            );
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Update message center status failed", e);
            return false;
        }
    }

    private MessageCenterStatusResponse parseStatus(String raw) {
        if (StringUtils.isEmpty(raw)) {
            return new MessageCenterStatusResponse(0, false);
        }
        try {
            MessageCenterStatusResponse status = GSON.fromJson(raw, MessageCenterStatusResponse.class);
            return status == null ? new MessageCenterStatusResponse(0, false) : status;
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Read message center status failed", e);
            return new MessageCenterStatusResponse(0, false);
        }
    }

    private boolean sameStatus(MessageCenterStatusResponse current, MessageCenterStatusResponse next) {
        return current.getRevision() == next.getRevision()
                && Objects.equals(current.isHasUnread(), next.isHasUnread());
    }
}
