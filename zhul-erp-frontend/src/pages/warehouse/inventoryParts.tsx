import { Link } from '@umijs/max';
import { type App, Form, type FormInstance } from 'antd';
import React, { useEffect, useState } from 'react';
import { FilterCard as ListFilterCard } from '@/components/ListLayout';
import { Pill } from '@/pages/inquiry/shared/components';
import { useAppTheme } from '@/theme/AppTheme';
import { attrTone } from '@/theme/palette';
import {
  batchHref,
  type InvWarehouse,
  WH_OWN,
  warehouseApi,
} from './inventoryService';

/** 库存页面共用的单元格与筛选（openspec inventory-foundation） */

/** 型号（等宽加粗）+ 品牌 · 品类 */
export const ModelCell: React.FC<{
  model?: string;
  brand?: string;
  category?: string;
}> = ({ model, brand, category }) => {
  const { palette } = useAppTheme();
  return (
    <div>
      <b
        style={{
          color: palette.ink,
          fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
        }}
      >
        {model || '—'}
      </b>
      <div style={{ fontSize: 12, color: palette.mute }}>
        {[brand, category].filter(Boolean).join(' · ') || '—'}
      </div>
    </div>
  );
};

/** 流水的业务类型：有显示文字（如「其他出库 · 报废」）用它；反向流水只显示反向的业务名（如「作废其他出库」） */
export const bizText = (m: {
  bizName: string;
  bizLabel?: string;
  reversal?: boolean;
}) => (m.reversal || !m.bizLabel ? m.bizName : m.bizLabel);

/** 仓库类型的属性色：自有仓、货代仓全站同色（D75） */
export const warehouseTypeTone = (type?: number) =>
  attrTone(type === WH_OWN ? 0 : 1);

/** 仓库名 + 自有仓 / 货代仓 */
export const WarehouseCell: React.FC<{ name?: string; type?: number }> = ({
  name,
  type,
}) => (
  <div>
    <div>{name || '—'}</div>
    {type != null && (
      <Pill tone={warehouseTypeTone(type)}>
        {type === WH_OWN ? '自有仓' : '货代仓'}
      </Pill>
    )}
  </div>
);

/** 批次号：采购批次、退货批次链到来源单据 */
export const BatchLink: React.FC<{
  batchNo?: string;
  sourceType?: number;
  sourceId?: number;
}> = ({ batchNo, sourceType, sourceId }) => {
  const href = batchHref(sourceType, sourceId);
  return href ? (
    <Link to={href} className="zhul-link zhul-link-doc">
      {batchNo}
    </Link>
  ) : (
    <span>{batchNo}</span>
  );
};

/** 状态：合格绿、不良红 */
export const StockStatusPill: React.FC<{ status: number; name?: string }> = ({
  status,
  name,
}) => (
  <Pill tone={status === 1 ? 'green' : 'red'}>
    {name ?? (status === 1 ? '合格' : '不良')}
  </Pill>
);

/** 数量：正数绿色带 +，负数红色 */
export const SignedQty: React.FC<{ value: number }> = ({ value }) => {
  const { palette } = useAppTheme();
  return (
    <b
      style={{
        color:
          value > 0 ? palette.green : value < 0 ? palette.red : palette.mute,
        fontVariantNumeric: 'tabular-nums',
      }}
    >
      {value > 0 ? `+${value}` : value < 0 ? `−${-value}` : '0'}
    </b>
  );
};

/** 仓库列表（筛选下拉用；停用的仓库也列出，标「停用」） */
export const useWarehouses = () => {
  const [list, setList] = useState<InvWarehouse[]>([]);
  useEffect(() => {
    warehouseApi
      .list()
      .then(setList)
      .catch(() => setList([]));
  }, []);
  return list;
};

/** 仓库筛选：全部 / 公司仓库 / 全部货代仓 / 某个货代仓 */
export type WarehouseFilter = 'all' | 'own' | 'forwarders' | number;

export const warehouseFilterOptions = (list: InvWarehouse[]) => [
  { value: 'all', label: '全部仓库' },
  { value: 'own', label: '公司仓库' },
  { value: 'forwarders', label: '全部货代仓' },
  ...list
    .filter((w) => w.type !== WH_OWN)
    .map((w) => ({
      value: w.id,
      label: w.status === 1 ? w.name : `${w.name}（停用）`,
    })),
];

export const warehouseQuery = (f: WarehouseFilter) =>
  typeof f === 'number'
    ? { warehouseId: f }
    : f === 'all'
      ? {}
      : { warehouseScope: f };

/**
 * 筛选卡片：库存各页的表单写法套用 ListLayout 的搜索条件区（标签在上、右侧重置 / 查询）。
 * 「查询」不是提交按钮，回车查询由各页的关键词输入框 onPressEnter 调 form.submit()
 */
export const FilterCard: React.FC<{
  form: FormInstance;
  initialValues: object;
  onSearch: (v: Record<string, unknown>) => void;
  onReset: () => void;
  children: React.ReactNode;
  /** 还没挪到主卡操作行的快捷筛选（新页面用 MainCard 的 quick、chips） */
  extra?: React.ReactNode;
  more?: React.ReactNode;
  moreCount?: number;
}> = ({
  form,
  initialValues,
  onSearch,
  onReset,
  children,
  extra,
  more,
  moreCount,
}) => (
  <Form form={form} initialValues={initialValues} onFinish={onSearch}>
    <ListFilterCard
      onSearch={() => form.submit()}
      onReset={() => {
        form.resetFields();
        onReset();
      }}
      quick={extra}
      more={more}
      moreCount={moreCount}
    >
      {children}
    </ListFilterCard>
  </Form>
);

/**
 * 库存模式下库存不够（错误码 STOCK_SHORTAGE，原型 I8）：弹窗逐行显示缺多少，返回 true；
 * 其他错误返回 false，由调用方照常提示
 */
export const showStockShortage = (
  modal: ReturnType<typeof App.useApp>['modal'],
  err: { errorCode?: string; detail?: Record<string, unknown> },
  action: '交货代' | '出运',
) => {
  if (err.errorCode !== 'STOCK_SHORTAGE') return false;
  const detail = err.detail as
    | { docNo?: string; items?: { model: string; shortage: number }[] }
    | undefined;
  modal.warning({
    title: `不能${action}`,
    okText: '知道了',
    width: 520,
    content: (
      <div>
        <div style={{ marginBottom: 12 }}>
          库存模式下，{action === '交货代' ? '公司仓库' : '货代仓'}
          的库存不够这次{action}
        </div>
        <div style={{ display: 'grid', gap: 8 }}>
          {(detail?.items ?? []).map((i) => (
            <div
              key={i.model}
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                gap: 12,
                padding: '8px 12px',
                borderRadius: 10,
                border: '1px solid var(--z-hairline)',
              }}
            >
              <span>
                <b style={{ fontFamily: 'ui-monospace, Menlo, monospace' }}>
                  {i.model}
                </b>
                {detail?.docNo ? (
                  <span style={{ color: 'var(--z-mute)' }}>
                    {' '}
                    · {detail.docNo}
                  </span>
                ) : null}
              </span>
              <b style={{ color: 'var(--z-red)' }}>缺 {i.shortage}</b>
            </div>
          ))}
        </div>
        <div style={{ marginTop: 12, color: 'var(--z-mute)' }}>
          {action === '交货代'
            ? '先完成采购入库，或在仓库档案把库存模式改回「无货源」（库存不够时自动补系统外入库）。'
            : '先核对货代仓的库存，或在仓库档案把库存模式改回「无货源」。'}
        </div>
      </div>
    ),
  });
  return true;
};
