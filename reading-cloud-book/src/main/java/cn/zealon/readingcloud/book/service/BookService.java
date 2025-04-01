package cn.zealon.readingcloud.book.service;

import cn.zealon.readingcloud.book.vo.BookVO;
import cn.zealon.readingcloud.common.pojo.book.Book;
import cn.zealon.readingcloud.common.result.Result;
import cn.zealon.readingcloud.common.vo.BookInitDto;

import java.util.List;

/**
 * 图书服务
 * @author: zealon
 * @since: 2019/7/4
 */
public interface BookService {

    /**
     * 查询图书基本信息
     * @param bookId
     * @return
     */
    Result getBookById(String bookId);

    /**
     * 获取图书详情
     * @param bookId
     * @return
     */
    Result<BookVO> getBookDetails(String bookId);

    /**
     * 查询所有图书信息
     * @return
     */
    Result<List<BookInitDto>> selectAllBook();
}
