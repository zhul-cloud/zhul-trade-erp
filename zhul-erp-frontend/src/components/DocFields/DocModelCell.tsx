import { WarningOutlined } from '@ant-design/icons';
import { Link, useAccess } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Tag, Tooltip } from 'antd';
import React from 'react';
import ViewMediaLink from '@/pages/operations/media/ViewMediaLink';
import { useAppTheme } from '@/theme/AppTheme';

/** 商品档案详情地址；从哪里点进来，由标签页的返回入口负责回去 */
export const productHref = (productId: number) =>
  `/product/products/${productId}`;

/** 候选商品标记：有候选审核权限的点开去候选池按商品编号查找 */
export const CandidateTag: React.FC<{ productId?: number | null }> = ({
  productId,
}) => {
  const access = useAccess() as Record<string, boolean>;
  const reviewable = !!productId && !!access['product:candidate:review'];
  return (
    <Tooltip title="还没审核建档的候选商品，审核通过后自动转正">
      <Tag color="orange" style={{ marginInlineStart: 6, fontWeight: 400 }}>
        {reviewable ? (
          <Link to={`/product/candidates?keyword=${productId}`}>候选</Link>
        ) : (
          '候选'
        )}
      </Tag>
    </Tooltip>
  );
};

/**
 * 单据型号单元格（询盘单、报价单、形式发票、销售订单共用）：
 * 型号（有商品档案且有商品档案菜单权限时可点，在当前标签打开商品档案）→ 客户型号 → 真实型号已更新的提示
 * → 订货号 → 查看素材 → 调用方追加的内容。
 */
const DocModelCell: React.FC<{
  model: string;
  brand?: string;
  orderCode?: string;
  productId?: number | null;
  /** 关联的是候选商品（还没审核建档）：型号不链到商品档案，后面标「候选」，有审核权限的点开去审核 */
  candidate?: boolean;
  /** 型号后面紧跟的内容，如停产替代 */
  suffix?: React.ReactNode;
  /** 客户询价时写的型号（与型号不同时后端才给） */
  customerModel?: string;
  /** 已发给客户或供应商的单据：真实型号已更新为这个型号 */
  updatedTo?: string;
  /** 提示后面的话，如「，请与客户确认」 */
  updatedTail?: string;
  /** 采购订单标红，其余用橙色 */
  updatedTone?: 'orange' | 'red';
  children?: React.ReactNode;
}> = ({
  model,
  brand,
  orderCode,
  productId,
  candidate,
  suffix,
  customerModel,
  updatedTo,
  updatedTail,
  updatedTone = 'orange',
  children,
}) => {
  const { palette } = useAppTheme();
  const access = useAccess() as Record<string, boolean>;
  const linkable = !!productId && !candidate && !!access.productList;
  const tone = updatedTone === 'red' ? palette.red : palette.orange;
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
        {candidate && <CandidateTag productId={productId} />}
        {suffix}
      </span>
      {customerModel && (
        <span style={{ color: palette.mute, fontSize: 12 }}>
          客户型号：{customerModel}
        </span>
      )}
      {updatedTo && (
        <span style={{ color: tone, fontSize: 12, fontWeight: 400 }}>
          <WarningOutlined style={{ marginRight: 4 }} />
          真实型号已更新为 <b>{updatedTo}</b>
          {updatedTail}
        </span>
      )}
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

/** 单据明细的描述（中文）：自动换行，最多 3 行，超出部分悬停看全文；没有时「—」 */
export const DocDescription: React.FC<{ text?: string }> = ({ text }) => {
  const { palette } = useAppTheme();
  if (!text) return <span style={{ color: palette.faint }}>—</span>;
  return (
    <Tooltip title={text} placement="topLeft">
      <span
        style={{
          color: palette.sub,
          lineHeight: 1.6,
          whiteSpace: 'normal',
          overflowWrap: 'anywhere',
          display: '-webkit-box',
          WebkitLineClamp: 3,
          WebkitBoxOrient: 'vertical',
          overflow: 'hidden',
        }}
      >
        {text}
      </span>
    </Tooltip>
  );
};

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
