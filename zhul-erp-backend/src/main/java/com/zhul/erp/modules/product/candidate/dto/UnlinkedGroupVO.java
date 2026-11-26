package com.zhul.erp.modules.product.candidate.dto;

import com.zhul.erp.modules.product.dto.ProductOptionVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 待关联清单的一组：同一品牌原文 + 归一化型号的还没关联商品的单据明细（spec product/document-product-link） */
@Data
public class UnlinkedGroupVO {
    /** 归并键（品牌原文小写 | 归一化型号），关联时原样传回 */
    private String key;
    /** 单据上的品牌原文 */
    private String brand;
    /** 单据上的型号原文（取第一条） */
    private String model;
    /** 涉及的单据类型 → 明细条数 */
    private Map<String, Integer> docs;
    /** 明细总条数 */
    private int lineCount;
    /** 最近出现时间 */
    private LocalDateTime lastSeenAt;
    /** 可能是的商品（同型号的已建档或候选） */
    private ProductOptionVO suggestion;
    /** 涉及的单据：最近出现的在前，最多 20 张；docCount 为总张数 */
    private List<DocRef> docRefs;
    private int docCount;
    /** AI 判断（1-商品型号、2-费用或非商品、3-看不出），没识别过为空 */
    private Integer aiKind;
    private String aiBrand;
    private String aiModel;
    /** 已标为不是商品（只在「显示已标记」时出现） */
    private Boolean ignored;
    private String ignoredBy;
    private LocalDateTime ignoredAt;

    /** 一张涉及的单据：类型（QT、PI、SO、PO、SD、GR、OB、AS、SR、RR、PR）、ID、单号 */
    @Data
    public static class DocRef {
        private String type;
        private Long id;
        private String no;
    }
}
