// 询盘中心共用的状态/枚举展示配置。数值需与后端保持一致，见：
// zhul-erp-backend .../inquiry/customerinquiry/constants/CustomerInquiryStatus.java
// zhul-erp-backend .../inquiry/customerinquiry/constants/CustomerInquirySource.java
// zhul-erp-backend .../inquiry/constants/ConfidenceLevel.java
// zhul-erp-backend .../inquiry/inquiryorder/constants/{InquiryOrderStatus,SupplierAssociationStatus,QuoteStatus}.java
// zhul-erp-backend .../inquiry/constants/ChannelPlatform.java

export const CUSTOMER_INQUIRY_STATUS = {
  PENDING_PARSE: 1,
  PARSING: 2,
  PENDING_CONFIRM: 3,
  PARSE_FAILED: 4,
  CONFIRMED: 5,
  PENDING_QUOTE: 6,
  QUOTING: 7,
  QUOTED: 8,
  DEAL: 9,
  CANCELLED: 10,
} as const;

interface StatusMeta {
  text: string;
  color: string;
}

// 配色沿用 PRD 6.1.2：待解析/解析中=灰，待确认=橙，解析失败=红，已确认~已报价=蓝，已成交=绿，已取消=灰
export const CUSTOMER_INQUIRY_STATUS_META: Record<number, StatusMeta> = {
  [CUSTOMER_INQUIRY_STATUS.PENDING_PARSE]: { text: '待解析', color: 'default' },
  [CUSTOMER_INQUIRY_STATUS.PARSING]: { text: '解析中', color: 'default' },
  [CUSTOMER_INQUIRY_STATUS.PENDING_CONFIRM]: {
    text: '待确认',
    color: 'orange',
  },
  [CUSTOMER_INQUIRY_STATUS.PARSE_FAILED]: { text: '解析失败', color: 'red' },
  [CUSTOMER_INQUIRY_STATUS.CONFIRMED]: { text: '已确认', color: 'blue' },
  [CUSTOMER_INQUIRY_STATUS.PENDING_QUOTE]: { text: '待报价', color: 'blue' },
  [CUSTOMER_INQUIRY_STATUS.QUOTING]: { text: '报价中', color: 'blue' },
  [CUSTOMER_INQUIRY_STATUS.QUOTED]: { text: '已报价', color: 'blue' },
  [CUSTOMER_INQUIRY_STATUS.DEAL]: { text: '已成交', color: 'green' },
  [CUSTOMER_INQUIRY_STATUS.CANCELLED]: { text: '已取消', color: 'default' },
};

export const CUSTOMER_INQUIRY_MANUAL_ADVANCE_SEQUENCE = [
  CUSTOMER_INQUIRY_STATUS.CONFIRMED,
  CUSTOMER_INQUIRY_STATUS.PENDING_QUOTE,
  CUSTOMER_INQUIRY_STATUS.QUOTING,
  CUSTOMER_INQUIRY_STATUS.QUOTED,
  CUSTOMER_INQUIRY_STATUS.DEAL,
];

export const CUSTOMER_INQUIRY_SOURCE_META: Record<number, string> = {
  1: '文本输入',
  2: 'Excel文件',
  3: '图片输入',
  4: '微信群',
  5: '邮件',
  6: '平台',
  7: '其他',
};

export const CONFIDENCE_META: Record<number, StatusMeta> = {
  1: { text: '确认', color: 'green' },
  2: { text: '已纠正', color: 'blue' },
  3: { text: '待核实', color: 'gold' },
  4: { text: '未识别', color: 'red' },
};

export const INQUIRY_ORDER_STATUS = {
  PENDING_ASSIGN: 1,
  ASSIGNED: 2,
  SENT_TO_SUPPLIER: 3,
  QUOTE_RECEIVED: 4,
  QUOTED_TO_CUSTOMER: 5,
  DEAL: 6,
  CANCELLED: 7,
} as const;

export const INQUIRY_ORDER_STATUS_META: Record<number, StatusMeta> = {
  1: { text: '待分配', color: 'default' },
  2: { text: '已分配', color: 'blue' },
  3: { text: '已发供应商', color: 'blue' },
  4: { text: '已收报价', color: 'blue' },
  5: { text: '已报客户', color: 'blue' },
  6: { text: '已成交', color: 'green' },
  7: { text: '已取消', color: 'default' },
};

// 人工可推进的状态序列，与后端 InquiryOrderStatus.MANUAL_ADVANCE_SEQUENCE 一致（不含取消）
export const INQUIRY_ORDER_MANUAL_ADVANCE_SEQUENCE = [
  INQUIRY_ORDER_STATUS.ASSIGNED,
  INQUIRY_ORDER_STATUS.SENT_TO_SUPPLIER,
  INQUIRY_ORDER_STATUS.QUOTE_RECEIVED,
  INQUIRY_ORDER_STATUS.QUOTED_TO_CUSTOMER,
  INQUIRY_ORDER_STATUS.DEAL,
];

// 报价来源类型：1=正式供应商 2=电商询价渠道，见 design.md 决策11
export const SOURCE_TYPE = {
  FORMAL_SUPPLIER: 1,
  ECOMMERCE_CHANNEL: 2,
} as const;

export const CHANNEL_PLATFORM_META: Record<number, string> = {
  1: '淘宝',
  2: '1688',
  3: '闲鱼',
  4: '其他',
};

export const SUPPLIER_ASSOCIATION_STATUS_META: Record<number, StatusMeta> = {
  1: { text: '待发送', color: 'default' },
  2: { text: '已发送', color: 'blue' },
  3: { text: '已回复', color: 'green' },
  4: { text: '未回复', color: 'red' },
};

export const QUOTE_STATUS_META: Record<number, StatusMeta> = {
  1: { text: '待报价', color: 'default' },
  2: { text: '已报价', color: 'blue' },
  3: { text: '无法报价', color: 'red' },
  4: { text: '客户确认', color: 'green' },
};
