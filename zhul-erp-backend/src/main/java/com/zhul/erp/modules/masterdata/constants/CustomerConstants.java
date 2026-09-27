package com.zhul.erp.modules.masterdata.constants;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 客户主数据常量，码值与 V1.2.9__customer_trade_profile.sql 的字段注释、前端 constants.ts 一致。 */
public final class CustomerConstants {

    private CustomerConstants() {
    }

    /** 自动生成编码的前缀：CUS + 5 位补零主键 */
    public static final String CODE_PREFIX = "CUS";
    /** 角色、行业、等级、来源、付款方式、运输方式的"未设置"码值 */
    public static final int UNSET = 0;
    public static final String DEFAULT_CURRENCY = "USD";

    public static final Map<Integer, String> ROLE_LABELS = Map.of(
            1, "终端用户", 2, "系统集成商", 3, "经销商", 4, "贸易商", 5, "OEM设备厂", 6, "维修服务商", 7, "其他");

    public static final Map<Integer, String> INDUSTRY_LABELS = Map.ofEntries(
            Map.entry(1, "汽车制造"), Map.entry(2, "电子半导体"), Map.entry(3, "食品饮料"), Map.entry(4, "包装印刷"),
            Map.entry(5, "纺织"), Map.entry(6, "冶金钢铁"), Map.entry(7, "石油化工"), Map.entry(8, "水处理"),
            Map.entry(9, "电力能源"), Map.entry(10, "矿山"), Map.entry(11, "港口物流"), Map.entry(12, "楼宇暖通"),
            Map.entry(13, "其他"));

    public static final Map<Integer, String> GRADE_LABELS = Map.of(1, "A", 2, "B", 3, "C");

    public static final Map<Integer, String> SOURCE_LABELS = Map.of(
            1, "阿里巴巴国际站", 2, "中国制造网", 3, "独立站", 4, "展会", 5, "社交媒体", 6, "老客户转介绍", 7, "主动开发",
            8, "其他");

    public static final Map<Integer, String> PAYMENT_LABELS = Map.of(
            1, "T/T 全额预付", 2, "T/T 定金 + 发货前付尾款", 3, "T/T 定金 + 见提单副本付尾款", 4, "L/C 即期",
            5, "L/C 远期", 6, "D/P", 7, "D/A", 8, "O/A 赊销", 9, "其他");
    /** 需要定金比例的付款方式 */
    public static final Set<Integer> PAYMENT_WITH_DEPOSIT = Set.of(2, 3);
    /** 需要账期的付款方式：L/C 远期、D/A、O/A */
    public static final Set<Integer> PAYMENT_WITH_DAYS = Set.of(5, 7, 8);

    public static final Map<Integer, String> SHIPPING_LABELS = Map.of(
            1, "海运整柜", 2, "海运拼箱", 3, "空运", 4, "国际快递", 5, "铁路", 6, "陆运");

    /** Incoterms 2020 */
    public static final List<String> INCOTERMS = List.of("EXW", "FCA", "FOB", "CFR", "CIF", "CPT", "CIP", "DAP", "DPU", "DDP");
    public static final List<String> CURRENCIES = List.of("USD", "EUR", "GBP", "JPY", "CNY");

    public static final int PARTY_CONSIGNEE = 1;
    public static final int PARTY_NOTIFY = 2;
    public static final int PARTY_BILL_TO = 3;
    public static final Map<Integer, String> PARTY_LABELS = Map.of(1, "收货人", 2, "通知方", 3, "发票抬头");
    public static final int MAX_PARTIES = 20;

    public static final int EXPORT_MAX_ROWS = 5000;

    public static final String ERROR_CODE_DUPLICATE = "CUSTOMER_CODE_DUPLICATE";
    public static final String ERROR_DUPLICATE = "CUSTOMER_DUPLICATE";
    public static final String NOT_FOUND_MESSAGE = "客户不存在或无权查看";
    public static final String ENGLISH_ONLY_MESSAGE = "单据字段请使用英文";
}
