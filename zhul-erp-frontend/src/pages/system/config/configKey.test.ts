import { CONFIG_KEY_PATTERN } from './configKey';

describe('CONFIG_KEY_PATTERN', () => {
  it('点分多段、段内短横线的键合法', () => {
    for (const key of [
      'custom.order.auto-close',
      'purchase.transit.default-days',
      'finance.bu.min-share-percent',
      'sys.site.name',
      'a.bc',
    ]) {
      expect(CONFIG_KEY_PATTERN.test(key)).toBe(true);
    }
  });

  it('下划线、单段、空段、短横线开头或结尾、大写、过短过长都不合法', () => {
    for (const key of [
      'custom.order.auto_close',
      'autoclose',
      'custom..close',
      'custom.-close',
      'custom.close-',
      'Custom.close',
      'a.b',
      `custom.${'a'.repeat(122)}`,
    ]) {
      expect(CONFIG_KEY_PATTERN.test(key)).toBe(false);
    }
  });
});
