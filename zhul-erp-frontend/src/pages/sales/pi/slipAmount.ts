import type { Receipt } from '../service';

/** 收款记录类型：1-水单（与 components.KIND.SLIP 一致） */
const SLIP = 1;

type SlipAmountOwner = {
  totalAmount: number;
  receivedAmount?: number;
  remainingAmount?: number;
  receipts: Pick<Receipt, 'kind' | 'status' | 'matched' | 'amount'>[];
};

/**
 * 水单默认付款金额：合计减去已到账（含手续费差额）和还没对上到账的有效水单；
 * 都已覆盖时留空，部分付款时由业务员改。
 */
export const defaultSlipAmount = (owner?: SlipAmountOwner) => {
  if (!owner) return null;
  const pendingSlips = owner.receipts
    .filter((r) => r.kind === SLIP && r.status === 1 && !r.matched)
    .reduce((sum, r) => sum + r.amount, 0);
  const rest =
    (owner.remainingAmount ?? owner.totalAmount - (owner.receivedAmount ?? 0)) -
    pendingSlips;
  const v = Math.round(rest * 100) / 100;
  return v > 0 ? v : null;
};
