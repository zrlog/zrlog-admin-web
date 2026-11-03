package com.zrlog.admin.business.service;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.hibegin.common.util.UrlEncodeUtils;
import com.zrlog.admin.business.exception.DeleteTypeException;
import com.zrlog.admin.business.rest.request.CreateTypeRequest;
import com.zrlog.admin.business.rest.request.UpdateTypeRequest;
import com.zrlog.common.cache.dto.TypeDTO;
import com.zrlog.admin.data.repository.AdminLogRepository;
import com.zrlog.admin.data.repository.AdminTypeRepository;
import jakarta.inject.Inject;
import com.zrlog.model.Type;

import java.sql.SQLException;
import java.util.Objects;

public class ArticleTypeService {
    private final jakarta.inject.Provider<AdminLogRepository> logProvider;
    private final jakarta.inject.Provider<AdminTypeRepository> typeProvider;

    @Inject
    public ArticleTypeService(
            jakarta.inject.Provider<AdminLogRepository> logProvider, jakarta.inject.Provider<AdminTypeRepository> typeProvider) {
        this.logProvider = logProvider;
        this.typeProvider = typeProvider;
    }

    public boolean delete(int typeId) throws SQLException {
        if (this.logProvider.get().countByTypeId(typeId) > 0) {
            throw new DeleteTypeException();
        }
        var repository = this.typeProvider.get();
        var row = repository.findById((long) typeId).orElse(null);
        if (row == null) return false;
        repository.delete(row);
        return true;
    }

    public boolean add(CreateTypeRequest request) throws SQLException {
        var repository = this.typeProvider.get();
        Type row = new Type();
        row.setTypeName(request.getTypeName());
        row.setAlias(request.getAlias());
        row.setRemark(Objects.requireNonNullElse(request.getRemark(), ""));
        repository.insert(row);
        return true;
    }

    public boolean update(UpdateTypeRequest request) throws SQLException {
        var repository = this.typeProvider.get();
        Type row = repository.findById(request.getId().longValue()).orElse(null);
        if (row == null) return false;
        row.setTypeName(request.getTypeName());
        row.setAlias(request.getAlias());
        row.setRemark(Objects.requireNonNullElse(request.getRemark(), ""));
        repository.update(row);
        return true;
    }


    public PageData<TypeDTO> find(String homeUrl, PageRequest page, boolean staticHtml) throws SQLException {
        PageData<TypeDTO> data = this.typeProvider.get().find(page);
        for (TypeDTO typeDTO : data.getRows()) {
            typeDTO.setAmount(typeDTO.getTypeamount());
            typeDTO.setUrl(homeUrl + UrlEncodeUtils.encodeUrl("sort/" + typeDTO.getAlias()) + (staticHtml ? ".html" : ""));
        }
        return data;
    }
}
