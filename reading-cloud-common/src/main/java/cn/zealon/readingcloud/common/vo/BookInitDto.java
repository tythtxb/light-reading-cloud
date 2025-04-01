package cn.zealon.readingcloud.common.vo;

import cn.zealon.readingcloud.common.pojo.book.Book;
import lombok.Data;

import java.io.Serializable;

@Data
public class BookInitDto extends Book implements Serializable {
    private String author;
    private String categoryName;
}
