package cn.zealon.readingcloud.homepage.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.zealon.readingcloud.book.feign.client.BookClient;
import cn.zealon.readingcloud.common.pojo.book.Book;
import cn.zealon.readingcloud.common.result.Result;
import cn.zealon.readingcloud.common.result.ResultUtil;
import cn.zealon.readingcloud.common.vo.BookInitDto;
import cn.zealon.readingcloud.homepage.dao.HotSearchWordMapper;
import cn.zealon.readingcloud.homepage.domain.RequestQuery;
import cn.zealon.readingcloud.homepage.domain.SearchBookItem;
import cn.zealon.readingcloud.homepage.domain.SearchBookResult;
import cn.zealon.readingcloud.homepage.service.SearchService;
import io.searchbox.client.JestClient;
import io.searchbox.core.Bulk;
import io.searchbox.core.Index;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import io.searchbox.indices.DeleteIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 图书查询服务
 * @author: zealon
 * @since: 2020/5/29
 */
@Service
public class SearchServiceImpl implements SearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchServiceImpl.class);

    /** ES Jest 客户端对象 */
    @Autowired
    private JestClient jestClient;

    /** 索引别名 */
    @Value("${es.aliasName}")
    private String aliasName;

    /** 类型 */
    @Value("${es.indexType}")
    private String indexType;

    @Autowired
    private HotSearchWordMapper hotSearchWordMapper;

    @Autowired
    private BookClient bookClient;

    @Override
    public Result getHotSearchWordList(Integer size) {
        List<String> hotSearchWordList = this.hotSearchWordMapper.getHotSearchWordList(size);
        return ResultUtil.success(hotSearchWordList);
    }

    @Override
    public Result getSearchResultBooks(String keyword, Integer page, Integer limit){
        // 查询条件
        Map query = new HashMap();
        // 多字段匹配
        Map multiMatch = new HashMap();
        multiMatch.put("query", keyword);
        multiMatch.put("type", "most_fields");
        String[] fields = new String[]{"bookName^2","bookName.pinyin","author"};
        multiMatch.put("fields", fields);
        query.put("multi_match",multiMatch);

        int from = (page - 1) * limit;
        int size = from + limit;
        RequestQuery requestQuery = new RequestQuery(from, size, query);
        SearchBookResult searchBookResult = this.getSearchResult(requestQuery.toString());
        return ResultUtil.success(searchBookResult);
    }

    @Override
    public Result initEsData() {
        //清空es类型book下索引库books得数据
        try {
            this.jestClient.execute(new DeleteIndex.Builder(aliasName).type(indexType).build());
        } catch (IOException e) {
            LOGGER.error("清空索引失败", e);
        }
        //查询数据库表book下所有数据，初始化es索引库books
        Result<List<BookInitDto>> resBooks=bookClient.selectAllBook();
        if(resBooks.getCode()==200){
            List<BookInitDto> books = resBooks.getData();
            if (!CollectionUtils.isEmpty(books)){
                List<SearchBookItem> searchBookItems = BeanUtil.copyToList(books, SearchBookItem.class);
                //批量插入es
                try {
                    Bulk.Builder bulkBuilder = new Bulk.Builder()
                            .defaultIndex(aliasName)
                            .defaultType(indexType);

                    // 遍历集合，逐一添加到批量操作中
                    for (SearchBookItem item : searchBookItems) {
                        Index index = new io.searchbox.core.Index.Builder(item).build();
                        bulkBuilder.addAction(index);
                    }

                    // 执行批量操作
                    this.jestClient.execute(bulkBuilder.build());
                } catch (IOException e) {
                    LOGGER.error("初始化ES数据失败", e);
                }
            }
        }
        return ResultUtil.success();
    }

    /**
     * ES 执行查询结果
     * @param query
     * @return
     */
    private SearchBookResult getSearchResult(String query){
        SearchBookResult result = new SearchBookResult();
        // 封装查询对象
        Search search = new Search.Builder(query)
                .addIndex(aliasName)
                .addType(indexType).build();

        // 执行查询
        try {
            SearchResult searchResult = this.jestClient.execute(search);
            List<SearchBookItem> bookList;
            if (searchResult.isSucceeded()) {
                // 查询成功，处理结果项
                List<SearchResult.Hit<SearchBookItem, Void>> hitList = searchResult.getHits(SearchBookItem.class);
                bookList = new ArrayList<>(hitList.size());
                for (SearchResult.Hit<SearchBookItem, Void> hit : hitList) {
                    bookList.add(hit.source);
                }
            } else {
                bookList = new ArrayList<>();
            }

            // 赋值
            long totalHits = getTotalHits(searchResult);
            result.setTotal(totalHits);
            result.setBookList(bookList);
        } catch (IOException e) {
            LOGGER.error("查询图书异常，查询语句:{}", query, e);
        }
        return result;
    }
    /**
     * 兼容不同版本的 API 获取总命中数
     * @param searchResult 查询结果
     * @return 总命中数
     */
    private long getTotalHits(SearchResult searchResult) {
        if (searchResult == null) {
            return 0L;
        }
        // 如果 getTotal() 不可用，尝试使用 getTotalHits()
        if (searchResult.getJsonObject() != null && searchResult.getJsonObject().getAsJsonObject("hits") != null) {
            return Long.valueOf(String.valueOf(searchResult.getJsonObject().getAsJsonObject("hits").getAsJsonObject("total").get("value")));
        }

        return 0L; // 默认返回 0
    }
}
