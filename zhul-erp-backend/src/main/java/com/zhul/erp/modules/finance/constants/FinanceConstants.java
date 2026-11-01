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

    /** 付款单类型 */
    public static final int KIND_PAYMENT = 1;
    public static final int KIND_REFUND = 2;

    /** 付款方式 */
    public static final int METHOD_COMPANY = 1;
    public static final int METHOD_ADVANCE = 2;
    /** 私对私：法人等个人账户付给对方个人账户，记录付款个人账户，不进报销 */
    public static final int METHOD_PRIVATE = 3;
    /** 收款账户是不是对私账户（account_type=2） */
    public static boolean isPrivateAccount(com.zhul.erp.modules.system.entity.BankAccountDO b) {
        return b != null && Integer.valueOf(2).equals(b.getAccountType());
    }

    public static final java.util.Map<Integer, String> METHOD_NAMES = java.util.Map.of(
            METHOD_COMPANY, "公司对公", METHOD_ADVANCE, "员工垫付", METHOD_PRIVATE, "私对私");

    /** 报销状态 */
    public static final int REIMBURSE_NA = 0;
    public static final int REIMBURSE_PENDING = 1;
    public static final int REIMBURSE_CLAIMED = 2;

    /** ③b：收款方类型「其他」（只有名称）与「员工」（报销付款） */
    public static final int PAYEE_OTHER = 4;
    public static final int PAYEE_EMPLOYEE = 5;
    /** ③b：来源类型「费用单」 */
    public static final int SOURCE_EXPENSE = 3;
    /** ③b：付款单类型「报销付款」 */
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

    /** 结汇单（spec finance/fx-settlement） */
    public static final String MENU_FX = "/finance/fx-settlements";
    public static final String PERM_FX_SETTLE = "finance:fx:settle";
    public static final int FX_VALID = 1;
    public static final int FX_VOID = 2;

    /** 退款单与其他收入（spec finance/customer-refund、finance/other-income） */
    public static final String MENU_REFUND = "/finance/refunds";
    public static final String MENU_OTHER_INCOME = "/finance/other-incomes";
    public static final String PERM_REFUND_EDIT = "finance:refund:edit";
    public static final String PERM_INCOME_EDIT = "finance:income:edit";
    public static final String DICT_REFUND_REASON = "refund_reason";
    public static final String DICT_INCOME_CATEGORY = "other_income_category";
    public static final int RECORD_VALID = 1;
    public static final int RECORD_VOID = 2;
    public static final int REFUND_PLATFORM = 1;
    public static final int REFUND_BANK = 2;
    /** 退款去向：转客户余额（只用于售后退款）；其他转出方式（支付宝、微信等，只用于余额提现） */
    public static final int REFUND_TO_CREDIT = 3;
    public static final int REFUND_OTHER = 4;
    public static final java.util.Map<Integer, String> REFUND_METHOD_NAMES = java.util.Map.of(REFUND_PLATFORM, "原路退回平台",
            REFUND_BANK, "银行汇款", REFUND_TO_CREDIT, "转客户余额", REFUND_OTHER, "其他");
    /** 退款单类型 */
    public static final int REFUND_AFTER_SALES = 1;
    public static final int REFUND_CREDIT_WITHDRAW = 2;

    /** 客户余额（spec finance/customer-credit） */
    public static final String MENU_CREDIT = "/finance/credits";
    public static final String PERM_CREDIT_ADJUST = "finance:credit:adjust";
    public static final int CREDIT_REFUND_IN = 1;
    public static final int CREDIT_COLLECTION_IN = 2;
    public static final int CREDIT_OVERPAID_IN = 3;
    public static final int CREDIT_OCCUPY = 4;
    public static final int CREDIT_RELEASE = 5;
    public static final int CREDIT_WITHDRAW = 6;
    public static final int CREDIT_ADJUST = 7;
    public static final int CREDIT_REVERSE = 8;
    public static final java.util.Map<Integer, String> CREDIT_TYPE_NAMES = java.util.Map.of(CREDIT_REFUND_IN, "售后退款转入",
            CREDIT_COLLECTION_IN, "代收转入", CREDIT_OVERPAID_IN, "多收转入", CREDIT_OCCUPY, "抵扣占用", CREDIT_RELEASE, "抵扣释放",
            CREDIT_WITHDRAW, "提现", CREDIT_ADJUST, "手工调整", CREDIT_REVERSE, "冲销");
    // ---------------------------------------------------------------- 退款申请（spec finance/refund-request）
    public static final int RQ_PENDING_APPROVAL = 1;
    public static final int RQ_PENDING_FINANCE = 2;
    public static final int RQ_EXECUTED = 3;
    public static final int RQ_REJECTED = 4;
    public static final int RQ_WITHDRAWN = 5;
    public static final java.util.Map<Integer, String> RQ_STATUS_NAMES = java.util.Map.of(RQ_PENDING_APPROVAL, "待审批",
            RQ_PENDING_FINANCE, "待财务处理", RQ_EXECUTED, "已执行", RQ_REJECTED, "已驳回", RQ_WITHDRAWN, "已撤回");
    public static final String CONFIG_REFUND_APPROVAL = "finance.refund-request.approval";
    public static final String PERM_REFUND_REQUEST_APPROVE = "finance:refund-request:approve";

    public static final String SRC_REFUND = "REFUND";
    public static final String SRC_PI = "PI";
    public static final String SRC_SO = "SO";
    public static final String SRC_MANUAL = "MANUAL";
    /** 其他收入的系统类别：余额提现的汇兑损益 */
    public static final String INCOME_FX_GAIN = "FX_GAIN";
}
