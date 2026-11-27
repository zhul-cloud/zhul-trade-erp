import React from 'react';
import { AlertLabel } from '@/components/MetricCard';
import { Pill } from '@/pages/inquiry/shared/components';
import { useAppTheme } from '@/theme/AppTheme';
import { METHOD } from './service';

/** 应付单约定的付款方式（openspec payable-payment-method）：0-未定、1-对公、2-对私 */
export const PAY_METHOD = { UNDECIDED: 0, PUBLIC: 1, PRIVATE: 2 } as const;

export const PAY_METHOD_NAMES: Record<number, string> = {
  0: '未定',
  1: '对公',
  2: '对私',
};

/**
 * 付款方式、账户类型的属性色，全站同一个值同一种颜色（D75）：对公 / 公司对公蓝、对私 / 私对私紫、员工垫付洋红、未定灰
 */
export const PAY_WAY_TONE = {
  public: 'blue',
  private: 'purple',
  advance: 'magenta',
} as const;

/** 约定的付款方式：属性标签，对公蓝、对私紫、未定灰，同列不重复（D75，对比图 R34） */
export const PayMethodTag: React.FC<{ value?: number | null }> = ({
  value,
}) => {
  if (value === PAY_METHOD.PUBLIC)
    return <Pill tone={PAY_WAY_TONE.public}>对公</Pill>;
  if (value === PAY_METHOD.PRIVATE)
    return <Pill tone={PAY_WAY_TONE.private}>对私</Pill>;
  return <Pill tone="gray">未定</Pill>;
};

/**
 * 付款方式下方的一行提示（随所选应付单变化的结果，规范「说明文字」放字段下方）：
 * warn 为约定不一致、需要确认，用带图标的浅底警告标签（规范「警告不用纯文字」）；note 为预选说明（次要色）。
 * 单笔、批量登记付款共用。
 */
export const MethodHint: React.FC<{
  tone: 'warn' | 'note';
  children: React.ReactNode;
}> = ({ tone, children }) => {
  const { palette } = useAppTheme();
  return tone === 'warn' ? (
    <div role="status" style={{ marginTop: 6 }}>
      <AlertLabel tone="warning">{children}</AlertLabel>
    </div>
  ) : (
    <div style={{ fontSize: 12, marginTop: 6, color: palette.mute }}>
      {children}
    </div>
  );
};

/**
 * 按所选应付单的约定给出登记付款的预选：都对公 → 公司对公，都对私 → 私对私；
 * 不一致返回 mixed，没有可判断的（都未定或没选）两者都为空。
 */
export function suggestMethod(payMethods: (number | null | undefined)[]): {
  method?: number;
  mixed: boolean;
} {
  const known = [
    ...new Set(
      payMethods.filter(
        (m): m is number => m === PAY_METHOD.PUBLIC || m === PAY_METHOD.PRIVATE,
      ),
    ),
  ];
  if (known.length > 1) return { mixed: true };
  if (known.length === 0) return { mixed: false };
  return {
    method: known[0] === PAY_METHOD.PUBLIC ? METHOD.COMPANY : METHOD.PRIVATE,
    mixed: false,
  };
}

/** 实际选的付款方式与约定不一致时的提示；一致或约定未定时为空。员工垫付也是个人账户付款，按对私算 */
export function methodMismatch(
  method: number,
  payMethods: (number | null | undefined)[],
): string | undefined {
  const { method: agreed, mixed } = suggestMethod(payMethods);
  if (mixed || agreed === undefined) return undefined;
  const personal = method !== METHOD.COMPANY;
  if (agreed === METHOD.COMPANY && personal)
    return payMethods.length > 1
      ? '所选应付单约定对公支付'
      : '这张应付单约定对公支付';
  if (agreed === METHOD.PRIVATE && !personal)
    return payMethods.length > 1
      ? '所选应付单约定对私支付'
      : '这张应付单约定对私支付';
  return undefined;
}

/** 采购订单付款方式的默认值：线上店铺、不开票为对私，供应商开票为对公 */
export const defaultPayMethod = (shop: boolean, invoice: boolean) =>
  !shop && invoice ? PAY_METHOD.PUBLIC : PAY_METHOD.PRIVATE;
