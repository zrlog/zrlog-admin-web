package com.zrlog.admin.business.service;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.zrlog.admin.business.rest.request.CreateLinkRequest;
import com.zrlog.admin.business.rest.request.UpdateLinkRequest;
import com.zrlog.common.cache.dto.LinkDTO;
import com.zrlog.model.Link;

import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.Objects;

public class LinkService {
    private final jakarta.inject.Provider<Link> linkProvider;

    @Inject
    public LinkService(jakarta.inject.Provider<Link> linkProvider) {
        this.linkProvider = linkProvider;
    }

    public boolean delete(int id) throws SQLException {
        return this.linkProvider.get().deleteById(id);
    }

    public boolean update(UpdateLinkRequest request) throws SQLException {
        return this.linkProvider
                .get()
                .set("linkName", request.getLinkName())
                .set("sort", request.getSort())
                .set("url", request.getUrl())
                .set("icon", request.getIcon())
                .set("alt", Objects.requireNonNullElse(request.getAlt(), ""))
                .updateById(request.getId());
    }

    public PageData<LinkDTO> find(PageRequest pageRequest) throws SQLException {
        return this.linkProvider.get().find(pageRequest);
    }

    public boolean add(CreateLinkRequest request) throws SQLException {
        return this.linkProvider
                .get()
                .set("linkName", request.getLinkName())
                .set("sort", Objects.requireNonNullElse(request.getSort(), 0))
                .set("url", request.getUrl())
                .set("icon", request.getIcon())
                .set("alt", Objects.requireNonNullElse(request.getAlt(), ""))
                .save();
    }
}
