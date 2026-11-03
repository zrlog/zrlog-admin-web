package com.zrlog.admin.data.repository;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.zrlog.common.cache.dto.LogNavDTO;
import com.zrlog.data.repository.LogNavRepository;
import jakarta.inject.Inject;

/** Paginated lognav queries for content administration. */
public class AdminLogNavRepository extends LogNavRepository {

    @Inject
    public AdminLogNavRepository(DataSourceWrapper dataSource) {
        super(dataSource);
    }

    public PageData<LogNavDTO> find(PageRequest page) {
        return queryPageData(
                "select l.navId as id,l.navName,l.url,l.sort,l.icon from "
                        + tableName
                        + " l where l.url is not null and l.navName is not null order by sort",
                page,
                new Object[0],
                LogNavDTO.class);
    }
}
