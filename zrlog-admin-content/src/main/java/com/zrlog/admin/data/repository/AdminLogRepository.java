package com.zrlog.admin.data.repository;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.ResultValueConvertUtils;
import com.hibegin.common.dao.dto.OrderBy;
import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.dao.dto.PageRequest;
import com.hibegin.common.dao.dto.PageRequestImpl;
import com.hibegin.common.util.StringUtils;
import com.zrlog.data.dto.ArticleBasicDTO;
import com.zrlog.data.exception.DAOException;
import com.zrlog.data.repository.CommentRepository;
import com.zrlog.data.repository.LogRepository;
import com.zrlog.util.ParseUtil;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;

/** Article queries used by content administration. */
public class AdminLogRepository extends LogRepository {
    private static final Map<String, String> sortKeyMap = new HashMap<>();

    static {
        sortKeyMap.put("typeName", "l.typeId");
        sortKeyMap.put("releaseTime", "l.releaseTime");
        sortKeyMap.put("commentSize", "commentSize");
        sortKeyMap.put("privacy", "l.privacy");
        sortKeyMap.put("click", "l.click");
        sortKeyMap.put("lastUpdateDate", "l.last_update_date");
    }

    @Inject
    public AdminLogRepository(DataSourceWrapper dataSource) {
        super(dataSource);
    }

    private static String getPageSort(PageRequest pageRequest) {
        List<OrderBy> orders = pageRequest.getSorts();
        if (orders == null || orders.isEmpty()) {
            return "l.logId desc";
        }
        StringBuilder orderSort = new StringBuilder();
        for (OrderBy orderBy : orders) {
            orderSort.append(sortKeyMap.getOrDefault(orderBy.getSortKey(), "l.logId"));
            orderSort.append(" ").append(orderBy.getDirection().name().toLowerCase());
        }
        return orderSort.toString();
    }

    /** Reads drafts and private articles; callers enforce account permissions. */
    public ArticleBasicDTO findByIdOrAlias(Object idOrAlias) throws SQLException {
        if (idOrAlias == null) {
            return null;
        }
        if (idOrAlias instanceof Number || ParseUtil.isNumeric(String.valueOf(idOrAlias))) {
            String sql =
                    "select l.*,last_update_date as lastUpdateDate,u.userName,u.header as header,(select count(commentId) from " + CommentRepository.TABLE_NAME + " where logId=l.logId) commentSize ,t.alias as typeAlias,t.typeName as typeName  from " + tableName + " l inner join user u,type t where t.typeId=l.typeId and u.userId=l.userId and l.logId=?";
            ArticleBasicDTO log = queryOne(sql, ArticleBasicDTO.class, idOrAlias);
            if (log != null) {
                return log;
            }
        }
        String sql =
                "select l.*,last_update_date as lastUpdateDate,u.userName,u.header as header,(select count(commentId) from " + CommentRepository.TABLE_NAME + " where logId=l.logId) commentSize ,t.alias as typeAlias,t.typeName as typeName  from " + tableName + " l inner join user u,type t where t.typeId=l.typeId and u.userId=l.userId and l.alias=?";
        ArticleBasicDTO log = queryOne(sql, ArticleBasicDTO.class, String.valueOf(idOrAlias));
        if (Objects.isNull(log)) {
            return null;
        }
        return log;
    }

    public long findMaxId() throws SQLException {
        Number number = (Number) queryFirstObj("select max(logId) max from " + tableName);
        if (Objects.isNull(number)) {
            return 0;
        }
        return number.longValue();

    }

    /**
     * 管理员查询文章
     */
    public PageData<ArticleBasicDTO> find(PageRequest pageRequest, String keywords, String typeAlias) {
        return find(pageRequest, keywords, typeAlias, "");
    }

    /**
     * 管理员查询文章
     */
    public PageData<ArticleBasicDTO> find(PageRequest pageRequest, String keywords, String typeAlias, String status) {
        return find(pageRequest, keywords, typeAlias, status, null);
    }

