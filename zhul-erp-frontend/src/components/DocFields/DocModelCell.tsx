import { Link, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import React from 'react';
import ViewMediaLink from '@/pages/operations/media/ViewMediaLink';
import { useAppTheme } from '@/theme/AppTheme';

/** 商品档案详情地址；从哪里点进来，由标签页的返回入口负责回去 */
export const productHref = (productId: number) =>
  `/product/products/${productId}`;

/**
 * 单据型号单元格（询盘单、报价单、形式发票、销售订单共用）：
 * 型号（有商品档案且有商品档案菜单权限时可点，在当前标签打开商品档案）→ 订货号 → 查看素材 → 调用方追加的内容。
 */
const DocModelCell: React.FC<{
  model: string;
  brand?: string;
  orderCode?: string;
  productId?: number | null;
  /** 型号后面紧跟的内容，如停产替代 */
  suffix?: React.ReactNode;
  children?: React.ReactNode;
}> = ({ model, brand, orderCode, productId, suffix, children }) => {
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const linkable = !!productId && !!access.productList;
  return (
    <span
      style={{ display: 'inline-flex', flexDirection: 'column', minWidth: 0 }}
    >
      <span style={{ fontWeight: 600, color: palette.ink }}>
        {linkable ? (
          <Link to={productHref(productId as number)}>{model}</Link>
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

/** 品牌、品类列需要的字段 */
export interface DocItemMeta {
  brand?: string;
  category?: string;
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
