/**
 * 列表重新加载后该选中哪个任务：原来选中的还在列表里就保留，否则选第一个。
 * kept 为 true 表示仍是原任务——选中项没变，明细不会因选中变化而重新加载，需要调用方自己刷新。
 */
export const nextActiveTask = (
  prev: number | undefined,
  list: { id: number }[],
): { id: number | undefined; kept: boolean } => {
  if (prev && list.some((t) => t.id === prev)) return { id: prev, kept: true };
  return { id: list[0]?.id, kept: false };
};
