package com.zhul.erp.modules.product.refine.dto;

import com.zhul.erp.modules.product.dto.ProductQuery;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 商品品类细分（spec product/category-refine）的入参与出参 */
public final class RefineDtos {

    private RefineDtos() {
    }

    /** 发起 AI 细分：有 ids 处理勾选的商品；没有时按 filter（列表当前筛选）取「待 AI 细分」最近更新的前 200 个 */
    @Data
    public static class SubmitRequest {
        private List<Long> ids;
        private ProductQuery filter;
    }

    /** 采纳 / 忽略 / 撤销忽略 */
    @Data
    public static class IdsRequest {
        private List<Long> ids;
    }

    /** AI 细分任务：提交时返回 total、skipped；查询进度时返回状态与结果数量 */
    @Data
    public static class TaskVO {
        private Long taskId;
        private Integer status;
        private String errorMessage;
        /** 交给 AI 的商品数 */
        private Integer total;
        /** 提交时跳过的商品数（不是品类未细分、不在「待 AI 细分」或不能维护） */
        private Integer skipped;
        /** 给出建议的商品数 */
        private Integer suggested;
        /** AI 判断不出的商品数 */
        private Integer unknown;
        /** 运行期间品类被改过、建议丢弃的商品数 */
        private Integer discarded;
    }

    /** 采纳 / 忽略 / 撤销忽略的结果 */
    @Data
    public static class ActionVO {
        private int done;
        private List<Skip> skipped = new ArrayList<>();
    }

    @Data
    public static class Skip {
        private Long id;
        private String mpn;
        private String reason;

        public static Skip of(Long id, String mpn, String reason) {
            Skip s = new Skip();
            s.setId(id);
            s.setMpn(mpn);
            s.setReason(reason);
            return s;
        }
    }
}
