import type { PasteRow } from '../shared/service';
import { aiSummary, editPrice, lineCount, shownPrice } from './pasteAi';

const row = (p: Partial<PasteRow>): PasteRow => ({
  raw: '',
  itemId: 1,
  unitPrice: 2850,
  taxIncluded: false,
  itemCondition: 0,
  leadTime: 0,
  note: '',
  status: 'MATCHED',
  locked: false,
  ...p,
});

describe('粘贴报价 AI 识别的预览', () => {
  it('含税行显示不含税价，改价后按不含税填入', () => {
    const taxed = row({
      taxIncluded: true,
      taxRate: 13,
      unitPriceExTax: 2522.12,
    });
    expect(shownPrice(taxed)).toBe(2522.12);
    expect(editPrice(taxed, 2500)).toEqual({
      unitPrice: 2500,
      unitPriceExTax: 2500,
      taxIncluded: false,
      taxRate: null,
    });
    expect(shownPrice(row({}))).toBe(2850);
    expect(editPrice(row({}), null)).toEqual({
      unitPrice: null,
      unitPriceExTax: null,
    });
  });

  it('统计找到几个型号、几个请核对', () => {
    const rows = [
      row({ itemId: 1, confidence: 'CHECK' }),
      row({ itemId: 2, confidence: 'SURE' }),
      row({ itemId: 3, confidence: 'SURE' }),
    ];
    expect(aiSummary(rows, 3)).toBe('本任务 3 个型号全部找到 · 1 个请核对');
    expect(aiSummary(rows.slice(1), 3)).toBe('本任务 3 个型号找到 2 个');
    expect(aiSummary([], 0)).toBe('本任务 0 个型号找到 0 个');
  });

  it('原文行数不算空行', () => {
    expect(lineCount('a\n\n b \n')).toBe(2);
  });
});
