/** 询价协作的码值，与后端 InquiryConstants 一致 */

export type Tone =
  | 'gray'
  | 'cyan'
  | 'accent'
  | 'violet'
  | 'orange'
  | 'green'
  | 'red'
  | 'mute';

export const STATUS = {
  PENDING_PARSE: 1,
  PARSING: 2,
  PENDING_CONFIRM: 3,
  PARSE_FAILED: 4,
  SOURCING: 5,
  READY: 6,
  QUOTED: 7,
  WON: 8,
  LOST: 9,
  CANCELLED: 10,
} as const;

export const STATUS_META: Record<number, { label: string; tone: Tone }> = {
  1: { label: '待解析', tone: 'gray' },
  2: { label: '解析中', tone: 'cyan' },
  3: { label: '待确认', tone: 'orange' },
  4: { label: '解析失败', tone: 'red' },
  5: { label: '询价中', tone: 'accent' },
  6: { label: '可报价', tone: 'green' },
  7: { label: '已报价', tone: 'violet' },
  8: { label: '已成交', tone: 'green' },
  9: { label: '未成交', tone: 'mute' },
  10: { label: '已取消', tone: 'mute' },
};

export const CUSTOMER_NEW = 1;
export const CUSTOMER_RETURNING = 2;

/** 询盘等级默认 3-B；名称取字典 inquiry_level */
export const LEVEL_DEFAULT = 3;

/** 生命周期码值中有固定含义的两个，名称与其余选项取字典 inquiry_lifecycle */
export const LIFECYCLE_DISCONTINUED = 2;
export const LIFECYCLE_UNKNOWN = 3;

export const DIFFICULTY_OPTIONS = [
  { value: 0, label: '未评估' },
  { value: 1, label: '简单' },
  { value: 2, label: '中等' },
  { value: 3, label: '困难' },
];

export const CONFIDENCE = {
  CONFIRMED: 1,
  CORRECTED: 2,
  PENDING_VERIFY: 3,
  UNRECOGNIZED: 4,
} as const;

export const PRICE_SOURCE_HISTORY = 1;
export const ITEM_PENDING = 1;
export const ITEM_PRICED = 2;
export const ITEM_NO_STOCK = 3;

export const TASK_STATUS_META: Record<number, { label: string; tone: Tone }> = {
  1: { label: '待分配', tone: 'orange' },
  2: { label: '询价中', tone: 'accent' },
  3: { label: '已回价', tone: 'green' },
  4: { label: '已取消', tone: 'mute' },
};

/** 询价渠道码值：新报价默认淘宝；选供应商时从供应商主数据里选 */
export const CHANNEL_TAOBAO = 1;
export const CHANNEL_SUPPLIER = 4;

export const CHANNEL_OPTIONS = [
  { value: 1, label: '淘宝' },
  { value: 2, label: '1688' },
  { value: 3, label: '闲鱼' },
  { value: 4, label: '供应商' },
  { value: 5, label: '其他' },
];
export const channelLabel = (v?: number) =>
  CHANNEL_OPTIONS.find((o) => o.value === v)?.label ?? '—';

export const conditionTone = (v?: number): Tone =>
  v === 1
    ? 'green'
    : v === 4 || v === 5
      ? 'orange'
      : v === 6
        ? 'violet'
        : 'gray';

export const RETURN_REASONS = [
  { value: 1, label: '型号存疑' },
  { value: 2, label: '停产无货' },
  { value: 3, label: '超出能力' },
  { value: 9, label: '其他' },
];

export const ATTACHMENT_ACCEPT =
  '.jpg,.jpeg,.png,.xls,.xlsx,.csv,.pdf,image/jpeg,image/png,application/pdf';
export const ATTACHMENT_MAX_BYTES = 10 * 1024 * 1024;
export const MAX_ATTACHMENTS = 10;

export const PATHS = {
  inquiries: '/inquiry/customer-inquiries',
  board: '/inquiry/sourcing-board',
  rules: '/inquiry/sourcing-board/rules',
  myTasks: '/inquiry/my-tasks',
  history: '/inquiry/price-history',
  partTimeBoard: '/inquiry/part-time-board',
};

/** 货源直达：按关键词拼淘宝、1688、闲鱼的搜索地址 */
export const searchLinks = (keyword: string) => {
  const q = encodeURIComponent(keyword);
  return [
    { platform: '淘宝', url: `https://s.taobao.com/search?q=${q}` },
    {
      platform: '1688',
      url: `https://s.1688.com/selloffer/offer_search.htm?keywords=${q}`,
    },
    { platform: '闲鱼', url: `https://www.goofish.com/search?q=${q}` },
  ];
};

/** 等待 / 剩余时长的中文表述 */
export const formatMinutes = (minutes?: number | null) => {
  if (minutes == null) return '—';
  const m = Math.abs(minutes);
  if (m < 60) return `${m} 分钟`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h} 小时${m % 60 ? ` ${m % 60} 分` : ''}`;
  return `${Math.floor(h / 24)} 天${h % 24 ? ` ${h % 24} 小时` : ''}`;
};
