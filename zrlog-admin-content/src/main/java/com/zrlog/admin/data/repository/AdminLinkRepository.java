package com.zrlog.admin.data.repository;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.zrlog.common.cache.dto.LinkDTO;
import com.zrlog.data.repository.LinkRepository;
import jakarta.inject.Inject;

/** Paginated link queries for content administration. */
public class AdminLinkRepository extends LinkRepository {

    @Inject
    public AdminLinkRepository(DataSourceWrapper dataSource) {
        super(dataSource);
    }

    public PageData<LinkDTO> find(PageRequest page) {
        return queryPageData(
                "select linkName,linkId as id,sort,url,alt,icon from "
                        + tableName
                        + " order by sort",
                page,
                new Object[0],
                LinkDTO.class);
    }
}
