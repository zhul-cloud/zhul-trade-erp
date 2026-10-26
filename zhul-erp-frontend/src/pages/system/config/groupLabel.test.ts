import { GROUP_OPTIONS, groupLabel } from './groupLabel';

describe('groupLabel', () => {
  it('内置配置的英文代码显示为 2 个字的中文名', () => {
    for (const code of [
      'inquiry',
      'quotation',
      'document',
      'sales',
      'purchase',
      'product',
      'finance',
      'appearance',
    ]) {
      expect(Array.from(groupLabel(code))).toHaveLength(2);
    }
    expect(groupLabel('appearance')).toBe('外观');
    expect(groupLabel('inquiry')).toBe('询价');
  });

  it('自定义配置的分组也存代码，显示为 2 个字', () => {
    expect(GROUP_OPTIONS).toEqual(['basic', 'security', 'mail', 'custom']);
    for (const g of GROUP_OPTIONS) {
      expect(g).toMatch(/^[a-z]+$/);
      expect(Array.from(groupLabel(g))).toHaveLength(2);
    }
    expect(groupLabel('custom')).toBe('其他');
  });

  it('对照表里没有的值原样显示', () => {
    expect(groupLabel('warehouse')).toBe('warehouse');
  });

  it('新增自定义配置不能选外观', () => {
    expect(GROUP_OPTIONS).not.toContain('appearance');
  });
});
