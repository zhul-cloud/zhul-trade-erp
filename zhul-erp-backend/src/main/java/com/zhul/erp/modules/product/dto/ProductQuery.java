package com.zhul.erp.modules.product.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductQuery extends PageQuery {
    /** 匹配型号（忽略空格、连字符、大小写）或产品名称 */
    private String keyword;
    private Long brandId;
    private Long categoryId;
    private Long seriesId;
    private Integer lifecycleStatus;
    private Integer status;
    /** 仅平台账号有效：同时返回已软删除的商品 */
    private Boolean includeDeleted;
    /** 按缺项筛选，取值 media、logistics、customs、price、refine（品类未细分）；租户账号的范围按行业过滤 */
    private String missing;
    /** 缺项为 refine（品类未细分）时的建议状态：todo-待 AI 细分、pending-待确认建议、ignored-已忽略；不传为三者全部 */
    private String refineState;
    /** 只看本租户维护的商品（租户账号有效，平台账号忽略） */
    private Boolean mine;
    /** 按热度排序（倒序）：inquiryCount-询盘次数、dealCount-成交次数；不传按更新时间倒序 */
    private String sort;
}
