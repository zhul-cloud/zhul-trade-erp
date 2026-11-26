import React from 'react';
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

/** 约定的付款方式：属性用中性灰标签（ui-redesign S13），未定用浅色文字 */
export const PayMethodTag: React.FC<{ value?: number | null }> = ({
  value,
}) => {
  const { palette } = useAppTheme();
  if (value === PAY_METHOD.PUBLIC || value === PAY_METHOD.PRIVATE)
    return <Pill tone="gray">{PAY_METHOD_NAMES[value]}</Pill>;
  return <span style={{ color: palette.mute }}>未定</span>;
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
