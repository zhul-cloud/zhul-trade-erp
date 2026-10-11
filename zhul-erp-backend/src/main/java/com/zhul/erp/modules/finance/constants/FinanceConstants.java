package com.zhul.erp.modules.finance.constants;

/** 应付与付款的常量（openspec add-accounts-payable） */
public final class FinanceConstants {

    public static final String MENU_PAYABLES = "/finance/payables";
    public static final String MENU_PAYMENTS = "/finance/payments";
    public static final String PERM_REGISTER = "finance:payment:register";
    public static final String PERM_ADVANCE = "finance:payment:advance";

    /** 收款方类型 */
    public static final int PAYEE_SUPPLIER = 1;
    public static final int PAYEE_SHOP = 2;
    public static final int PAYEE_FORWARDER = 3;

    /** 来源类型 */
    public static final int SOURCE_PURCHASE_ORDER = 1;
    public static final int SOURCE_STATEMENT = 2;

    /** 应付单与期次状态 */
    public static final int STATUS_UNPAID = 1;
    public static final int STATUS_PARTIAL = 2;
    public static final int STATUS_PAID = 3;
    public static final int STATUS_CLOSED = 4;

    /** 期次到期时点：1-下单后、2-发货前、3-入库后（offset_days 天）同付款条件；5-对账确认 */
    public static final int TRIGGER_STATEMENT = 5;

    /** 付款记录类型 */
    public static final int KIND_PAYMENT = 1;
    public static final int KIND_REFUND = 2;

    /** 付款方式 */
    public static final int METHOD_COMPANY = 1;
    public static final int METHOD_ADVANCE = 2;

    /** 报销状态 */
    public static final int REIMBURSE_NA = 0;
    public static final int REIMBURSE_PENDING = 1;
    public static final int REIMBURSE_CLAIMED = 2;

    /** ③b：收款方类型「其他」（只有名称）与「员工」（报销付款） */
    public static final int PAYEE_OTHER = 4;
    public static final int PAYEE_EMPLOYEE = 5;
    /** ③b：来源类型「费用单」 */
    public static final int SOURCE_EXPENSE = 3;
    /** ③b：付款记录类型「报销付款」 */
    public static final int KIND_REIMBURSE = 3;

    public static final String MENU_REIMBURSE = "/finance/reimbursements";
    public static final String MENU_EXPENSE = "/finance/expenses";
    public static final String PERM_REIMBURSE_PAY = "finance:reimburse:pay";
    public static final String PERM_EXPENSE_EDIT = "finance:expense:edit";

    /** 待报销项来源 */
    public static final int ITEM_PAYMENT = 1;
    public static final int ITEM_COURIER = 2;
    public static final int ITEM_RETURN_FREIGHT = 3;
    public static final int ITEM_EXPENSE = 4;
    public static final int ITEM_REFUND = 5;

    /** 报销单状态 */
    public static final int BX_PENDING = 1;
    public static final int BX_PAID = 2;
    public static final int BX_WITHDRAWN = 3;

    /** 费用单状态 */
    public static final int FE_VALID = 1;
    public static final int FE_VOID = 2;

    /** 员工垫付的欠款超过 N 天没退回时提醒本人 */
    public static final int REFUND_REMIND_DAYS = 7;

    /** 7 天内到期算「即将到期」 */
    public static final int DUE_SOON_DAYS = 7;

    private FinanceConstants() {
    }
}
