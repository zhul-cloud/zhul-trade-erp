import { defaultSlipAmount } from './slipAmount';

const slip = (amount: number, matched = false, status = 1) => ({
  kind: 1,
  status,
  matched,
  amount,
});

describe('defaultSlipAmount', () => {
  it('没有任何收款时默认填合计', () => {
    expect(
      defaultSlipAmount({
        totalAmount: 441.1,
        remainingAmount: 441.1,
        receipts: [],
      }),
    ).toBe(441.1);
  });

  it('已有一笔还没到账的水单时扣掉它', () => {
    expect(
      defaultSlipAmount({
        totalAmount: 1000,
        remainingAmount: 1000,
        receipts: [slip(300)],
      }),
    ).toBe(700);
  });

  it('已对上到账的水单不重复扣，作废的水单不扣', () => {
    expect(
      defaultSlipAmount({
        totalAmount: 1000,
        remainingAmount: 700,
        receipts: [
          slip(300, true),
          slip(200, false, 2),
          { kind: 2, status: 1, matched: false, amount: 300 },
        ],
      }),
    ).toBe(700);
  });

  it('余额扣到 0 或以下时留空', () => {
    expect(
      defaultSlipAmount({
        totalAmount: 441.1,
        remainingAmount: 0,
        receipts: [],
      }),
    ).toBeNull();
    expect(
      defaultSlipAmount({
        totalAmount: 500,
        remainingAmount: 500,
        receipts: [slip(500)],
      }),
    ).toBeNull();
  });

  it('精度：两位小数四舍五入', () => {
    expect(
      defaultSlipAmount({
        totalAmount: 100.3,
        remainingAmount: 100.3,
        receipts: [slip(0.1), slip(0.2)],
      }),
    ).toBe(100);
  });

  it('没有余额字段时按合计减已到账', () => {
    expect(
      defaultSlipAmount({
        totalAmount: 800,
        receivedAmount: 300,
        receipts: [],
      }),
    ).toBe(500);
  });
});
