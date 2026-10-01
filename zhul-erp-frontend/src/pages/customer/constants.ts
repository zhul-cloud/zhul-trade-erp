/** 码值与后端 CustomerConstants、V1.2.9 迁移注释一致；0 为「未设置 / 未分级」 */
type Option = { value: number; label: string };

export const ROLE_OPTIONS: Option[] = [
  { value: 1, label: '终端用户' },
  { value: 2, label: '系统集成商' },
  { value: 3, label: '经销商' },
  { value: 4, label: '贸易商' },
  { value: 5, label: 'OEM 设备厂' },
  { value: 6, label: '维修服务商' },
  { value: 7, label: '其他' },
];

export const INDUSTRY_OPTIONS: Option[] = [
  '汽车制造',
  '电子半导体',
  '食品饮料',
  '包装印刷',
  '纺织',
  '冶金钢铁',
  '石油化工',
  '水处理',
  '电力能源',
  '矿山',
  '港口物流',
  '楼宇暖通',
  '其他',
].map((label, i) => ({ value: i + 1, label }));

export const GRADE_OPTIONS: Option[] = [
  { value: 1, label: 'A' },
  { value: 2, label: 'B' },
  { value: 3, label: 'C' },
  { value: 0, label: '未分级' },
];

export const SOURCE_OPTIONS: Option[] = [
  '阿里巴巴国际站',
  '中国制造网',
  '独立站',
  '展会',
  '社交媒体',
  '老客户转介绍',
  '主动开发',
  '其他',
].map((label, i) => ({ value: i + 1, label }));

export const PAYMENT_OPTIONS: Option[] = [
  'T/T 全额预付',
  'T/T 定金 + 发货前付尾款',
  'T/T 定金 + 见提单副本付尾款',
  'L/C 即期',
  'L/C 远期',
  'D/P',
  'D/A',
  'O/A 赊销',
  '其他',
].map((label, i) => ({ value: i + 1, label }));
/** 需要定金比例 / 账期的付款方式 */
export const PAYMENT_WITH_DEPOSIT = [2, 3];
export const PAYMENT_WITH_DAYS = [5, 7, 8];

export const SHIPPING_OPTIONS: Option[] = [
  '海运整柜',
  '海运拼箱',
  '空运',
  '国际快递',
  '铁路',
  '陆运',
].map((label, i) => ({ value: i + 1, label }));

export const INCOTERMS = [
  'EXW',
  'FCA',
  'FOB',
  'CFR',
  'CIF',
  'CPT',
  'CIP',
  'DAP',
  'DPU',
  'DDP',
];

export const CURRENCY_OPTIONS = [
  { value: 'USD', label: 'USD 美元' },
  { value: 'EUR', label: 'EUR 欧元' },
  { value: 'GBP', label: 'GBP 英镑' },
  { value: 'JPY', label: 'JPY 日元' },
  { value: 'CNY', label: 'CNY 人民币' },
];

export const PARTY_TYPES: { value: number; label: string; en: string }[] = [
  { value: 1, label: '收货人', en: 'Consignee' },
  { value: 2, label: '通知方', en: 'Notify Party' },
  { value: 3, label: '发票抬头', en: 'Bill To' },
];
export const MAX_PARTIES = 20;

export const labelOf = (options: Option[], value?: number | null) =>
  options.find((o) => o.value === value)?.label;

/** 角色标签配色（原型）：经销商蓝、集成商青、贸易商橙、OEM 绿、终端用户紫，其余灰 */
export type Tone = 'accent' | 'cyan' | 'orange' | 'green' | 'violet' | 'gray';
export const ROLE_TONE: Record<number, Tone> = {
  1: 'violet',
  2: 'cyan',
  3: 'accent',
  4: 'orange',
  5: 'green',
};
export const GRADE_TONE: Record<number, Tone> = {
  1: 'green',
  2: 'accent',
  3: 'orange',
};

/** 电话 / WhatsApp：+ 数字 空格 - 括号，最多 30 位 */
export const PHONE_PATTERN = /^[+0-9 ()-]{0,30}$/;
export const CUSTOMER_CODE_PATTERN = /^[A-Za-z0-9]{1,20}$/;
/** 单据字段不允许中日韩文字（允许欧洲语言变音字母） */
export const CJK_PATTERN =
  /[\u3040-\u30ff\u3400-\u9fff\uac00-\ud7af\uf900-\ufaff]/;
export const ENGLISH_ONLY_MESSAGE = '单据字段请使用英文';

export const DISABLE_CONFIRM_TEXT =
  '禁用后该客户将无法在新的询盘、报价、订单中被选择，已有单据不受影响。';
