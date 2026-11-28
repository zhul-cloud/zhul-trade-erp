import React from 'react';
import { useAppTheme } from '@/theme/AppTheme';

/** 下拉里显示一个银行账户需要的字段（收款账户、付款账户、报销收款账户共用） */
export interface BankAccountLike {
  bankName: string;
  accountName?: string;
  accountNoMasked: string;
  /** 1-对公、2-对私 */
  accountType?: number;
  currencyCode?: string;
}

/** 纯文字：选项的 title、读屏、测试用 */
export const bankAccountText = (
  a: BankAccountLike,
  opts: { name?: boolean; currency?: boolean } = {},
) =>
  [
    opts.currency && a.currencyCode,
    opts.name && a.accountName
      ? `${a.bankName} · ${a.accountName}`
      : a.bankName,
    // lint-ui-ignore bank-label: 这里就是公共组件的纯文字版本（title、读屏用）
    `${a.accountNoMasked}${a.accountType === 2 ? '（对私）' : ''}`,
  ]
    .filter(Boolean)
    .join(' · ');

/**
 * 银行账户选项：银行名（含户名）放不下时省略，账号尾号与「对私」、右侧提示（默认、事业部）始终完整显示。
 * 原来整串拼成一行，长银行名把尾号挤出去、或压在右侧提示上（UI-107）。下拉选项和选中后的框里共用。
 */
export const BankAccountLabel: React.FC<{
  account: BankAccountLike;
  /** 显示户名：付款、报销等要核对收款人的场景 */
  name?: boolean;
  /** 前面加币种：账户不按币种筛过时 */
  currency?: boolean;
  /** 右侧的次要提示，如「默认」、事业部名 */
  hint?: React.ReactNode;
}> = ({ account: a, name, currency, hint }) => {
  const { palette } = useAppTheme();
  const head = [
    currency && a.currencyCode,
    name && a.accountName ? `${a.bankName} · ${a.accountName}` : a.bankName,
  ]
    .filter(Boolean)
    .join(' · ');
  return (
    <span
      title={bankAccountText(a, { name, currency })}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 6,
        minWidth: 0,
        overflow: 'hidden',
      }}
    >
      <span
        style={{
          minWidth: 0,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {head}
      </span>
      <span className="num" style={{ flex: 'none', whiteSpace: 'nowrap' }}>
        {a.accountNoMasked}
        {a.accountType === 2 && '（对私）'}
      </span>
      {hint && (
        <span
          style={{
            flex: 'none',
            marginLeft: 'auto',
            paddingLeft: 8,
            fontSize: 12,
            color: palette.mute,
            whiteSpace: 'nowrap',
          }}
        >
          {hint}
        </span>
      )}
    </span>
  );
};

export default BankAccountLabel;
