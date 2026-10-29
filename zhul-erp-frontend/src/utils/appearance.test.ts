import { DEFAULT_APPEARANCE, logoChar } from './appearance';

describe('logoChar', () => {
  it('取系统名称的第一个字', () => {
    expect(logoChar('烛龙ERP')).toBe('烛');
    expect(logoChar('福唯ERP')).toBe('福');
  });

  it('英文名称取第一个字母', () => {
    expect(logoChar('Fouwell ERP')).toBe('F');
  });

  it('去掉首尾空格后再取', () => {
    expect(logoChar('  福唯 ')).toBe('福');
  });

  it('名称为空时回退为「烛」', () => {
    expect(logoChar('')).toBe('烛');
    expect(logoChar('   ')).toBe('烛');
    expect(logoChar(undefined)).toBe('烛');
  });

  it('生僻字与表情不会被拆成半个字符', () => {
    expect(logoChar('𠮷野家')).toBe('𠮷');
    expect(logoChar('🐉龙')).toBe('🐉');
  });

  it('默认系统名称对应「烛」', () => {
    expect(logoChar(DEFAULT_APPEARANCE.siteName)).toBe('烛');
  });
});
