import { defaultPayMethod, methodMismatch, suggestMethod } from './payMethod';
import { METHOD } from './service';

describe('登记付款按应付单约定预选（openspec payable-payment-method）', () => {
  it('全对公预选公司对公，全对私预选私对私', () => {
    expect(suggestMethod([1, 1])).toEqual({
      method: METHOD.COMPANY,
      mixed: false,
    });
    expect(suggestMethod([2])).toEqual({
      method: METHOD.PRIVATE,
      mixed: false,
    });
  });

  it('混合时不预选并标出不一致，未定的不参与判断', () => {
    expect(suggestMethod([1, 2])).toEqual({ mixed: true });
    expect(suggestMethod([0, 2])).toEqual({
      method: METHOD.PRIVATE,
      mixed: false,
    });
    expect(suggestMethod([0, undefined])).toEqual({ mixed: false });
    expect(suggestMethod([])).toEqual({ mixed: false });
  });

  it('改选与约定不一致时提示，员工垫付按对私算', () => {
    expect(methodMismatch(METHOD.COMPANY, [2])).toBe('这张应付单约定对私支付');
    expect(methodMismatch(METHOD.PRIVATE, [1, 1])).toBe(
      '所选应付单约定对公支付',
    );
    expect(methodMismatch(METHOD.ADVANCE, [1])).toBe('这张应付单约定对公支付');
    expect(methodMismatch(METHOD.ADVANCE, [2])).toBeUndefined();
    expect(methodMismatch(METHOD.COMPANY, [1])).toBeUndefined();
    expect(methodMismatch(METHOD.COMPANY, [1, 2])).toBeUndefined();
    expect(methodMismatch(METHOD.COMPANY, [0])).toBeUndefined();
  });
});

describe('采购订单付款方式默认值', () => {
  it('供应商开票对公，不开票或线上店铺对私', () => {
    expect(defaultPayMethod(false, true)).toBe(1);
    expect(defaultPayMethod(false, false)).toBe(2);
    expect(defaultPayMethod(true, true)).toBe(2);
  });
});
