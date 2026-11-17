import { CalendarOutlined } from '@ant-design/icons';
import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import type { Payable } from './service';

/** 当期应付（spec finance/supplier-payment「登记付款」）：登记付款、批量付款的默认金额 */

const round2 = (n: number) => Math.round(n * 100) / 100;

/** 默认付款金额：后端算出的当期应付；接口没带时退回全部未付 */
export const dueNowOf = (r: Payable) =>
  round2(r.dueNowAmount ?? r.unpaidAmount);

/** 一组应付单的当期应付合计与说明（批量付款每组一行说明） */
export const dueNowOfRows = (rows: Payable[]) => ({
  amount: round2(rows.reduce((s, r) => s + dueNowOf(r), 0)),
  note:
    rows.length === 1
      ? (rows[0].dueNowNote ?? '')
      : rows
          .filter((r) => r.dueNowNote)
          .map((r) => `${r.payableNo} ${r.dueNowNote}`)
          .join('；'),
});

/**
 * 付款金额下面的说明：默认金额由哪几期组成，「付全部未付」/「改回当期应付」一键切换。
 * 当期应付就是全部未付且没有付款计划时不显示。
 */
export const DueNowHint: React.FC<{
  amount: number | null;
  due: number;
  unpaid: number;
  note: string;
  currency: string;
  onChange: (amount: number) => void;
}> = ({ amount, due, unpaid, note, currency, onChange }) => {
  const { palette } = useAppTheme();
  const partial = due < unpaid;
  if (!partial && !note) return null;
  const all = amount != null && round2(amount) === round2(unpaid);
  const text = all && partial ? '全部未付' : `当期应付：${note || '全部未付'}`;
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 6,
        marginTop: 6,
        fontSize: 12,
        color: palette.mute,
      }}
    >
      <CalendarOutlined />
      <span style={{ flex: 1, minWidth: 0 }}>{text}</span>
      {partial && (
        <button
          type="button"
          className="zhul-link"
          style={{ whiteSpace: 'nowrap' }}
          onClick={() => onChange(all ? due : unpaid)}
        >
          {all
            ? `改回当期应付 ${formatAmount(due, currency)}`
            : `付全部未付 ${formatAmount(unpaid, currency)}`}
        </button>
      )}
    </div>
  );
};
