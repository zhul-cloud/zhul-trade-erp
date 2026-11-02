/** 询盘附件与铭牌图片的展示规则（spec inquiry/inquiry-intake「附件预览」） */

export interface PreviewFile {
  id: number;
  fileName: string;
  contentType?: string;
}

export type FileKind = 'image' | 'pdf' | 'sheet';

const ext = (name: string) => name.split('.').pop()?.toLowerCase() ?? '';

/** 按文件类型（没有时按扩展名）分成图片、PDF 与表格 */
export const fileKind = (f: PreviewFile): FileKind => {
  const type = f.contentType ?? '';
  const e = ext(f.fileName);
  if (type.startsWith('image/') || ['jpg', 'jpeg', 'png'].includes(e))
    return 'image';
  if (type === 'application/pdf' || e === 'pdf') return 'pdf';
  return 'sheet';
};

/** 同一组里能左右切换的只有图片；返回从 index 出发、往 step 方向的下一张图片的下标，没有时不动 */
export const stepImage = (
  files: PreviewFile[],
  index: number,
  step: 1 | -1,
): number => {
  for (let i = index + step; i >= 0 && i < files.length; i += step) {
    if (fileKind(files[i]) === 'image') return i;
  }
  return index;
};

/** 标题里的「第 n / 共 m 张」：只数图片 */
export const imagePosition = (files: PreviewFile[], index: number) => {
  const images = files.filter((f) => fileKind(f) === 'image');
  return {
    n: images.indexOf(files[index]) + 1,
    total: images.length,
  };
};

/** 铭牌图片在解析确认页按行对应：一张图只给一行，返回 图片 ID → 行 key */
export const plateOwners = (
  rows: { key: string; nameplateIds?: number[] }[],
): Map<number, string> => {
  const owners = new Map<number, string>();
  for (const r of rows) {
    for (const id of r.nameplateIds ?? []) {
      if (!owners.has(id)) owners.set(id, r.key);
    }
  }
  return owners;
};

/** 把一张铭牌图改到 rowKey（为空时改为未对应），其他行里去掉这张图 */
export const movePlate = <T extends { key: string; nameplateIds?: number[] }>(
  rows: T[],
  plateId: number,
  rowKey?: string,
): T[] =>
  rows.map((r) => {
    const ids = (r.nameplateIds ?? []).filter((id) => id !== plateId);
    if (r.key === rowKey) ids.push(plateId);
    return { ...r, nameplateIds: ids };
  });
