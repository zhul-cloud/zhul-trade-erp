import { formatAmount } from './format';

describe('formatAmount（金额显示规范：币种代码 + 空格 + 两位小数千分位）', () => {
  it('整数补两位小数', () => {
    expect(formatAmount(4120, 'CNY')).toBe('CNY 4,120.00');
  });

  it('两位小数与千分位', () => {
    expect(formatAmount(1285.62, 'USD')).toBe('USD 1,285.62');
    expect(formatAmount(1234567.891, 'EUR')).toBe('EUR 1,234,567.89');
    expect(formatAmount('463.2', 'USD')).toBe('USD 463.20');
  });

  it('负数保留负号', () => {
    expect(formatAmount(-85.56, 'USD')).toBe('USD -85.56');
  });

  it('空值显示「—」', () => {
    expect(formatAmount(null)).toBe('—');
    expect(formatAmount(undefined, 'USD')).toBe('—');
    expect(formatAmount('', 'USD')).toBe('—');
    expect(formatAmount('abc', 'USD')).toBe('—');
  });

  it('没给币种时按人民币', () => {
    expect(formatAmount(0)).toBe('CNY 0.00');
  });
});
