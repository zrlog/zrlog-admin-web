package com.zrlog.admin.business.service;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.hibegin.common.util.UrlEncodeUtils;
import com.zrlog.common.cache.dto.TypeDTO;
import com.zrlog.model.Type;

import java.sql.SQLException;

public class ArticleTypeService {


    public PageData<TypeDTO> find(String homeUrl, PageRequest page, boolean staticHtml) throws SQLException {
        PageData<TypeDTO> data = new Type().find(page);
        for (TypeDTO typeDTO : data.getRows()) {
            typeDTO.setAmount(typeDTO.getTypeamount());
            typeDTO.setUrl(homeUrl + UrlEncodeUtils.encodeUrl("sort/" + typeDTO.getAlias()) + (staticHtml ? ".html" : ""));
        }
        return data;
    }
}