    public PageData<ArticleBasicDTO> find(PageRequest pageRequest, String keywords, String typeAlias, String status, Integer authorId) {
        return find(pageRequest, keywords, typeAlias, status, authorId, null);
    }

    public PageData<ArticleBasicDTO> find(PageRequest pageRequest, String keywords, String typeAlias, String status, Integer authorId, Integer privateOwnerId) {
        String searchKeywords = "";
        List<Object> searchParam = new ArrayList<>();
        if (StringUtils.isNotEmpty(keywords)) {
            searchKeywords = " and (l.title like ? or l.plain_content like ? or l.keywords like ? or l.alias like ?)";
            searchParam.add("%" + keywords + "%");
            searchParam.add("%" + keywords + "%");
            searchParam.add("%" + keywords + "%");
            searchParam.add("%" + keywords + "%");
        }
        if (StringUtils.isNotEmpty(typeAlias)) {
            searchKeywords += " and t.alias = ?";
            searchParam.add(typeAlias);
        }
        if (StringUtils.isNotEmpty(status)) {
            switch (status) {
                case "published":
                    searchKeywords += " and l.rubbish = ? and l.privacy = ?";
                    searchParam.add(false);
                    searchParam.add(false);
                    break;
                case "private":
                    searchKeywords += " and l.privacy = ?";
                    searchParam.add(true);
                    break;
                case "draft":
                    searchKeywords += " and l.rubbish = ?";
                    searchParam.add(true);
                    break;
            }
        }
        if (authorId != null) {
            searchKeywords += " and l.userId = ?";
            searchParam.add(authorId);
        }
        if (privateOwnerId != null) {
            searchKeywords += " and (l.privacy=? or l.userId=?)";
            searchParam.add(false);
            searchParam.add(privateOwnerId);
        }
        String pageSort = getPageSort(pageRequest);
        String sql =
                "select l.*,t.typeName,l.logId as id,l.last_update_date as lastUpdateDate,t" +
                        ".alias as typeAlias,u.userName,u.header as header,(select count(commentId) from " + CommentRepository.TABLE_NAME + " " +
                        "where " + "logId=l.logId ) commentSize from " + tableName + " l inner join user u inner " + "join type t where u" + ".userId=l.userId" + searchKeywords +
                        " and t.typeid=l.typeid and l.typeid is not null order " + "by " + pageSort;
        return queryPageData(
                sql, pageRequest,
                searchParam.toArray(), ArticleBasicDTO.class);
    }

    public Map<String, Long> getArticleData() throws SQLException {
        List<ArticleBasicDTO> lo = queryList("select releaseTime from " + tableName + " order by releaseTime desc", ArticleBasicDTO.class);
        Map<String, Long> archives = new LinkedHashMap<>();
        for (ArticleBasicDTO entry : lo) {
            Object value = entry.getReleaseTime();
            if (Objects.isNull(value)) {
                continue;
            }
            String key = ResultValueConvertUtils.formatDate(value, "yyyy-MM-dd");
            if (archives.containsKey(key)) {
                archives.put(key, archives.get(key) + 1);
            } else {
                archives.put(key, 1L);
            }
        }
        return archives;
    }

    public BigDecimal sumClick() throws SQLException {
        Number sum = (Number) queryFirstObj("select sum(click) from " + tableName);
        return sum == null ? new BigDecimal(0) : new BigDecimal(sum.longValue());
    }

    public long countByTypeId(Integer typeId) {
        try {
            return ((Number) queryFirstObj("select count(1) as count from " + tableName + " where typeId=?", typeId)).longValue();
        } catch (SQLException e) {
            throw new DAOException(e);
        }
    }

    public long getCount() {
        return find(new PageRequestImpl(1L, 0L), null, null).getTotalElements();
    }
}
