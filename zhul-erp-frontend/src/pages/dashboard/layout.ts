import { createContext, useContext } from 'react';

/**
 * 工作台按行排列（规范「工作台」，ui-redesign D18、D33）：12 栏栅格，同一行的组件等高。
 * 高度档含卡外标题：S 180（卡 144，等于一排数字卡）、M 340（卡 304，等于两排数字卡）；
 * 组件按自己的档设计内容（列表限条数加「查看全部」，卡内不滚动）。
 */
export type Tier = 'S' | 'M';
export const TIER_HEIGHT: Record<Tier, number> = { S: 180, M: 340 };

type Size = { span: number; tier?: Tier };

/**
 * 各组件占几栏、属于哪个高度档；没登记的整行组件占 12 栏、半行占 6 栏，高度随内容（同行仍拉齐）。
 * 经营概览有主角卡时为 2×2，没有趋势权限（看不到主角卡）时退回一行 5 张（D28）。
 */
const LAYOUT: Record<string, Size | ((keys: Set<string>) => Size)> = {
  // 老板
  'revenue-trend': { span: 6, tier: 'M' },
  'biz-overview': (keys) =>
    keys.has('revenue-trend')
      ? { span: 6, tier: 'M' }
      : { span: 12, tier: 'S' },
  cash: { span: 6, tier: 'S' },
  'risk-alerts': { span: 6, tier: 'S' },
  'sales-funnel': { span: 4, tier: 'M' },
  rankings: { span: 8, tier: 'M' },
  // 业务员
  'my-todo': { span: 6, tier: 'M' },
  'overdue-pi': { span: 6, tier: 'M' },
  'my-performance': { span: 12, tier: 'S' },
  'quote-progress': { span: 5, tier: 'S' },
  'my-opportunities': { span: 7, tier: 'S' },
  // 老板、业务负责人共用：业务员对比整行
  'sales-compare': { span: 12, tier: 'M' },
  // 业务负责人（原型 I1，UI-108）：团队业绩一行 → 要跟进 | 报价转化 → 业务员对比 → 商机统计（高度随内容）
  'team-performance': { span: 12, tier: 'S' },
  'follow-ups': { span: 6, tier: 'M' },
  'quote-conversion': { span: 6, tier: 'M' },
  'opportunity-stats': { span: 12 },
  // 采购负责人（原型 I2）：询价概况一行 → 采购员效率 | 采购执行 → 砍价成果 | 应付
  'sourcing-overview': { span: 12, tier: 'S' },
  'buyer-efficiency': { span: 6, tier: 'M' },
  'purchase-execution': { span: 6, tier: 'M' },
  'bargain-result': { span: 6, tier: 'S' },
  payables: { span: 6, tier: 'S' },
  // 运营负责人（原型 I3）：内容流水线一行 → 平台发品进度 | 素材概况 → 本月产出一行
  'content-pipeline': { span: 12, tier: 'M' },
  'platform-progress': { span: 6, tier: 'M' },
  'media-overview': { span: 6, tier: 'M' },
  'monthly-output': { span: 12, tier: 'S' },
  // 采购员（原型 I4）：我的询价一行 → 回价统计 | 近 30 天回价 → 我的采购一行
  'my-inquiry': { span: 12, tier: 'M' },
  'quote-stats': { span: 4, tier: 'M' },
  'quote-trend': { span: 8, tier: 'M' },
  'my-purchase': { span: 12, tier: 'S' },
};

export interface BoardWidget {
  key: string;
  size?: string;
}

export interface LaidOut {
  key: string;
  span: number;
  tier?: Tier;
}

const sizeOf = (w: BoardWidget, keys: Set<string>): Size => {
  const l = LAYOUT[w.key];
  if (typeof l === 'function') return l(keys);
  return l ?? { span: w.size === 'full' ? 12 : 6 };
};

/**
 * 按模板顺序装行：放不下或高度档不同就换行；一行没占满时最后一个组件补满整行（落单的组件改整行宽，不留空洞）。
 */
export const layoutRows = (widgets: BoardWidget[]): LaidOut[][] => {
  const keys = new Set(widgets.map((w) => w.key));
  const rows: LaidOut[][] = [];
  let row: LaidOut[] = [];
  let used = 0;
  const close = () => {
    if (row.length === 0) return;
    row[row.length - 1].span += 12 - used;
    rows.push(row);
    row = [];
    used = 0;
  };
  for (const w of widgets) {
    const s = sizeOf(w, keys);
    const span = Math.min(12, Math.max(1, s.span));
    // 放不下，或高度档不同（同档才配成一行，D18），就换行
    const rowTier = row.find((x) => x.tier)?.tier;
    if (used + span > 12 || (s.tier && rowTier && s.tier !== rowTier)) close();
    row.push({ key: w.key, span, tier: s.tier });
    used += span;
  }
  close();
  return rows;
};

/** 一行的最小高度：取行里最高的档；都没登记档时随内容 */
export const rowMinHeight = (row: LaidOut[]) =>
  Math.max(0, ...row.map((w) => (w.tier ? TIER_HEIGHT[w.tier] : 0)));

/** 当前看板上有哪些组件（经营概览要知道有没有主角卡） */
export const BoardContext = createContext<Set<string>>(new Set());
export const useBoardKeys = () => useContext(BoardContext);
