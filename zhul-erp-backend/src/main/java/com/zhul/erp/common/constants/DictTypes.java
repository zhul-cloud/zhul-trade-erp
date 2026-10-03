package com.zhul.erp.common.constants;

/** 业务代码引用的内置字典类型编码 */
public final class DictTypes {

    /** 来源渠道：客户、商机、客户询盘共用 */
    public static final String SOURCE_CHANNEL = "crm_source_channel";

    /** 货况：采购回价、历史询价共用 */
    public static final String ITEM_CONDITION = "inquiry_item_condition";

    /** 货期：采购回价、历史询价共用 */
    public static final String LEAD_TIME = "inquiry_lead_time";

    /** 型号生命周期（在产、停产、待查）：解析确认、采购核实、询价包共用 */
    public static final String LIFECYCLE = "inquiry_lifecycle";

    /** 询盘等级（S/A/B/C）：码值越小越优先，采购任务按它排序 */
    public static final String INQUIRY_LEVEL = "inquiry_level";

    /** 询价税率：含税报价的增值税税率，字典值为百分比整数 */
    public static final String TAX_RATE = "inquiry_tax_rate";

    private DictTypes() {
    }
}
