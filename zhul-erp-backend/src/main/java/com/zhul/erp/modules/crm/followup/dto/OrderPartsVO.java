package com.zhul.erp.modules.crm.followup.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 订单履约行展开的分部分进度（第二期 spec「订单履约列表与分部分进度」），只读 */
@Data
public class OrderPartsVO {
    private List<Part> parts = new ArrayList<>();
    /** 已经告诉客户的节点（这张订单的跟进记录） */
    private List<Told> told = new ArrayList<>();
    /** 分批出运时每批的预计送达 */
    private List<Batch> batches = new ArrayList<>();

    @Data
    public static class Part {
        /** 第 n 部分 */
        private String label;
        private String goods;
        /** 来源：采购单号与供应商，库存直发时 poId 为空、supplier 为「库存直发」 */
        private Long poId;
        private String poNo;
        private String supplier;
        private String stageCode;
        private String stageName;
        private LocalDate expectedShipDate;
        /** 到仓情况 */
        private String arrival;
        private String situation;
        /** 要告诉客户的事（该阶段的默认动作） */
        private String tell;
    }

    @Data
    public static class Told {
        private LocalDateTime date;
        /** 节点：运单号和发货照片、新的交期 / 送达日 */
        private String node;
        private String content;
        private String channelName;
    }

    @Data
    public static class Batch {
        /** 出运单 ID，前端「查看出运单」用 */
        private Long shipmentId;
        private String shNo;
        private String carrier;
        private String waybillNo;
        private LocalDate shippedDate;
        private LocalDate etaDate;
    }
}
