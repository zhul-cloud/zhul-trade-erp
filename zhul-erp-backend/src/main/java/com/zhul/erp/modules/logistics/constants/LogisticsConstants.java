package com.zhul.erp.modules.logistics.constants;

import java.math.BigDecimal;
import java.util.Map;

/** 出库与出运：出库单、国内快递、出运单、单证组、运费分摊、货代对账 */
public final class LogisticsConstants {

    public static final String MENU_OUTBOUND = "出库打包";
    public static final String MENU_SHIPMENT = "出运单";
    public static final String MENU_STATEMENT = "货代对账";

    public static final int OB_NOTICE = 1;
    public static final int OB_DIRECT = 2;
    public static final Map<Integer, String> OB_SOURCE_NAMES = Map.of(OB_NOTICE, "发货通知", OB_DIRECT, "直发货代");

    public static final int OB_PENDING = 1;
    public static final int OB_PACKED = 2;
    public static final int OB_HANDED = 3;
    public static final int OB_WITHDRAWN = 4;
    public static final Map<Integer, String> OB_STATUS_NAMES = Map.of(OB_PENDING, "待打包", OB_PACKED, "已打包", OB_HANDED, "已交货代",
            OB_WITHDRAWN, "已撤回");

    public static final int WAYBILL_VALID = 1;
    public static final int WAYBILL_UNDONE = 2;

    public static final int SH_PENDING = 1;
    public static final int SH_SHIPPED = 2;
    public static final int SH_VOID = 3;
    public static final Map<Integer, String> SH_STATUS_NAMES = Map.of(SH_PENDING, "待出运", SH_SHIPPED, "已出运", SH_VOID, "已作废");

    public static final int DOC_VALID = 1;
    public static final int DOC_VOID = 2;

    public static final int FREIGHT_DOMESTIC = 1;
    public static final int FREIGHT_INTERNATIONAL = 2;

    public static final int STATEMENT_DRAFT = 1;
    public static final int STATEMENT_CONFIRMED = 2;
    public static final Map<Integer, String> STATEMENT_STATUS_NAMES = Map.of(STATEMENT_DRAFT, "草稿", STATEMENT_CONFIRMED, "已确认");

    /** 国内快递的体积系数固定为 5000；货代默认也是 5000 */
    public static final BigDecimal DOMESTIC_DIVISOR = BigDecimal.valueOf(5000);
    /** 服务商（货代、快递公司）的供应商类型 */
    public static final int SUPPLIER_SERVICE = 3;

    private LogisticsConstants() {
    }
}
