package com.zrlog.admin.business.service;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.hibegin.common.util.UrlEncodeUtils;
import com.zrlog.common.cache.dto.TypeDTO;
import com.zrlog.admin.business.exception.DeleteTypeException;
import com.zrlog.admin.business.rest.request.CreateTypeRequest;
import com.zrlog.admin.business.rest.request.UpdateTypeRequest;
import com.zrlog.model.Type;
import com.zrlog.model.Log;

import java.sql.SQLException;
import java.util.Objects;

public class ArticleTypeService {

    public boolean delete(int typeId) throws SQLException {
        if (new Log().countByTypeId(typeId) > 0) {
            throw new DeleteTypeException();
        }
        return new Type().deleteById(typeId);
    }

    public boolean add(CreateTypeRequest request) throws SQLException {
        return new Type().set("typeName", request.getTypeName())
                .set("alias", request.getAlias())
                .set("remark", Objects.requireNonNullElse(request.getRemark(), ""))
                .save();
    }

    public boolean update(UpdateTypeRequest request) throws SQLException {
        return new Type().set("typeName", request.getTypeName())
                .set("alias", request.getAlias())
                .set("remark", Objects.requireNonNullElse(request.getRemark(), ""))
                .updateById(request.getId());
    }


    public PageData<TypeDTO> find(String homeUrl, PageRequest page, boolean staticHtml) throws SQLException {
        PageData<TypeDTO> data = new Type().find(page);
        for (TypeDTO typeDTO : data.getRows()) {
            typeDTO.setAmount(typeDTO.getTypeamount());
            typeDTO.setUrl(homeUrl + UrlEncodeUtils.encodeUrl("sort/" + typeDTO.getAlias()) + (staticHtml ? ".html" : ""));
        }
        return data;
    }
}
