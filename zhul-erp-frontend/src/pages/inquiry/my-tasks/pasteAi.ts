import type { PasteRow } from '../shared/service';

/** 与后端一致：AI 识别原文最多 20,000 字，按规则识别最多 10,000 字 */
export const AI_RAW_MAX = 20000;
export const RULE_RAW_MAX = 10000;
/** 后端 90 秒判超时；前端多等几秒，拿到后端的超时结果 */
export const AI_WAIT_MS = 95_000;

/** 跳到其他任务时带上的原文与渠道店铺（只在页面里，不保存） */
export interface PasteCarry {
  taskId: number;
  text: string;
  channel: number;
  shopName: string;
  supplierId?: number;
}

/** 预览里显示的单价：含税时显示不含税价，下面另起一行写含税价与税率 */
export const shownPrice = (r: PasteRow) =>
  r.taxIncluded ? (r.unitPriceExTax ?? r.unitPrice) : r.unitPrice;

/** AI 模式下改单价：改的是不含税价，改完按不含税填入 */
export const editPrice = (r: PasteRow, v: number | null): Partial<PasteRow> =>
  r.taxIncluded
    ? { unitPrice: v, unitPriceExTax: v, taxIncluded: false, taxRate: null }
    : { unitPrice: v, unitPriceExTax: v };

/** 顶部统计：找到几个、几个请核对 */
export const aiSummary = (rows: PasteRow[], itemCount: number) => {
  const found = new Set(rows.map((r) => r.itemId)).size;
  const check = rows.filter((r) => r.confidence === 'CHECK').length;
  return [
    found === itemCount && itemCount > 0
      ? `本任务 ${itemCount} 个型号全部找到`
      : `本任务 ${itemCount} 个型号找到 ${found} 个`,
    check > 0 ? `${check} 个请核对` : '',
  ]
    .filter(Boolean)
    .join(' · ');
};

/** 原文行数（不算空行） */
export const lineCount = (text: string) =>
  text.split('\n').filter((l) => l.trim()).length;
