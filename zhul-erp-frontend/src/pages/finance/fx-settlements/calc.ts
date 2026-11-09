/**
 * 结汇金额 × 结汇汇率 → 实际入账人民币，四舍五入（HALF_UP）到分。
 * 金额按 2 位、汇率按 6 位转成整数后用 BigInt 相乘，避免浮点误差（如 1.005 → 1.01）。
 */
export const settleCny = (amount: number, rate: number): number => {
  const cents = BigInt(Math.round(amount * 100));
  const micro = BigInt(Math.round(rate * 1_000_000));
  const product = cents * micro; // 单位：1e-8 元
  const unit = 1_000_000n; // 1e-8 元 → 分
  const half = unit / 2n;
  const sign = product < 0n ? -1n : 1n;
  const abs = product * sign;
  return Number(((abs + half) / unit) * sign) / 100;
};

/** 实际入账人民币 ÷ 结汇金额，四舍五入（HALF_UP）到 6 位，与后端保存的汇率一致 */
export const settleRate = (amount: number, cny: number): number => {
  const cents = BigInt(Math.round(amount * 100));
  const cnyCents = BigInt(Math.round(cny * 100));
  if (cents === 0n) return 0;
  const scaled = cnyCents * 1_000_000n * 2n;
  return Number((scaled / cents + 1n) / 2n) / 1_000_000;
};
