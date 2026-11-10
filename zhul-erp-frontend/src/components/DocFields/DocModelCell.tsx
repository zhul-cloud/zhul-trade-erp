import { Link, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import React from 'react';
import ViewMediaLink from '@/pages/operations/media/ViewMediaLink';
import { useAppTheme } from '@/theme/AppTheme';

/** 从单据进入商品档案后要回到的地方，如 { path: '/quotation/12', label: '报价单 QT20261105001' } */
export interface DocFrom {
  path: string;
  label: string;
}

/** 商品档案详情地址；带上原单据，商品详情页据此显示「返回 报价单 QT…」 */
export const productHref = (productId: number, from?: DocFrom) => {
  const base = `/product/products/${productId}`;
  if (!from) return base;
  const q = new URLSearchParams({ from: from.path, fromLabel: from.label });
  return `${base}?${q.toString()}`;
};

/** 只接受站内路径，避免返回参数被用来跳到站外 */
export const safeFromPath = (path?: string | null) =>
  path?.startsWith('/') && !path.startsWith('//') ? path : undefined;

/**
 * 单据型号单元格（询盘单、报价单、形式发票、销售订单共用）：
 * 型号（有商品档案且有商品档案菜单权限时可点，在当前页打开商品档案）→ 订货号 → 查看素材 → 调用方追加的内容。
 */
const DocModelCell: React.FC<{
  model: string;
  brand?: string;
  orderCode?: string;
  productId?: number | null;
  from?: DocFrom;
  /** 型号后面紧跟的内容，如停产替代 */
  suffix?: React.ReactNode;
  children?: React.ReactNode;
}> = ({ model, brand, orderCode, productId, from, suffix, children }) => {
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const linkable = !!productId && !!access.productList;
  return (
    <span
      style={{ display: 'inline-flex', flexDirection: 'column', minWidth: 0 }}
    >
      <span style={{ fontWeight: 600, color: palette.ink }}>
        {linkable ? (
          <Link to={productHref(productId as number, from)}>{model}</Link>
        ) : (
          model
        )}
        {suffix}
      </span>
      {orderCode && (
        <span style={{ color: palette.mute, fontSize: 12 }}>
          订货号 {orderCode}
        </span>
      )}
      {children}
      <ViewMediaLink brand={brand} model={model} />
    </span>
  );
};

export default DocModelCell;

/** 品牌、品类、来源询盘列需要的字段 */
export interface DocItemMeta {
  brand?: string;
  category?: string;
  inquiryCode?: string;
  customerInquiryId?: number;
}

const BrandText: React.FC<{ text?: string }> = ({ text }) => {
  const { palette } = useAppTheme();
  return (
    <span style={{ fontWeight: 600, color: palette.ink }}>{text || '—'}</span>
  );
};

/** 单据明细的品牌、品类两列（放在型号列之前），每行都显示 */
export const docBrandColumns = <T,>(
  get: (r: T) => DocItemMeta | undefined,
): TableColumnsType<T> => [
  {
    title: '品牌',
    key: 'brand',
    width: 110,
    render: (_, r) => <BrandText text={get(r)?.brand} />,
  },
  {
    title: '品类',
    key: 'category',
    width: 110,
    render: (_, r) => get(r)?.category || '—',
  },
];

/** 单据明细的「来源询盘」列（放在型号列之后），可点击打开询盘单 */
export const docInquiryColumn = <T,>(
  get: (r: T) => DocItemMeta | undefined,
): TableColumnsType<T>[number] => ({
  title: '来源询盘',
  key: 'inquiry',
  width: 140,
  render: (_, r) => {
    const m = get(r);
    return m?.inquiryCode && m.customerInquiryId ? (
      <Link to={`/inquiry/customer-inquiries/${m.customerInquiryId}`}>
        {m.inquiryCode}
      </Link>
    ) : (
      '—'
    );
  },
});
