package com.zhul.erp.modules.inquiry.constants;

import java.util.Map;
import java.util.Set;

/** 询价协作常量，码值与 V1.2.15__inquiry_sourcing.sql 的字段注释一致 */
public final class InquiryConstants {

    private InquiryConstants() {
    }

    /** 客户询盘状态 */
    public static final int STATUS_PENDING_PARSE = 1;
    public static final int STATUS_PARSING = 2;
    public static final int STATUS_PENDING_CONFIRM = 3;
    public static final int STATUS_PARSE_FAILED = 4;
    public static final int STATUS_SOURCING = 5;
    public static final int STATUS_READY_TO_QUOTE = 6;
    public static final int STATUS_QUOTED = 7;
    public static final int STATUS_WON = 8;
    public static final int STATUS_LOST = 9;
    public static final int STATUS_CANCELLED = 10;

    /** 询盘等级：码值见字典 inquiry_level（1-S、2-A、3-B、4-C），越小越优先 */
    public static final int LEVEL_DEFAULT = 3;

    /** 内置角色「兼职采购」：看不到客户名称 */
    public static final String PART_TIME_ROLE = "ROLE_PTBUYER";

    /** 新老客户 */
    public static final int CUSTOMER_NEW = 1;
    public static final int CUSTOMER_RETURNING = 2;

    /** 型号来源 */
    public static final int PARSE_MODE_NONE = 0;
    public static final int PARSE_MODE_AI = 1;
    public static final int PARSE_MODE_MANUAL = 2;

    /** 置信度 */
    public static final int CONFIDENCE_CONFIRMED = 1;
    public static final int CONFIDENCE_CORRECTED = 2;
    public static final int CONFIDENCE_PENDING_VERIFY = 3;
    public static final int CONFIDENCE_UNRECOGNIZED = 4;

    /** 生命周期 */
    public static final int LIFECYCLE_ACTIVE = 1;
    public static final int LIFECYCLE_DISCONTINUED = 2;
    public static final int LIFECYCLE_UNKNOWN = 3;

    /** 价格来源 */
    public static final int PRICE_SOURCE_HISTORY = 1;
    public static final int PRICE_SOURCE_SOURCING = 2;

    /** 明细回价状态 */
    public static final int ITEM_PENDING = 1;
    public static final int ITEM_PRICED = 2;
    public static final int ITEM_NO_STOCK = 3;

    /** 询价任务状态 */
    public static final int TASK_UNASSIGNED = 1;
    public static final int TASK_SOURCING = 2;
    public static final int TASK_DONE = 3;
    public static final int TASK_CANCELLED = 4;
    /** 分配工作台「待审核」页签的查询值（不是任务状态：任务下有兼职回价待审核） */
    public static final int BOARD_TAB_REVIEW = 5;

    /** 分配方式 */
    public static final int ASSIGN_MANUAL = 1;
    public static final int ASSIGN_RECOMMEND = 2;
    public static final int ASSIGN_RULE = 3;
    public static final int ASSIGN_EXTRA = 4;
    public static final int ASSIGN_REASSIGN = 5;

    /** 退回原因 */
    public static final int RETURN_MODEL_DOUBT = 1;
    public static final Map<Integer, String> RETURN_REASONS = Map.of(
            RETURN_MODEL_DOUBT, "型号存疑", 2, "停产无货", 3, "超出能力", 9, "其他");

    /** 渠道 */
    public static final int CHANNEL_TAOBAO = 1;
    public static final int CHANNEL_1688 = 2;
    public static final int CHANNEL_XIANYU = 3;
    public static final int CHANNEL_SUPPLIER = 4;
    public static final int CHANNEL_OTHER = 5;
    public static final Map<Integer, String> CHANNELS = Map.of(
            CHANNEL_TAOBAO, "淘宝", CHANNEL_1688, "1688", CHANNEL_XIANYU, "闲鱼", CHANNEL_SUPPLIER, "供应商", CHANNEL_OTHER, "其他");

    /** 按钮权限：查看货源信息（询价平台、店铺），供应链内部信息，业务员默认没有 */
    public static final String PERM_SUPPLIER_VIEW = "inquiry:supplier:view";
    /** 按钮权限：审核兼职回价 */
    public static final String PERM_QUOTE_REVIEW = "inquiry:quote:review";

    /** 货况：全新原装（默认选价优先），其余码值见字典 inquiry_item_condition */
    public static final int CONDITION_NEW = 1;

    /** 含税报价没写税率时默认 13%（字典 inquiry_tax_rate 的默认值） */
    public static final int DEFAULT_TAX_RATE = 13;

    /** 询价记录状态 */
    public static final int QUOTE_DRAFT = 1;
    public static final int QUOTE_SUBMITTED = 2;
    /** 兼职采购提交后等待采购负责人审核，审核通过前不计入任何价格 */
    public static final int QUOTE_PENDING_REVIEW = 3;
    /** 审核时作废，不计入任何价格，仅留痕 */
    public static final int QUOTE_VOIDED = 4;

    /** 兼职回价在「我的询价任务」中的审核状态 */
    public static final int REVIEW_PENDING = 1;
    public static final int REVIEW_APPROVED = 2;
    public static final int REVIEW_REJECTED = 3;
    /** 审核操作日志（菜单「分配工作台」），型号的修改记录按这两个操作名读取 */
    public static final String LOG_REVIEW_APPROVE = "审核通过兼职回价";
    public static final String LOG_REVIEW_REJECT = "退回兼职回价";

    /** 录入方式 */
    public static final int ENTRY_ONLINE = 1;
    public static final int ENTRY_IMPORT = 2;

    /** 分配规则匹配方式 */
    public static final int RULE_BY_BRAND = 1;
    public static final int RULE_BY_CATEGORY = 2;

    /** 推荐依据的统计窗口 */
    public static final int RECOMMEND_DAYS = 180;

    /** 本位币 */
    public static final String BASE_CURRENCY = "CNY";

    /** 询盘附件：图片、Excel、PDF，单个 ≤10MB，每条询盘 ≤10 个 */
    public static final String ATTACHMENT_MODULE = "customer-inquiry";
    public static final Set<String> ATTACHMENT_EXTS = Set.of("jpg", "png", "xls", "xlsx", "csv", "pdf");
    public static final long ATTACHMENT_MAX_BYTES = 10L * 1024 * 1024;
    public static final int MAX_ATTACHMENTS = 10;
    public static final String ATTACHMENT_TYPE_MESSAGE = "文件类型不支持，只支持 JPG、PNG、XLS、XLSX、CSV、PDF";

    /** 导入询价结果的文件 */
    public static final String IMPORT_MODULE = "sourcing-import";
    public static final Set<String> IMPORT_EXTS = Set.of("xlsx");
    public static final long IMPORT_MAX_BYTES = 5L * 1024 * 1024;

    /** 系统设置键 */
    public static final String CONFIG_TIMEOUT_HOURS = "inquiry.sourcing.timeout-hours";
    public static final String CONFIG_URGENT_TIMEOUT_HOURS = "inquiry.sourcing.urgent-timeout-hours";
    public static final String CONFIG_AUTO_ASSIGN = "inquiry.sourcing.auto-assign";

    /** 菜单路径（权限判断用） */
    public static final String MENU_CUSTOMER_INQUIRY = "/inquiry/customer-inquiries";
    public static final String MENU_BOARD = "/inquiry/sourcing-board";
    public static final String MENU_MY_TASKS = "/inquiry/my-tasks";
    public static final String MENU_PRICE_HISTORY = "/inquiry/price-history";
}
