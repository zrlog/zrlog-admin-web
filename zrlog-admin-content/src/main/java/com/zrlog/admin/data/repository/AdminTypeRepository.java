package com.zrlog.admin.data.repository;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.zrlog.common.cache.dto.TypeDTO;
import com.zrlog.data.repository.LogRepository;
import com.zrlog.data.repository.TypeRepository;
import jakarta.inject.Inject;

/** Paginated type queries for content administration. */
public class AdminTypeRepository extends TypeRepository {

    @Inject
    public AdminTypeRepository(DataSourceWrapper dataSource) {
        super(dataSource);
    }

    public PageData<TypeDTO> find(PageRequest page) {
        return queryPageData("select t.typeId as id,t.alias,t.typeName,t.remark,t.arrange_plugin,(select count(logId) from " + LogRepository.TABLE_NAME +
                " where typeid=t.typeid) as typeamount from " + tableName + " t", page, new Object[0], TypeDTO.class);
    }
}
