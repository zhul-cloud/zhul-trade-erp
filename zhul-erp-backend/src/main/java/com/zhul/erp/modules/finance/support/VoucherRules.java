package com.zhul.erp.modules.finance.support;

import com.zhul.erp.modules.inventory.constants.InvConstants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 业务凭证规则（spec finance/business-voucher，design 决策 2）：业务项与默认科目、库存流水业务类型的归属。
 * <p>
 * 一张凭证 = 一张来源单据的一次库存动作。库存商品记在动作的常规一侧，金额为正；反向动作（冲销、撤销、作废、撤回）
 * 记在原动作同一侧、金额为负（红字）。对方业务项记在另一侧，金额相同、按往来单位分行。
 */
public final class VoucherRules {

    private VoucherRules() {
    }

    // ---------------------------------------------------------------- 类别

    public static final int CAT_PURCHASE = 1;
    public static final int CAT_COGS = 2;
    public static final int CAT_OTHER_OUT = 3;
    public static final int CAT_ADJUST = 4;
    public static final Map<Integer, String> CATEGORY_NAMES = Map.of(CAT_PURCHASE, "采购入库", CAT_COGS, "销售成本结转",
            CAT_OTHER_OUT, "其他出库", CAT_ADJUST, "成本调整");

    public static final int DR = 1;
    public static final int CR = 2;
    public static final int PARTNER_NONE = 0;
    public static final int PARTNER_SUPPLIER = 1;

    // ---------------------------------------------------------------- 业务项与默认科目

    public static final String STOCK = "STOCK";
    public static final String COGS = "COGS";
    public static final String AP = "AP";
    public static final String RETURN_IN = "RETURN_IN";
    public static final String OTHER_OUT = "OTHER_OUT:";
    public static final String ADJ_OFFSET = "ADJ_OFFSET:";

    /** 业务项：键、分组、名称、默认科目编码与名称 */
    public record Item(String key, String group, String name, String defaultCode, String defaultName) {
    }

    /** 全部业务项，按科目设置页的分组与顺序 */
    public static final List<Item> ITEMS;
    private static final Map<String, Item> BY_KEY = new LinkedHashMap<>();

    static {
        List<Item> items = new ArrayList<>();
        items.add(new Item(STOCK, "库存与成本", "库存商品", "1405", "库存商品"));
        items.add(new Item(COGS, "库存与成本", "主营业务成本", "6401", "主营业务成本"));
        items.add(new Item(AP, "库存与成本", "应付账款", "2202", "应付账款"));
        items.add(new Item(RETURN_IN, "库存与成本", "退货入库", "6401", "主营业务成本"));
        items.add(new Item(otherOut(InvConstants.OO_SCRAP), "其他出库", "报废", "6711", "营业外支出"));
        items.add(new Item(otherOut(InvConstants.OO_SAMPLE), "其他出库", "转样品", "6601", "销售费用"));
        items.add(new Item(otherOut(InvConstants.OO_LOST), "其他出库", "货代丢失", "1221", "其他应收款"));
        items.add(new Item(otherOut(InvConstants.OO_DAMAGED), "其他出库", "货损", "6711", "营业外支出"));
        for (int reason = InvConstants.CA_PAYMENT; reason <= InvConstants.CA_UPGRADE; reason++) {
            items.add(new Item(adjOffset(reason), "成本调整对方科目", InvConstants.CA_REASON_NAMES.get(reason), "2202", "应付账款"));
        }
        ITEMS = List.copyOf(items);
        ITEMS.forEach(i -> BY_KEY.put(i.key(), i));
    }

    public static String otherOut(int outboundType) {
        return OTHER_OUT + outboundType;
    }

    public static String adjOffset(int reason) {
        return ADJ_OFFSET + reason;
    }

    public static Item item(String key) {
        return BY_KEY.get(key);
    }

    /** 分录上显示的业务项名称，如「其他出库 · 报废」「成本调整对方 · 外币付款」 */
    public static String itemLabel(String key) {
        Item i = BY_KEY.get(key);
        if (i == null) {
            return key;
        }
        if (key.startsWith(OTHER_OUT)) {
            return "其他出库 · " + i.name();
        }
        if (key.startsWith(ADJ_OFFSET)) {
            return "成本调整对方 · " + i.name();
        }
        return i.name();
    }

    // ---------------------------------------------------------------- 库存流水业务类型的归属

    /**
     * 生成凭证的库存动作。
     *
     * @param category  凭证类别
     * @param action    摘要里的动作
     * @param stockSide 库存商品记在哪一侧（借 / 贷）
     * @param counter   对方业务项；其他出库为 null（按出库单类型取）
     * @param supplier  对方是否按供应商分行
     */
    public record Action(int category, String action, int stockSide, String counter, boolean supplier) {
    }

    public static final Map<String, Action> ACTIONS = Map.of(
            InvConstants.BIZ_PURCHASE_IN, new Action(CAT_PURCHASE, "采购入库", DR, AP, true),
            InvConstants.BIZ_PURCHASE_IN_REVERSE, new Action(CAT_PURCHASE, "冲销采购入库", DR, AP, true),
            InvConstants.BIZ_PURCHASE_RETURN_OUT, new Action(CAT_PURCHASE, "采购退货", CR, AP, true),
            InvConstants.BIZ_SALES_OUT, new Action(CAT_COGS, "出运结转成本", CR, COGS, false),
            InvConstants.BIZ_SALES_OUT_UNDO, new Action(CAT_COGS, "撤销出运", CR, COGS, false),
            InvConstants.BIZ_RETURN_IN, new Action(CAT_COGS, "退货入库", DR, RETURN_IN, false),
            InvConstants.BIZ_RETURN_IN_UNDO, new Action(CAT_COGS, "撤回退货入库", DR, RETURN_IN, false),
            InvConstants.BIZ_OTHER_OUT, new Action(CAT_OTHER_OUT, "其他出库", CR, null, false),
            InvConstants.BIZ_OTHER_OUT_VOID, new Action(CAT_OTHER_OUT, "作废其他出库", CR, null, false));

    /** 改变库存金额、但不生成凭证的业务类型：在核对里单列，由会计在 T+ 里另行处理 */
    public static final Map<String, String> NO_VOUCHER = Map.of(
            InvConstants.BIZ_OPENING, "期初（含补关联商品）",
            InvConstants.BIZ_SYSTEM_IN, "系统外入库",
            InvConstants.BIZ_MERGE, "商品合并");

    /**
     * 不改变全部仓库库存金额的业务类型：调拨（调入调出抵消）、不良转合格、预占释放；成本调整另由调整单生成凭证。
     * 新增业务类型必须归到 ACTIONS、NO_VOUCHER 或这里（VoucherRulesTest 检查）。
     */
    public static final Set<String> NOT_COUNTED = Set.of(InvConstants.BIZ_HANDOVER, InvConstants.BIZ_HANDOVER_UNDO,
            InvConstants.BIZ_DISCOUNT_ACCEPT, InvConstants.BIZ_DISCOUNT_ACCEPT_UNDO, InvConstants.BIZ_RESERVE,
            InvConstants.BIZ_RELEASE, InvConstants.BIZ_COST_ADJUST);
}
