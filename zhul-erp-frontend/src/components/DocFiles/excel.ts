/** Excel 附件预览：只读出单元格显示的文字，不计算公式、不执行宏；每个工作表最多显示 1000 行 */
export const MAX_ROWS = 1000;

export interface SheetData {
  name: string;
  /** 列标题 A、B、C… */
  columns: string[];
  rows: string[][];
  truncated: boolean;
}

type Xlsx = typeof import('xlsx');

/** 解析工作簿（SheetJS 由调用方传入，页面里按需加载） */
export const parseSheets = (XLSX: Xlsx, buf: ArrayBuffer): SheetData[] => {
  const wb = XLSX.read(buf, {
    type: 'array',
    cellFormula: false,
    cellHTML: false,
    sheetStubs: false,
  });
  return wb.SheetNames.map((name) => {
    const ws = wb.Sheets[name];
    const all = XLSX.utils.sheet_to_json<string[]>(ws, {
      header: 1,
      raw: false,
      defval: '',
      blankrows: true,
    });
    const rows = all
      .slice(0, MAX_ROWS)
      .map((r) => r.map((c) => String(c ?? '')));
    const width = rows.reduce((m, r) => Math.max(m, r.length), 0);
    return {
      name,
      columns: Array.from({ length: width }, (_, i) =>
        XLSX.utils.encode_col(i),
      ),
      rows,
      truncated: all.length > MAX_ROWS,
    };
  });
};

/** 按需加载 SheetJS 后解析，不进首屏包 */
export const readSheets = async (buf: ArrayBuffer): Promise<SheetData[]> =>
  parseSheets(await import('xlsx'), buf);
