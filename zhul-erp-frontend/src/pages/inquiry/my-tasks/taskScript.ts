/** 合并询价话术：把一个任务的型号合成一段话，一次发给同一家店铺或供应商 */
export const TASK_SCRIPT_HEAD =
  '你好，帮我查一下以下型号，报不含税价，有货的话货期也说一下，谢谢：';

export interface ScriptItem {
  model: string;
  quantity: number;
  unit?: string;
  /** 本人在该型号的回价记录（草稿、已提交、无货） */
  quotes: unknown[];
}

/** 本人还没有任何回价记录的型号 */
export const pendingItems = <T extends ScriptItem>(items: T[]) =>
  items.filter((i) => i.quotes.length === 0);

/** 「开头一句 + 编号清单」；品牌为空只写型号，单位为空写「个」 */
export const buildTaskScript = (
  brand: string | undefined,
  items: ScriptItem[],
) =>
  [
    TASK_SCRIPT_HEAD,
    ...items.map(
      (i, n) =>
        `${n + 1}. ${brand?.trim() ? `${brand.trim()} ` : ''}${i.model}，要 ${i.quantity} ${i.unit?.trim() || '个'}`,
    ),
  ].join('\n');
