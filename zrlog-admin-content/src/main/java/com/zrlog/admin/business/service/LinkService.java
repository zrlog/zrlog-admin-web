package com.zrlog.admin.business.service;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.zrlog.admin.business.rest.request.CreateLinkRequest;
import com.zrlog.admin.business.rest.request.UpdateLinkRequest;
import com.zrlog.admin.data.repository.AdminLinkRepository;
import com.zrlog.common.cache.dto.LinkDTO;
import com.zrlog.model.Link;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.Objects;

public class LinkService {
    private final jakarta.inject.Provider<AdminLinkRepository> linkProvider;

    @Inject
    public LinkService(jakarta.inject.Provider<AdminLinkRepository> linkProvider) {
        this.linkProvider = linkProvider;
    }

    public boolean delete(int id) throws SQLException {
        var repository = this.linkProvider.get();
        var row = repository.findById((long) id).orElse(null);
        if (row == null) return false;
        repository.delete(row);
        return true;
    }

    public boolean update(UpdateLinkRequest request) throws SQLException {
        var repository = this.linkProvider.get();
        Link row = repository.findById(request.getId().longValue()).orElse(null);
        if (row == null) return false;
        row.setLinkName(request.getLinkName());
        row.setSort(Math.toIntExact(Objects.requireNonNullElse(request.getSort(), 0L)));
        row.setUrl(request.getUrl());
        row.setIcon(request.getIcon());
        row.setAlt(Objects.requireNonNullElse(request.getAlt(), ""));
        repository.update(row);
        return true;
    }

    public PageData<LinkDTO> find(PageRequest pageRequest) throws SQLException {
        return this.linkProvider.get().find(pageRequest);
    }

    public boolean add(CreateLinkRequest request) throws SQLException {
        var repository = this.linkProvider.get();
        Link row = new Link();
        row.setLinkName(request.getLinkName());
        row.setSort(Math.toIntExact(Objects.requireNonNullElse(request.getSort(), 0L)));
        row.setUrl(request.getUrl());
        row.setIcon(request.getIcon());
        row.setAlt(Objects.requireNonNullElse(request.getAlt(), ""));
        repository.insert(row);
        return true;
    }
}
