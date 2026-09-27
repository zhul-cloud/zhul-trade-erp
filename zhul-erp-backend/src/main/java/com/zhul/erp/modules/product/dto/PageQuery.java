package com.zhul.erp.modules.product.dto;

import com.zhul.erp.modules.product.constants.ProductConstants;
import lombok.Data;

/** 分页查询入参基类：page 从 1 开始，pageSize 缺省 20、最大 100。 */
@Data
public class PageQuery {
    private Integer page;
    private Integer pageSize;

    public long pageOrDefault() {
        return page == null || page < 1 ? 1 : page;
    }

    public long pageSizeOrDefault() {
        if (pageSize == null || pageSize < 1) {
            return ProductConstants.DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, ProductConstants.MAX_PAGE_SIZE);
    }
}
