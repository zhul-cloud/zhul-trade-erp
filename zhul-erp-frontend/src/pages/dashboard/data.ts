export interface KpiCardData {
  icon: 'shopping-cart' | 'inbox' | 'wallet' | 'rise';
  label: string;
  value: string;
  hint: string;
  hintType: 'up' | 'down' | 'warning';
}

export const kpiCards: KpiCardData[] = [
  {
    icon: 'shopping-cart',
    label: '本月订单金额',
    value: '¥1,286,400',
    hint: '▲ 12.4% 较上月',
    hintType: 'up',
  },
  {
    icon: 'inbox',
    label: '待处理询盘',
    value: '18',
    hint: '3条超24小时未跟进',
    hintType: 'warning',
  },
  {
    icon: 'wallet',
    label: '应收账款余额',
    value: '$342,150',
    hint: '其中逾期 $28,600',
    hintType: 'down',
  },
  {
    icon: 'rise',
    label: '本月毛利率',
    value: '27.8%',
    hint: '▲ 1.2pt 较上月',
    hintType: 'up',
  },
];

export interface OrderTrendPoint {
  month: string;
  value: number;
}

export const orderTrend: OrderTrendPoint[] = [
  { month: '3月', value: 62 },
  { month: '4月', value: 78 },
  { month: '5月', value: 70 },
  { month: '6月', value: 95 },
  { month: '7月', value: 88 },
  { month: '8月', value: 128 },
];

export interface TodoItemData {
  icon: 'file-done' | 'clock' | 'warning' | 'truck';
  label: string;
  desc: string;
  badge: number;
}

export const todoItems: TodoItemData[] = [
  { icon: 'file-done', label: '待审批合同', desc: '3份合同待审批', badge: 3 },
  {
    icon: 'clock',
    label: '即将到期信用证',
    desc: '2笔LC将于7日内到期',
    badge: 2,
  },
  { icon: 'warning', label: '逾期应收账款', desc: '5笔账款已逾期', badge: 5 },
  { icon: 'truck', label: '待发货订单', desc: '8个订单待安排发货', badge: 8 },
];

export interface QuickActionData {
  icon:
    | 'message'
    | 'file-add'
    | 'shopping-cart'
    | 'appstore-add'
    | 'user-add'
    | 'send';
  label: string;
}

export const quickActions: QuickActionData[] = [
  { icon: 'message', label: '新建询盘' },
  { icon: 'file-add', label: '新建报价' },
  { icon: 'shopping-cart', label: '新建订单' },
  { icon: 'appstore-add', label: '新建采购单' },
  { icon: 'user-add', label: '新建客户' },
  { icon: 'send', label: '发起付款' },
];

export interface FxRateData {
  currency: string;
  label: string;
  rate: string;
  up: boolean;
  pct: string;
}

export const fxRates: FxRateData[] = [
  { currency: 'USD', label: '美元', rate: '7.1235', up: true, pct: '0.15%' },
  { currency: 'EUR', label: '欧元', rate: '7.7820', up: false, pct: '0.08%' },
  { currency: 'GBP', label: '英镑', rate: '9.0142', up: true, pct: '0.22%' },
  {
    currency: 'JPY',
    label: '日元(100)',
    rate: '4.6390',
    up: false,
    pct: '0.05%',
  },
];

export interface LogisticsStageData {
  label: string;
  count: number;
  color: string;
}

export const logisticsStages: LogisticsStageData[] = [
  { label: '待报关', count: 6, color: '#9CA3AF' },
  { label: '运输中', count: 14, color: '#1677FF' },
  { label: '目的港清关中', count: 3, color: '#F97316' },
  { label: '本月已完成', count: 22, color: '#16A34A' },
];

export interface TopCustomerData {
  name: string;
  country: string;
  amount: number;
  pct: number;
}

export const topCustomers: TopCustomerData[] = [
  {
    name: 'Global Trade Partners LLC',
    country: '美国',
    amount: 68.4,
    pct: 100,
  },
  { name: 'Hamburg Import GmbH', country: '德国', amount: 52.1, pct: 76 },
  { name: 'Nordic Retail Group', country: '瑞典', amount: 41.7, pct: 61 },
  { name: 'Tokyo Wholesale Co.', country: '日本', amount: 33.5, pct: 49 },
  { name: 'Emirates Sourcing FZE', country: '阿联酋', amount: 27.9, pct: 41 },
];

export interface ActivityItemData {
  who: string;
  action: string;
  time: string;
  color: string;
}

export const activities: ActivityItemData[] = [
  {
    who: '张明',
    action: '创建了新订单 SO2026-0842',
    time: '12分钟前',
    color: '#1677FF',
  },
  {
    who: '李薇',
    action: '审批通过合同 CT2026-0316',
    time: '45分钟前',
    color: '#7C3AED',
  },
  {
    who: '王强',
    action: '确认收款 $18,600（Hamburg Import）',
    time: '2小时前',
    color: '#F97316',
  },
  {
    who: '系统',
    action: '汇率自动更新：USD/CNY 7.1235',
    time: '3小时前',
    color: '#16A34A',
  },
  {
    who: '陈静',
    action: '提交采购单 PO2026-0507 待审批',
    time: '5小时前',
    color: '#DC2626',
  },
];
