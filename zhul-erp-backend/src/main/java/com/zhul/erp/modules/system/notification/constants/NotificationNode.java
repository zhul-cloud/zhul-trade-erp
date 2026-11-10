package com.zhul.erp.modules.system.notification.constants;

/**
 * 通知节点（spec system/notification）。标题按节点模板 + 单据编号 + 数量生成，数量变化时标题随之更新。
 * 待办型节点由业务模块的 {@code NotificationTodoChecker} 判断是否已处理。
 */
public enum NotificationNode {
    // 询价
    SOURCING_POOL("询价任务待分配", Kind.TODO, NotificationPost.PURCHASE_LEAD, "{no} 有 {n} 个任务待分配"),
    SOURCING_ASSIGNED("询价任务分配给我", Kind.TODO, null, "你有 {n} 个新的询价任务"),
    INQUIRY_QUOTABLE("询盘可以报价了", Kind.INFO, null, "{no} 已全部回价，可以报价了"),
    MODEL_DOUBT("型号存疑，请核实", Kind.INFO, null, "{no} 有 {n} 个型号被退回：型号存疑"),
    // 收款
    RECEIPT_SLIP("付款水单待登记", Kind.TODO, NotificationPost.FINANCE, "{no} 客户付款待登记"),
    RECEIPT_CONFIRMED("客户款已到账", Kind.INFO, null, "{no} 客户款已到账"),
    // 销售订单与采购
    SO_PURCHASE("有型号需要你采购", Kind.TODO, null, "{no} 有 {n} 个型号需要你采购"),
    SO_NO_PURCHASER("有型号没指定采购员", Kind.TODO, NotificationPost.PURCHASE_LEAD, "{no} 有 {n} 个型号没指定采购员"),
    PURCHASER_CHANGED("采购员变更", Kind.INFO, null, "{no} 的 {n} 个型号已转给别人"),
    SO_CANCELLED("订单已取消", Kind.INFO, null, "{no} 取消了 {n} 个已下单的型号"),
    // 收货
    SUPPLIER_SHIPPED("供应商已发货", Kind.TODO, NotificationPost.RECEIVER, "{no} 供应商已发货，待收货"),
    SHIPPED_FOR_SALES("供应商已发货", Kind.INFO, null, "{no} 有货已发出"),
    ARRIVAL_DUE("到期还没收货", Kind.TODO, NotificationPost.RECEIVER, "{no} 到了预计到货日，还没收货"),
    GOODS_RECEIVED("货已入库", Kind.INFO, null, "{no} 有货已入库"),
    DISCREPANCY("到货有差异", Kind.TODO, null, "{no} 到货有 {n} 处差异"),
    ORDER_READY("货已到齐", Kind.INFO, null, "{no} 的货已到齐，可以安排打包出运");

    public enum Kind {
        TODO(1), INFO(2);

        private final int code;

        Kind(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }
    }

    private final String label;
    private final Kind kind;
    private final NotificationPost post;
    private final String titleTemplate;

    NotificationNode(String label, Kind kind, NotificationPost post, String titleTemplate) {
        this.label = label;
        this.kind = kind;
        this.post = post;
        this.titleTemplate = titleTemplate;
    }

    public String label() {
        return label;
    }

    public Kind kind() {
        return kind;
    }

    public boolean todo() {
        return kind == Kind.TODO;
    }

    /** 发给哪个岗位的接收人；null 表示只发给单据负责人 */
    public NotificationPost post() {
        return post;
    }

    public String title(String bizNo, int count) {
        if (this == SOURCING_ASSIGNED && count == 1) {
            return "你有新的询价任务 " + bizNo;
        }
        return titleTemplate.replace("{no}", bizNo == null ? "" : bizNo).replace("{n}", String.valueOf(count));
    }

    public static NotificationNode of(String code) {
        try {
            return valueOf(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
