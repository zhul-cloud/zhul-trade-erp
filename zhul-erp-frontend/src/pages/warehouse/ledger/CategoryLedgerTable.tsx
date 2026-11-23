import { RightOutlined } from '@ant-design/icons';
import type { TableColumnsType } from 'antd';
import { Table } from 'antd';
import React from 'react';
import { tableEmpty } from '@/components/EmptyState';
import { SignedMoney } from '@/pages/finance/costingParts';
import { useAppTheme } from '@/theme/AppTheme';
import { formatAmount } from '@/utils/format';
import { tableScrollX } from '@/utils/tableScroll';
import type { CategoryLedger, CategoryLedgerRow } from '../inventoryService';

/**
 * 库存类目账表格（spec warehouse/inventory「库存台账」，原型 29-库存三级账 L1、P1）：
 * 每个一级品类一行，表头按算式排；商品数与销售成本放在算式之后作参考；合计行即总账。
 * 库存台账页与库存月结详情共用。
 */
export const CategoryLedgerTable: React.FC<{
  data?: CategoryLedger;
  loading?: boolean;
  /** 合计行名称：库存台账页为「总账合计」，月结详情为「合计」 */
  totalLabel: string;
  /** 点品类：打开该品类的明细账 */
  onPick: (categoryId: number) => void;
}> = ({ data, loading, totalLabel, onPick }) => {
  const { palette } = useAppTheme();
  const withCogs = data?.total.cogsAmount != null;
  const money = (v: number) =>
    formatAmount(v ?? 0, 'CNY').replace(/^CNY\s/, '');
  const num = (v: React.ReactNode, strong?: boolean) => (
    <span
      style={{
        fontVariantNumeric: 'tabular-nums',
        fontWeight: strong ? 600 : undefined,
      }}
    >
      {v}
    </span>
  );
  const secondary = (v: React.ReactNode) => (
    <span style={{ color: palette.sub, fontVariantNumeric: 'tabular-nums' }}>
      {v}
    </span>
  );

  const columns: TableColumnsType<CategoryLedgerRow> = [
    {
      title: '一级品类',
      dataIndex: 'categoryName',
      width: 200,
      fixed: 'left',
      render: (v: string, r) => (
        <span
          style={{
            fontWeight: 600,
            color: r.categoryId === 0 ? palette.sub : palette.ink,
          }}
        >
          {v}
        </span>
      ),
    },
    {
      title: '期初金额',
      dataIndex: 'openingAmount',
      width: 140,
      align: 'right',
      render: (v: number) => num(money(v)),
    },
    {
      title: '+ 本月入库',
      dataIndex: 'inAmount',
      width: 140,
      align: 'right',
      render: (v: number) => num(money(v)),
    },
    {
      title: '− 本月出库',
      dataIndex: 'outAmount',
      width: 140,
      align: 'right',
      render: (v: number) => num(money(v)),
    },
    {
      title: '+ 在库成本调整',
      dataIndex: 'adjustAmount',
      width: 150,
      align: 'right',
      render: (v: number) => <SignedMoney value={v} />,
    },
    {
      title: '= 期末金额',
      dataIndex: 'closingAmount',
      width: 150,
      align: 'right',
      render: (v: number) => num(money(v), true),
    },
    {
      title: secondary('商品数'),
      dataIndex: 'productCount',
      width: 90,
      align: 'right',
      render: (v: number) => secondary(v),
    },
    ...(withCogs
      ? [
          {
            title: secondary('本月销售成本'),
            dataIndex: 'cogsAmount',
            width: 140,
            align: 'right' as const,
            render: (v: number) => secondary(money(v)),
          },
        ]
      : []),
    {
      key: 'go',
      width: 40,
      align: 'right',
      render: () => (
        <RightOutlined style={{ color: palette.mute, fontSize: 12 }} />
      ),
    },
  ];

  const t = data?.total;
  return (
    <Table<CategoryLedgerRow>
      rowKey={(r) => String(r.categoryId)}
      loading={loading}
      columns={columns}
      dataSource={data?.rows ?? []}
      pagination={false}
      scroll={{ x: tableScrollX(columns) }}
      locale={{ emptyText: tableEmpty('这个月没有库存', loading) }}
      onRow={(r) => ({
        onClick: () => onPick(r.categoryId ?? 0),
        style: { cursor: 'pointer' },
      })}
      summary={() =>
        t && (data?.rows.length ?? 0) > 0 ? (
          <Table.Summary.Row>
            <Table.Summary.Cell index={0}>
              <b>{totalLabel}</b>
            </Table.Summary.Cell>
            {(
              [
                ['opening', t.openingAmount],
                ['in', t.inAmount],
                ['out', t.outAmount],
              ] as const
            ).map(([k, v], i) => (
              <Table.Summary.Cell key={k} index={i + 1} align="right">
                {num(money(v), true)}
              </Table.Summary.Cell>
            ))}
            <Table.Summary.Cell index={4} align="right">
              <b>
                <SignedMoney value={t.adjustAmount} />
              </b>
            </Table.Summary.Cell>
            <Table.Summary.Cell index={5} align="right">
              <b style={{ fontSize: 15 }}>
                {num(money(t.closingAmount), true)}
              </b>
            </Table.Summary.Cell>
            <Table.Summary.Cell index={6} align="right">
              {secondary(t.productCount)}
            </Table.Summary.Cell>
            {withCogs && (
              <Table.Summary.Cell index={7} align="right">
                {secondary(money(t.cogsAmount ?? 0))}
              </Table.Summary.Cell>
            )}
            <Table.Summary.Cell index={withCogs ? 8 : 7} />
          </Table.Summary.Row>
        ) : null
      }
    />
  );
};
