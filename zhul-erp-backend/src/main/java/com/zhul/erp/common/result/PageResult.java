package com.zhul.erp.common.result;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class PageResult<T> implements Serializable {
    private Long total;
    private List<T> records;

    public static <T> PageResult<T> of(Long total, List<T> records) {
        PageResult<T> page = new PageResult<>();
        page.setTotal(total);
        page.setRecords(records);
        return page;
    }
}
