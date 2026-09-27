package com.zrlog.admin.business.rest.request;

import com.hibegin.common.util.StringUtils;
import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;

public class PersonalDataPreviewRequest implements Validator {

    private static final int MAX_QUERY_LENGTH = 160;

    private String query;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    @Override
    public void doValid() {
        if (StringUtils.isEmpty(query) || query.trim().isEmpty()) {
            throw new ArgsException("query");
        }
        if (query.trim().length() > MAX_QUERY_LENGTH) {
            throw new ArgsException("query");
        }
    }

    @Override
    public void doClean() {
        query = query.trim();
        if (StringUtils.isEmpty(query) || query.trim().isEmpty()) {
            throw new ArgsException("query");
        }
    }
}
