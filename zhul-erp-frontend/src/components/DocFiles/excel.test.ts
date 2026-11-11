import * as XLSX from 'xlsx';
import { MAX_ROWS, parseSheets } from './excel';

const book = (sheets: Record<string, unknown[][]>) => {
  const wb = XLSX.utils.book_new();
  Object.entries(sheets).forEach(([name, rows]) => {
    XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(rows), name);
  });
  return XLSX.write(wb, { type: 'array', bookType: 'xlsx' }) as ArrayBuffer;
};

describe('parseSheets', () => {
  it('读出每个工作表的文字与列标题', () => {
    const sheets = parseSheets(
      XLSX,
      book({
        退货: [
          ['型号', '数量'],
          ['HG-SN202BJ-S100', 1],
        ],
        说明: [['外壳烧焦']],
      }),
    );
    expect(sheets.map((s) => s.name)).toEqual(['退货', '说明']);
    expect(sheets[0].columns).toEqual(['A', 'B']);
    expect(sheets[0].rows[1]).toEqual(['HG-SN202BJ-S100', '1']);
    expect(sheets[0].truncated).toBe(false);
  });

  it('公式只显示结果，不保留公式', () => {
    const wb = XLSX.utils.book_new();
    const ws = XLSX.utils.aoa_to_sheet([[1, 2]]);
    ws.C1 = { t: 'n', v: 3, f: 'A1+B1' };
    ws['!ref'] = 'A1:C1';
    XLSX.utils.book_append_sheet(wb, ws, 'S');
    const [s] = parseSheets(
      XLSX,
      XLSX.write(wb, { type: 'array', bookType: 'xlsx' }) as ArrayBuffer,
    );
    expect(s.rows[0]).toEqual(['1', '2', '3']);
  });

  it(`超过 ${MAX_ROWS} 行时截断并标记`, () => {
    const rows = Array.from({ length: MAX_ROWS + 5 }, (_, i) => [i]);
    const [s] = parseSheets(XLSX, book({ S: rows }));
    expect(s.rows).toHaveLength(MAX_ROWS);
    expect(s.truncated).toBe(true);
  });
});
