import { tableScrollX } from './tableScroll';

describe('tableScrollX（audit UI-041）', () => {
  it('按各列宽度求和，没写宽度的列按 120 计', () => {
    expect(tableScrollX([{ width: 160 }, { width: 90 }, {}])).toBe(370);
  });

  it('隐藏的列不计入', () => {
    expect(
      tableScrollX([
        { width: 100 },
        { width: 200, hidden: true },
        { width: 50, hideInTable: true },
      ]),
    ).toBe(100);
  });

  it('分组表头按子列求和', () => {
    expect(
      tableScrollX([
        { children: [{ width: 80 }, { width: 70 }] },
        { width: 100 },
      ]),
    ).toBe(250);
  });

  it('百分比等非数字宽度按默认宽度计', () => {
    expect(tableScrollX([{ width: '20%' }])).toBe(120);
  });

  it('没有列时为 0', () => {
    expect(tableScrollX(undefined)).toBe(0);
  });
});
